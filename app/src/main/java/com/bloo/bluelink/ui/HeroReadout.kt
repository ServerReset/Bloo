package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.lerp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.displayChargeLimit
import com.bloo.bluelink.data.targetForCurrentPlug

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
            // "Parked" is a real state, not a caption, and it sits on the hero's photo when
            // the card is open. It reads the SAME colour the title and the numbers use on
            // that photo -- heroOnPhoto(), which is near-WHITE in light mode and near-black in
            // dark mode -- rather than LocalContentColor. LocalContentColor is right when the
            // card is open but equals the card's own onSurface while collapsed, and the parked
            // line is the one piece the user called out as needing to stay light over the photo
            // in light mode specifically ("because of the contrast"). "Battery"/"Fuel" (the
            // fallback descriptors) keep the muted tone.
            drivingLabel == "Parked" -> heroOnPhoto()
            else -> {
                // The inherited content colour, muted -- NOT a surface role or a raw
                // isSystemInDarkTheme() test. Reading LocalContentColor tracks the active
                // scheme (incl. custom palettes) and the hero's on-photo scrim as the card opens.
                LocalContentColor.current.copy(
                    alpha = if (LocalForceExpanded.current) 0.92f else MutedContentAlpha,
                )
            }
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
        animationSpec = lowPowerAwareSpring(dampingRatio = SoftDamping, stiffness = Spring.StiffnessLow),
        label = "chargeFill",
    )
    return frac  // Extracted as a reusable composable to avoid hand-copied animations
}


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
    // The name/numbers alignment now lives entirely on the NAME's own side (see PebbleShell's
    // `atRestScale`): at rest the car name renders in a genuine, unscaled titleMedium Text --
    // the exact style HeroNumbers already uses for pctStyle at t = 0 -- so this row's plain
    // CenterVertically is centring two boxes built from the IDENTICAL style, which cannot land
    // their glyphs apart. Two earlier attempts at reconciling a SCALED headlineSmall title
    // against these numbers (a computed baseline correction, and baseline-alignment lines
    // through the several Rows and the AnimatedContent between here and the digits) both left a
    // residual few-px gap, confirmed from real screenshots -- removing the mismatch at its
    // source instead of correcting for it after the fact is what finally closes it.
    Row(
        // The leading gap off the car name. PebbleShell deliberately puts no Spacer
        // before `titleTrailing` -- a gap left behind an absent node would squeeze the
        // expanded title -- so the slot owns it, and this slot did not. The name ran
        // straight into the percentage: "SONATA N-Line40%". Reported from a real device.
        //
        // Inside the faded Row, so it leaves with the numbers rather than holding a
        // 10dp hole open in the title row after they have gone.
        Modifier
            // Keeps reporting its position but gives up its WIDTH as the card opens: once expanded this
            // anchor paints nothing, yet it used to keep reserving its full width in the title row and
            // squeezed the car's name down to "Da..." in a narrow column.
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(maxWidth = androidx.compose.ui.unit.Constraints.Infinity))
                val w = (p.width * fade).roundToInt().coerceAtMost(constraints.maxWidth)
                layout(w, p.height) { p.place(0, 0) }
            }
            .graphicsLayer { alpha = if (hoisted) 0f else fade }
            .padding(start = 10.dp)
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
    // t = 0f and never shows the line at all, see the `t > 0.01f` guard below) needs no
    // changes at its call site.
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
            color = if (data.charging) lerp(ChargeGreen, LocalContentColor.current, t)
            else LocalContentColor.current,
        )
        Spacer(Modifier.width(lerp(8.dp, 14.dp, t)))
        Column(horizontalAlignment = Alignment.End) {
            // LocalContentColor explicitly, exactly like the percentage beside it (see
            // that call's own comment): the range left its colour UNSPECIFIED, so over the
            // expanded hero it did not pick up the on-photo colour the local provider
            // travels to as the card opens -- the percentage read light in light mode while
            // "the miles" beside it stayed dark. Same provider, same colour, both numbers.
            RollingNumber(
                data.rangeText ?: "--",
                rangeStyle,
                FontWeight.Bold,
                color = LocalContentColor.current,
            )
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
                        data.statusColor, animationSpec = tween(MotionMedium), label = "statusLineColor",
                    )
                    RollingNumber(
                        data.statusLine,
                        style = type.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = statusColor,
                        modifier = Modifier.graphicsLayer { alpha = statusAlpha.coerceIn(0f, 1f) },
                    )
                }
            }
        }
    }
}
