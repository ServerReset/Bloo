package com.bloo.bluelink

import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.StartupTrace
import java.util.Locale

/**
 * Per-frame jank telemetry for the first seconds of a cold start, plus the two
 * end-of-startup markers (`first frame`, `frames settled`).
 *
 * WHY: [StartupTrace] answers "which phase is slow"; this answers "and does the user
 * SEE it". A phase can take 300ms and cost nothing perceptible (it happened before the
 * first frame), or take 40ms and be an obvious hitch (it happened while scrolling the
 * garage). The only way to tell them apart is frame timings, and the only moment that
 * matters is startup -- so this runs for a bounded window (default 6s or 400 frames,
 * whichever first) and then unregisters itself completely. It is NOT a continuous
 * profiler: after the window closes this costs exactly zero.
 *
 * OUTPUT (same `BlooStartup` logcat tag as the phase marks, so the two interleave):
 *   frame 12: 48ms (+1 hitch) ...        -- only frames over the threshold are logged
 *   frames settled: 380 frames in 6002ms, avg 15.8ms, >16ms 128, >32ms 41, >50ms 12, >100ms 3, worst 214ms
 *
 * The buckets matter more than the average: a steady 12ms average with twelve 100ms+
 * frames is what "laggy for the first few seconds" actually looks like, and the average
 * alone would hide it.
 */
object StartupFrameMonitor {

    /** A frame longer than this is logged individually (2x a 60Hz frame). */
    private const val HITCH_MS = 32.0

    /** Stop after this many milliseconds, even if frames keep coming. */
    private const val WINDOW_MS = 6_000L

    /** Hard cap, for a device that somehow renders thousands of frames in the window. */
    private const val MAX_FRAMES = 400

    private var scheduled = false

    // Collected stats.
    private var frames = 0
    private var firstFrameAtUptime = 0L
    private var windowStartUptime = 0L
    private var totalMs = 0.0
    private var worstMs = 0.0
    private var over16 = 0
    private var over32 = 0
    private var over50 = 0
    private var over100 = 0
    private var lastFrameNanos = 0L

    /**
     * Begin the window. Safe to call more than once (later calls are ignored), so both
     * [BlooApplication] and [MainActivity] can call it without coordinating.
     */
    fun start() {
        if (scheduled) return
        scheduled = true
        windowStartUptime = SystemClock.uptimeMillis()
        Choreographer.getInstance().postFrameCallback(frameCallback)
        // A frame callback only ever re-arms when a frame is ACTUALLY produced, so on an
        // idle app the callbacks simply stop and the summary would never be written --
        // exactly the "no more frames to measure" state a settled app is in. This
        // wall-clock backstop guarantees the summary lands WINDOW_MS after start
        // regardless, and marks the window closed so a late frame can't double-report it.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            { settle() },
            WINDOW_MS + 250L,
        )
    }

    private var settled = false

    /** Emits the one-time summary and stops measuring. Idempotent. */
    private fun settle() {
        if (settled) return
        settled = true
        val elapsedInWindow = SystemClock.uptimeMillis() - windowStartUptime
        StartupTrace.mark(
            "frames settled: $frames frames in ${elapsedInWindow}ms, " +
                "avg ${String.format(Locale.US, "%.1f", if (frames > 0) totalMs / frames else 0.0)}ms, " +
                ">16ms $over16, >32ms $over32, >50ms $over50, >100ms $over100, " +
                "worst ${String.format(Locale.US, "%.1f", worstMs)}ms",
        )
        AppLog.log(
            "⏱ cold-start frames: ${frames} frames, >32ms $over32, worst " +
                String.format(Locale.US, "%.0f", worstMs) + "ms",
        )
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            val nowUptime = SystemClock.uptimeMillis()
            if (firstFrameAtUptime == 0L) {
                firstFrameAtUptime = nowUptime
                StartupTrace.mark("first frame drawn")
                // The system's own "fully drawn" signal (activity launched -> first draw),
                // which is also what `am start -W` reports -- marked separately so the
                // app-side trace and the shell's number can be compared directly.
                (currentActivity() as? MainActivity)?.reportFullyDrawn()
            }
            if (lastFrameNanos != 0L) {
                val frameMs = (frameTimeNanos - lastFrameNanos) / 1_000_000.0
                frames++
                totalMs += frameMs
                if (frameMs > worstMs) worstMs = frameMs
                if (frameMs > 16.0) over16++
                if (frameMs > 32.0) over32++
                if (frameMs > 50.0) over50++
                if (frameMs > 100.0) over100++
                if (frameMs > HITCH_MS) {
                    StartupTrace.mark(
                        "frame $frames: ${String.format(Locale.US, "%.1f", frameMs)}ms  " +
                            "(+${nowUptime - windowStartUptime}ms into window)",
                    )
                }
            }
            lastFrameNanos = frameTimeNanos

            val elapsedInWindow = nowUptime - windowStartUptime
            if (elapsedInWindow < WINDOW_MS && frames < MAX_FRAMES) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                settle()
            }
        }
    }

    /** The resumed MainActivity, if any -- used only for [reportFullyDrawn]. */
    private fun currentActivity(): android.app.Activity? =
        runCatching {
            // Reflection-free route: MainActivity keeps a weak self-reference for exactly
            // this kind of system-level integration.
            MainActivity.current
        }.getOrNull()
}
