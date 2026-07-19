package com.bloo.bluelink.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The alpha floating chrome's own solid tint should use for its base fill --
 * a flat, frosted semi-transparent look used everywhere in the app (search
 * bar, floating buttons, pebble backgrounds, dialogs, widget). Liquid/Ultra
 * glass (a real hardware-blurred, refractive material) was removed in favor
 * of this simpler, consistent look across every device and surface.
 */
fun glassContainerAlpha(frosted: Float = 0.62f): Float = frosted

/**
 * The hairline rim every piece of frosted chrome (floating pills, dialogs,
 * pebbles) should share -- brighter along the top, fading down the sides,
 * like a real card's edge catching ambient light. Without the old glass
 * blur behind these surfaces, this rim is what now reads as "this element
 * is a distinct, lifted piece of material" rather than a flat color patch.
 */
@Composable
fun Modifier.frostedRim(shape: Shape): Modifier {
    val onSurface = MaterialTheme.colorScheme.onSurface
    return this.border(
        BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    onSurface.copy(alpha = 0.14f),
                    onSurface.copy(alpha = 0.05f),
                    onSurface.copy(alpha = 0.08f),
                ),
            ),
        ),
        shape,
    )
}
