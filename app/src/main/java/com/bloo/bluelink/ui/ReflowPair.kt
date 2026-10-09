package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The garage's in-place expand/collapse reflow. Two content blocks -- a "lead" block (header, hero,
 * controls, the hot-seat slot) and a "trail" block (the reorderable pebble list) -- are laid out by a
 * single animated fraction [expandedT]:
 *
 *  - 0 (collapsed): one full-width column -- trail directly UNDER lead.
 *  - 1 (expanded): two half-width columns -- trail BESIDE lead.
 *
 * Everything in between is a real layout position, so expanding slides the pebbles up and out to the
 * side and collapsing pulls them back underneath, instead of swapping to a different screen. The
 * caller owns the scrolling (this only positions the two blocks).
 */
@Composable
internal fun ReflowPair(
    expandedT: Float,
    modifier: Modifier = Modifier,
    gap: Dp = GapSection,
    maxContentWidth: Dp = 960.dp,
    lead: @Composable () -> Unit,
    trail: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            lead()
            trail()
        },
    ) { measurables, constraints ->
        val t = expandedT.coerceIn(0f, 1f)
        val gapPx = gap.roundToPx()
        // Never wider than the container; the grid already caps itself, this is just the max width the
        // two columns share when expanded.
        val content = minOf(constraints.maxWidth, maxContentWidth.roundToPx())
        val full = content
        val half = ((content - gapPx) / 2f).roundToInt()
        val leadW = (full + (half - full) * t).roundToInt()
        val trailW = (full + (half - full) * t).roundToInt()
        val leadPlaceable = measurables[0].measure(
            constraints.copy(minWidth = leadW, maxWidth = leadW, minHeight = 0),
        )
        val trailPlaceable = measurables[1].measure(
            constraints.copy(minWidth = trailW, maxWidth = trailW, minHeight = 0),
        )
        // Under lead when collapsed, even with it when expanded.
        val stackedY = leadPlaceable.height + gapPx
        val y = (stackedY + (0f - stackedY) * t).roundToInt()
        val x = (0f + ((leadW + gapPx) - 0f) * t).roundToInt()
        val height = maxOf(leadPlaceable.height, y + trailPlaceable.height)
        layout(content, height) {
            leadPlaceable.place(0, 0)
            trailPlaceable.place(x, y)
        }
    }
}
