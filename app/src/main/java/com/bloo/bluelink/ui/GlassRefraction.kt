package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate

/**
 * The edge of a pane of glass catching light: a bright bevel along the top-left and a fainter
 * caustic along the bottom-right, with a thin chromatic split (cyan one way, magenta the other) just
 * inside the rim, which is how a real glass edge bends white light into its colours. Everything is
 * clipped to [shape] and drawn in one cached pass, so it costs nothing per frame.
 *
 * This is a simulated refraction (it draws the look of one over the blurred backdrop rather than
 * bending the pixels behind it); a true lens warp of the backdrop is the next step.
 */
@Composable
internal fun Modifier.glassRefraction(shape: Shape): Modifier {
    val dark = appIsDarkTheme()
    val lit = if (dark) Color.White else Color.White.copy(alpha = 0.9f)
    val boost = if (dark) 1f else 0.8f
    return this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = Path().apply { addOutline(outline) }
        val px = density
        val bevel = Brush.linearGradient(
            0f to lit.copy(alpha = 0.60f * boost),
            0.35f to lit.copy(alpha = 0.06f),
            0.7f to lit.copy(alpha = 0.04f),
            1f to lit.copy(alpha = 0.34f * boost),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        val cyan = Color(0xFF5CE1FF).copy(alpha = 0.26f * boost)
        val magenta = Color(0xFFFF6BD6).copy(alpha = 0.22f * boost)
        val glow = Brush.linearGradient(
            0f to lit.copy(alpha = 0.16f * boost),
            0.5f to Color.Transparent,
            1f to lit.copy(alpha = 0.10f * boost),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        onDrawBehind {
            clipPath(path) {
                // Soft inner glow: the pane's thickness.
                drawPath(path, glow, style = Stroke(width = 14f * px))
                // Chromatic split, offset in opposite directions along the light.
                translate(1.6f * px, 1.6f * px) { drawPath(path, cyan, style = Stroke(width = 1.4f * px)) }
                translate(-1.6f * px, -1.6f * px) { drawPath(path, magenta, style = Stroke(width = 1.4f * px)) }
                // The bright bevel on the very edge.
                drawPath(path, bevel, style = Stroke(width = 2.2f * px))
            }
        }
    }
}
