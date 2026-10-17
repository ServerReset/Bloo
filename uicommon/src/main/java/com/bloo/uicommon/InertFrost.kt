package com.bloo.uicommon

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Real backdrop blur: API 31+ (a real RenderEffect; no-op below) and the policy allows it. */
@Composable
fun canBlurBackdrops(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && LocalPowerPolicy.current.blurBackdrops

/**
 * The app's one inert (disabled) treatment: soften and fade whatever is inert, leaving its own
 * (morphing) shape alone. Disabled buttons, sliders and locked-out control groups all wear this, so
 * "disabled" looks identical everywhere instead of each deriving its own blur.
 */
@Composable
fun Modifier.inertButtonFrost(inert: Boolean): Modifier {
    val amount = animateFloatAsState(if (inert) 1f else 0f, tween(MotionLong), label = "inertFrost")
    // A real blur is expensive; without it (older API, battery saver) the fade carries the whole effect.
    val canBlur = canBlurBackdrops()
    // Driven from the layer block: the transition re-composites the layer instead of rebuilding the
    // modifier chain (and the blur) on every frame, and an active control carries no effect at all.
    return this.graphicsLayer {
        val a = amount.value
        if (a > 0.001f) {
            val r = InertBlurRadius.toPx() * a
            renderEffect = if (canBlur) androidx.compose.ui.graphics.BlurEffect(r, r, androidx.compose.ui.graphics.TileMode.Decal) else null
            alpha = 1f - (if (canBlur) 0.08f else 0.4f) * a
        } else {
            renderEffect = null
            alpha = 1f
        }
    }
}

/** The small blur an inert control wears: enough to read as "off", not enough to be unreadable. */
private val InertBlurRadius = 1.2.dp
