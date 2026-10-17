package com.bloo.uicommon

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Three small things the button system reads. They came with it rather than being duplicated: a
// spring that leans under battery saver, the one hairline, and the glass tint a floating button
// resolves to.

@Composable
fun <T> lowPowerAwareSpring(dampingRatio: Float, stiffness: Float) =
    if (!LocalPowerPolicy.current.bouncySprings) {
        tween<T>(durationMillis = 140, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    } else {
        spring<T>(dampingRatio = dampingRatio, stiffness = stiffness)
    }

/**
 * The one hairline: the outline of an inner box, a disabled button's rim, a divider-weight edge.
 */
const val HairlineAlpha = 0.14f

@Composable
fun hairlineColor(): androidx.compose.ui.graphics.Color =
    androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = HairlineAlpha)

const val GlassBlurredTintAlpha = 0.02f
const val GlassTintAlpha = 0.10f

@Composable
fun glassTint(blurred: Boolean): Color {
    // Resolve dark via the app's override (Theme.kt), not the system setting.
    val dark = LocalIsDarkTheme.current
    return if (dark) {
        val alpha = if (blurred) GlassBlurredTintAlpha else GlassTintAlpha
        Color.White.copy(alpha = alpha)
    } else {
        // Theme-aware surface colour keeps floating buttons from reading as black overlays.
        val scheme = MaterialTheme.colorScheme
        // Blurred needs less alpha; unblurred needs more visual weight.
        val surfaceColor = scheme.surfaceContainer
        val alpha = if (blurred) 0.06f else 0.09f
        surfaceColor.copy(alpha = alpha)
    }
}
