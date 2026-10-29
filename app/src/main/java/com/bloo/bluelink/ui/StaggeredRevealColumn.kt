package com.bloo.bluelink.ui

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.LocalCascade
import com.bloo.uicommon.newCascade

/**
 * The body of a pebble. It opens the pebble's cascade: every component inside that calls `cascadeItem()` pops in as the card grows
 * down to it, in the order it appears, and pops out bottom first as the card closes. The column itself only stacks its children.
 *
 * Every component pops on its own, wherever it sits in the body, so nested groups and rows cascade too.
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
    val progressState = transition.animateFloat(
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
    // One cascade per pebble: its progress and direction are read in each component's draw phase.
    val cascade = remember(transition) {
        newCascade(
            progress = { progressState.value },
            closing = { transition.targetState != EnterExitState.Visible },
        )
    }
    val gapPx = with(LocalDensity.current) { verticalGap.roundToPx() }
    // `content` takes the ColumnScope receiver PebbleShell bodies use, via NoOpColumnScope.
    // Column.weight is a no-op here (wrap-content); the shim only makes `content` type-check.
    CompositionLocalProvider(LocalCascade provides cascade) {
        Layout(content = { NoOpColumnScope.content() }, modifier = modifier) { measurables, constraints ->
            val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
            val placeables = measurables.map { it.measure(childConstraints) }
            val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceAtMost(constraints.maxWidth)
            val gaps = gapPx * (placeables.size - 1).coerceAtLeast(0)
            val height = (placeables.sumOf { it.height } + gaps).coerceIn(constraints.minHeight, constraints.maxHeight)
            layout(width, height) {
                var y = 0
                placeables.forEach { p ->
                    p.place(0, y)
                    y += p.height + gapPx
                }
            }
        }
    }
}
