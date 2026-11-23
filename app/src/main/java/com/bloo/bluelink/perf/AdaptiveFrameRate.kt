package com.bloo.bluelink.perf

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import com.bloo.bluelink.data.AppLog

/**
 * Steps a high-refresh screen down to a steady 60 Hz while the app's frames cannot keep up with it, and back up once they can.
 *
 * The glass the cards are drawn with costs the GPU about 8-12 ms a frame while they move (seen in device logs). That fits a 60 Hz frame
 * (16.7 ms) with room to spare and overruns a 120 Hz one (8.3 ms), so on a 120 Hz phone nine frames in ten of a garage swipe missed
 * their deadline and the motion stuttered. Asking for 60 Hz while that is happening makes the same motion steady, without drawing the
 * glass any differently. Menus, text and anything light stay at the screen's full rate: the request is dropped a few seconds after the
 * last heavy frame.
 *
 * It only votes for a rate ([android.view.WindowManager.LayoutParams.preferredRefreshRate]); the system decides, and on screens that
 * cannot switch it changes nothing.
 */
internal object AdaptiveFrameRate {
    /** Below this the screen is already 60 Hz class, and there is nothing to step down from. */
    private const val HIGH_REFRESH_HZ = 75f

    /** How many recent frames are judged, and how many of them must overrun before the rate steps down. */
    private const val WINDOW = 48
    private const val HEAVY_TO_CAP = 24

    /** How long without a heavy frame before the screen is given its full rate back. */
    private const val CALM_MS = 3_000L

    private const val CAPPED_HZ = 60f

    private var listener: Window.OnFrameMetricsAvailableListener? = null
    private var thread: HandlerThread? = null
    private val recent = BooleanArray(WINDOW)
    private var cursor = 0
    private var heavyCount = 0

    @Volatile private var capped = false

    @Volatile private var lastHeavyAt = 0L

    /** Starts watching [activity]'s frames. Safe to call again; a screen at 60 Hz class is left alone. */
    fun install(activity: Activity) {
        if (listener != null) return
        val refresh = refreshRate(activity)
        if (refresh < HIGH_REFRESH_HZ) return
        val budgetMs = 1000f / refresh
        val t = HandlerThread("bloo-frame-rate").also { it.start() }
        thread = t
        val handler = Handler(t.looper)
        val main = Handler(Looper.getMainLooper())
        val l = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            val totalMs = metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1_000_000f
            val gpuMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) metrics.getMetric(FrameMetrics.GPU_DURATION) / 1_000_000f else 0f
            // Heavy: the frame would miss the high-refresh deadline.
            val heavy = maxOf(totalMs, gpuMs) > budgetMs * 1.05f
            if (recent[cursor]) heavyCount--
            recent[cursor] = heavy
            if (heavy) heavyCount++
            cursor = (cursor + 1) % WINDOW
            if (heavy) lastHeavyAt = SystemClock.uptimeMillis()
            if (!capped && heavyCount >= HEAVY_TO_CAP) {
                capped = true
                main.post { vote(activity, CAPPED_HZ) }
                AppLog.log("Frame rate: stepping down to ${CAPPED_HZ.toInt()} Hz ($heavyCount of $WINDOW frames over ${"%.1f".format(budgetMs)} ms)")
                scheduleCalmCheck(activity, handler, main)
            }
        }
        listener = l
        activity.window.addOnFrameMetricsAvailableListener(l, handler)
    }

    /** Stops watching (the activity is going away) and drops any rate request with it. */
    fun uninstall(activity: Activity) {
        listener?.let { runCatching { activity.window.removeOnFrameMetricsAvailableListener(it) } }
        listener = null
        thread?.quitSafely()
        thread = null
        if (capped) {
            capped = false
            vote(activity, 0f)
        }
        recent.fill(false)
        heavyCount = 0
    }

    /** Gives the screen its full rate back once a stretch of [CALM_MS] has passed with no heavy frame; checks again until then. */
    private fun scheduleCalmCheck(activity: Activity, handler: Handler, main: Handler) {
        handler.postDelayed({
            if (!capped) return@postDelayed
            if (SystemClock.uptimeMillis() - lastHeavyAt >= CALM_MS) {
                capped = false
                recent.fill(false)
                heavyCount = 0
                main.post { vote(activity, 0f) }
                AppLog.log("Frame rate: back to the screen's full rate")
            } else {
                scheduleCalmCheck(activity, handler, main)
            }
        }, CALM_MS)
    }

    /** Asks the window for [hz] (0 = no preference, the screen's own rate). */
    private fun vote(activity: Activity, hz: Float) {
        if (activity.isFinishing || activity.isDestroyed) return
        val window = activity.window
        val attributes = window.attributes
        if (attributes.preferredRefreshRate == hz) return
        attributes.preferredRefreshRate = hz
        window.attributes = attributes
    }

    @Suppress("DEPRECATION")
    private fun refreshRate(activity: Activity): Float =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display?.refreshRate ?: 60f else activity.windowManager.defaultDisplay.refreshRate
}
