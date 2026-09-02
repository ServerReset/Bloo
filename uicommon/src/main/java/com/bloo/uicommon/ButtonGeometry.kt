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
fun connectedGroupShape(
    index: Int,
    count: Int,
    cornerPercent: Int,
    /**
     * The same 0..1 morph the segment's own MorphButton is running.
     *
     * The seam used to be a flat 12dp that ignored the morph entirely, while the split pill
     * next to it interpolated 10dp -> 16dp through exactly this value. Two different seams in
     * one app, and the static one read as a sharp corner beside pill-round neighbours. Both now
     * come from [seamCorner], so a connected group and a split pill are the same geometry.
     */
    morph: Float = 0f,
): RoundedCornerShape {
    val outer = CornerSize(percent = cornerPercent)
    val inner = seamCorner(morph)
    val startCorner = if (index == 0) outer else inner
    val endCorner = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = startCorner, bottomStart = startCorner, topEnd = endCorner, bottomEnd = endCorner)
}

/** The shared inner (seam) corner: a soft nub at rest that opens toward the morphed radius as
 *  the segment is pressed. Stated in Dp, so it is the same physical corner at any row height. */
fun seamCorner(morph: Float, idle: Dp = 10.dp, morphed: Dp = 16.dp): CornerSize =
    CornerSize(idle + (morphed - idle) * morph)

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
    val inner = seamCorner(morph, innerIdle, innerMorphed)
    val outer = CornerSize(percent = cornerPercent)
    fun corners(outerOnStart: Boolean) = RoundedCornerShape(
        topStart = if (outerOnStart) outer else inner,
        bottomStart = if (outerOnStart) outer else inner,
        topEnd = if (outerOnStart) inner else outer,
        bottomEnd = if (outerOnStart) inner else outer,
    )
    return corners(true) to corners(false)
}
