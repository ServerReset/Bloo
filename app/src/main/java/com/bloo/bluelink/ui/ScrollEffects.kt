package com.bloo.bluelink.ui

import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.bloo.uicommon.canBlurBackdrops

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

/** The scroll state and viewport height of the search list, so each row can soften near its top and bottom edge. */
internal class EdgeScroll(
    val scroll: ScrollState,
    val viewportPx: androidx.compose.runtime.State<Int>,
    /** The list viewport's top edge in window coordinates, so a row at any depth can find its place in it. */
    val windowTop: androidx.compose.runtime.State<Float>,
)

internal val LocalEdgeScroll = androidx.compose.runtime.staticCompositionLocalOf<EdgeScroll?> { null }

/**
 * A row of a vertically scrolling glass list that bends as it nears an edge with more content past
 * it: it flattens, narrows, blurs and fades, then straightens toward the middle. The vertical counterpart of
 * [scrollEdgeBlur].
 */
@Composable
internal fun Modifier.glassEdgeFocus(): Modifier {
    val edge = LocalEdgeScroll.current ?: return this
    val pagerIdle = LocalPagerIdle.current
    var y by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var h by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fadePx = with(density) { 48.dp.toPx() }
    val maxBlurPx = with(density) { 4.dp.toPx() }
    val blurOk = canBlurBackdrops()
    return this
        .onGloballyPositioned {
            // Not while the car pager is swiping: the page's window position moves with the swipe, and every
            // write would redraw every row for nothing (rows near an edge only matter once it rests).
            if (pagerIdle?.value != false) { y = it.positionInWindow().y; h = it.size.height.toFloat() }
        }
        .graphicsLayer {
            val vp = edge.viewportPx.value.toFloat()
            val scroll = edge.scroll
            if (vp <= 0f || h <= 0f) return@graphicsLayer
            val top = y - edge.windowTop.value
            val bottom = top + h
            val topGate = (scroll.value / fadePx).coerceIn(0f, 1f)
            val bottomGate = ((scroll.maxValue - scroll.value) / fadePx).coerceIn(0f, 1f)
            // A row bends only as its trailing edge slides off the top or its leading edge enters at the
            // bottom, so a tall block stays crisp until it is almost gone, not for its whole length.
            val intoTop = ((fadePx - bottom) / fadePx).coerceIn(0f, 1f) * topGate
            val intoBottom = ((top - (vp - fadePx)) / fadePx).coerceIn(0f, 1f) * bottomGate
            val t = maxOf(intoTop, intoBottom)
            alpha = 1f - 0.55f * t
            // The pane bends the row as it slides under the edge: it flattens toward that edge and narrows a
            // little, so it reads as curving away like glass, not as being cropped or just going out of focus.
            scaleY = 1f - 0.22f * t
            scaleX = 1f - 0.07f * t
            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (intoTop >= intoBottom) 0f else 1f)
            renderEffect = if (blurOk && t > 0.03f) {
                androidx.compose.ui.graphics.BlurEffect(maxBlurPx * t, maxBlurPx * t, androidx.compose.ui.graphics.TileMode.Decal)
            } else {
                null
            }
        }
}
