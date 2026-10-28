package com.bloo.uicommon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import kotlin.math.abs

/** Speed, in pixels per second, at which a moving layer reaches its full touch of blur. Tuned low on purpose: a softness, not a smear. */
private const val FullBlurSpeedPx = 2_500f

/** The most a moving layer blurs, in dp. */
private const val MaxMotionBlurDp = 2f

/** Below this the blur is invisible and is not applied at all. */
private const val MinMotionBlurDp = 0.25f

/**
 * A touch of motion blur for anything that moves: the layer softens in proportion to how fast [positionPx] is changing, and sharpens as
 * it settles. Used for surfaces that slide, grow or swipe, so the motion reads as motion rather than as a sequence of stills.
 *
 * [positionPx] is read in the draw phase, so a moving layer is never recomposed for its blur. Blur needs Android 12 (API 31); below that
 * the effect is simply not drawn, and the motion is unchanged.
 */
@Composable
fun Modifier.motionBlur(positionPx: () -> Float): Modifier {
    val speed = remember { MotionSpeed() }
    return graphicsLayer { applyMotionBlur(speed.sample(positionPx(), System.nanoTime())) }
}

/**
 * The same touch of blur for any layer that moves or changes size, with no caller having to say what moves it: the layer measures its own
 * window position and size on each layout, and blurs in proportion to how fast those change. Use it on surfaces that slide, grow or swipe
 * with their parent (cards in a pager, pop-ups, toasts). Not for text, scrolling lists, progress bars, the map or a haze source: those are
 * either read every frame or must stay sharp to be read.
 */
@Composable
fun Modifier.motionBlurWhenMoving(): Modifier {
    val tracker = remember { MovementTracker() }
    return this
        .onGloballyPositioned { coords ->
            val at = coords.positionInWindow()
            tracker.record(at.x, at.y, coords.size.width.toFloat(), coords.size.height.toFloat(), System.nanoTime())
        }
        .graphicsLayer { applyMotionBlur(tracker.speedAt(System.nanoTime())) }
}

/** Draws the blur for a layer moving at [pxPerSecond]: nothing below the visible threshold, and never more than the cap. */
private fun GraphicsLayerScope.applyMotionBlur(pxPerSecond: Float) {
    val radius = (pxPerSecond / FullBlurSpeedPx * MaxMotionBlurDp).coerceAtMost(MaxMotionBlurDp)
    renderEffect = if (radius >= MinMotionBlurDp) {
        val px = radius * density
        BlurEffect(px, px)
    } else {
        null
    }
}

/**
 * Tracks a layer's window position and size across layout passes and turns the change into a smoothed speed. Read in the draw phase, so
 * a moving layer is never recomposed for its blur, and a layer that stops reads as still once [StillAfterSeconds] has passed.
 */
private class MovementTracker {
    private var x = Float.NaN
    private var y = 0f
    private var w = 0f
    private var h = 0f
    private var lastNanos = 0L
    private var smoothed = 0f

    fun record(x: Float, y: Float, w: Float, h: Float, nanos: Long) {
        if (this.x.isNaN()) {
            store(x, y, w, h, nanos)
            return
        }
        val seconds = (nanos - lastNanos) / 1_000_000_000f
        if (seconds > 0f) {
            val moved = abs(x - this.x) + abs(y - this.y) + abs(w - this.w) + abs(h - this.h)
            smoothed += (moved / seconds - smoothed) * Smoothing
        }
        store(x, y, w, h, nanos)
    }

    /** The current speed, or zero once no layout has moved it for a moment. */
    fun speedAt(nanos: Long): Float = if ((nanos - lastNanos) / 1_000_000_000f > StillAfterSeconds) 0f else smoothed

    private fun store(x: Float, y: Float, w: Float, h: Float, nanos: Long) {
        this.x = x
        this.y = y
        this.w = w
        this.h = h
        lastNanos = nanos
    }

    private companion object {
        const val StillAfterSeconds = 0.1f
        const val Smoothing = 0.5f
    }
}

/**
 * Turns a position sampled once per draw into a smoothed speed. A position that has not changed for a while reads as still, so a layer
 * that stops mid-frame does not stay soft.
 */
private class MotionSpeed {
    private var lastPosition = Float.NaN
    private var lastNanos = 0L
    private var smoothed = 0f

    fun sample(position: Float, nanos: Long): Float {
        if (lastPosition.isNaN()) {
            lastPosition = position
            lastNanos = nanos
            return 0f
        }
        val seconds = (nanos - lastNanos) / 1_000_000_000f
        if (seconds <= 0f) return smoothed
        val raw = if (seconds > StillAfterSeconds) 0f else abs(position - lastPosition) / seconds
        smoothed += (raw - smoothed) * Smoothing
        lastPosition = position
        lastNanos = nanos
        return smoothed
    }

    private companion object {
        const val StillAfterSeconds = 0.1f
        const val Smoothing = 0.4f
    }
}
