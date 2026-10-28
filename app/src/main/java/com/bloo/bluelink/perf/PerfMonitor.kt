package com.bloo.bluelink.perf

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.core.content.ContextCompat
import com.bloo.bluelink.data.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * The app's own performance instrumentation, so a log or a copied report says where the time went without a profiler.
 *
 * Every frame's cost comes from the platform (animation/composition, layout, draw, sync, GPU...) and is filed under what the user was
 * doing at the time ([scope]) or the screen they were on. A watchdog samples the main thread's stack whenever it stalls, so a stall
 * names the code that was running. Composables count their recompositions ([TrackRecompositions]), code can time a stretch of work
 * ([span]) and count events ([count]). A short line goes to the activity log every few seconds while frames are being missed, and
 * [report] writes the whole picture for the Debug card's Copy button.
 *
 * It is cheap by design: the frame listener runs on its own thread, the watchdog wakes twenty times a second, and the counters are
 * plain atomics.
 */
object PerfMonitor {
    private const val WINDOW_MS = 5_000L
    private const val STALL_MS = 60L
    private const val SLOW_FRAME_LOG_MS = 100f
    private const val KEEP = 30
    private const val RECENT_EVENTS = 200
    private const val PROBE_MS = 2_000L

    private val clock = ScopeClock()
    private val stats = ConcurrentHashMap<String, ScopeStats>()
    // Frames arrive and the window is summarised on the same (perf) thread, so this needs no lock.
    private var window = ScopeStats()
    private val worstFrames = ArrayDeque<String>()
    private val stalls = ArrayDeque<String>()
    private val spans = ConcurrentHashMap<String, SpanStats>()
    private val recompositions = ConcurrentHashMap<String, AtomicLong>()
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    private val lastRecomp = HashMap<String, Long>()
    private val lastCount = HashMap<String, Long>()
    // The recent story: screens, scopes, power, thermal and battery changes, newest last.
    private val recentEvents = ArrayDeque<String>()
    // How long the main thread, and the two shared pools, take to answer a request. Reset each window.
    private val mainPost = LatencyStats()
    private val defaultPool = LatencyStats()
    private val ioPool = LatencyStats()
    private val probes = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var probeJob: Job? = null
    private var device: Context? = null
    private var thermalListener: PowerManager.OnThermalStatusChangedListener? = null
    /** The manager the listener was added to: removal has to go back to the same one. */
    private var thermalPower: PowerManager? = null
    private var powerReceiver: BroadcastReceiver? = null
    private var lastBatteryBucket = -2
    private var lastCharging: Boolean? = null
    private var lastScreen = "start"

    private var budgetMs = 16.7f
    private var startedAt = SystemClock.uptimeMillis()
    private var windowDropped = 0
    private var windowStalls = 0
    private var windowStallMs = 0L
    private var lastGc = GcSample.read()
    private var lastCpuMs = 0L
    private var lastCpuAt = 0L
    private var peakHeapMb = 0L
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private var attachedTo: Window? = null
    private val watching = AtomicBoolean(false)
    private var watchdog: Thread? = null
    private var windowTick: Runnable? = null

    /** Starts watching [activity]'s window. Idempotent; undone by [uninstall]. */
    @Synchronized fun install(activity: Activity) {
        if (attachedTo != null) return
        @Suppress("DEPRECATION")
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display else activity.windowManager.defaultDisplay
        budgetMs = 1000f / (display?.refreshRate ?: 60f).coerceAtLeast(30f)
        val t = HandlerThread("bloo-perf").also { it.start() }
        val h = Handler(t.looper)
        thread = t
        handler = h
        val l = Window.OnFrameMetricsAvailableListener { _, metrics, dropped -> onFrame(metrics, dropped) }
        listener = l
        activity.window.addOnFrameMetricsAvailableListener(l, h)
        attachedTo = activity.window
        startedAt = SystemClock.uptimeMillis()
        val tick = object : Runnable {
            override fun run() {
                summarise()
                h.postDelayed(this, WINDOW_MS)
            }
        }
        windowTick = tick
        h.postDelayed(tick, WINDOW_MS)
        val app = activity.applicationContext
        device = app
        PerfFile.init(app)
        PerfDevice.header(app, budgetMs).forEach { PerfFile.append("HEADER $it") }
        PerfFile.append("HEADER log ${PerfFile.path()}")
        AppLog.log("perf: monitor on (display ${"%.0f".format(1000f / budgetMs)} Hz, frame budget ${fmt(budgetMs)} ms); log ${PerfFile.path()}")
        event("install", "frame budget ${fmt(budgetMs)} ms")
        registerDeviceListeners(activity, h)
        startProbes()
    }

    /** Thermal, power-save and battery changes, as events: they explain a sudden change in the frames that follows them. */
    private fun registerDeviceListeners(activity: Activity, h: Handler) {
        val power = activity.getSystemService(PowerManager::class.java)
        if (power != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val l = PowerManager.OnThermalStatusChangedListener { status ->
                event("thermal", "now ${PerfDevice.thermalName(power)} (status $status)")
            }
            thermalListener = l
            thermalPower = power
            power.addThermalStatusListener(Executor { h.post(it) }, l)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
                        val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                        val charging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == android.os.BatteryManager.BATTERY_STATUS_FULL
                        val bucket = percent / 5
                        if (bucket != lastBatteryBucket || charging != lastCharging) {
                            lastBatteryBucket = bucket
                            lastCharging = charging
                            event("battery", "$percent% ${if (charging) "charging" else "on battery"}")
                        }
                    }
                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                        event("power-save", "${activity.getSystemService(PowerManager::class.java)?.isPowerSaveMode}")
                    }
                }
            }
        }
        powerReceiver = receiver
        ContextCompat.registerReceiver(
            activity, receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Probes the main thread and both shared pools every two seconds: a pool that is busy shows here before a frame does. */
    private fun startProbes() {
        probeJob?.cancel()
        probeJob = probes.launch {
            while (isActive) {
                probe(Dispatchers.Default, defaultPool)
                probe(Dispatchers.IO, ioPool)
                delay(PROBE_MS)
            }
        }
    }

    private suspend fun probe(dispatcher: CoroutineDispatcher, into: LatencyStats) {
        val t0 = System.nanoTime()
        withContext(dispatcher) { }
        into.add((System.nanoTime() - t0) / 1_000_000f)
    }

    /** The watchdog wakes twenty times a second, so it runs only while the app is on screen. */
    @Synchronized fun foreground(on: Boolean) {
        if (attachedTo == null) return
        event(if (on) "foreground" else "background")
        if (on) {
            startWatchdog()
        } else {
            watching.set(false)
            watchdog?.interrupt()
            watchdog = null
        }
    }

    @Synchronized fun uninstall() {
        val w = attachedTo ?: return
        listener?.let { w.removeOnFrameMetricsAvailableListener(it) }
        attachedTo = null
        listener = null
        watching.set(false)
        watchdog?.interrupt()
        watchdog = null
        windowTick?.let { handler?.removeCallbacks(it) }
        probeJob?.cancel()
        probeJob = null
        // Best effort: a listener the system no longer holds must not take the activity down with it.
        thermalListener?.let { l -> runCatching { thermalPower?.removeThermalStatusListener(l) } }
        thermalListener = null
        thermalPower = null
        powerReceiver?.let { r -> runCatching { device?.unregisterReceiver(r) } }
        powerReceiver = null
        event("uninstall")
        thread?.quitSafely()
        thread = null
        handler = null
    }

    // --- Labels ---------------------------------------------------------------------------------------------

    /** The screen now showing; frames with no [scope] running are filed under it. */
    fun screen(name: String) {
        if (name == lastScreen) return
        lastScreen = name
        clock.screen = name
        event("screen", name)
    }

    /** For the next [durationMs], frames are filed under [label] (on top of the screen): "map-open", "garage-swipe". */
    fun scope(label: String, durationMs: Long = 1_000L) {
        clock.begin(label, durationMs, System.nanoTime())
        event("scope", "$label for ${durationMs} ms")
    }

    /**
     * Writes one line of the story to the performance file and the in-app ring: what happened, and when (uptime since install).
     * Use it for a discrete moment (a screen, a toggle, a request starting) so the frames that follow can be read against it.
     */
    fun event(name: String, detail: String = "") {
        val line = "${uptime()} EVENT $name${if (detail.isEmpty()) "" else " $detail"}"
        synchronized(recentEvents) { recentEvents.addLast(line); while (recentEvents.size > RECENT_EVENTS) recentEvents.removeFirst() }
        PerfFile.append(line)
    }

    // --- Counting -------------------------------------------------------------------------------------------

    /** A composable recomposed; called by [TrackRecompositions]. */
    fun recomposed(name: String) { recompositions.getOrPut(name) { AtomicLong() }.incrementAndGet() }

    /** An event happened [n] times (a state emission, a cache miss, a decode). */
    fun count(name: String, n: Long = 1) { counters.getOrPut(name) { AtomicLong() }.addAndGet(n) }

    /** Runs [block] and records how long it took under [name]; a run over [thresholdMs] on the main thread is logged as it happens. */
    inline fun <T> span(name: String, thresholdMs: Long = 8, block: () -> T): T {
        val t0 = SystemClock.elapsedRealtimeNanos()
        try {
            return block()
        } finally {
            spanDone(name, (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000f, thresholdMs)
        }
    }

    @PublishedApi internal fun spanDone(name: String, ms: Float, thresholdMs: Long) {
        spans.getOrPut(name) { SpanStats() }.add(ms)
        if (ms >= thresholdMs && Looper.myLooper() == Looper.getMainLooper()) {
            val line = "${uptime()} SPAN $name ${fmt(ms)} ms on main"
            PerfFile.append(line)
            AppLog.log("perf span $name ${fmt(ms)} ms on main")
        }
    }

    // --- Frames ---------------------------------------------------------------------------------------------

    private fun onFrame(m: FrameMetrics, dropped: Int) {
        fun ms(metric: Int) = m.getMetric(metric) / 1_000_000f
        val gpu = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) ms(FrameMetrics.GPU_DURATION) else 0f
        val cost = FrameCost(
            totalMs = ms(FrameMetrics.TOTAL_DURATION),
            inputMs = ms(FrameMetrics.INPUT_HANDLING_DURATION),
            animationMs = ms(FrameMetrics.ANIMATION_DURATION),
            layoutMs = ms(FrameMetrics.LAYOUT_MEASURE_DURATION),
            drawMs = ms(FrameMetrics.DRAW_DURATION),
            syncMs = ms(FrameMetrics.SYNC_DURATION),
            commandMs = ms(FrameMetrics.COMMAND_ISSUE_DURATION),
            swapMs = ms(FrameMetrics.SWAP_BUFFERS_DURATION),
            gpuMs = gpu,
            // Late to start: the unexplained delay, and how long after its vsync the frame actually began.
            delayMs = ms(FrameMetrics.UNKNOWN_DELAY_DURATION) + (m.getMetric(FrameMetrics.VSYNC_TIMESTAMP) - m.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)) / 1_000_000f,
        )
        // The first frame after the window appears is always expensive and says nothing about the app.
        if (m.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L) return
        val label = clock.labelAt(m.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP))
        stats.getOrPut(label) { ScopeStats() }.add(cost, budgetMs)
        window.add(cost, budgetMs)
        windowDropped += dropped
        if (cost.totalMs >= SLOW_FRAME_LOG_MS) {
            val line = "${uptime()} [$label] ${fmt(cost.totalMs)} ms, mostly ${cost.worstPhase()}: ${cost.phases()}"
            synchronized(worstFrames) { worstFrames.addLast(line); while (worstFrames.size > KEEP) worstFrames.removeFirst() }
            PerfFile.append("SLOW $line")
            AppLog.log("perf slow frame $line")
        }
    }

    // --- The five-second line ------------------------------------------------------------------------------

    private fun summarise() {
        val snapshot = window
        val frames = snapshot.frames
        val gc = GcSample.read()
        val gcDelta = gc - lastGc
        lastGc = gc
        val heapMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
        if (heapMb > peakHeapMb) peakHeapMb = heapMb
        val recomp = delta(recompositions, lastRecomp)
        val events = delta(counters, lastCount)
        val seconds = WINDOW_MS / 1000.0
        val busyPct = if (frames > 0) snapshot.totalMs / WINDOW_MS * 100.0 else 0.0
        val allocMbPerSec = gcDelta.allocatedMb / seconds
        val pssMb = memoryPssMb()
        // Process CPU over this window, as a share of wall time (a core is 100%), and the native heap the Java counts miss.
        val now = SystemClock.uptimeMillis()
        val cpuMs = android.os.Process.getElapsedCpuTime()
        val cpuPct = if (lastCpuAt > 0L) (cpuMs - lastCpuMs) * 100.0 / (now - lastCpuAt).coerceAtLeast(1L) else 0.0
        lastCpuMs = cpuMs
        lastCpuAt = now
        val nativeMb = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        val label = clock.labelAt(System.nanoTime())
        val line = buildString {
            append("WINDOW ${WINDOW_MS / 1000}s [$label] $frames frames, ${snapshot.missed} missed (${snapshot.missedPercent()}%), ")
            append("busy ${"%.0f".format(busyPct)}%, p50 ${fmt(snapshot.percentile(0.5))} p95 ${fmt(snapshot.percentile(0.95))} ")
            append("p99 ${fmt(snapshot.percentile(0.99))} worst ${fmt(snapshot.worstMs)} ms")
            if (windowDropped > 0) append(", $windowDropped dropped")
            if (frames > 0) append(" | avg ms: ${snapshot.avgPhases()} | distribution ${snapshot.distribution()}")
            append(" | gc ${gcDelta.count} (blocking ${gcDelta.blocking}, ${gcDelta.timeMs} ms), alloc ${"%.1f".format(allocMbPerSec)} MB/s")
            append(", heap $heapMb MB, native $nativeMb MB, pss $pssMb MB, threads ${threadCount()}, cpu ${"%.0f".format(cpuPct)}%")
            append(" | main-post ${mainPost.line()} | pool-default ${defaultPool.line()} | pool-io ${ioPool.line()}")
            if (windowStalls > 0) append(" | stalls $windowStalls (${windowStallMs} ms)")
            if (recomp.isNotEmpty()) append(" | recomposed/s: ${top(recomp, seconds)}")
            if (events.isNotEmpty()) append(" | events/s: ${top(events, seconds)}")
        }
        PerfFile.append(line)
        // The in-app log gets a line only when something was wrong, so the activity log keeps its room for the rest of the app.
        if (frames > 0 && (snapshot.missed > 0 || windowStalls > 0 || gcDelta.blocking > 0)) AppLog.log("perf $line")
        mainPost.reset(); defaultPool.reset(); ioPool.reset()
        resetWindow()
    }

    private fun resetWindow() {
        window = ScopeStats()
        windowDropped = 0
        windowStalls = 0
        windowStallMs = 0
    }

    private fun delta(now: ConcurrentHashMap<String, AtomicLong>, last: HashMap<String, Long>): Map<String, Long> {
        val out = HashMap<String, Long>()
        for ((k, v) in now) {
            val total = v.get()
            val d = total - (last[k] ?: 0L)
            if (d > 0) out[k] = d
            last[k] = total
        }
        return out
    }

    private fun top(m: Map<String, Long>, seconds: Double, n: Int = 6): String =
        m.entries.sortedByDescending { it.value }.take(n).joinToString(" ") { "${it.key}×${"%.1f".format(it.value / seconds)}" }

    /** Resident memory the app holds (proportional set size), in MB: what the system charges the app for, not just the Java heap. */
    private fun memoryPssMb(): Long = runCatching {
        val info = android.os.Debug.MemoryInfo()
        android.os.Debug.getMemoryInfo(info)
        info.totalPss / 1024L
    }.getOrDefault(0L)

    // --- Stalls ---------------------------------------------------------------------------------------------

    private fun startWatchdog() {
        if (!watching.compareAndSet(false, true)) return
        val main = Looper.getMainLooper()
        val mainHandler = Handler(main)
        val t = Thread({
            while (watching.get()) {
                val posted = SystemClock.uptimeMillis()
                val acked = AtomicBoolean(false)
                mainHandler.post { acked.set(true) }
                val samples = ArrayList<List<StackTraceElement>>()
                var waited = 0L
                try {
                    while (!acked.get() && waited < 3_000 && watching.get()) {
                        Thread.sleep(25)
                        waited += 25
                        // A sample every 50 ms of the stall: what the main thread is doing while it is not drawing.
                        if (waited >= STALL_MS - 10 && (waited % 50L == 0L || samples.isEmpty())) samples += main.thread.stackTrace.toList()
                    }
                    val took = SystemClock.uptimeMillis() - posted
                    mainPost.add(took.toFloat())
                    if (took >= STALL_MS && samples.isNotEmpty()) recordStall(took, samples)
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                    return@Thread
                }
            }
        }, "bloo-perf-watchdog")
        t.isDaemon = true
        watchdog = t
        t.start()
    }

    private fun recordStall(tookMs: Long, samples: List<List<StackTraceElement>>) {
        val label = clock.labelAt(System.nanoTime())
        val where = StallStacks.describe(samples)
        val line = "${uptime()} [$label] main blocked ${tookMs} ms in $where"
        synchronized(stalls) { stalls.addLast(line); while (stalls.size > KEEP) stalls.removeFirst() }
        windowStalls++
        windowStallMs += tookMs
        PerfFile.append("STALL $line")
        AppLog.log("perf STALL $line")
    }

    // --- The report -----------------------------------------------------------------------------------------

    private fun uptime(): String = "+${(SystemClock.uptimeMillis() - startedAt) / 1000}s"

    private fun threadCount(): Int = runCatching {
        java.io.File("/proc/self/status").useLines { lines -> lines.firstOrNull { it.startsWith("Threads:") }?.substringAfter(':')?.trim()?.toInt() }
    }.getOrNull() ?: 0

    /** Everything seen since the monitor started (or since [reset]), as text for the Debug card's Copy button. */
    /** The report, then every timeline line still kept on the device: what Save writes out. */
    fun saveableReport(): String = buildString {
        append(report())
        appendLine()
        appendLine("-- Timeline: every line still kept, oldest first --")
        append(PerfFile.readAll())
    }

    fun report(): String = buildString {
        val gc = GcSample.read()
        appendLine("== Bloo performance report ==")
        device?.let { d -> PerfDevice.header(d, budgetMs).forEach { appendLine(it) } }
        appendLine("Log file: ${PerfFile.path() ?: "not started"}")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}, ${"%.0f".format(1000f / budgetMs)} Hz (budget ${fmt(budgetMs)} ms)")
        appendLine("Watching for ${(SystemClock.uptimeMillis() - startedAt) / 1000} s. Heap now ${(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)} MB, peak seen $peakHeapMb MB, threads ${threadCount()}.")
        appendLine("GC since process start: ${gc.count} runs, ${gc.blocking} blocking, ${gc.timeMs} ms, ${gc.allocatedMb} MB allocated.")
        appendLine("Memory: pss ${memoryPssMb()} MB now, native heap ${Debug.getNativeHeapAllocatedSize() / (1024 * 1024)} MB.")
        val cpuSinceStart = android.os.Process.getElapsedCpuTime()
        val wallSinceStart = (SystemClock.uptimeMillis() - startedAt).coerceAtLeast(1L)
        appendLine("CPU: ${cpuSinceStart} ms of process CPU since process start (${"%.0f".format(cpuSinceStart * 100.0 / wallSinceStart)}% of wall time, a core is 100%).")
        appendLine()
        appendLine("-- Frames by what was happening (frames, missed %, >32 ms, >100 ms, p50 / p95 / worst ms; average ms per phase) --")
        stats.entries.sortedByDescending { it.value.frames }.forEach { (label, s) ->
            appendLine("$label: ${s.frames} frames, ${s.missedPercent()}% missed, ${s.over32} over 32, ${s.over100} over 100, p50 ${fmt(s.percentile(0.5))} p95 ${fmt(s.percentile(0.95))} worst ${fmt(s.worstMs)}")
            appendLine("    avg ${fmt(s.avgTotal())} ms = ${s.avgPhases()}")
            appendLine("    distribution: ${s.distribution()}")
            s.worstCost?.let { appendLine("    worst ${fmt(it.totalMs)} ms, mostly ${it.worstPhase()}: ${it.phases()}") }
        }
        appendLine()
        appendLine("-- Slowest frames (over ${SLOW_FRAME_LOG_MS.toInt()} ms) --")
        synchronized(worstFrames) { if (worstFrames.isEmpty()) appendLine("none") else worstFrames.forEach { appendLine(it) } }
        appendLine()
        appendLine("-- Main-thread stalls (over $STALL_MS ms), with the code that was running --")
        synchronized(stalls) { if (stalls.isEmpty()) appendLine("none") else stalls.forEach { appendLine(it) } }
        appendLine()
        appendLine("-- Timed spans (count, total, average, worst ms) --")
        if (spans.isEmpty()) appendLine("none") else spans.entries.sortedByDescending { it.value.totalMs }.forEach { (n, s) -> appendLine("$n: ${s.count} runs, ${fmt(s.totalMs)} total, ${fmt(s.totalMs / s.count)} avg, ${fmt(s.worstMs)} worst") }
        appendLine()
        appendLine("-- Latency, last window: main thread answering a request, and the shared pools --")
        appendLine("main-post ${mainPost.line()}; pool-default ${defaultPool.line()}; pool-io ${ioPool.line()}")
        appendLine()
        appendLine("-- The last events (newest last) --")
        synchronized(recentEvents) { if (recentEvents.isEmpty()) appendLine("none") else recentEvents.forEach { appendLine(it) } }
        appendLine()
        appendLine("-- Recompositions since start --")
        if (recompositions.isEmpty()) appendLine("none") else recompositions.entries.sortedByDescending { it.value.get() }.take(30).forEach { appendLine("${it.key}: ${it.value.get()}") }
        appendLine()
        appendLine("-- Events since start --")
        if (counters.isEmpty()) appendLine("none") else counters.entries.sortedByDescending { it.value.get() }.forEach { appendLine("${it.key}: ${it.value.get()}") }
    }

    /** Forgets everything seen so far, so the next report covers only what happens from now. */
    fun reset() {
        stats.clear(); spans.clear(); recompositions.clear(); counters.clear(); lastRecomp.clear(); lastCount.clear()
        synchronized(worstFrames) { worstFrames.clear() }
        synchronized(stalls) { stalls.clear() }
        startedAt = SystemClock.uptimeMillis()
        peakHeapMb = 0
    }

    /** How long something took to answer, across a window: the count, the average and the worst. */
    private class LatencyStats {
        private var count = 0
        private var sum = 0f
        private var worst = 0f

        @Synchronized fun add(ms: Float) {
            count++
            sum += ms
            if (ms > worst) worst = ms
        }

        @Synchronized fun reset() {
            count = 0
            sum = 0f
            worst = 0f
        }

        @Synchronized fun line(): String = if (count == 0) "idle" else "avg ${fmt(sum / count)} max ${fmt(worst)} ms (${count} asks)"
    }

    private class SpanStats {
        var count = 0
        var totalMs = 0f
        var worstMs = 0f
        @Synchronized fun add(ms: Float) { count++; totalMs += ms; if (ms > worstMs) worstMs = ms }
    }

    /** The runtime's garbage-collection counters at an instant; subtract two for what happened between them. */
    private class GcSample(val count: Long, val blocking: Long, val timeMs: Long, val allocatedMb: Long) {
        operator fun minus(o: GcSample) = GcSample(count - o.count, blocking - o.blocking, timeMs - o.timeMs, allocatedMb - o.allocatedMb)

        companion object {
            fun read(): GcSample {
                fun stat(key: String) = runCatching { Debug.getRuntimeStat(key)?.toLongOrNull() }.getOrNull() ?: 0L
                return GcSample(stat("art.gc.gc-count"), stat("art.gc.blocking-gc-count"), stat("art.gc.gc-time"), stat("art.gc.bytes-allocated") / (1024 * 1024))
            }
        }
    }
}

/** Counts this composable's recompositions under [name]: put it at the top of a composable whose cost is in question. */
@Composable
fun TrackRecompositions(name: String) {
    SideEffect { PerfMonitor.recomposed(name) }
}
