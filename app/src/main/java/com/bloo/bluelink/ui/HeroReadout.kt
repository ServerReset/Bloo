package com.bloo.bluelink.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
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
    /** Whether the state line is worth bolding: charging, or actually moving. */
    val emphasizeStatus: Boolean,
    /**
     * Target fill, 0..1. Deliberately the TARGET and not an already-animated value: each site
     * springs towards it through [animatedChargeFrac] with the same spec, so the two agree at rest
     * — and at rest is when the pebble gets toggled. Holding an animating float in here instead
     * would rebuild this object every frame.
     */
    val frac: Float,
    /** The AC/DC charge limit to mark, when plugged in and below full. */
    val limitPct: Int?,
    /** The pack reached its configured limit ("topped up"), independent of [charging]. */
    val stuckAtLimit: Boolean,
    /**
     * Plug-in hybrid only: the fuel tank alongside the pack. Null when there is no tank to show, so
     * callers need no second `hasBattery && hasFuel` test.
     */
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
    // displayChargeLimit, not targetForCurrentPlug: the latter is null when unplugged, which would
    // drop the split bar.
    val limitPct = status?.evStatus?.displayChargeLimit()?.takeIf { it in 1..99 }
    // Charging time + type go in the badge slot (parked/driving is hidden while charging).
    val chargeMinutes = status?.evStatus?.minutesToFull
    val chargeType = when (status?.evStatus?.batteryPlugin) {
        1 -> "DC"
        2 -> "AC"
        else -> null
    }
    return ChargeReadout(
        pctText = pct?.let { "$it%" } ?: "--",
        rangeText = range?.let { formatDistance(it, metric) },
        // State line: charging (time/type), then driving/parked, then a plain battery/fuel
        // descriptor.
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
            // Color.Unspecified: the render site resolves it to LocalContentColor, so "Parked"
            // tracks the on-photo colour as the card morphs. This object is shared by both
            // densities and can't hold a frame-varying colour.
            drivingLabel == "Parked" -> Color.Unspecified
            else -> {
                // Muted inherited content colour; tracks the active scheme and the hero's on-photo
                // scrim.
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

/** The one spring the charge fill uses, so collapsed and expanded bars animate identically. */
@Composable
internal fun animatedChargeFrac(target: Float): Float {
    val frac by animateFloatAsState(
        targetValue = target,
        // Critically damped. This was SoftDamping (0.82) at low stiffness, which is UNDERdamped, so
        // the fill visibly overshot the real charge and bounced back before settling.
        animationSpec = lowPowerAwareSpring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "chargeFill",
    )
    return frac
}

/**
 * The hero's readout as one set of components that morphs between collapsed and expanded. [t] is 0
 * collapsed, 1 expanded; type sizes, state-line alpha and gaps all lerp on it.
 */
/** How far above its resting position the hero photo starts (entrance) or travels to (exit). */
internal val HeroPhotoSlideDistance = 28.dp

/**
 * The COLLAPSED percentage and range, drawn as trailing content on the pebble's title Row so the
 * Row places them beside the name. A second copy exists in [HeroMorphReadout]; disjoint alpha
 * ranges keep the two from overlapping.
 */
@Composable
internal fun HeroCollapsedNumbers(
    data: ChargeReadout,
    t: Float,
    /** Reports where this row landed, in the overlay's coordinate space. */
    onPositioned: (LayoutCoordinates) -> Unit = {},
    /**
     * True once the overlay draws the real numbers; this row still measures and positions but
     * paints nothing.
     */
    hoisted: Boolean = false,
) {
    // Gone by t = 0.35, where the expanded copy starts appearing -- unless the overlay has taken
    // over, in which case this stays laid out for its whole life as the collapsed ANCHOR and simply
    // never paints.
    val fade = (1f - t / 0.35f).coerceIn(0f, 1f)
    if (fade <= 0f && !hoisted) return
    // Name/numbers alignment lives on the name's side (see PebbleShell's `atRestScale`): at rest
    // both use the identical titleMedium style, so plain CenterVertically lines them up.
    Row(
        // Leading gap off the car name; PebbleShell adds no Spacer before `titleTrailing`. Inside
        // the faded Row so it leaves with the numbers.
        Modifier
            // Keeps reporting its position but gives up its width as the card opens, so the name
            // isn't squeezed.
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(maxWidth = androidx.compose.ui.unit.Constraints.Infinity))
                val w = (p.width * fade).roundToInt().coerceAtMost(constraints.maxWidth)
                layout(w, p.height) { p.place(0, 0) }
            }
            .graphicsLayer { alpha = if (hoisted) 0f else fade }
            .padding(start = GapRow)
            .onGloballyPositioned(onPositioned),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroNumbers(data, t = 0f, verticalAlign = Alignment.CenterVertically)
    }
}

/**
 * The percentage and range as one definition, rendered by the collapsed anchor, the expanded anchor
 * and the overlay's travelling instance. The anchors are only measured; [t] drives only type size.
 */
@Composable
internal fun HeroNumbers(
    data: ChargeReadout,
    t: Float,
    width: Dp? = null,
    // Stretches the inner Row to the width the parent already resolved; ignored when [width] is
    // set.
    fillWidth: Boolean = false,
    // The status line's own fade -- see the top-level `statusAlpha` this defaults from for why it
    // isn't just `t`. Defaults to `t` so the collapsed anchor (which calls this with t = 0f and
    // never shows the line at all, see the `t > 0.01f` guard below) needs no changes at its call
    // site.
    statusAlpha: Float = t,
    /**
     * Vertical alignment for the numbers. Expanded bottom-aligns; the collapsed copy centres beside
     * the name.
     */
    verticalAlign: Alignment.Vertical = Alignment.Bottom,
) {
    val type = MaterialTheme.typography
    val pctStyle = lerp(type.titleMedium, type.displayMedium, t)
    // Expanded, the range is a headline: it is the figure the driver acts on.
    val rangeStyle = lerp(type.titleMedium, type.headlineMedium, t)
    Row(
        modifier = when {
            width != null -> Modifier.width(width)
            fillWidth -> Modifier.fillMaxWidth()
            else -> Modifier
        },
        verticalAlignment = verticalAlign,
        // With a width, the percentage sits left and the range right; collapsed it equals content
        // width.
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // RollingNumber keeps the digit roll on the only percentage instance.
        RollingNumber(
            data.pctText,
            pctStyle,
            FontWeight.Bold,
            // Collapsed charging shows in colour (no room for the word); it fades to content colour
            // on open.
            color = if (data.charging) lerp(ChargeGreen, LocalContentColor.current, t)
            else LocalContentColor.current,
        )
        Spacer(Modifier.width(lerp(8.dp, 14.dp, t)))
        Column(horizontalAlignment = Alignment.End) {
            // LocalContentColor so the range picks up the on-photo colour like the percentage.
            RollingNumber(
                data.rangeText ?: "--",
                rangeStyle,
                FontWeight.Bold,
                color = LocalContentColor.current,
            )
            // The status line ("Parked", "Charging - 25 min - DC") travels under the range,
            // right-aligned.
            val statusSlot = with(LocalDensity.current) { type.labelLarge.lineHeight.toDp() }
            Box(
                Modifier
                    .height(lerp(0.dp, statusSlot, t))
                    .clipToBounds(),
            ) {
                if (t > 0.01f) {
                    val statusColor by androidx.compose.animation.animateColorAsState(
                        data.statusColor.takeOrElse { LocalContentColor.current },
                        animationSpec = tween(MotionMedium), label = "statusLineColor",
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
