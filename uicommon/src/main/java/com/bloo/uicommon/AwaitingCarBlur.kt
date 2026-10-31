package com.bloo.uicommon

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
fun Modifier.awaitingCarBlur(awaiting: Boolean): Modifier =
    if (!awaiting) this else graphicsLayer {
        val px = AwaitingCarBlurDp * density
        renderEffect = BlurEffect(px, px, TileMode.Clamp)
        compositingStrategy = CompositingStrategy.Offscreen
    }
