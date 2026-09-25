@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.displayChargeLimit
import com.bloo.bluelink.data.targetForCurrentPlug

/**
 * The hero's segmented charge bar and its layout math. Split out of
 * HeroReadout.kt to separate this self-contained visual from the numeric
 * readouts (HeroNumbers/HeroMorphReadout) that call into it.
 */

/**
 * The hero's charge bar: three separately-rounded segments -- filled up to the
 * current charge, a track segment up to the limit, and a darker-backdrop dim
 * segment past it -- or two when the charge is already at (or past) its limit,
 * since there's no "still charging toward the limit" zone left to show
 * separately. Each piece is its own fully-rounded pill with a real gap either
 * side of it, explicitly requested over an earlier flush, one-continuous-shape
 * version: "I want it to be three rounded segments instead of one continuous
 * bar."
 *
 * Earlier designs, in order, and why each was replaced:
 *  1. A seam where the fill ended, plus a small circular marker drawn on top at
 *     the limit -- charge sitting AT its limit (the common case) put both devices
 *     on the same pixel, "a 5dp hole under a 14dp dot."
 *  2. Three segments with a gap only at the limit split, the rest flush -- fixed
 *     (1)'s collision, but read as an uneven mix of one joined piece and one
 *     separate piece rather than a consistent shape.
 *  3. All three segments flush, no gap anywhere, legibility carried by a darker
 *     backdrop instead of any physical break -- this was mistakenly taken from
 *     a reference image showing a smooth SINGLE bar, but the actual request was
 *     for the "smooth rounded corners" style applied to each of three DISTINCT
 *     pieces, not one continuous shape. This version.
 *
 * Blue fill instead of green when the charge has reached its limit -- "topped
 * up," not "still filling" -- regardless of whether the car is actively
 * reporting a charging session, so the colour stays accurate hours after the
 * car finished charging to that limit, not just while plugged in.
 *
 * The limit split still animates: the fill springs to its target the same way it
 * always did, and the limit split slides to a new position rather than snapping
 * between two frames if the limit itself changes while charging.
 *
 * Two more animations, phone-only ("more motion on the phone card... keep others
 * static but visually matching" -- the notification is a real RemoteViews
 * surface with no animation APIs to reach for, so this is the one place any of
 * this can live):
 *  - the fill's own colour springs between green and blue rather than snapping the
 *    instant [stuckAtLimit] flips, so reaching the limit reads as the bar arriving
 *    somewhere rather than a hard colour cut mid-frame;
 *  - while [charging] is true, a soft highlight sweeps once across the filled
 *    segment on a loop -- the one piece of genuinely ambient motion on this card,
 *    there specifically to read as "still happening" during the long stretches
 *    where the fill itself has already settled and isn't moving on its own.
 */
@Composable
internal fun ChargeSegmentBar(
    frac: Float,
    limitPct: Int?,
    stuckAtLimit: Boolean,
    charging: Boolean,
    modifier: Modifier = Modifier,
    /** True when the segment sits on a genuinely dark backdrop (the hero's
     *  photo + scrim, the cover tile): the "won't charge past here" zone then
     *  paints LIGHT so it stays legible. The LocalContentColor heuristic
     *  this used got the hero wrong (HeroOnPhoto is near-white content, but
     *  the BACKDROP behind it is the dark scrim), which is exactly the two
     *  impossible-to-infer facts this override exists for. */
    darkBackdrop: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val limit = limitPct?.takeIf { it in 1..99 }
    val trackColor = scheme.onSurface.copy(alpha = 0.16f)
    // The past-the-limit zone. It used to be a genuinely darker BACKDROP (fixed
    // black, deliberately not a theme colour, on the "a black scrim is what
    // already keeps text legible over the photo" argument) -- but that made it
    // a light-gray-on-black smudge exactly where the contrast already fails:
    // on a dark hero photo the dim gray almost disappears, and on a light card
    // it reads as nothing. The bar now matches its own host's contrast
    // language instead: LocalContentColor is the SAME tone every surrounding
    // element already checked against this backdrop (HeroOnPhoto white over the
    // photo scrim, onSurface on flat cards), so the "won't fill past here"
    // zone paints WHITE-on-dark and DARK-on-light by construction -- black
    // backdrop -> white segment, white backdrop -> dark segment, and it tracks
    // whatever the backdrop behind the segment actually is (the hero photo
    // scrim, a pebble card, the cover tile) because it inherits the reader's
    // own text colour rather than guessing a colour itself.
    val heavyScrim = darkBackdrop || LocalContentColor.current.luminance() < 0.5f
    val farBackdropColor = if (heavyScrim) Color.White.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.24f)
    val trackDimColor = if (heavyScrim) Color.White.copy(alpha = 0.13f) else scheme.onSurface.copy(alpha = 0.14f)
    // Sprung, not a plain `if`: this used to pick the two-item colour list outright,
    // so a car finishing its last percent to the limit cut from green to blue on
    // whatever single frame stuckAtLimit flipped. Springing both gradient stops gives
    // that moment an actual transition instead of a colour popping mid-draw.
    val fillDark by androidx.compose.animation.animateColorAsState(
        targetValue = if (stuckAtLimit) ChargeBlueDark else ChargeGreenDark,
        animationSpec = lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "chargeFillDark",
    )
    val fillLight by androidx.compose.animation.animateColorAsState(
        targetValue = if (stuckAtLimit) ChargeBlue else ChargeGreen,
        animationSpec = lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "chargeFillLight",
    )
    // Animatable, not animateFloatAsState, for the same reason the old marker's slide
    // was: snap to the first-ever value (no previous position to animate FROM when a
    // limit first appears), spring for every change after that.
    val limitAnim = remember { Animatable(0f) }
    var limitSeen by remember { mutableStateOf(false) }
    // Read here, at composable scope, not inline inside the LaunchedEffect below --
    // lowPowerAwareSpring is itself @Composable, so it can't be called from a suspend lambda.
    val limitSpring = lowPowerAwareSpring<Float>(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow)
    LaunchedEffect(limit) {
        val target = (limit ?: return@LaunchedEffect) / 100f
        if (limitSeen) {
            limitAnim.animateTo(target, limitSpring)
        } else {
            limitSeen = true
            limitAnim.snapTo(target)
        }
    }
    // The charging shimmer's own travelling position, 0 at the fill's start and 1 at
    // its end -- built (not just gated) only while charging, so an idle/parked car
    // pays nothing for an InfiniteTransition it will never render: no ticket, no
    // per-frame invalidation, nothing running in the background of a page that's
    // sitting on a fully charged or unplugged car.
    // The STATE, not its value. `by` reads at the use site, and the use site was this
    // composable's body -- so a charging car recomposed this bar on every display frame,
    // indefinitely, re-running its colour animations and rebuilding the Canvas lambda. Not
    // during a gesture: for as long as the car is plugged in. Everything else in this file was
    // moved into draw scope for exactly this reason; the shimmer was the one that leaked.
    // Read inside the Canvas below instead, where it invalidates draw and nothing more.
    // Also gated on battery saver, same reasoning as the idle-car case just above but for a
    // different resource: this is the one INDEFINITELY-repeating animation in the whole phone
    // UI, ticking every frame for as long as the car stays plugged in -- which, overnight,
    // is hours. Every other animation in this app plays once and stops; this is the one where
    // "reduce animations under low power" has a real, continuous frame budget to actually give
    // back, not just a shorter one-shot transition.
    val shimmerX: State<Float>? = if (charging && !isBatterySaverOn()) {
        val shimmer = rememberInfiniteTransition(label = "chargeShimmer")
        shimmer.animateFloat(
            initialValue = -0.6f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
            label = "chargeShimmerX",
        )
    } else {
        null
    }
    // DRAWN, not composed -- see the git history here for why: this used to be a
    // BoxWithConstraints holding a Row of Boxes plus an offset child for the marker, and
    // BoxWithConstraints is SUBCOMPOSITION, which cost a re-measure on every frame of the
    // fill/marker animations. One Canvas pass costs nothing per frame that isn't already
    // being paid for the fill's own animateFloatAsState.
    Canvas(modifier.fillMaxWidth().height(ChargeBarHeight)) {
        val h = size.height
        val radius = CornerRadius(h / 2f)
        // The actual segment math lives in chargeBarLayout, a plain function with no
        // Compose/DrawScope dependency, specifically so it's unit-testable -- this
        // Canvas lambda cannot be. See ChargeSegmentBarTest, which sweeps a wide range
        // of width x percent x limit combinations asserting the three-segment case
        // (fill, track-to-limit, dim-track-past-it) genuinely produces three
        // positive-width, correctly-gapped segments, not just that the formula looks
        // right by eye.
        val layout = chargeBarLayout(
            totalWidth = size.width,
            barHeight = h,
            filledFrac = frac,
            limitFrac = limit?.let { limitAnim.value },
            stuckAtLimit = stuckAtLimit,
            gap = ChargeSegmentGap.toPx(),
        )
        if (layout.fillWidth > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(fillDark, fillLight),
                    startX = 0f,
                    endX = layout.fillWidth,
                ),
                size = Size(layout.fillWidth, h),
                cornerRadius = radius,
            )
            // The shimmer band: transparent everywhere except a soft white peak that
            // travels with shimmerX. Drawn as a SECOND rounded rect the same size as
            // the fill (rather than a separate clip) -- drawRoundRect only lights up
            // the pixels its own shape covers, so this rides on top of the gradient
            // above without needing to clip anything itself. A linear (not radial)
            // brush with Transparent at both ends is safe to position anywhere,
            // including bandCenter values outside the fill's own bounds, because
            // Brush.linearGradient clamps to its end colour past start/end -- which
            // is Transparent here -- so there is no stop-ordering math to get wrong
            // as the band enters and leaves.
            if (shimmerX != null) {
                val bandWidth = layout.fillWidth * 0.35f
                val bandCenter = layout.fillWidth * shimmerX.value
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent),
                        start = Offset(bandCenter - bandWidth, 0f),
                        end = Offset(bandCenter + bandWidth, 0f),
                    ),
                    size = Size(layout.fillWidth, h),
                    cornerRadius = radius,
                )
            }
        }
        if (layout.hasSingleTrack) {
            // No limit at all, or already at/past it: one remaining segment, the
            // ordinary track colour when there's no limit to speak of, the DARKER
            // backdrop + dim tint when the charge is stuck there -- the whole
            // remainder past the current charge means "won't fill further" in
            // that case, not "still on the way".
            if (layout.singleTrackWidth > 0f) {
                val at = Offset(layout.singleTrackStart, 0f)
                val sz = Size(layout.singleTrackWidth, h)
                if (layout.singleTrackDim) {
                    drawRoundRect(color = farBackdropColor, topLeft = at, size = sz, cornerRadius = radius)
                    drawRoundRect(color = trackDimColor, topLeft = at, size = sz, cornerRadius = radius)
                } else {
                    drawRoundRect(color = trackColor, topLeft = at, size = sz, cornerRadius = radius)
                }
            }
        } else {
            // Two remaining segments, each its own rounded piece: current -> limit
            // (still filling toward it) and limit -> 100% (won't fill past it).
            if (layout.midWidth > 0f) {
                drawRoundRect(
                    color = trackColor, topLeft = Offset(layout.midStart, 0f),
                    size = Size(layout.midWidth, h), cornerRadius = radius,
                )
            }
            if (layout.farWidth > 0f) {
                val at = Offset(layout.farStart, 0f)
                val sz = Size(layout.farWidth, h)
                drawRoundRect(color = farBackdropColor, topLeft = at, size = sz, cornerRadius = radius)
                drawRoundRect(color = trackDimColor, topLeft = at, size = sz, cornerRadius = radius)
            }
        }
    }
}

/**
 * Pure segment-boundary math for [ChargeSegmentBar], pulled out of its DrawScope
 * specifically so it can be unit-tested without a Compose runtime -- see
 * ChargeSegmentBarTest. [limitFrac]/[stuckAtLimit] mirror the composable's own params.
 * [gap] is the physical break reserved on BOTH sides of every internal boundary --
 * between the fill and whatever follows it, and (when there's a limit and it hasn't
 * been reached) between that and the far segment too -- so every piece comes out as
 * its own separately-rounded segment rather than any two of them reading as one
 * joined shape.
 */
internal data class ChargeBarLayout(
    val fillWidth: Float,
    /** True for the collapsed one-segment remainder (no limit at all, or already at/past
     *  it) -- [midWidth]/[farWidth] are both 0 in that case, and vice versa. */
    val hasSingleTrack: Boolean,
    val singleTrackStart: Float,
    val singleTrackWidth: Float,
    /** Dim track when stuck at the limit, ordinary track when there's no limit to speak
     *  of -- only meaningful when [hasSingleTrack]. */
    val singleTrackDim: Boolean,
    val midStart: Float,
    val midWidth: Float,
    val farStart: Float,
    val farWidth: Float,
)

internal fun chargeBarLayout(
    totalWidth: Float,
    barHeight: Float,
    filledFrac: Float,
    limitFrac: Float?,
    stuckAtLimit: Boolean,
    gap: Float,
): ChargeBarLayout {
    val clampedFrac = filledFrac.coerceIn(0f, 1f)
    // Floored at the bar's own height when there is ANY charge: below that the 50%
    // corner radius eats the whole shape, so 3% and 0% would otherwise draw the same
    // nothing. This is the CONCEPTUAL current-charge boundary -- the fill segment's
    // own width is derived from it below, shrunk by half the gap.
    val filledXRaw = if (clampedFrac <= 0f) 0f else minOf(totalWidth, maxOf(totalWidth * clampedFrac, barHeight))
    val halfGap = gap / 2f
    // Every segment's own bound is coerced against its neighbour's, the same pattern
    // repeated at each boundary: shrink towards the gap first, never past 0 width and
    // never past the far edge of the bar, so a transient animation frame (the fill
    // still catching up to a just-lowered limit, the limit sitting right next to the
    // fill, a charge near 0% or 100%) can only ever yield the gap or a zero-width
    // segment, never a negative one or an overflow.
    val fillWidth = (filledXRaw - halfGap).coerceAtLeast(0f)

    if (limitFrac == null || stuckAtLimit) {
        val trackStart = minOf(totalWidth, filledXRaw + halfGap)
        val trackWidth = (totalWidth - trackStart).coerceAtLeast(0f)
        return ChargeBarLayout(
            fillWidth = fillWidth,
            hasSingleTrack = true,
            singleTrackStart = trackStart,
            singleTrackWidth = trackWidth,
            singleTrackDim = limitFrac != null,
            midStart = 0f, midWidth = 0f, farStart = 0f, farWidth = 0f,
        )
    }
    val limitXRaw = (totalWidth * limitFrac).coerceIn(filledXRaw, totalWidth)
    val midStart = minOf(totalWidth, filledXRaw + halfGap)
    val midEnd = (limitXRaw - halfGap).coerceIn(midStart, totalWidth)
    val midWidth = (midEnd - midStart).coerceAtLeast(0f)
    val farStart = (limitXRaw + halfGap).coerceIn(limitXRaw, totalWidth)
    val farWidth = (totalWidth - farStart).coerceAtLeast(0f)
    return ChargeBarLayout(
        fillWidth = fillWidth,
        hasSingleTrack = false,
        singleTrackStart = 0f, singleTrackWidth = 0f, singleTrackDim = false,
        midStart = midStart, midWidth = midWidth,
        farStart = farStart, farWidth = farWidth,
    )
}
