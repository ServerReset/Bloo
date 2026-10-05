package com.bloo.bluelink.ui

import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Density

/**
 * Cache of resting child widths for one [ExpressiveButtonGroup]; a plain object, not snapshot
 * state.
 */
internal class NaturalWidths {
    /**
     * Whether the last layout dropped members to their symbols. Sticky: it returns to words only
     * with a clear margin, so a width hovering at the threshold doesn't flip icon/text per pixel.
     */
    var compacted: Boolean = false

    /**
     * The standalone button's own single resting width, cached from its last resting measure. Only
     * [SafeExpansiveButton]'s non-group path uses this; the group derives its widths from
     * intrinsics instead ([content]/[compact]) and needs no measured cache at all.
     */
    var widths: IntArray? = null

    /** What each child's content asked for -- the floor a donor stops at. */
    var content: IntArray? = null

    /** The icon-only width each member falls back to when the line cannot fit the labels. */
    var compact: IntArray? = null
}

/**
 * Carries a child's live press fraction to the group's measure policy; a lambda so the group reads
 * it during layout without the child recomposing.
 */
internal data class ExpressiveGroupData(
    val pressFraction: () -> Float,
    /**
     * Share of the row's leftover space this member takes, 0 to opt out. The group's own answer to
     * Modifier.weight, which cannot reach it: weight is RowScope parent data read by a Row's
     * measure policy, and a child of this group is not a child of a Row.
     */
    val weight: Float,
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@ExpressiveGroupData
}

/**
 * A [ExpressiveGroupData.weight] meaning "take spare room in proportion to my own label width". The
 * default for every labelled button ([MorphTextButton], [MorphActionButton]); icon-only buttons
 * keep 0 and therefore their size.
 */
internal const val GroupWeightProportional = -1f

/** Receiver for [ExpressiveButtonGroup]'s children. */
@Stable
object ExpressiveButtonGroupScope {
}

/**
 * Splits buttons of the given [widths] into lines for a group [maxWidth] wide, [gap] apart. Greedy
 * like a FlowRow first, then balanced: same line count, break points moved so lines are as even as
 * possible.
 */
internal fun balancedLineBreaks(widths: IntArray, gap: Int, maxWidth: Int): List<IntArray> {
    fun breakInto(cap: Int): List<IntArray> {
        val result = ArrayList<IntArray>()
        var cur = ArrayList<Int>()
        var used = 0
        for (i in widths.indices) {
            val add = widths[i] + if (cur.isEmpty()) 0 else gap
            if (cur.isNotEmpty() && used + add > cap) {
                result.add(cur.toIntArray()); cur = ArrayList(); used = 0
            }
            cur.add(i)
            used += if (cur.size == 1) widths[i] else add
        }
        if (cur.isNotEmpty()) result.add(cur.toIntArray())
        return result
    }
    val greedy = breakInto(maxWidth)
    if (greedy.size <= 1 || maxWidth == Int.MAX_VALUE) return greedy
    var lo = minOf(widths.max(), maxWidth)
    var hi = maxWidth
    while (lo < hi) {
        val mid = (lo + hi) ushr 1
        if (breakInto(mid).size <= greedy.size) hi = mid else lo = mid + 1
    }
    return breakInto(lo)
}
