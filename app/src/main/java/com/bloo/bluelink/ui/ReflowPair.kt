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
 *  - 0 (collapsed): both blocks fill the page, [trail] sitting UNDER [lead] (the single-column look,
 *    whether the page is a phone's full width or one column of the wide grid).
 *  - 1 (expanded): the page is the full width and the two blocks sit BESIDE each other, each half.
 *
 * The block width is derived from the ACTUAL container width (the pager page for this frame), not a
 * guessed screen width, so it fills correctly at any column count. It shrinks with [expandedT] on the
 * same spring the caller animates that fraction with, so the whole reflow -- width and position --
 * moves as one fluid motion. The caller owns the scrolling (this only positions the two blocks).
 */
@Composable
internal fun ReflowPair(
    expandedT: Float,
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
        val cw = constraints.maxWidth
        // Collapsed the block fills the page; expanded it is (page - seam) / 2. Dividing by (1 + t)
        // keeps the two blocks' combined right edge exactly on the container edge at EVERY t, so
        // nothing ever overflows mid-flight, and for a two-column page the width barely changes at
        // all (the page doubles as the block halves).
        val w = ((cw - gapPx * t) / (1f + t)).roundToInt().coerceIn(0, cw)
        val blockConstraints = constraints.copy(minWidth = w, maxWidth = w, minHeight = 0)
        val leadPlaceable = measurables[0].measure(blockConstraints)
        val trailPlaceable = measurables[1].measure(blockConstraints)
        // Under lead while collapsed (0), even with it while expanded (1).
        val stackedY = leadPlaceable.height + gapPx
        val y = (stackedY * (1f - t)).roundToInt()
        val x = ((w + gapPx) * t).roundToInt()
        val height = maxOf(leadPlaceable.height, y + trailPlaceable.height)
        layout(cw, height) {
            leadPlaceable.place(0, 0)
            trailPlaceable.place(x, y)
        }
    }
}
