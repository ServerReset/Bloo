package com.bloo.bluelink.ui

/**
 * Diagnostics pebble: DiagRow, DiagnosticsPebble, warn/yesNo/onOff -- extracted from Pebbles.kt to
 * keep the UI file focused.
 */

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.degLabel
import com.bloo.bluelink.data.fmtMinutes
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.openLabels
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.uicommon.AppIcons
import androidx.compose.ui.graphics.vector.ImageVector

// --- Diagnostics ----------------------------------------------------------

internal data class DiagRow(val label: String, val value: String, val indent: Boolean = false)

@Composable
internal fun DiagnosticsPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel, modifier: Modifier) {
    val a = LocalAppearance.current
    val fahrenheit = a.useFahrenheit
    val metric = a.metricDistance
    // A pure EV has no tank, but the API still reports fuelLevel=0, which rendered as a misleading
    // "Fuel level 0% / Low fuel No" pair. Only show the fuel rows where there is a tank to read.
    val hasFuel = state.powertrainOf(v) != com.bloo.bluelink.data.Powertrain.EV
    val rows = remember(status, fahrenheit, metric, hasFuel) { buildList {
        status?.tirePressureLamp?.let { tp ->
            // No psi suffix. `TirePressure.all` was only ever populated FROM the warning lamp --
            // Kia read `tirePressure.all` (a 0/1 indicator) and Canada read
            // `tirePressureLamp.tirePressureLampAll` outright -- so this rendered "Warning · 1 psi"
            // and "OK · 0 psi".
            add(DiagRow("Tire pressure", if (tp.hasWarning) "Warning" else "OK"))
            tp.frontLeft?.let { add(DiagRow("Front left", warn(it), indent = true)) }
            tp.frontRight?.let { add(DiagRow("Front right", warn(it), indent = true)) }
            tp.rearLeft?.let { add(DiagRow("Rear left", warn(it), indent = true)) }
            tp.rearRight?.let { add(DiagRow("Rear right", warn(it), indent = true)) }
        }
        status?.battery?.let { b ->
            b.level?.let { soc ->
                add(DiagRow("12V battery", "$soc%"))
            }
        }
        status?.evStatus?.batteryStatus?.let { add(DiagRow("Drive battery", "$it%")) }
        status?.rangeMiFor(state.hasBattery(v))?.let { add(DiagRow("Range", formatDistance(it, metric))) }
        status?.airTemp?.let { t ->
            t.value?.let { add(DiagRow("Climate setpoint", degLabel(it, fahrenheit, t.unit))) }
        }
        if (hasFuel) {
            status?.fuelLevel?.let { add(DiagRow("Fuel level", "$it%")) }
            status?.lowFuelLight?.let { add(DiagRow("Low fuel", yesNo(it))) }
        }
        status?.washerFluidStatus?.let { add(DiagRow("Washer fluid", if (it) "Low" else "OK")) }
        status?.breakOilStatus?.let { add(DiagRow("Brake fluid", if (it) "Check" else "OK")) }
        status?.smartKeyBatteryWarning?.let { add(DiagRow("Key fob battery", if (it) "Low" else "OK")) }
        status?.steerWheelHeat?.let { add(DiagRow("Steering wheel heat", onOff(it))) }
        status?.sideBackWindowHeat?.let { add(DiagRow("Rear defroster", onOff(it))) }
        status?.sideMirrorHeat?.let { add(DiagRow("Mirror heat", onOff(it))) }
        status?.seatHeaterVentState?.let { s ->
            val seats = listOfNotNull(
                s.flSeatHeatState?.takeIf { it != 0 }?.let { "Driver" },
                s.frSeatHeatState?.takeIf { it != 0 }?.let { "Passenger" },
                s.rlSeatHeatState?.takeIf { it != 0 }?.let { "Rear-left" },
                s.rrSeatHeatState?.takeIf { it != 0 }?.let { "Rear-right" },
            )
            if (seats.isNotEmpty()) add(DiagRow("Seat heat/vent active", seats.joinToString(", ")))
        }
        status?.evStatus?.pluggedInLabel?.let { add(DiagRow("Plug", it)) }
        // fmtMinutes, not "$it min" -- the charge pebble's own "Time to full" row a few hundred
        // lines up already used it, so a 95-minute estimate read "1h 35m" there and "95 min" here,
        // in the same app on the same screen.
        status?.evStatus?.minutesToFull?.let { add(DiagRow("Time to full", fmtMinutes(it))) }
        status?.doorOpen?.openLabels()?.takeIf { it.isNotEmpty() }
            ?.let { add(DiagRow("Doors open", it.joinToString(", "))) }
        if (status?.trunkOpen == true) add(DiagRow("Trunk", "Open"))
        if (status?.hoodOpen == true) add(DiagRow("Hood", "Open"))
        if (status?.doorLock == false && status.engine != true) add(DiagRow("Lock", "Car is unlocked while parked"))
    } }
    // The count of actual problems.
    val issueCount = remember(status) {
        listOf(
            status?.tirePressureLamp?.hasWarning == true,
            status?.lowFuelLight == true,
            status?.washerFluidStatus == true,
            status?.breakOilStatus == true,
            status?.smartKeyBatteryWarning == true,
        ).count { it }
    }
    val hasWarning = issueCount > 0
    // A VERDICT, not a tally.
    val diagSummary = remember(rows, hasWarning, issueCount) {
        when {
            rows.isEmpty() -> "No data"
            !hasWarning -> "All systems OK"
            issueCount == 1 -> "1 issue"
            else -> "$issueCount issues"
        }
    }
    Pebble(
        v, "diagnostics", "Diagnostics", Icons.Filled.ErrorOutline, state, vm, modifier,
        summary = diagSummary,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        headerAction = if (hasWarning) PebbleHeaderAction(
            label = "",
            icon = AppIcons.Warning,
            onClick = { vm.togglePebble(v, "diagnostics") },
            isWarning = true,
            contentDescription = "Diagnostics warning",
        ) else null,
    ) {
        // See diagSummary above.
        if (rows.isEmpty()) {
            SecondaryText("No diagnostics yet.")
        }
        // Grouped by what the reading is about, in the order a person would check them; a group with nothing to say is left out.
        val grouped = remember(rows) { rows.groupBy { diagGroupOf(it.label) } }
        DiagGroup.entries.forEach { group ->
            val inGroup = grouped[group].orEmpty()
            if (inGroup.isEmpty()) return@forEach
            if (group == DiagGroup.Tires) {
                val verdict = inGroup.firstOrNull { !it.indent }
                PebbleSection(
                    group.title, group.icon,
                    trailing = verdict?.value,
                    trailingColor = if (verdict?.value == "OK") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                ) { WheelGrid(inGroup.filter { it.indent }) }
            } else {
                PebbleSection(group.title, group.icon) { inGroup.forEach { StatusRow(it.label, it.value) } }
            }
        }
    }
}

/** The headings of the Diagnostics pebble, in the order they appear. */
internal enum class DiagGroup(val title: String, val icon: ImageVector) {
    Tires("Tires", Icons.Filled.TireRepair),
    Power("Battery & range", Icons.Filled.BatteryChargingFull),
    Fluids("Fluids & key", Icons.Filled.WaterDrop),
    Comfort("Climate & comfort", AppIcons.Climate),
    Alerts("Doors & alerts", Icons.Filled.Warning),
}

/** Which heading a diagnostic reading sits under, by its label. */
internal fun diagGroupOf(label: String): DiagGroup = when (label) {
    "Tire pressure", "Front left", "Front right", "Rear left", "Rear right" -> DiagGroup.Tires
    "12V battery", "Drive battery", "Range", "Fuel level", "Low fuel", "Plug", "Time to full" -> DiagGroup.Power
    "Washer fluid", "Brake fluid", "Key fob battery" -> DiagGroup.Fluids
    "Climate setpoint", "Steering wheel heat", "Rear defroster", "Mirror heat", "Seat heat/vent active" -> DiagGroup.Comfort
    else -> DiagGroup.Alerts
}

internal fun warn(v: Int) = if (v == 0) "OK" else "Warning"
internal fun yesNo(v: Boolean) = if (v) "Yes" else "No"
internal fun onOff(v: Int) = if (v == 0) "Off" else "On"
