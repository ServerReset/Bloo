package com.bloo.uicommon

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    innerIdle: Dp = 10.dp,
    innerMorphed: Dp = 16.dp,
): Pair<Shape, Shape> {
    // The seam nub is stated in Dp and handed to CornerSize(Dp) directly, rather than converted
    // into a percent-of-short-side. It used to take the row's measured height and divide, which
    // is a roundabout way of writing an absolute radius -- and an expensive one: the caller had
    // to measure the row with onSizeChanged, write that height to state, and recompose the whole
    // row to rebuild these shapes, every time the height changed. Two absolute radii also
    // interpolate cleanly, which a percent and a Dp cannot.
    val inner = CornerSize(innerIdle + (innerMorphed - innerIdle) * morph)
    val outer = CornerSize(percent = cornerPercent)
    fun corners(outerOnStart: Boolean) = RoundedCornerShape(
        topStart = if (outerOnStart) outer else inner,
        bottomStart = if (outerOnStart) outer else inner,
        topEnd = if (outerOnStart) inner else outer,
        bottomEnd = if (outerOnStart) inner else outer,
    )
    return corners(true) to corners(false)
}
