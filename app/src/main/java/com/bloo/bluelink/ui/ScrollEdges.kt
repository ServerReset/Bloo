package com.bloo.bluelink.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The axis a soft-edged scroll surface scrolls along, so its fade lands on the right pair of sides. */
internal enum class ScrollAxis { Vertical, Horizontal }

/** How far the soft edge reaches in from a scroll bound, by default. */
internal val ScrollEdgeLength = 36.dp

/**
 * The app's ONE soft-edge treatment for scrolling content -- the screen's own top/bottom, a big list
 * (the weather card's hour strip, the debug log), anywhere content runs past an edge.
 *
 * Content FADES and warps into a glass rim at any edge that still has more past it, and clears as
 * you scroll toward the middle. [rim] adds the bright refractive band that reads as the glass the
 * content is passing under; [ScrollAxis] picks the pair of sides ([ScrollState] is shared by both).
 */
internal fun Modifier.glassScrollEdges(
    scroll: ScrollState,
    axis: ScrollAxis,
    length: Dp = ScrollEdgeLength,
    rim: Boolean = true,
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val lenPx = length.toPx()
        if (lenPx <= 0f) return@drawWithContent
        val vertical = axis == ScrollAxis.Vertical
        val startFrac = (scroll.value / lenPx).coerceIn(0f, 1f)
        val endFrac = ((scroll.maxValue - scroll.value) / lenPx).coerceIn(0f, 1f)

        fun edge(start: Boolean, frac: Float) {
            if (frac <= 0.001f) return
            // The alpha fade: transparent at the outer edge, opaque toward the middle.
            val fade = if (vertical) {
                Brush.verticalGradient(
                    colors = if (start) listOf(Color.Transparent, Color.Black) else listOf(Color.Black, Color.Transparent),
                    startY = if (start) 0f else size.height - lenPx,
                    endY = if (start) lenPx else size.height,
                )
            } else {
                Brush.horizontalGradient(
                    colors = if (start) listOf(Color.Transparent, Color.Black) else listOf(Color.Black, Color.Transparent),
                    startX = if (start) 0f else size.width - lenPx,
                    endX = if (start) lenPx else size.width,
                )
            }
            drawRect(fade, blendMode = BlendMode.DstIn, alpha = frac)
            if (rim) {
                // The glass rim: a soft bright band at the very edge, the light the pane catches.
                val sheen = if (vertical) {
                    Brush.verticalGradient(
                        colors = if (start) listOf(Color.White.copy(alpha = 0.10f), Color.Transparent) else listOf(Color.Transparent, Color.White.copy(alpha = 0.10f)),
                        startY = if (start) 0f else size.height - lenPx,
                        endY = if (start) lenPx else size.height,
                    )
                } else {
                    Brush.horizontalGradient(
                        colors = if (start) listOf(Color.White.copy(alpha = 0.10f), Color.Transparent) else listOf(Color.Transparent, Color.White.copy(alpha = 0.10f)),
                        startX = if (start) 0f else size.width - lenPx,
                        endX = if (start) lenPx else size.width,
                    )
                }
                drawRect(sheen, alpha = frac)
            }
        }

        edge(start = true, startFrac)
        edge(start = false, endFrac)
    }

/** Top/bottom only: the common case (a vertical list or the debug log). */
internal fun Modifier.fadingEdges(scroll: ScrollState, length: Dp = ScrollEdgeLength): Modifier =
    glassScrollEdges(scroll, ScrollAxis.Vertical, length)
