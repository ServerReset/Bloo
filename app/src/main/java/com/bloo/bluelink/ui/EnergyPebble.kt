package com.bloo.bluelink.ui

/** Charge/fuel pebbles: ChargePebble, FuelPebble, chargerLabel, fmtMinutes, degLabel. */

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
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.DEFAULT_AC_CHARGE_LIMIT_PCT
import com.bloo.bluelink.data.DEFAULT_DC_CHARGE_LIMIT_PCT
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.isPluggedOrCharging

/**
 * Charge pebble: collapsed shows the charge start/stop control; expand to set the charge limits.
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

    // Separate AC (level-2) and DC (fast) limit targets, seeded from the shared
    // DEFAULT_*_CHARGE_LIMIT_PCT until the car's real targets load. "Set" sends both values as a
    // pair, so a wrong seed would push a value the user never chose.
    var acLimit by remember(v.vin) { mutableIntStateOf(DEFAULT_AC_CHARGE_LIMIT_PCT) }
    var dcLimit by remember(v.vin) { mutableIntStateOf(DEFAULT_DC_CHARGE_LIMIT_PCT) }
    // Seeded independently, one latch each, so a car that reports AC before DC still picks up the
    // DC target. Canada never reports reservChargeInfos, so its pills are hidden (see
    // Brand.supportsChargeLimits).
    var acSeeded by remember(v.vin) { mutableStateOf(false) }
    var dcSeeded by remember(v.vin) { mutableStateOf(false) }
    // Keying on the VIN alone ran this exactly once, at first composition -- which is almost always
    // before the car's status has arrived, since the pebble composes as soon as the garage does and
    // the status fetch lands later.
    val acReported = ev?.reservChargeInfos?.level(1)
    val dcReported = ev?.reservChargeInfos?.level(0)
    LaunchedEffect(v.vin, acReported, dcReported) {
        if (!acSeeded) acReported?.let { acLimit = it; acSeeded = true }
        if (!dcSeeded) dcReported?.let { dcLimit = it; dcSeeded = true }
    }
    // Collapsing throws away limits that were dragged but never applied: re-opening the pebble
    // shows the targets the car actually has. (The latches above only ever seed once, so without
    // this a half-edited slider would survive a close and open and look like the car's real
    // setting.)
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
            // Running means the button is Stop: the deny red.
            activeContainer = denyTone().container,
            activeContent = denyTone().content,
        ),
    ) {
        // Charge limits are EDITABLE here (see the AC/DC pills below, which replaced an earlier
        // read-only ChargeFuelBar state). ChargeFuelBar itself only renders in a forced-open
        // context for a brand that cannot report limits at all (Canada) -- there would otherwise be
        // nothing to show.
        if (LocalForceExpanded.current && !v.brand.supportsChargeLimits) {
            ChargeFuelBar(
                status,
                state.hasBattery(v),
                state.hasFuel(v),
                state.drivingLabel(v),
                metric = LocalAppearance.current.metricDistance,
            )
        }
        // Its own PopVisible: this row arrives/leaves live while the pebble is open -- plugging or
        // unplugging the car doesn't require re-expanding to see it change.
        PopVisible(visible = plugged) {
            chargerLabel(ev?.batteryPlugin)?.let { StatusRow("Charger", it) }
        }
        // Shown only for brands that can actually report the targets. Canada can't
        // (reservChargeInfos is always null), so the sliders would sit on the 80/90 display
        // defaults and "Set" would push a value the user never chose to the car -- so we hide them
        // entirely there. See Brand.supportsChargeLimits.
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
 * The energy pebble for a gas/hybrid car: fuel level + range, no charge UI. Uses the "charge" slot
 * so order/collapse state carry over.
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
    Pebble(
        v, "fuel", "Fuel", Icons.Filled.LocalGasStation, state, vm, modifier,
        summary = summary,
    ) {
        // The two StatusRows carry the detail; no hero repeating the summary.
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
 * A climate setpoint rendered in the user's chosen unit. Non-numeric values pass through with a
 * bare degree sign. [sourceUnit] is the API's own unit code for this value -- 0 Celsius, 1
 * Fahrenheit. That is exactly what happened -- the four setpoint call sites were updated to pass
 * the unit and failed to compile against this wrapper.
 */
internal fun degLabel(valueF: String, fahrenheit: Boolean, sourceUnit: Int? = null): String =
    com.bloo.bluelink.data.degLabel(valueF, fahrenheit, sourceUnit)
