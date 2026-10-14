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

/** How far the soft edge reaches in from a scroll bound, by default. */
private val ScrollEdgeLength = 36.dp

/**
 * The soft-edge treatment for the one surface that wants it: the debug activity log, a wall of text
 * scrolling past the top and bottom of its card.
 *
 * Content FADES into a glass rim at an edge that still has more past it, and clears as you scroll
 * toward the middle -- the bright band is the light the pane catches where the content passes under
 * it. Top and bottom only.
 *
 * This was generalised over the axis (a horizontal variant, for the weather hour strip that has since
 * gone) and over whether the rim draws at all. Nothing ever passed either, so both are gone rather
 * than left here as branches that no build has executed.
 */
internal fun Modifier.fadingEdges(scroll: ScrollState, length: Dp = ScrollEdgeLength): Modifier = this
    // Offscreen because the fade is a DstIn mask: it needs the content in a layer to cut into.
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val lenPx = length.toPx()
        if (lenPx <= 0f) return@drawWithContent
        val startFrac = (scroll.value / lenPx).coerceIn(0f, 1f)
        val endFrac = ((scroll.maxValue - scroll.value) / lenPx).coerceIn(0f, 1f)

        fun edge(start: Boolean, frac: Float) {
            if (frac <= 0.001f) return
            val outer = if (start) 0f else size.height - lenPx
            val inner = if (start) lenPx else size.height
            // The alpha fade: transparent at the outer edge, opaque toward the middle.
            drawRect(
                Brush.verticalGradient(
                    colors = if (start) listOf(Color.Transparent, Color.Black) else listOf(Color.Black, Color.Transparent),
                    startY = outer,
                    endY = inner,
                ),
                blendMode = BlendMode.DstIn,
                alpha = frac,
            )
            // The glass rim: a soft bright band at the very edge, the light the pane catches.
            drawRect(
                Brush.verticalGradient(
                    colors = if (start) {
                        listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)
                    } else {
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.10f))
                    },
                    startY = outer,
                    endY = inner,
                ),
                alpha = frac,
            )
        }

        edge(start = true, startFrac)
        edge(start = false, endFrac)
    }
