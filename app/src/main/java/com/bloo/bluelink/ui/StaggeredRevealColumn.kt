package com.bloo.bluelink.ui

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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

/**
 * Drop-in replacement for [PebbleShell]'s body `Column`: every DIRECT CHILD gets its own
 * pop-in/pop-out as the pebble opens and closes, cascading top to bottom, with no change to
 * callers' rows.
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
                // A short head start for the CARD, not the rows -- asked for explicitly: the pop
                // should read as arriving just after the pebble has started opening, not racing it
                // from the same frame. collapseEnter's own bounce has no fixed duration (it's a
                // spring, not a tween), so this can't be timed to "wait until the card is exactly
                // this far open" -- a flat delay is what's available, short enough that the rows
                // are still clearly popping in DURING the open rather than only once it's fully
                // settled.
                // The body's own opening spring: a row's pop is keyed to how far the body has grown.
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = PebbleOpenStiffness)
            } else {
                tween(durationMillis = 400, easing = LinearEasing)
            }
        },
    ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
    // Windowing (see [PebbleStaggerSpan]) applies only on the way IN. Closing, late-window rows
    // would stay opaque then cut out at the end, so closing maps every row to the same un-windowed
    // `progress` and they fade together.
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
                // Where this row sits in the body, as the fraction of the opening it takes to uncover it,
                // plus a short beat so it pops just AFTER the card reaches it.
                val reached = if (height > 0) y.toFloat() / height else 0f
                val start = (reached * RowRevealCoverage + RowPopDelay).coerceAtMost(0.9f)
                // `progress` is read INSIDE the layerBlock (draw phase), not in the placement body,
                // so transition ticks redraw instead of re-measuring every child.
                p.placeWithLayer(0, y) {
                    // No windowing on the way out (see `closing`).
                    val raw = if (closing) {
                        progress.coerceIn(0f, 1f)
                    } else {
                        ((progress - start) / RowPopWindow).coerceIn(0f, 1f)
                    }
                    // Smoothstep for alpha: opacity cannot overshoot, so a plain ease fits.
                    val local = raw * raw * (3f - 2f * raw)
                    alpha = local
                    // Scale uses [pebbleRowOvershoot] rather than `local`, so rows share the card's
                    // overshoot-then-settle character (symmetric in `raw`, so closing pops slightly
                    // larger first).
                    val scaleT = pebbleRowOvershoot(raw)
                    // 0.7 -> 1.0, not 0.85: a smaller change is lost next to the alpha fade.
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
private const val RowRevealCoverage = 0.8f

/** The beat, in opening progress, between the card reaching a row and the row popping. */
private const val RowPopDelay = 0.05f

/** How much opening progress a row's pop takes. */
private const val RowPopWindow = 0.22f
