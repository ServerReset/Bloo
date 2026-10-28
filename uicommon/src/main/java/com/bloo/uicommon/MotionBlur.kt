package com.bloo.uicommon

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
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
    return graphicsLayer {
        val pxPerSecond = speed.sample(positionPx(), System.nanoTime())
        val radius = (pxPerSecond / FullBlurSpeedPx * MaxMotionBlurDp).coerceAtMost(MaxMotionBlurDp)
        renderEffect = if (radius >= MinMotionBlurDp) {
            val px = radius * density
            BlurEffect(px, px)
        } else {
            null
        }
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
