@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

/**
 * Diagnostics pebble: DiagRow, DiagnosticsPebble, warn/yesNo/onOff --
 * extracted from Pebbles.kt to keep the UI file focused.
 */

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.openLabels
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.formatDistance


// --- Diagnostics ----------------------------------------------------------

internal data class DiagRow(val label: String, val value: String, val indent: Boolean = false)

@Composable
internal fun DiagnosticsPebble(v: Vehicle, status: VehicleStatus?, state: UiState, vm: AppViewModel, dragHandle: Modifier) {
    val a = LocalAppearance.current
    val fahrenheit = a.useFahrenheit
    val metric = a.unitSystem == "metric"
    val rows = remember(status, fahrenheit, metric) { buildList {
        status?.tirePressureLamp?.let { tp ->
            // No psi suffix. `TirePressure.all` was only ever populated FROM the warning lamp --
            // Kia read `tirePressure.all` (a 0/1 indicator) and Canada read
            // `tirePressureLamp.tirePressureLampAll` outright -- so this rendered "Warning · 1
            // psi" and "OK · 0 psi". No producer in this app has ever supplied a real
            // pressure, and both assignments are now deleted, so there is nothing to suffix.
            add(DiagRow("Tire pressure", if (tp.hasWarning) "Warning" else "OK"))
            tp.frontLeft?.let { add(DiagRow("Front left", warn(it), indent = true)) }
            tp.frontRight?.let { add(DiagRow("Front right", warn(it), indent = true)) }
            tp.rearLeft?.let { add(DiagRow("Rear left", warn(it), indent = true)) }
            tp.rearRight?.let { add(DiagRow("Rear right", warn(it), indent = true)) }
        }
        status?.battery?.let { b ->
            b.batSoc?.let { soc ->
                add(DiagRow("12V battery", "$soc%"))
            }
        }
        status?.evStatus?.batteryStatus?.let { add(DiagRow("Drive battery", "$it%")) }
        status?.rangeMiFor(state.hasBattery(v))?.let { add(DiagRow("Range", formatDistance(it, metric))) }
        status?.airTemp?.let { t ->
            t.value?.let { add(DiagRow("Climate setpoint", degLabel(it, fahrenheit, t.unit))) }
        }
        status?.fuelLevel?.let { add(DiagRow("Fuel level", "$it%")) }
        status?.lowFuelLight?.let { add(DiagRow("Low fuel", yesNo(it))) }
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
        // fmtMinutes, not "$it min" -- the charge pebble's own "Time to full" row a
        // few hundred lines up already used it, so a 95-minute estimate read
        // "1h 35m" there and "95 min" here, in the same app on the same screen.
        status?.evStatus?.minutesToFull?.let { add(DiagRow("Time to full", fmtMinutes(it))) }
        status?.doorOpen?.openLabels()?.takeIf { it.isNotEmpty() }
            ?.let { add(DiagRow("Doors open", it.joinToString(", "))) }
        if (status?.trunkOpen == true) add(DiagRow("Trunk", "Open"))
        if (status?.hoodOpen == true) add(DiagRow("Hood", "Open"))
        if (status?.doorLock == false && status.engine != true) add(DiagRow("Lock", "Car is unlocked while parked"))
    } }
    // The count of actual problems. The warning affordance is then just "any problem at all"
    // -- issueCount > 0 -- rather than a second hand-kept copy of these five predicates, which
    // is what this used to be (a parallel `hasWarning` ||-chain that had to stay in sync with
    // this list by hand). One source now; they can't drift.
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
    // A VERDICT, not a tally. This used to read "12 checks", which is a count of rows rendered
    // rather than anything about the car -- it cannot tell you whether to care, which is the
    // only question a collapsed diagnostics pebble is asked. The cover's hero already computed
    // the verdict and showed it one line below the tally; now the one line says it, on both
    // surfaces, and the hero is gone.
    val diagSummary = remember(rows, hasWarning, issueCount) {
        when {
            rows.isEmpty() -> "No data"
            !hasWarning -> "All systems OK"
            issueCount == 1 -> "1 issue"
            else -> "$issueCount issues"
        }
    }
    Pebble(
        v, "diagnostics", "Diagnostics", Icons.Filled.ErrorOutline, state, vm, dragHandle,
        summary = diagSummary,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        headerAction = if (hasWarning) PebbleHeaderAction(
            label = "",
            icon = Icons.Filled.Warning,
            onClick = { vm.togglePebble(v, "diagnostics") },
            isWarning = true,
            contentDescription = "Diagnostics warning",
        ) else null,
        // NOT alwaysExpandedInSimpleMode: that flag is for pebbles with a single setting
        // that reads better inline without an expand/collapse control (see its own doc).
        // This one renders ~12 diagnostic rows -- forcing it permanently open in simple
        // mode, as an earlier pass did, just removed the ability to collapse a long list
        // that most people only want to check occasionally.
    ) {
        // No cover hero: the verdict it rendered is now the pebble's own summary, which
        // CoverTile shows as the tile headline. See diagSummary above.
        if (rows.isEmpty()) {
            Text(
                "No diagnostics yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        rows.forEach { row ->
            if (row.indent) {
                // DiagnosticsPebble has no cover-vs-phone split of its own -- this whole
                // ~12-row list renders on the cover too, below the health-verdict hero.
                // The label stayed at the fixed dim onSurfaceVariant role there, same
                // class of issue StatusRow's label had; boosted on the cover the same
                // way. The value used to be entirely unstyled (inheriting the pebble's
                // own ambient onSurfaceVariant content color) rather than pinned to a
                // legible tone the way StatusRow's own value already is -- an indented
                // sub-row's VALUE is still the thing a user is actually checking.
                // onSurfaceVariant is already full-alpha as a raw theme color -- its
                // dimness is the ROLE itself (a lower-contrast RGB against the
                // surface), not an alpha multiply, so unlike StatusRow/CoverHero this
                // needed a color swap, not an alpha bump, to actually read stronger.
                val indentLabelColor = if (LocalForceExpanded.current) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        row.label,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = indentLabelColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        row.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
            } else {
                StatusRow(row.label, row.value)
            }
        }
    }
}

internal fun warn(v: Int) = if (v == 0) "OK" else "Warning"
internal fun yesNo(v: Boolean) = if (v) "Yes" else "No"
internal fun onOff(v: Int) = if (v == 0) "Off" else "On"
