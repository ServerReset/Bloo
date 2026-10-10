package com.bloo.bluelink.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// --- Soft scroll edges and edge bleed for horizontal strips ---

/** How wide the strip's soft edge is: cells blur and fade as they slide into it and sharpen as they leave it. */
private val ScrollEdgeFade = 40.dp

/**
 * A cell of a horizontally scrolling strip that melts into blur as it nears an edge that has more
 * content past it, and comes back into focus as it scrolls toward the middle -- the same soft edge
 * a faded list has, with focus going as well as opacity.
 */
@Composable
internal fun Modifier.scrollEdgeBlur(scroll: ScrollState, viewportPx: androidx.compose.runtime.State<Int>, blur: Boolean): Modifier {
    var x by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var w by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fadePx = with(density) { ScrollEdgeFade.toPx() }
    val maxBlurPx = with(density) { 3.dp.toPx() }
    return this
        .onPlaced { x = it.positionInParent().x; w = it.size.width.toFloat() }
        .graphicsLayer {
            val vp = viewportPx.value.toFloat()
            if (vp <= 0f) return@graphicsLayer
            val left = x - scroll.value
            val right = left + w
            val leftGate = (scroll.value / fadePx).coerceIn(0f, 1f)
            val rightGate = ((scroll.maxValue - scroll.value) / fadePx).coerceIn(0f, 1f)
            val intoLeft = ((fadePx - left) / fadePx).coerceIn(0f, 1f) * leftGate
            val intoRight = ((right - (vp - fadePx)) / fadePx).coerceIn(0f, 1f) * rightGate
            val t = maxOf(intoLeft, intoRight)
            alpha = 1f - 0.5f * t
            renderEffect = if (blur && t > 0.03f) {
                androidx.compose.ui.graphics.BlurEffect(maxBlurPx * t, maxBlurPx * t, androidx.compose.ui.graphics.TileMode.Decal)
            } else {
                null
            }
        }
}

/** Lets a child extend [inset] past both sides of the space its parent gives it, so it can reach the parent's own edges. */
internal fun Modifier.bleedHorizontally(inset: Dp): Modifier = layout { measurable, constraints ->
    val extra = inset.roundToPx() * 2
    val width = constraints.maxWidth + extra
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}
