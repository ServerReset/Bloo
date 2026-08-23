package com.bloo.uicommon

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The shared button-geometry helpers, pulled out of the app(s) so the pill
 * math — the corner percentages, the split-pill seams, the connected-group
 * silhouettes — lives in ONE place and both the phone and the watch build
 * their controls from byte-identical shapes.
 */

/** The standard text-field corner, shared by every input in both apps. */
val FieldShape: RoundedCornerShape = RoundedCornerShape(18.dp)

/**
 * Shape for segment [index] of [count] in a Material 3 "connected" button
 * group (m3.material.io/components/button-groups): the group's outer corners
 * are fully round, every seam between two segments is a small square corner
 * instead, so the row reads as one continuous shape split into parts rather
 * than a row of separate pills sitting next to each other. Pair with a 2dp
 * gap between segments -- the spec's connected-group spacing at any size.
 *
 * [cornerPercent] is the same 50 (pill) <-> 28 (pressed/active) morph every
 * MorphButton animates through -- passed in per-frame from the segment's own
 * MorphButton so a segment still visibly squeezes on press instead of being
 * frozen into a static silhouette just because it's part of a group.
 */
fun connectedGroupShape(index: Int, count: Int, cornerPercent: Int, smallCorner: Dp = 12.dp): RoundedCornerShape {
    val outer = CornerSize(percent = cornerPercent)
    val inner = CornerSize(smallCorner)
    val startCorner = if (index == 0) outer else inner
    val endCorner = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = startCorner, bottomStart = startCorner, topEnd = endCorner, bottomEnd = endCorner)
}

/**
 * Corner shapes for the two-segment "split pill" family (the preset pill and
 * the charge-limit pill): outer corner is the pill↔rounded-square morph every
 * MorphButton runs, inner (seam) corner is a small nub that follows the same
 * morph toward the same morphed radius. A pure function of the shared morph
 * progress, so every half of every split pill draws the same geometry with
 * no per-pill animation state of its own -- the pills and the split
 * action/chevron button all speak the shared corner-percent language.
 */
fun splitPillShapes(
    morph: Float,
    cornerPercent: Int,
    rowHeight: Dp,
): Pair<Shape, Shape> {
    val base = rowHeight.value.coerceAtLeast(1f)
    // The 16dp morphed radius and 10dp idle seam nub, in the shared percent-
    // of-short-side language.
    val morphedPct = 100f * 16.dp.value / base
    val innerIdlePct = 100f * 10.dp.value / base
    val innerPct = innerIdlePct + (morphedPct - innerIdlePct) * morph
    fun corners(outerOnStart: Boolean) = RoundedCornerShape(
        topStart = CornerSize(percent = if (outerOnStart) cornerPercent else innerPct.roundToInt()),
        bottomStart = CornerSize(percent = if (outerOnStart) cornerPercent else innerPct.roundToInt()),
        topEnd = CornerSize(percent = if (outerOnStart) innerPct.roundToInt() else cornerPercent),
        bottomEnd = CornerSize(percent = if (outerOnStart) innerPct.roundToInt() else cornerPercent),
    )
    return corners(true) to corners(false)
}
