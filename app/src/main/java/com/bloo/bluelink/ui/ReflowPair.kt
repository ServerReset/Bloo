package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt

/**
 * The garage's in-place expand/collapse reflow. Two content blocks -- a "lead" block (header, hero,
 * controls, the hot-seat slot) and a "trail" block (the reorderable pebble list) -- are positioned by
 * a single animated fraction [expandedT]:
 *
 *  - 0 (collapsed): the two blocks sit UNDER each other, both one column wide.
 *  - 1 (expanded): they sit BESIDE each other, each one column wide.
 *
 * Both blocks are measured at the CONSTANT [blockWidth] -- one column, the same width whether the
 * page is showing one column (collapsed) or two (expanded) -- so nothing ever re-wraps as the page
 * grows; only their POSITIONS animate. The caller owns the scrolling (this positions the two
 * blocks).
 *
 * [expandedT] is a SUPPLIER, read only here in the measure phase, never in composition: that is what
 * keeps a running expand/collapse from recomposing the whole car every frame (it only re-lays-out).
 */
@Composable
internal fun ReflowPair(
    expandedT: () -> Float,
    blockWidth: Dp,
    modifier: Modifier = Modifier,
    gap: Dp = GapSection,
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
        val t = expandedT().coerceIn(0f, 1f)
        val gapPx = gap.roundToPx()
        val w = blockWidth.roundToPx().coerceIn(0, constraints.maxWidth)
        val blockConstraints = constraints.copy(minWidth = w, maxWidth = w, minHeight = 0)
        val leadPlaceable = measurables[0].measure(blockConstraints)
        val trailPlaceable = measurables[1].measure(blockConstraints)
        // Under the lead when collapsed (0), even with it when expanded (1).
        val stackedY = leadPlaceable.height + gapPx
        val y = (stackedY * (1f - t)).roundToInt()
        val x = ((w + gapPx) * t).roundToInt()
        val height = maxOf(leadPlaceable.height, y + trailPlaceable.height)
        layout(constraints.maxWidth, height) {
            leadPlaceable.place(0, 0)
            trailPlaceable.place(x, y)
        }
    }
}
