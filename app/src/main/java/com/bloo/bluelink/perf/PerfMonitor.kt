package com.bloo.bluelink.perf

import android.app.Activity
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.bloo.bluelink.data.AppLog
import java.util.concurrent.ConcurrentHashMap
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

    private var budgetMs = 16.7f
    private var startedAt = SystemClock.uptimeMillis()
    private var windowDropped = 0
    private var windowStalls = 0
    private var windowStallMs = 0L
    private var lastGc = GcSample.read()
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
        AppLog.log("perf: monitor on (display ${"%.0f".format(1000f / budgetMs)} Hz, frame budget ${fmt(budgetMs)} ms)")
    }

    /** The watchdog wakes twenty times a second, so it runs only while the app is on screen. */
    @Synchronized fun foreground(on: Boolean) {
        if (attachedTo == null) return
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
        thread?.quitSafely()
        thread = null
        handler = null
    }

    // --- Labels ---------------------------------------------------------------------------------------------

    /** The screen now showing; frames with no [scope] running are filed under it. */
    fun screen(name: String) { clock.screen = name }

    /** For the next [durationMs], frames are filed under [label] (on top of the screen): "map-open", "garage-swipe". */
    fun scope(label: String, durationMs: Long = 1_000L) = clock.begin(label, durationMs, System.nanoTime())

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
        if (ms >= thresholdMs && Looper.myLooper() == Looper.getMainLooper()) AppLog.log("perf span $name ${fmt(ms)} ms on main")
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
        // Quiet when nothing was wrong: a log full of healthy windows hides the bad ones.
        if (frames > 0 && (snapshot.missed > 0 || windowStalls > 0 || gcDelta.blocking > 0)) {
            val label = clock.labelAt(System.nanoTime())
            AppLog.log(
                "perf ${WINDOW_MS / 1000}s [$label]: $frames frames, ${snapshot.missed} missed (${snapshot.missedPercent()}%), " +
                    "p50 ${fmt(snapshot.percentile(0.5))} p95 ${fmt(snapshot.percentile(0.95))} worst ${fmt(snapshot.worstMs)} ms" +
                    (if (windowDropped > 0) ", $windowDropped dropped" else "") +
                    " | avg ms: ${snapshot.avgPhases()}" +
                    " | gc ${gcDelta.count} (blocking ${gcDelta.blocking}, ${gcDelta.timeMs} ms) heap ${heapMb} MB threads ${threadCount()}" +
                    (if (windowStalls > 0) " | stalls $windowStalls (${windowStallMs} ms)" else "") +
                    (if (recomp.isNotEmpty()) " | recomposed: ${top(recomp)}" else "") +
                    (if (events.isNotEmpty()) " | events: ${top(events)}" else ""),
            )
        }
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

    private fun top(m: Map<String, Long>, n: Int = 6): String = m.entries.sortedByDescending { it.value }.take(n).joinToString(" ") { "${it.key}×${it.value}" }

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
        AppLog.log("perf STALL $line")
    }

    // --- The report -----------------------------------------------------------------------------------------

    private fun uptime(): String = "+${(SystemClock.uptimeMillis() - startedAt) / 1000}s"

    private fun threadCount(): Int = runCatching {
        java.io.File("/proc/self/status").useLines { lines -> lines.firstOrNull { it.startsWith("Threads:") }?.substringAfter(':')?.trim()?.toInt() }
    }.getOrNull() ?: 0

    /** Everything seen since the monitor started (or since [reset]), as text for the Debug card's Copy button. */
    fun report(): String = buildString {
        val gc = GcSample.read()
        appendLine("== Bloo performance report ==")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}, ${"%.0f".format(1000f / budgetMs)} Hz (budget ${fmt(budgetMs)} ms)")
        appendLine("Watching for ${(SystemClock.uptimeMillis() - startedAt) / 1000} s. Heap now ${(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)} MB, peak seen $peakHeapMb MB, threads ${threadCount()}.")
        appendLine("GC since process start: ${gc.count} runs, ${gc.blocking} blocking, ${gc.timeMs} ms, ${gc.allocatedMb} MB allocated.")
        appendLine()
        appendLine("-- Frames by what was happening (frames, missed %, >32 ms, >100 ms, p50 / p95 / worst ms; average ms per phase) --")
        stats.entries.sortedByDescending { it.value.frames }.forEach { (label, s) ->
            appendLine("$label: ${s.frames} frames, ${s.missedPercent()}% missed, ${s.over32} over 32, ${s.over100} over 100, p50 ${fmt(s.percentile(0.5))} p95 ${fmt(s.percentile(0.95))} worst ${fmt(s.worstMs)}")
            appendLine("    avg ${fmt(s.avgTotal())} ms = ${s.avgPhases()}")
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
