package com.bloo.bluelink.ui

import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Density

/** Cache of resting child widths for one [ExpressiveButtonGroup]. See its own comment for why
 *  this is a plain object and not snapshot state. */
internal class NaturalWidths {
    /**
     * The standalone button's own single resting width, cached from its last resting measure.
     *
     * Only [SafeExpansiveButton]'s non-group path uses this; the group derives its widths from
     * intrinsics instead ([content]/[compact]) and needs no measured cache at all.
     */
    var widths: IntArray? = null

    /** What each child's content asked for -- the floor a donor stops at. */
    var content: IntArray? = null

    /** The icon-only width each member falls back to when the line cannot fit the labels. */
    var compact: IntArray? = null
}


/** Carries a child's live press fraction to [ExpressiveButtonGroup]'s measure policy. The
 *  fraction is a lambda, not a value, so the group reads it during layout instead of the child
 *  having to recompose to report it. */
internal data class ExpressiveGroupData(
    val pressFraction: () -> Float,
    /**
     * Share of the row's leftover space this member takes, 0 to opt out.
     *
     * The group's own answer to Modifier.weight, which cannot reach it: weight is RowScope
     * parent data read by a Row's measure policy, and a child of this group is not a child of a
     * Row. Every attempt to size a group member with Modifier.weight in this app was therefore
     * silently dead. Declaring it here means filling the row and redistributing on press are
     * the same calculation, instead of a Row doing one and the group doing the other.
     */
    val weight: Float,
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@ExpressiveGroupData
}


/**
 * A [ExpressiveGroupData.weight] meaning "take spare room in proportion to my own label width".
 * The default for every labelled button ([MorphTextButton], [MorphActionButton]); icon-only buttons
 * keep 0 and therefore their size.
 */
internal const val GroupWeightProportional = -1f


/** Receiver for [ExpressiveButtonGroup]'s children. */
@Stable
object ExpressiveButtonGroupScope {
    /**
     * One button in the group. Its own press grows it and squeezes its neighbours; being
     * squeezed by a neighbour is what makes the effect read as buttons physically pushing each
     * other rather than each one popping in isolation.
     */
    @Composable
    fun GroupButton(
        interactionSource: InteractionSource,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        /** See [ExpressiveGroupData.weight]. */
        groupWeight: Float = 0f,
        content: @Composable () -> Unit,
    ) {
        val press by expressivePressFraction(interactionSource, enabled)
        Box(
            modifier
                // propagateMinConstraints (below) so the button itself fills the width the
                // group hands it -- otherwise it would sit at its own natural width inside a
                // slot growing and shrinking around it, and nothing would appear to move.
                .then(ExpressiveGroupData({ press }, groupWeight)),
            propagateMinConstraints = true,
        ) {
            // FALSE inside, exactly as SafeExpansiveButton's own group branch does it: this
            // slot has already joined the group on the child's behalf, and without this the
            // MorphButton inside would join a second time (it does that itself now), adding a
            // redundant layout node and a second press spring per half whose parent data the
            // group would never read.
            CompositionLocalProvider(LocalExpressiveGroup provides false) { content() }
        }
    }
}


/**
 * Splits buttons of the given [widths] into lines for a group [maxWidth] wide, [gap] apart.
 *
 * First greedily, exactly as a FlowRow would: as many buttons as fit on each line. Then BALANCED:
 * the same number of lines, but with the break points moved so the lines are as even as they can be
 * (the narrowest cap that still fits in that many lines) -- not a full line over a stray button.
 * Each line then stretches to the full width, so evenly filled lines make evenly sized buttons.
 * Order is always preserved, and an unbounded width is one line.
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
