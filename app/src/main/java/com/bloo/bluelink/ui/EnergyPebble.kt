package com.bloo.bluelink.ui

/**
 * Charge/fuel pebbles: ChargePebble, FuelPebble, chargerLabel, fmtMinutes,
 * degLabel -- extracted from Pebbles.kt.
 */

import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.DEFAULT_AC_CHARGE_LIMIT_PCT
import com.bloo.bluelink.data.DEFAULT_DC_CHARGE_LIMIT_PCT
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.isPluggedOrCharging
import kotlinx.coroutines.flow.first


/**
 * Charge pebble: collapsed shows just the charge start/stop control; expand to
 * set the charge limit and see charging info. Long-press to drag-reorder.
 */
@Composable
internal fun ChargePebble(v: Vehicle, status: VehicleStatus?, enabled: Boolean, state: UiState, vm: AppViewModel, modifier: Modifier) {
    val ev = status?.evStatus
    val charging = ev?.batteryCharge == true
    val plugged = ev.isPluggedOrCharging
    val pending = state.isPending(v.vin, "charge")
    val limitPending = state.isPending(v.vin, "chargeLimit")
    val summary = when {
        charging -> "Charging"
        plugged -> "Plugged in · idle"
        else -> "Not plugged in"
    }

    // Separate AC (home / level-2) and DC (fast) charge-limit targets, each
    // seeded to a healthy default until the car's real targets load in. The
    // seeds are the shared DEFAULT_*_CHARGE_LIMIT_PCT constants, so the phone
    // seed can't drift from the wire defaults (this once defaulted BOTH
    // to 80%, so tapping "Set" before the real DC target loaded pushed it low).
    // Both pills' "Set" sends BOTH values together (setChargeLimits(v,
    // acLimit, dcLimit)), so leaving one un-seeded at a wrong default meant
    // tapping "Set" on just the AC pill silently reset a DC target that had
    // never actually been what it was seeded to -- and vice versa.
    var acLimit by remember(v.vin) { mutableIntStateOf(DEFAULT_AC_CHARGE_LIMIT_PCT) }
    var dcLimit by remember(v.vin) { mutableIntStateOf(DEFAULT_DC_CHARGE_LIMIT_PCT) }
    // Seeded INDEPENDENTLY, one latch each. A single `limitsSeeded` flag was set as soon as
    // EITHER limit arrived, and the effect then returned early forever -- so a car that reports
    // its AC target first and its DC target on a later poll (or not in the same payload) left
    // dcLimit pinned to the hardcoded 90 and could never pick the real one up.
    //
    // That is not a display bug. The note above records that both pills' "Set" sends BOTH values
    // together, because setChargeLimits writes them as a pair -- so tapping Set on the AC pill
    // pushed a DC limit of 90 to the CAR, a value the user never chose and the car may never have
    // had. Latching per limit shrinks that to the case where a limit has genuinely never been
    // reported, instead of the far commoner case where it merely arrived second.
    //
    // Canada is the extreme case of this: CanadaApi never populates reservChargeInfos at all,
    // so on those cars neither latch could ever close. That is exactly why the pills below are
    // hidden for Canada (see Brand.supportsChargeLimits) -- the seeding here would never fire,
    // Set would only ever send the 80/90 defaults, so the whole editable control is suppressed.
    var acSeeded by remember(v.vin) { mutableStateOf(false) }
    var dcSeeded by remember(v.vin) { mutableStateOf(false) }
    // Keyed on the reported NUMBERS, not just the VIN. Keying on the VIN alone ran this exactly
    // once, at first composition -- which is almost always before the car's status has arrived,
    // since the pebble composes as soon as the garage does and the status fetch lands later. `ev`
    // was therefore null on the only pass that could seed, neither latch ever closed, and both
    // limits stayed pinned to the constants above for the rest of the session. That defeats the
    // entire per-limit latch below it: tapping Set then pushed 80/90 to the car, the exact harm
    // the comment above describes, on EVERY car rather than only ones that never report a target.
    //
    // Keying on the two Ints instead of on reservChargeInfos answers the original worry, which
    // was that the object churns on every charging-status poll: these restart only when a
    // reported target actually CHANGES value, and the latches then no-op. So the effect settles
    // after the first real payload and stops competing with the user's slider.
    val acReported = ev?.reservChargeInfos?.level(1)
    val dcReported = ev?.reservChargeInfos?.level(0)
    LaunchedEffect(v.vin, acReported, dcReported) {
        if (!acSeeded) acReported?.let { acLimit = it; acSeeded = true }
        if (!dcSeeded) dcReported?.let { dcLimit = it; dcSeeded = true }
    }
    // Collapsing throws away limits that were dragged but never applied: re-opening the pebble shows
    // the targets the car actually has. (The latches above only ever seed once, so without this a
    // half-edited slider would survive a close and open and look like the car's real setting.)
    val expanded = LocalForceExpanded.current || state.isPebbleExpanded(v.vin, "charge")
    LaunchedEffect(expanded) {
        if (!expanded) {
            acReported?.let { acLimit = it }
            dcReported?.let { dcLimit = it }
        }
    }

    Pebble(
        v, "charge", "Charge", Icons.Filled.Bolt, state, vm, modifier,
        summary = summary,
        headerAction = PebbleHeaderAction(
            label = if (charging) "Stop" else "Start",
            icon = Icons.Filled.Bolt,
            onClick = { if (charging) vm.stopCharge(v) else vm.startCharge(v) },
            enabled = plugged,
            pending = pending,
            active = charging,
            activeContainer = ChargeGreen,
            activeContent = Color.White,
        ),
    ) {
        // Charge limits are EDITABLE here (see the AC/DC pills below, which replaced
        // an earlier read-only ChargeFuelBar state). ChargeFuelBar itself only renders
        // in a forced-open context for a brand that cannot report limits at all
        // (Canada) -- there would otherwise be nothing to show.
        if (LocalForceExpanded.current && !v.brand.supportsChargeLimits) {
            ChargeFuelBar(
                status,
                state.hasBattery(v),
                state.hasFuel(v),
                state.drivingLabel(v),
                metric = LocalAppearance.current.metricDistance,
            )
        }
        // Its own PopVisible: this row arrives/leaves live while the pebble is open --
        // plugging or unplugging the car doesn't require re-expanding to see it change.
        PopVisible(visible = plugged) {
            chargerLabel(ev?.batteryPlugin)?.let { StatusRow("Charger", it) }
        }
        // Shown only for brands that can actually report the targets. Canada can't
        // (reservChargeInfos is always null), so the sliders would sit on the 80/90 display
        // defaults and "Set" would push a value the user never chose to the car -- so we hide
        // them entirely there. See Brand.supportsChargeLimits.
        if (v.brand.supportsChargeLimits) {
            ChargeLimitPill(
                label = "AC (home) limit",
                icon = Icons.Filled.Power,
                limit = acLimit,
                pending = limitPending,
                enabled = enabled,
                onValueChange = { acLimit = it },
                onApply = { vm.setChargeLimits(v, acLimit, dcLimit) },
            )
            ChargeLimitPill(
                label = "DC (fast) limit",
                icon = Icons.Filled.Bolt,
                limit = dcLimit,
                pending = limitPending,
                enabled = enabled,
                onValueChange = { dcLimit = it },
                onApply = { vm.setChargeLimits(v, acLimit, dcLimit) },
            )
        }
    }
}

/**
 * The energy pebble for a gas/hybrid car: fuel level + range, no charge UI at
 * all. Occupies the same "charge" slot so order/collapse state carry over.
 */
@Composable
internal fun FuelPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel, modifier: Modifier) {
    val metric = LocalAppearance.current.metricDistance
    val fuelPct = status?.fuelLevel
    val range = status?.dte?.value?.toInt()
    val summary = when {
        fuelPct != null && range != null -> "$fuelPct% · ${formatDistance(range, metric)}"
        fuelPct != null -> "$fuelPct%"
        range != null -> "${formatDistance(range, metric)}"
        else -> "--"
    }
    // NOT alwaysExpandedInSimpleMode: that flag is for pebbles with a single setting
    // that reads better inline without an expand/collapse control (see its own doc).
    // This one renders both a fuel-level row and a range row, so forcing it always
    // open in simple mode just removed the ability to collapse it.
    Pebble(
        v, "fuel", "Fuel", Icons.Filled.LocalGasStation, state, vm, modifier,
        summary = summary,
    ) {
        // The old cover hero repeated the pebble summary ("84% · 120 mi") that the
        // headline already carries, with the two StatusRows below carrying the detail --
        // the percentage used to appear three times on this tile.
        PebbleStatusGate(status, state.refreshing) { status ->
            fuelPct?.let { StatusRow("Fuel level", "$it%") }
            range?.let { StatusRow("Range (distance to empty)", formatDistance(it, metric)) }
            if (fuelPct == null && range == null) Text("No fuel data reported.")
        }
    }
}

internal fun chargerLabel(plugin: Int?): String? = com.bloo.bluelink.data.chargerLabel(plugin)

internal fun fmtMinutes(min: Int) = com.bloo.bluelink.data.fmtMinutes(min)

/**
 * A climate setpoint rendered in the user's chosen unit. Non-numeric values pass
 * through with a bare degree sign.
 *
 * [sourceUnit] is the API's own unit code for this value -- 0 Celsius, 1
 * Fahrenheit. It has to be forwarded rather than dropped: this file-private
 * wrapper SHADOWS the shared function for every call site in this file, so a
 * two-argument version here silently pinned all of them to the old
 * assume-Fahrenheit behaviour no matter what the shared one learned to do.
 * That is exactly what happened -- the four setpoint call sites were updated
 * to pass the unit and failed to compile against this wrapper.
 */
internal fun degLabel(valueF: String, fahrenheit: Boolean, sourceUnit: Int? = null): String =
    com.bloo.bluelink.data.degLabel(valueF, fahrenheit, sourceUnit)
