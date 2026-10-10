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
 *  - 0 (collapsed): [trail] sits UNDER [lead].
 *  - 1 (expanded): [trail] sits BESIDE [lead].
 *
 * Both blocks are measured at the fixed [blockWidth] the whole time, so nothing re-wraps mid-flight;
 * only their positions animate. The caller owns the scrolling (this only positions the two blocks).
 */
@Composable
internal fun ReflowPair(
    expandedT: Float,
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
        val t = expandedT.coerceIn(0f, 1f)
        val gapPx = gap.roundToPx()
        // A constant width for both blocks, so the animation never re-lays-out their contents.
        val w = blockWidth.roundToPx().coerceAtMost(constraints.maxWidth).coerceAtLeast(0)
        val blockConstraints = constraints.copy(minWidth = w, maxWidth = w, minHeight = 0)
        val leadPlaceable = measurables[0].measure(blockConstraints)
        val trailPlaceable = measurables[1].measure(blockConstraints)
        // Under lead when collapsed, even with it when expanded.
        val stackedY = leadPlaceable.height + gapPx
        val y = (stackedY + (0f - stackedY) * t).roundToInt()
        val x = (0f + ((w + gapPx) - 0f) * t).roundToInt()
        val height = maxOf(leadPlaceable.height, y + trailPlaceable.height)
        layout(constraints.maxWidth, height) {
            leadPlaceable.place(0, 0)
            trailPlaceable.place(x, y)
        }
    }
}
