package com.bloo.bluelink.ui

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.container
import com.bloo.uicommon.content

/**
 * Drop-in replacement for [PebbleShell]'s body `Column`: every DIRECT CHILD gets its own
 * pop-in/pop-out as the pebble opens and closes. Opening, the rows cascade top to bottom, each
 * popping a beat AFTER the card has grown to it; closing runs the same cascade in reverse, the
 * bottom rows popping out first, just ahead of the card shrinking up past them.
 */
@Composable
internal fun StaggeredRevealColumn(
    transition: Transition<EnterExitState>,
    modifier: Modifier = Modifier,
    verticalGap: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    // targetValueByState: for a freshly entering pebble the transition's current state is PreEnter
    // (0f), so the first frame is already correct.
    val progress by transition.animateFloat(
        label = "pebbleRowCascade",
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                // Content enters AFTER the container: a short delay so the card opens first and the
                // rows cascade into the space it just made, rather than racing it open.
                tween(durationMillis = 360, delayMillis = 150, easing = LinearEasing)
            } else {
                // Content exits BEFORE the container: a shorter burst with no delay, so every row is
                // gone a beat before the card finishes shrinking rather than leaving with it.
                tween(durationMillis = 220, easing = LinearEasing)
            }
        },
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    // True while the exit transition runs: the row cascade reads it to reverse (bottom rows pop out
    // first, top rows last) instead of the open's top-to-bottom order.
    val closing = transition.targetState != EnterExitState.Visible
    val gapPx = with(LocalDensity.current) { verticalGap.roundToPx() }
    // `content` takes the ColumnScope receiver PebbleShell bodies use, via NoOpColumnScope.
    // Column.weight is a no-op here (wrap-content); the shim only makes `content` type-check.
    Layout(content = { NoOpColumnScope.content() }, modifier = modifier) { measurables, constraints ->
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(childConstraints) }
        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceAtMost(constraints.maxWidth)
        val gaps = gapPx * (placeables.size - 1).coerceAtLeast(0)
        val height = (placeables.sumOf { it.height } + gaps).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            var y = 0
            placeables.forEachIndexed { i, p ->
                // Where this row sits in the body (0 at the top, 1 at the bottom).
                val reached = if (height > 0) y.toFloat() / height else 0f
                // `progress` is read INSIDE the layerBlock (draw phase), not in the placement body,
                // so transition ticks redraw instead of re-measuring every child.
                p.placeWithLayer(0, y) {
                    // Rows never overlap themselves: modulate each draw's alpha instead of rendering the row
                    // (often glass) into its own offscreen buffer for the length of the open.
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                    // Each row runs its own window over the shared open/close progress. Opening, the
                    // rows cascade top to bottom, each popping a beat AFTER the card reaches it.
                    // Closing runs the cascade in reverse -- the bottom rows leave first -- so the
                    // body empties line by line just ahead of the card shrinking up past it.
                    val vis: Float
                    val scaleT: Float
                    if (closing) {
                        val start = ((1f - reached) * RowExitCoverage + RowPopDelay).coerceAtMost(0.9f)
                        // progress falls 1 -> 0 on the way out, so (1 - progress) is the closing clock.
                        val raw = (((1f - progress) - start) / RowPopWindow).coerceIn(0f, 1f)
                        // Smoothstep so opacity cannot overshoot; the row shrinks as it goes.
                        val gone = raw * raw * (3f - 2f * raw)
                        vis = 1f - gone
                        scaleT = 1f - gone
                    } else {
                        val start = (reached * RowRevealCoverage + RowPopDelay).coerceAtMost(0.9f)
                        val raw = ((progress - start) / RowPopWindow).coerceIn(0f, 1f)
                        val local = raw * raw * (3f - 2f * raw)
                        vis = local
                        // Rows share the card's overshoot-then-settle character on the way in.
                        scaleT = pebbleRowOvershoot(raw)
                    }
                    alpha = vis
                    // 0.82 -> 1.0, not 0.9: a smaller change is lost next to the alpha fade.
                    scaleX = 0.82f + 0.18f * scaleT
                    scaleY = 0.82f + 0.18f * scaleT
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                y += p.height + gapPx
            }
        }
    }
}

/** How much of the opening a row's position maps onto (the last row is reached near the end). */
private const val RowRevealCoverage = 0.6f

/** How much of the closing a row's position maps onto; bottom rows leave first (mirror of the open). */
private const val RowExitCoverage = 0.5f

/** The beat, in opening progress, between the card reaching a row and the row popping. */
private const val RowPopDelay = 0.08f

/** How much opening progress a row's pop takes. */
private const val RowPopWindow = 0.25f
