package com.bloo.uicommon

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer

/** How soft a control reads while the car has taken a command but not yet confirmed it, in dp. */
private const val AwaitingCarBlurDp = 6f

/**
 * Blurs a control for as long as [awaiting] is true: the car has accepted the command and has not yet shown it done. It is not
 * about movement; it holds until the car confirms, so the control reads as waiting, not as settled.
 *
 * The blur clamps its edges, so the control's own edge colour spreads outward instead of fading to nothing and reading bigger than the
 * control. It is drawn into an offscreen buffer, so the blur keeps its full resolution while the control is resizing around a transition.
 */
@Composable
fun Modifier.awaitingCarBlur(awaiting: Boolean): Modifier {
    // The radius eases out when the car confirms, rather than dropping to nothing in one frame: the blur is removed only
    // once it has faded to zero, so the control does not jump from a soft blur to a sharp one.
    val amount by animateFloatAsState(if (awaiting) 1f else 0f, tween(MotionLong), label = "awaitingCarBlur")
    return if (!awaiting && amount <= 0.001f) this else graphicsLayer {
        val px = AwaitingCarBlurDp * density * amount
        renderEffect = if (px > 0f) BlurEffect(px, px, TileMode.Clamp) else null
        compositingStrategy = CompositingStrategy.Offscreen
    }
}
