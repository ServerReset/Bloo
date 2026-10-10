package com.bloo.uicommon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.dropShadow

/**
 * The hairline rim every piece of frosted chrome (floating pills, dialogs, pebbles) should share --
 * brighter along the top, fading down the sides, like a real card's edge catching ambient light.
 */
fun Modifier.frostedRim(shape: Shape, onSurface: Color): Modifier =
    // A single 1dp border stroke, but painted with a vertical gradient brush instead of a flat
    // color: brightest at the top edge (0.24 alpha), dimmest through the middle (0.10), then a
    // touch brighter again at the bottom (0.16) -- the top-lit/bottom-dim asymmetry is what reads
    // as a physical highlight, since Compose's border draws this same brush uniformly all the way
    // around the shape's outline.
    this.border(
        BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    onSurface.copy(alpha = 0.24f),
                    onSurface.copy(alpha = 0.10f),
                    onSurface.copy(alpha = 0.16f),
                ),
            ),
        ),
        shape,
    )

/**
 * Meant to be chained before dropShadow/ frostedRim on anything that floats over an unpredictable
 * photo background.
 */
fun Modifier.ambientRing(shape: Shape): Modifier =
    this.dropShadow(shape, color = Color.Black.copy(alpha = 0.30f), blurRadius = 10.dp, offsetY = 0.dp, offsetX = 0.dp)
