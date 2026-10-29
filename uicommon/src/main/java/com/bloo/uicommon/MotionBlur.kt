package com.bloo.uicommon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/** Speed, in pixels per second, at which a moving layer reaches its full touch of blur. Tuned low on purpose: a softness, not a smear. */
private const val FullBlurSpeedPx = 2_500f

/** The most a moving layer blurs, in dp. */
private const val MaxMotionBlurDp = 2f

/** Below this the blur is invisible and is not applied at all. */
private const val MinMotionBlurDp = 0.25f

/**
 * The app's one motion blur. Two ways to say what moves, and one gate that decides whether any of it draws:
 *
 * - `motionBlur { position }`: the layer blurs with the speed of [positionPx], for motion the layer does not get from its own layout (a
 *   slider thumb, a sheet moved by a graphics translation, a screen turn).
 * - `motionBlur()`: the layer measures its own window position and size on each layout, and blurs with how fast those change. Use it on
 *   surfaces that slide, grow or swipe with their parent (pebbles, pop-ups, the toast stack).
 *
 * Off when the user has switched motion blur off, in Battery Saver, under reduce motion, and for the length of any scroll ([motionBlurScrollGate]).
 * Blur needs Android 12 (API 31); below that the effect is not drawn and the motion is unchanged.
 * Not for text, scrolling lists, progress bars, the map or a haze source: those are read every frame or must stay sharp to be read.
 */
@Composable
fun Modifier.motionBlur(positionPx: () -> Float): Modifier {
    if (!motionBlurAllowed()) return this
    val speed = remember { MotionSpeed() }
    return graphicsLayer { applyMotionBlur(speed.sample(positionPx(), System.nanoTime())) }
}

/** The layer tracks its own window position and size; see the [motionBlur] doc for when to use it. */
@Composable
fun Modifier.motionBlur(): Modifier {
    if (!motionBlurAllowed()) return this
    val tracker = remember { MovementTracker() }
    return this
        .onGloballyPositioned { coords ->
            val at = coords.positionInWindow()
            tracker.record(at.x, at.y, coords.size.width.toFloat(), coords.size.height.toFloat(), System.nanoTime())
        }
        .graphicsLayer { applyMotionBlur(tracker.speedAt(System.nanoTime())) }
}

/** The one gate every blur reads: the user's switch (already folded into [LocalMotionBlur] with Battery Saver), and reduce motion. */
@Composable
private fun motionBlurAllowed(): Boolean = LocalMotionBlur.current && !LocalReduceMotion.current

/**
 * Marks the app's scrolling, so the blur stays off while a list, pager or sheet is being scrolled or flung. Scrolling is not motion
 * blur: the content moves under the finger and stays sharp. Set by [motionBlurScrollGate] on the app root.
 */
private object MotionBlurScrollGate {
    @Volatile
    private var quietUntilNanos = 0L

    fun scrolling(nanos: Long): Boolean = nanos < quietUntilNanos

    fun touch(nanos: Long) {
        quietUntilNanos = nanos + ScrollQuietNanos
    }

    private const val ScrollQuietNanos = 250_000_000L
}

private val MotionBlurScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available != Offset.Zero) MotionBlurScrollGate.touch(System.nanoTime())
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (consumed != Offset.Zero) MotionBlurScrollGate.touch(System.nanoTime())
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        MotionBlurScrollGate.touch(System.nanoTime())
        return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        MotionBlurScrollGate.touch(System.nanoTime())
        return Velocity.Zero
    }
}

/**
 * Put once, on the app root: every scroll, swipe and fling beneath it turns the motion blur off while it lasts. It consumes nothing, so
 * it changes no scrolling behaviour.
 */
fun Modifier.motionBlurScrollGate(): Modifier = nestedScroll(MotionBlurScrollConnection)

/** Draws the blur for a layer moving at [pxPerSecond]: nothing below the visible threshold, and never more than the cap. */
private fun GraphicsLayerScope.applyMotionBlur(pxPerSecond: Float) {
    val now = System.nanoTime()
    val radius = if (MotionBlurScrollGate.scrolling(now)) {
        0f
    } else {
        (pxPerSecond / FullBlurSpeedPx * MaxMotionBlurDp).coerceAtMost(MaxMotionBlurDp)
    }
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
