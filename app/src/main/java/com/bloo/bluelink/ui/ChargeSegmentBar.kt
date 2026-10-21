package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bloo.uicommon.rememberChargeFillColors
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import com.bloo.uicommon.LocalPowerPolicy
import com.bloo.uicommon.lowPowerAwareSpring
import com.bloo.uicommon.SoftDamping

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
    /** Plugged in (charging or not): the already-full part gets a slight sheen while it is. */
    plugged: Boolean = charging,
    modifier: Modifier = Modifier,
    /**
     * True while the hero card is COLLAPSED (the small readout). The remaining/trailing track is
     * drawn darker then, so the coloured fill reads clearly against it on the compact bar;
     * expanded, the longer bar keeps the lighter track it always had.
     */
    collapsed: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val limit = limitPct?.takeIf { it in 1..99 }
    // The trailing track has to read against whatever is ACTUALLY behind the bar, and that flips with
    // the state:
    //  - collapsed, the card is its plain surface (no photo), so the track is the surface's own
    //    content tone -- dark on a light card, light on a dark one;
    //  - expanded, the bar sits on the photo and its scrim, and the scrim is the theme's OPPOSITE
    //    tone (black in light, white in dark), so the track is the on-photo colour.
    // These two were swapped, which left the track nearly invisible in every combination.
    val trackColor = if (collapsed) scheme.onSurface.copy(alpha = 0.30f) else heroOnPhoto().copy(alpha = 0.42f)
    // The past-the-limit zone. The trailing (dimmed) segments FLIP between collapsed and expanded,
    // on top of the theme's own inversion.
    val darkCardBehind = appIsDarkTheme() == collapsed
    val heavyScrim = darkCardBehind
    val farBackdropColor = if (heavyScrim) Color.White.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.24f)
    val trackDimColor = if (heavyScrim) Color.White.copy(alpha = 0.13f) else Color.Black.copy(alpha = 0.14f)
    // Springing both gradient stops gives that moment an actual transition instead of a colour
    // popping mid-draw.
    val fill = rememberChargeFillColors(stuckAtLimit, lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow))
    val limitAnim = remember { Animatable(0f) }
    var limitSeen by remember { mutableStateOf(false) }
    // Read here, at composable scope, not inline inside the LaunchedEffect below --
    // lowPowerAwareSpring is itself @Composable, so it can't be called from a suspend lambda.
    val limitSpring = lowPowerAwareSpring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
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
    // It steps at ~20fps rather than the display's rate (see rememberSlowShimmer): the band is a
    // wide, slow sweep, so 20 steps a second is smooth and it is a third of the redraws of a 60Hz
    // one -- a real drain on a phone that sits on the charge screen for an hour.
    val shimmer: State<Float>? = if (plugged && LocalPowerPolicy.current.ambientMotion) {
        rememberSlowShimmer(periodMs = 2600L)
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
                    listOf(fill.fillDark, fill.fillLight),
                    startX = 0f,
                    endX = layout.fillWidth,
                ),
                size = Size(layout.fillWidth, h),
                cornerRadius = radius,
            )
            // The shimmer band: transparent everywhere except a soft white peak that travels with
            // shimmerX. Drawn as a SECOND rounded rect the same size as the fill (rather than a
            // separate clip) -- drawRoundRect only lights up the pixels its own shape covers, so
            // this rides on top of the gradient above without needing to clip anything itself.
            if (shimmer != null) {
                val bandWidth = layout.fillWidth * 0.35f
                // -0.6 .. 1.6: starts off the left edge and runs off the right before the sawtooth
                // restarts, so the band is fully clear of the fill at each wrap. Read here, in the
                // DrawScope, so the 20fps step invalidates a DRAW rather than recomposing the bar.
                val bandCenter = layout.fillWidth * (-0.6f + shimmer.value * 2.2f)
                drawRoundRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent),
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

/** How often the charging shimmer advances, in nanos: ~20fps. See [rememberSlowShimmer]. */
private const val ShimmerStepNanos = 50_000_000L

/**
 * A 0..1 sawtooth that advances at ~20fps rather than the display's refresh rate. It still rides the
 * frame clock (so it stops with the window, never ticks in the background), but only PUBLISHES a
 * value every [ShimmerStepNanos]; the bar's Canvas then redraws ~20 times a second instead of 60-120.
 * On a wide, slow shimmer band the step is invisible, and it is a real saving on a phone that sits on
 * the charging screen for an hour.
 */
@Composable
private fun rememberSlowShimmer(periodMs: Long): State<Float> {
    val state = remember { mutableFloatStateOf(0f) }
    val periodNanos = periodMs * 1_000_000L
    LaunchedEffect(periodNanos) {
        val start = System.nanoTime()
        while (true) {
            // A plain delay, not withFrameNanos: waiting on every vsync kept the frame clock awake at the
            // panel's full rate for as long as the car charged, only to skip most of those frames.
            kotlinx.coroutines.delay(ShimmerStepNanos / 1_000_000L)
            val now = System.nanoTime()
            state.floatValue = ((now - start) % periodNanos).toFloat() / periodNanos.toFloat()
        }
    }
    return state
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
    // eats the whole shape, so 3% and 0% would otherwise draw the same nothing. This is the
    // CONCEPTUAL current-charge boundary -- the fill segment's own width is derived from it below,
    // shrunk by half the gap.
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
