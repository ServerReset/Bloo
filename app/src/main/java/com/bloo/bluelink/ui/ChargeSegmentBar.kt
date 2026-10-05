package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.composed

/**
 * The hero's segmented charge bar and its layout math. Split out of HeroReadout.kt to separate this
 * self-contained visual from the numeric readouts (HeroNumbers/HeroMorphReadout) that call into it.
 */

/**
 * The hero's charge bar: three separately-rounded segments -- filled up to the current charge, a
 * track segment up to the limit, and a darker-backdrop dim segment past it -- or two when the
 * charge is already at (or past) its limit, since there's no "still charging toward the limit" zone
 * left to show separately.
 */
@Composable
internal fun ChargeSegmentBar(
    frac: Float,
    limitPct: Int?,
    stuckAtLimit: Boolean,
    charging: Boolean,
    modifier: Modifier = Modifier,
    /** True while the hero card is COLLAPSED (the small readout). */
    collapsed: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val limit = limitPct?.takeIf { it in 1..99 }
    val trackColor = if (collapsed) Color.White.copy(alpha = 0.28f) else scheme.onSurface.copy(alpha = 0.16f)
    // The past-the-limit zone. The trailing (dimmed) segments FLIP between collapsed and expanded,
    // on top of the theme's own inversion.
    val darkCardBehind = collapsed || appIsDarkTheme()
    val heavyScrim = darkCardBehind
    val farBackdropColor = if (heavyScrim) Color.White.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.24f)
    val trackDimColor = if (heavyScrim) Color.White.copy(alpha = 0.13f) else scheme.onSurface.copy(alpha = 0.14f)
    // Springing both gradient stops gives that moment an actual transition instead of a colour
    // popping mid-draw.
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
    // The charging shimmer's own travelling position, 0 at the fill's start and 1 at its end --
    // built (not just gated) only while charging, so an idle/parked car pays nothing for an
    // InfiniteTransition it will never render: no ticket, no per-frame invalidation, nothing
    // running in the background of a page that's sitting on a fully charged or unplugged car.
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
    // One Canvas pass costs nothing per frame that isn't already being paid for the fill's own
    // animateFloatAsState.
    Canvas(modifier.fillMaxWidth().height(ChargeBarHeight)) {
        val h = size.height
        val radius = CornerRadius(h / 2f)
        // The actual segment math lives in chargeBarLayout, a plain function with no
        // Compose/DrawScope dependency, specifically so it's unit-testable -- this Canvas lambda
        // cannot be.
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
            // The shimmer band: transparent everywhere except a soft white peak that travels with
            // shimmerX.
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
            // No limit at all, or already at/past it: one remaining segment, the ordinary track
            // colour when there's no limit to speak of, the DARKER backdrop + dim tint when the
            // charge is stuck there -- the whole remainder past the current charge means "won't
            // fill further" in that case, not "still on the way".
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
            // Two remaining segments, each its own rounded piece: current -> limit (still filling
            // toward it) and limit -> 100% (won't fill past it).
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
 * Pure segment-boundary math for [ChargeSegmentBar], pulled out of its DrawScope specifically so it
 * can be unit-tested without a Compose runtime -- see ChargeSegmentBarTest.
 * [limitFrac]/[stuckAtLimit] mirror the composable's own params. [gap] is the physical break
 * reserved on BOTH sides of every internal boundary -- between the fill and whatever follows it,
 * and (when there's a limit and it hasn't been reached) between that and the far segment too -- so
 * every piece comes out as its own separately-rounded segment rather than any two of them reading
 * as one joined shape.
 */
internal data class ChargeBarLayout(
    val fillWidth: Float,
    /**
     * True for the collapsed one-segment remainder (no limit at all, or already at/past it) --
     * [midWidth]/[farWidth] are both 0 in that case, and vice versa.
     */
    val hasSingleTrack: Boolean,
    val singleTrackStart: Float,
    val singleTrackWidth: Float,
    /**
     * Dim track when stuck at the limit, ordinary track when there's no limit to speak of -- only
     * meaningful when [hasSingleTrack].
     */
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
    // Floored at the bar's own height when there is ANY charge: below that the 50% corner radius
    // eats the whole shape, so 3% and 0% would otherwise draw the same nothing.
    val filledXRaw = if (clampedFrac <= 0f) 0f else minOf(totalWidth, maxOf(totalWidth * clampedFrac, barHeight))
    val halfGap = gap / 2f
    // Every segment's own bound is coerced against its neighbour's, the same pattern repeated at
    // each boundary: shrink towards the gap first, never past 0 width and never past the far edge
    // of the bar, so a transient animation frame (the fill still catching up to a just-lowered
    // limit, the limit sitting right next to the fill, a charge near 0% or 100%) can only ever
    // yield the gap or a zero-width segment, never a negative one or an overflow.
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
