package com.bloo.bluelink.data

import android.os.Debug
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log

/**
 * Cold-start instrumentation: one greppable datapoint per phase of app startup.
 *
 * WHY THIS EXISTS: "it lags for the first few seconds" is not actionable on its own --
 * the app already logged two breadcrumbs bracketing the whole cold start ("App starting"
 * and "Garage loaded"), and a slow stretch anywhere between them was invisible. Every
 * [mark] narrows the gap: [StartupTrace.mark] stamps one line with the time since the
 * PROCESS was created (not since some arbitrary object was constructed), the thread it
 * ran on, and the heap/native/thread counts at that instant. The sequence of marks then
 * reads as a timeline, and the deltas between consecutive marks point straight at the
 * phase that is eating the time.
 *
 * HOW TO READ IT: run
 *   adb logcat -c && adb shell am start -n com.bloo.bluelink/.MainActivity && sleep 12
 *   adb logcat -d -s BlooStartup
 * Lines are chronological; `+Nms` is time since process start. The frame monitor
 * ([com.bloo.bluelink.StartupFrameMonitor]) interleaves with the same tag, so janky
 * frames line up against the phase that caused them.
 *
 * COST: [mark] is a String format, one Log.i, and one [AppLog] append (a synchronized
 * list copy capped at 500 lines). Tens of marks during startup is negligible; it is
 * deliberately NOT hooked to anything that runs per-frame or per-keystroke.
 */
object StartupTrace {

    const val TAG = "BlooStartup"

    /** Uptime at which THIS PROCESS was created -- the only honest zero for a cold
     *  start. [Process.getStartUptimeMillis] is API 24+; minSdk here is 26, so it is
     *  always available. (Deliberately not a static-init timestamp: that would measure
     *  from "when this object was first touched", which is already mid-startup.) */
    private val processStartUptimeMs = Process.getStartUptimeMillis()

    /** Mirrors marks to logcat. On by default (the app is sideloaded, and logcat is the
     *  only way to read a cold start's timing without the app running); a release build
     *  can set this false to keep the log quiet without losing the in-app copy. */
    @Volatile
    var logcatEnabled = true

    /** Every mark this process has emitted, for the end-of-window summary. */
    private val marks = java.util.Collections.synchronizedList(mutableListOf<Pair<Long, String>>())

    /** Milliseconds since this process was created. */
    fun elapsedMs(): Long = SystemClock.uptimeMillis() - processStartUptimeMs

    /** One timestamped line: `+1234ms (main) heap=48MB native=12MB thr=26  label`. */
    fun mark(label: String) {
        val elapsed = elapsedMs()
        val thread = Thread.currentThread()
        val onMain = Looper.myLooper() == Looper.getMainLooper()
        val runtime = Runtime.getRuntime()
        val heapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val nativeMb = runCatching { Debug.getNativeHeapAllocatedSize() / (1024 * 1024) }.getOrDefault(0L)
        val line = buildString {
            append('+').append(elapsed).append("ms ")
            append('(').append(if (onMain) "main" else thread.name).append(") ")
            append("heap=").append(heapMb).append("MB ")
            append("native=").append(nativeMb).append("MB ")
            append("thr=").append(Thread.activeCount()).append(' ')
            append(" ").append(label)
        }
        marks.add(elapsed to label)
        if (logcatEnabled) runCatching { Log.i(TAG, line) }
        AppLog.log("⏱ $line")
    }

    /**
     * Schedules the end-of-window summary on the main looper [delayMs] from now, once.
     * The summary turns the raw mark stream into the answer "where did the time go":
     * the largest GAPS between consecutive marks, by duration, each naming the phase it
     * spans. Raw marks say what happened; the gaps say what cost.
     */
    fun scheduleSummary(delayMs: Long = 8_000L) {
        if (!summaryScheduled.compareAndSet(false, true)) return
        runCatching {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ dumpSummary() }, delayMs)
        }
    }

    private val summaryScheduled = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Logs `=== startup summary ===` plus the top gaps. Safe to call more than once. */
    fun dumpSummary() {
        val snapshot = synchronized(marks) { marks.toList() }
        if (snapshot.isEmpty()) return
        val total = snapshot.last().first
        val gaps = snapshot.zipWithNext { (t0, l0), (t1, l1) ->
            Triple(t1 - t0, l0, l1)
        }.sortedByDescending { it.first }

        mark("=== startup summary: ${snapshot.size} marks, ${total}ms since process start ===")
        gaps.take(10).forEachIndexed { i, (gap, from, to) ->
            mark("gap ${i + 1}: ${gap}ms   [$from] -> [$to]")
        }
    }

    /** Marks emitted while [elapsedMs] is under this are "startup"; after it, one-shot
     *  helpers go quiet so a warm app doesn't keep appending to the trace. */
    private const val STARTUP_WINDOW_MS = 10_000L

    private val emittedOnce = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /**
     * Emits [label] at most once per process, and only during the startup window. For
     * "first composition of screen X" / "first read of store Y" style marks: a screen
     * that composes once per launch costs one line on a cold start and nothing later,
     * with no per-call-site bookkeeping.
     */
    fun once(key: String, label: String) {
        if (elapsedMs() > STARTUP_WINDOW_MS) return
        if (emittedOnce.add(key)) mark(label)
    }

    /** Emits a normal mark, but only during the startup window -- for calls that can
     *  repeat during normal use (store reads, network kicks) without spamming the log. */
    fun markIfStarting(label: String) {
        if (elapsedMs() <= STARTUP_WINDOW_MS) mark(label)
    }

    /**
     * Times [block] and emits one mark when it returns: `label took 123ms`. Use it for
     * synchronous phases (a store read, a JSON decode, a network client build) where the
     * interesting number is the DURATION rather than the instant. Exceptions still emit
     * the timing (via `finally`) and propagate untouched.
     */
    fun <T> trace(label: String, block: () -> T): T {
        val started = SystemClock.uptimeMillis()
        try {
            return block()
        } finally {
            mark("$label took ${SystemClock.uptimeMillis() - started}ms")
        }
    }

    /**
     * `begin`/`end` pair helpers for phases that cannot be wrapped in [trace] because
     * they span suspend points or callbacks. [end] returns the duration so callers can
     * reuse it.
     */
    fun begin(): Long = SystemClock.uptimeMillis()

    fun end(label: String, startedAt: Long): Long {
        val took = SystemClock.uptimeMillis() - startedAt
        mark("$label took ${took}ms")
        return took
    }
}
