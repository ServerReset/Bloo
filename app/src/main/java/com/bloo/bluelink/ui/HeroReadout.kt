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
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.displayChargeLimit
import com.bloo.bluelink.data.targetForCurrentPlug
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

internal class ChargeReadout(
    val pctText: String,
    val rangeText: String?,
    val statusLine: String,
    val statusColor: Color,
    val charging: Boolean,
    /** Whether the state line is worth bolding -- charging, or actually moving. Derived
     *  here rather than re-tested at the render site, which needed [drivingLabel] passed
     *  alongside a [ChargeReadout] that had already consumed it. */
    val emphasizeStatus: Boolean,
    /**
     * Target fill, 0..1. Deliberately the TARGET and not an already-animated value:
     * each site springs towards it through [animatedChargeFrac] with the same spec,
     * so the two agree at rest — and at rest is when the pebble gets toggled. Holding
     * an animating float in here instead would rebuild this object every frame.
     */
    val frac: Float,
    /** The AC/DC charge limit to mark, when plugged in and below full. */
    val limitPct: Int?,
    /**
     * The pack has reached (or passed) its own configured limit -- "topped up," not
     * "still filling." Independent of [charging]: a car reported as charged to its
     * limit stays blue on this reading even hours later, unplugged, until either the
     * percentage or the limit itself changes -- there is no live session to lose.
     */
    val stuckAtLimit: Boolean,
    /** Plug-in hybrid only: the fuel tank alongside the pack. Null when there is no
     *  tank to show, so callers need no second `hasBattery && hasFuel` test. */
    val fuelPct: Int?,
)

/** Derives the [ChargeReadout] — the single source for both densities. */
@Composable
internal fun chargeReadoutOf(
    status: VehicleStatus?,
    hasBattery: Boolean,
    hasFuel: Boolean,
    drivingLabel: String?,
    metric: Boolean,
): ChargeReadout {
    val pct = status?.percentFor(hasBattery)
    val range = status?.rangeMiFor(hasBattery)
    val charging = hasBattery && status?.evStatus?.batteryCharge == true
    // displayChargeLimit, not targetForCurrentPlug directly: the latter is null the
    // instant nothing is plugged in, which used to silently drop the whole bar back to
    // a plain unsplit track (and lose the blue "topped up" state) for every parked car
    // -- see that function's own doc. Reported from a real device.
    val limitPct = status?.evStatus?.displayChargeLimit()?.takeIf { it in 1..99 }
    // Charging time + type, shown in the badge slot (replacing parked/driving,
    // which is hidden while charging) so the pebble doesn't grow taller.
    val chargeMinutes = status?.evStatus?.minutesToFull
    val chargeType = when (status?.evStatus?.batteryPlugin) {
        1 -> "DC"
        2 -> "AC"
        else -> null
    }
    return ChargeReadout(
        pctText = pct?.let { "$it%" } ?: "--",
        rangeText = range?.let { formatDistance(it, metric) },
        // The state line under the range: charging (with time/type) replaces it while
        // charging, then driving/parked, then a plain battery/fuel descriptor.
        statusLine = when {
            charging -> buildString {
                append("Charging")
                chargeMinutes?.let { append(" · ${fmtMinutes(it)}") }
                chargeType?.let { append(" · $it") }
            }
            drivingLabel != null -> drivingLabel
            else -> if (hasBattery) "Battery" else "Fuel"
        },
        statusColor = when {
            charging -> ChargeGreen
            drivingLabel == "Driving" || drivingLabel == "Running" -> MaterialTheme.colorScheme.primary
            // MutedContentAlpha compounds with the cover's already-dim default
            // container content color (surfaceVariant -> onSurfaceVariant) the same
            // way StatusRow's label and CoverHero's subline did -- this is the idle
            // "Battery"/"Fuel" caption directly under the headline percentage on the
            // Charge/Fuel cover tile, high-visibility real estate for how washed out
            // it read.
            else -> LocalContentColor.current.copy(
                alpha = if (LocalForceExpanded.current) 0.92f else MutedContentAlpha,
            )
        },
        charging = charging,
        emphasizeStatus = charging || drivingLabel == "Driving",
        frac = ((pct ?: 0).coerceIn(0, 100)) / 100f,
        limitPct = limitPct,
        stuckAtLimit = pct != null && limitPct != null && pct >= limitPct,
        fuelPct = status?.fuelLevel?.takeIf { hasBattery && hasFuel },
    )
}

/**
 * The one spring the charge fill uses, wherever the bar is drawn.
 *
 * Expressive motion: the fill settles in with a gentle overshoot. Extracted so the
 * collapsed and expanded hero bars animate identically — two hand-copied
 * `animateFloatAsState` blocks with the same numbers is exactly the drift this
 * refactor exists to remove.
 */
@Composable
internal fun animatedChargeFrac(target: Float): Float {
    val frac by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "chargeFill",
    )
    return frac
}

// ChargeStatsBlock was deleted here. It was the expanded-density readout, and it had become
// a near-duplicate of HeroMorphReadout's t = 1 end: the same Row, the same weighted spacer,
// the same two RollingNumbers at the same two type steps. ChargeFuelBar now calls
// HeroMorphReadout directly, so there is exactly ONE readout implementation serving the
// phone hero, the flip cover's tile and the EV Charge pebble.

// ChargeStatsLine was deleted here. It drew the collapsed one-line copy of the
// percentage and range for the hero's title row, and there is no collapsed copy any
// more -- HeroMorphReadout below is one set of components that morphs between both
// densities, so the second implementation has nothing left to render.

// heroReadoutReserve() was deleted here. It measured the height the collapsed header had to
// leave for an absolutely-positioned readout. The collapsed card is now two ordinary rows --
// the numbers beside the name in the header's own row, the bar under them in headerContent --
// so the header reserves the right space by CONTAINING the content instead of by computing a
// height that has to match it. That removes the class of bug this constant kept producing.

// HeroCollapsedStats() was deleted here. It rendered a SECOND copy of the percentage and range
// beside the car name, crossfading against HeroMorphReadout's copy on heroT. Two renderings of
// the same digits at similar weight, both half-visible mid-morph, is what actually read as
// rough -- and no amount of tuning the two alphas fixes a duplicate. There is now one readout,
// visible in both states, that moves and changes shape; see HeroMorphReadout.
//
// Its scale-not-lerp argument was still correct for what it was doing, and is preserved where
// it now applies: nothing depended on ITS size, whereas the surviving readout's Column height
// must grow, which is why that one lerps real type steps.


/**
 * The hero's readout as ONE set of components that morphs between the collapsed and expanded
 * states, rather than two sets trading places.
 *
 * [t] is 0 collapsed, 1 expanded, and everything here is a lerp on it: the percentage's and
 * range's type sizes, the state line's alpha, the gaps. There is exactly one [RollingNumber]
 * per number and one [ChargeSegmentBar] in the whole card, so nothing can be duplicated and
 * nothing can drift.
 *
 * NO `SharedTransitionLayout`, and that is the point. Three earlier attempts used
 * `sharedBounds`, which needs a `LookaheadScope` -- `SharedBoundsNode` implements
 * `ApproachLayoutModifierNode`, so it participates in layout, and the hero sits on every car
 * page. With `beyondViewportPageCount = 1` that meant three lookahead scopes measuring twice
 * at 60Hz during a pager drag, which is what made the car swipe judder. It could not be tuned
 * out either: `RemeasureToBounds` re-lays out text every frame, and `ScaleToBounds` draws the
 * entering node at the wrong scale.
 *
 * The travel is FREE, and that is the insight the first three attempts missed. The card's
 * height is ALREADY animating -- the photo grows and shrinks on its own transition. Anchor
 * this to the card's bottom and it rides that height change from the header down to the base
 * of the photo with no bounds animation at all. I was animating a position that something
 * else was already animating for me. Only the SIZE morph needs driving, which is what [t] does.
 *
 * Cost per frame, deliberately bounded: two `Text` measures (the two type sizes lerp) plus one
 * `Canvas`, in a single layout pass. The version that felt laggy was ~8 paragraph layouts
 * DOUBLED by a lookahead pass.
 */
/** How far above its resting position the hero photo starts (entrance) / travels to
 *  (exit) -- see the AnimatedVisibility wrapping [HeroPhotoBackdrop]. Real enough to
 *  read as arriving from somewhere, short enough that it doesn't fight the card's own
 *  height reveal for what the eye follows. */
internal val HeroPhotoSlideDistance = 28.dp

/**
 * The COLLAPSED percentage and range, drawn as trailing content on the pebble's own title Row.
 *
 * This exists because six attempts to place these numbers next to the car name by arithmetic --
 * bottom-anchoring plus a derived lift, a measured title width, a scaled ratio -- all landed
 * slightly off, in one direction or the other. The title Row can lay them out beside the name
 * exactly, for free, because that is what a Row does. PebbleShell's `titleTrailing` slot was
 * built for precisely this and its KDoc still said so while nothing used it.
 *
 * The cost, stated plainly: the numbers now have TWO instances -- this one and the expanded one in
 * [HeroMorphReadout]. The charge BAR is still a single instance. Two text copies that are never
 * both visible is a better trade than one copy whose position has to be computed from four
 * unrelated paddings, and the earlier roughness came from the two copies overlapping at similar
 * opacity, which the disjoint alpha ranges here and in [HeroMorphReadout] prevent.
 */
@Composable
internal fun HeroCollapsedNumbers(
    data: ChargeReadout,
    t: Float,
    /** Reports where this row landed, in the coordinate space the overlay uses.
     *  The title Row positions it beside the name for free -- that placement is
     *  the thing six arithmetic attempts could not reproduce -- so the way to
     *  get a single travelling copy is to keep letting the Row do the placing
     *  and then read the answer off it. */
    onPositioned: (LayoutCoordinates) -> Unit = {},
    /** True once the overlay has both anchors and is drawing the real numbers.
     *  This row then measures and positions exactly as before but paints
     *  nothing, so the title Row still reserves the right space and reports the
     *  right position. */
    hoisted: Boolean = false,
) {
    // Gone by t = 0.35, where the expanded copy starts appearing -- unless the
    // overlay has taken over, in which case this stays laid out for its whole
    // life as the collapsed ANCHOR and simply never paints.
    val fade = (1f - t / 0.35f).coerceIn(0f, 1f)
    if (fade <= 0f && !hoisted) return
    // A SEVENTH attempt at the name/numbers alignment -- CenterVertically alone (attempt six,
    // and the one this row's own doc still describes) is not quite it either, confirmed from a
    // real screenshot: the digits still sit a few px off the name's own baseline.
    //
    // The car NAME beside this row is not drawn at a native titleMedium -- it is headlineSmall,
    // measured at full size and then scaled down (see PebbleShell's title `.layout{}`), because
    // the hero's name grows continuously as the card expands and a discrete style swap cannot
    // animate that smoothly. A style scaled down by its FONT SIZE ratio does not necessarily
    // reproduce a native style's own baseline-to-box-centre ratio -- lineHeight is not always a
    // fixed fraction of fontSize across type steps -- so centring two BOXES of the same height,
    // which is all CenterVertically can do, still leaves the GLYPHS inside them a few px apart
    // whenever those two ratios differ. That gap is exactly what six earlier, purely
    // Row-alignment attempts (baseline lines through this row, RollingNumber's own Row and
    // AnimatedContent -- none of which forward one without an explicit per-child opt-in) kept
    // landing "slightly off" on.
    //
    // So this measures both font's real metrics directly instead of asking Compose's layout
    // machinery to infer them: a fixed, deterministic px correction, independent of the digit
    // roll, of which car or value is showing, and of every Row in between.
    val type = MaterialTheme.typography
    val textMeasurer = rememberTextMeasurer()
    val correctionPx = remember(type, textMeasurer) {
        val nameScale = type.titleMedium.fontSize.value / type.headlineSmall.fontSize.value
        // Baseline position relative to the MIDDLE of the text's own box -- the same axis
        // CenterVertically aligns on, so this is exactly the residual CenterVertically leaves
        // behind. Any non-empty string measures the same font metrics; "0" matches what is
        // actually on screen for the numbers.
        fun baselineFromCenter(style: TextStyle): Float {
            val result = textMeasurer.measure("0", style.copy(fontWeight = FontWeight.Bold))
            return result.firstBaseline - result.size.height / 2f
        }
        val namePx = baselineFromCenter(type.headlineSmall) * nameScale
        val numbersPx = baselineFromCenter(type.titleMedium)
        namePx - numbersPx
    }
    Row(
        // The leading gap off the car name. PebbleShell deliberately puts no Spacer
        // before `titleTrailing` -- a gap left behind an absent node would squeeze the
        // expanded title -- so the slot owns it, and this slot did not. The name ran
        // straight into the percentage: "SONATA N-Line40%". Reported from a real device.
        //
        // Inside the faded Row, so it leaves with the numbers rather than holding a
        // 10dp hole open in the title row after they have gone.
        Modifier
            .graphicsLayer { alpha = if (hoisted) 0f else fade }
            .padding(start = 10.dp)
            .offset { IntOffset(0, correctionPx.roundToInt()) }
            .onGloballyPositioned(onPositioned),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroNumbers(data, t = 0f, verticalAlign = Alignment.CenterVertically)
    }
}

/**
 * The percentage and range, as ONE definition.
 *
 * Three call sites render this and only one of them is ever visible: the
 * collapsed anchor in the title Row, the expanded anchor in the readout, and
 * the real travelling instance the overlay draws between them. That is what
 * makes the single copy true rather than nominal -- the two anchors exist to be
 * MEASURED, not read, so there is one set of glyphs on screen and one place
 * that decides what they say.
 *
 * [t] drives only the type size, because position is the overlay's job.
 */
@Composable
internal fun HeroNumbers(
    data: ChargeReadout,
    t: Float,
    width: Dp? = null,
    // Stretches the inner Row to whatever width its PARENT already resolved via its own
    // fillMaxWidth, instead of this function measuring/being handed one. Exists so a caller
    // that is already fillMaxWidth (HeroMorphReadout's un-hoisted anchor) doesn't need
    // BoxWithConstraints to hand a Dp down -- that was tried and reverted for the
    // subcomposition cost, see the call site. Ignored when [width] is set; the two are
    // mutually exclusive ways of getting the same SpaceBetween arrangement a real width.
    fillWidth: Boolean = false,
    // The status line's own fade -- see the top-level `statusAlpha` this defaults from for
    // why it isn't just `t`. Defaults to `t` so the collapsed anchor (which calls this with
    // t = 0f and never shows the line at all, see the `t > 0.01f` guard below) and the
    // CoverTile call site need no changes.
    statusAlpha: Float = t,
    /** Vertical alignment for the numbers themselves. The EXPANDED copy
     *  bottom-aligns (the range and the status line stack under the pct);
     *  the COLLAPSED instance sits beside the car name in the header row
     *  where Bottom pinned the whole block a line lower than the name
     *  ("the name and the % / mi&km aren't aligned" -- reported). The row
     *  it lives in is already centered, so the numbers should be too. */
    verticalAlign: Alignment.Vertical = Alignment.Bottom,
) {
    val type = MaterialTheme.typography
    val pctStyle = lerp(type.titleMedium, type.displayMedium, t)
    // Expanded, the range is a HEADLINE rather than a slightly-larger title. It is
    // the number a driver actually acts on -- "can I get there" -- and at titleLarge
    // it read as a caption beside the percentage instead of the second real figure
    // on the card.
    val rangeStyle = lerp(type.titleMedium, type.headlineMedium, t)
    Row(
        modifier = when {
            width != null -> Modifier.width(width)
            fillWidth -> Modifier.fillMaxWidth()
            else -> Modifier
        },
        verticalAlignment = verticalAlign,
        // Given a width, the two ends push apart: percentage on the left, range on
        // the right. Collapsed the width IS the natural content width, so
        // SpaceBetween lays out exactly as a wrapped Row would and there is no jump
        // when the arrangement starts to matter -- the gap simply opens as the card
        // does.
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // RollingNumber, not Text: this is now the ONLY instance of the percentage,
        // so it has to keep the digit roll the readout's copy used to own. Losing it
        // would have traded one animation for another rather than adding the travel.
        RollingNumber(
            data.pctText,
            pctStyle,
            FontWeight.Bold,
            // Charging shows in the COLOUR while collapsed: that row has no space for
            // the word, and the expanded readout spells it out in its state line, so
            // the cue fades back to the ordinary content colour as the card opens.
            if (data.charging) lerp(ChargeGreen, LocalContentColor.current, t)
            else LocalContentColor.current,
        )
        Spacer(Modifier.width(lerp(8.dp, 14.dp, t)))
        Column(horizontalAlignment = Alignment.End) {
            RollingNumber(data.rangeText ?: "--", rangeStyle, FontWeight.Bold)
            // The status line ("Parked", "Charging - 25 min - DC") travels WITH the
            // numbers, under the range, right-aligned to it.
            //
            // It has to live here rather than in the readout: hoisting the numbers
            // into a single travelling instance hid the readout's whole numbers row,
            // and the status line was inside it, so the expanded card simply stopped
            // saying what the car was doing. That is the regression this fixes.
            //
            // Height LERPED rather than the node being dropped, which is what made
            // the mileage "go to the top and then snap down": this Column is
            // bottom-aligned in the Row, so its bottom edge is the status line's
            // while the line exists and the RANGE's the instant it stops. Removing it
            // at the end of the collapse teleported the range down by a whole line in
            // one frame. clipToBounds because the Text keeps its intrinsic height as
            // the slot shrinks.
            //
            // Alpha uses [statusAlpha], not `t` directly -- see the top-level `val
            // statusAlpha` for why (a deliberate short delay before the line fades in,
            // requested after an earlier version tied alpha straight to `t` and it read
            // as arriving too eagerly). It's still safe against the clip-vs-alpha
            // mismatch that WAS here (alpha on an offset 0.2..1 window while height-reveal
            // ran on plain `t`, so a half-clipped glyph was also half-transparent and read
            // as stuttering): statusAlpha stays at exactly 0 -- not partway -- for the
            // whole delay, and by the time it starts moving, `t` (and so the height reveal)
            // has long since finished, so there is no partial-clip-plus-partial-opacity
            // combination left to produce.
            val statusSlot = with(LocalDensity.current) { type.labelLarge.lineHeight.toDp() }
            Box(
                Modifier
                    .height(lerp(0.dp, statusSlot, t))
                    .clipToBounds(),
            ) {
                if (t > 0.01f) {
                    val statusColor by androidx.compose.animation.animateColorAsState(
                        data.statusColor, animationSpec = tween(300), label = "statusLineColor",
                    )
                    Text(
                        data.statusLine,
                        style = type.labelLarge,
                        color = statusColor,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer { alpha = statusAlpha.coerceIn(0f, 1f) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun HeroMorphReadout(
    data: ChargeReadout,
    t: Float,
    modifier: Modifier = Modifier,
    /** Start inset for the NUMBERS row only, so it can sit after the car name while the bar
     *  below still spans the card. Zero for [ChargeFuelBar], which has no name beside it. */
    numbersStart: Dp = 0.dp,
    /** Reports where the NUMBERS row landed, for the travelling overlay. See
     *  [HeroCollapsedNumbers.onPositioned] -- same idea at the other end. */
    onNumbersPositioned: (LayoutCoordinates) -> Unit = {},
    /** True once the overlay draws the real numbers. This row keeps measuring and
     *  positioning so it stays a valid anchor, and stops painting. */
    numbersHoisted: Boolean = false,
    /** The status line's own fade, on its own delayed clock -- see the caller's
     *  `statusAlpha` for why it isn't just `t`. Defaults to `t` so the CoverTile call
     *  site (a fixed t = 1f, nothing animating) keeps behaving exactly as before. */
    statusAlpha: Float = t,
    /** Vertical alignment for the numbers themselves. The EXPANDED copy
     *  bottom-aligns (the range and the status line stack under the pct);
     *  the COLLAPSED instance sits beside the car name in the header row
     *  where Bottom pinned the whole block a line lower than the name
     *  ("the name and the % / mi&km aren't aligned" -- reported). The row
     *  it lives in is already centered, so the numbers should be too. */
    verticalAlign: Alignment.Vertical = Alignment.Bottom,
) {
    val type = MaterialTheme.typography
    // Real type steps, lerped -- not a graphicsLayer scale -- and the reason is DEPENDENT
    // LAYOUT, not glyph quality. This Column's height must genuinely grow as the numbers do:
    // the state line below has to be pushed down and the card's content has to reserve the
    // space. `graphicsLayer` explicitly does not affect that -- it "does not change the
    // measured size or placement", so siblings would stay put and the scaled digits would
    // draw OVER them. [HeroCollapsedStats] (now deleted) could scale precisely because nothing depended on its
    // size; this cannot.
    //
    // So this pays a real cost, knowingly: a `Text` measures through the SINGLE-SLOT
    // ParagraphLayoutCache, so a per-frame font size misses it every frame. Bounded to two
    // Text nodes in one layout pass, with no lookahead pass doubling it.
    //
    // (The previous claim here -- that "a scaled 45sp glyph is soft at every intermediate
    // frame" -- was not a verified mechanism, and it contradicted the since-deleted
    // HeroCollapsedStats' comment
    // arguing the reverse. If this ever needs to become free, the move is Compose's own:
    // sharedBounds with scaleToBounds + skipToLookaheadSize, which scales a layout measured
    // once. That was tried and reverted for a different reason -- the lookahead cost on every
    // pager page -- documented in this function's KDoc above.)
    // The type scale for the numbers lives in [HeroNumbers] now, with the numbers
    // themselves. It was duplicated here, and a second copy of a lerped type scale
    // is exactly the drift this rework exists to remove -- the two would have had to
    // be kept in step by hand for the anchor to keep describing what the overlay
    // draws.
    Column(
        // NO alpha ramp. This node is present and fully visible in BOTH states, which is the
        // whole point: one bar and one pair of numbers that move and change shape, rather than
        // two copies crossfading. The `t * t` fade that was here existed only to hide this copy
        // while a second one was drawn in the header.
        modifier,
        verticalArrangement = Arrangement.spacedBy(lerp(2.dp, 6.dp, t)),
    ) {
        // Fades IN on the back half only. The collapsed numbers are drawn by the header's own
        // title Row (see HeroCollapsedNumbers), because that is the only way to guarantee they sit
        // on the name's line -- so this copy must be invisible until that one has gone, or both
        // are on screen at once and the morph reads as a double image.
        //
        // A plain Row, not BoxWithConstraints -- that was tried (to hand HeroNumbers its own
        // measured width so this anchor doesn't render left-packed for however many frames it
        // takes the travelling overlay to hoist) and reverted for the same reason ChargeBar's
        // own KDoc already warns about a few hundred lines down: BoxWithConstraints is
        // SUBCOMPOSITION, found there once already "while chasing dropped frames in the hero's
        // collapse". This Row is present and re-measured on every frame of the whole heroT
        // transition (only its alpha changes, never its existence), so a subcomposition here
        // paid that cost every frame the card was opening or closing, times however many pager
        // pages keep this composed at once -- reported as the animation "dropping frames" after
        // that change landed.
        //
        // fillMaxWidth achieves the same thing for free: this Row already stretches to the
        // readout's full available width, and HeroNumbers' own inner Row can be told to do the
        // same (fillWidth = true) rather than being handed a measured Dp -- both end up
        // constrained to the identical width, but the fillMaxWidth version costs one ordinary
        // layout pass instead of a second, nested composition pass.
        Row(
            Modifier
                .padding(start = numbersStart)
                // fillMaxWidth so this anchor reports the readout's real span rather
                // than its own wrapped content width. The overlay lerps to that
                // width, and it is what puts the range against the right edge; a
                // wrapped anchor would have left it packed beside the percentage.
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = if (numbersHoisted) 0f
                    else ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
                }
                .onGloballyPositioned(onNumbersPositioned),
            verticalAlignment = Alignment.Bottom,
        ) {
            // ONE definition of the numbers, shared with the collapsed anchor and the
            // travelling overlay -- see [HeroNumbers]. This row's job is now only to
            // be MEASURED: it lays the numbers out where the expanded card wants them
            // and reports that, and the overlay draws the copy anyone actually sees.
            //
            // Rendering the same composable here rather than a hand-kept twin is what
            // makes the anchor trustworthy: if this drew a different size from the
            // overlay, the interpolation would be between two points that describe
            // different things, and the numbers would drift as the card opened.
            //
            // fillWidth = true, not a measured `width`: this Row is already fillMaxWidth,
            // so HeroNumbers' own inner SpaceBetween Row just needs to be told to match it
            // (see [HeroNumbers]'s own `fillWidth` param) rather than being handed the number
            // back through a subcomposition.
            HeroNumbers(data, t, fillWidth = true, statusAlpha = statusAlpha)
        }
        // Plug-in hybrid's fuel tank: expanded only, same reasoning as the state line. Fades
        // in over the back half of the morph so it does not compete with the numbers growing.
        //
        // The pump icon is here because dropping it was a second regression in my first pass
        // at this morph -- ChargeFuelBar has always drawn one, and "Fuel 40%" on its own reads
        // as another battery figure in a card that is otherwise all battery.
        data.fuelPct?.takeIf { statusAlpha > 0.01f }?.let { fuelPct ->
            val fuelColor = MaterialTheme.colorScheme.onSurfaceVariant
                .copy(alpha = statusAlpha)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocalGasStation,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = fuelColor,
                )
                Spacer(Modifier.width(6.dp))
                Text("Fuel $fuelPct%", style = type.bodyMedium, color = fuelColor, maxLines = 1)
            }
        }
        ChargeSegmentBar(
            frac = animatedChargeFrac(data.frac),
            limitPct = data.limitPct,
            stuckAtLimit = data.stuckAtLimit,
            charging = data.charging,
            // This readout's bar always sits over the hero's photo + dark
            // scrim when the card is open -- the fixed-white "won't charge
            // past here" zone reads against it, regardless of theme.
            darkBackdrop = true,
        )
    }
}

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
 * static but visually matching" -- the widget and the notification are real
 * RemoteViews/Glance surfaces with no animation APIs to reach for, so this is the
 * one place any of this can live):
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
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "chargeFillDark",
    )
    val fillLight by androidx.compose.animation.animateColorAsState(
        targetValue = if (stuckAtLimit) ChargeBlue else ChargeGreen,
        animationSpec = spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "chargeFillLight",
    )
    // Animatable, not animateFloatAsState, for the same reason the old marker's slide
    // was: snap to the first-ever value (no previous position to animate FROM when a
    // limit first appears), spring for every change after that.
    val limitAnim = remember { Animatable(0f) }
    var limitSeen by remember { mutableStateOf(false) }
    LaunchedEffect(limit) {
        val target = (limit ?: return@LaunchedEffect) / 100f
        if (limitSeen) {
            limitAnim.animateTo(target, spring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow))
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
    val shimmerX: State<Float>? = if (charging) {
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
