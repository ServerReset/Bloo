package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.bloo.uicommon.GapSection
import com.bloo.uicommon.content

/**
 * The garage's in-place expand/collapse reflow. Two content blocks -- a "lead" block (header, hero,
 * controls, the hot-seat slot) and a "trail" block (the reorderable pebble list) -- are positioned by
 * a single animated fraction [expandedT]:
 *
 *  - 0 (collapsed): the two blocks sit UNDER each other, both one column wide.
 *  - 1 (expanded): they sit BESIDE each other, each one column wide.
 *
 * Both blocks are measured at [blockWidth] -- one column, the same width whether the page is showing
 * one column (collapsed) or two (expanded) -- so they do not re-wrap as the page grows between the
 * two; only their POSITIONS animate. When a car widens to full screen the width itself eases
 * continuously (the caller supplies it per frame), so the blocks grow with the page instead of jumping. The caller owns the scrolling (this positions the two
 * blocks).
 *
 * [expandedT] is a SUPPLIER, read only here in the measure phase, never in composition: that is what
 * keeps a running expand/collapse from recomposing the whole car every frame (it only re-lays-out).
 */
@Composable
internal fun ReflowPair(
    expandedT: () -> Float,
    /** One column's width, read in the measure phase: it may change every frame (a car widening to full screen). */
    blockWidth: () -> Dp,
    modifier: Modifier = Modifier,
    gap: Dp = GapSection,
    /** Extra space above the trail block when it sits BESIDE the lead (scaled by the reflow), so it does not start flush with the page top. */
    trailInset: Dp = 0.dp,
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
        // Snapped to 6 dp steps: while a car widens, the width changes every frame, and every wrapping text in both blocks re-laid out
        // on each one. Stepped, the texts re-lay out only when the step changes -- about a sixth as often -- and a 6 dp step is
        // hard to see across a widen.
        val stepPx = WidthStep.roundToPx()
        val rawW = blockWidth().roundToPx()
        val w = ((rawW + stepPx / 2) / stepPx * stepPx).coerceIn(0, constraints.maxWidth)
        val blockConstraints = constraints.copy(minWidth = w, maxWidth = w, minHeight = 0)
        val leadPlaceable = measurables[0].measure(blockConstraints)
        val trailPlaceable = measurables[1].measure(blockConstraints)
        // Under the lead when collapsed (0), even with it when expanded (1).
        val stackedY = leadPlaceable.height + gapPx
        val y = (stackedY * (1f - t) + trailInset.toPx() * t).roundToInt()
        val x = ((w + gapPx) * t).roundToInt()
        val height = maxOf(leadPlaceable.height, y + trailPlaceable.height)
        layout(constraints.maxWidth, height) {
            leadPlaceable.place(0, 0)
            trailPlaceable.place(x, y)
        }
    }
}

/** The width step, in dp, that a block's measured width snaps to while a car widens (see [ReflowPair]). */
private val WidthStep = 6.dp

/** How wide, in dp, the band is over which a car's columns turn into two: the reflow eases across it rather than switching at its edge. */
private const val TwoColumnRampDp = 12f


/**
 * The garage's column change as one motion between two layouts: from the car count it started at ([from]) to the one it is going to
 * ([to]), with [columns] the eased count in between. The reflow (two columns or one, see [ReflowPair]) and the width each block is laid
 * out at are each eased from their value in the starting layout to their value in the final one.
 *
 * Deriving them from the in-between count instead, as the garage once did, squashed and stretched a car opening to full screen: in the
 * grid each car is half the window, and at full screen its two columns are half the window each, so the block width is the same at both
 * ends; but at 1.5 cars the reflow was already two columns, the blocks fell to a third of the window and grew back, and the hero shrank
 * and swelled on the way. Eased between the ends, a width that is the same at both ends stays the same throughout.
 *
 * At rest (the motion done) both read the live window width, so a window that changes size (a fold opened) still reflows as before.
 */
@androidx.compose.runtime.Stable
internal class ColumnMotion(
    private val columns: State<Float>,
    private val from: State<Float>,
    private val to: State<Float>,
    private val widthDp: () -> Float,
) {
    private fun reflowAt(count: Float): Float {
        val column = widthDp() / count.coerceAtLeast(1f)
        val low = TWO_COLUMN_MIN_DP - TwoColumnRampDp
        return ((column - low) / (2f * TwoColumnRampDp)).coerceIn(0f, 1f)
    }

    private fun blockAt(count: Float): Float = widthDp() / count.coerceAtLeast(1f) / (1f + reflowAt(count))

    /** How far the motion has gone, 0 (the starting layout) to 1 (the final one). */
    private fun progress(): Float {
        val a = from.value
        val b = to.value
        if (kotlin.math.abs(b - a) < 0.001f) return 1f
        return ((columns.value - a) / (b - a)).coerceIn(0f, 1f)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** The reflow, 0 (one column) to 1 (two): read only in measure and draw, so driving it never recomposes the car. */
    val reflow: State<Float> = object : State<Float> {
        override val value: Float get() = lerp(reflowAt(from.value), reflowAt(to.value), progress())
    }

    /** The width, in dp, each of a car's blocks is laid out at right now. Read in the measure phase. */
    fun blockWidthDp(): Float = lerp(blockAt(from.value), blockAt(to.value), progress())
}
