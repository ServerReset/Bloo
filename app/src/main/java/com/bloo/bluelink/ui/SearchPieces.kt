package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.bloo.bluelink.data.CarLabels
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.coordString
import com.bloo.bluelink.data.degLabel
import com.bloo.bluelink.data.doorLockLabel
import com.bloo.bluelink.data.engineLabel
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.openLabels
import com.bloo.bluelink.data.parseOdometerMiles
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.MorphActionButton
import com.bloo.uicommon.PrimaryTextStyle
import com.bloo.uicommon.RevealText
import dev.chrisbanes.haze.HazeState

/*
 * Search answers with PIECES OF THE APP: the charge bar the hero draws, the lock and horn buttons, the climate switch, the map, the
 * status rows. Each is lifted out of the car's page into a floating glass piece (tagged with where it came from), still live and
 * still doing what it does at home: the lock button locks, the climate button starts the climate.
 */

/** The tag and glyph a topic's piece carries: where in the app it was lifted from. */
private fun FactTopic.origin(): Pair<String, ImageVector> = when (this) {
    FactTopic.CHARGE -> "Battery & range" to AppIcons.BatteryFull
    FactTopic.TWELVE_VOLT -> "12V battery" to AppIcons.BatteryAlert
    FactTopic.DOORS -> "Doors & lock" to AppIcons.Lock
    FactTopic.CLIMATE -> "Climate" to AppIcons.Climate
    FactTopic.ENGINE -> "Engine" to AppIcons.DirectionsCar
    FactTopic.ODOMETER -> "Odometer" to Icons.Filled.Speed
    FactTopic.LOCATION -> "Location" to Icons.Filled.Place
}

/** One lifted piece for [piece], or nothing when the car or its reading has gone. */
@Composable
internal fun SearchPieceView(
    piece: SearchPiece,
    state: UiState,
    vm: AppViewModel,
    appearance: SettingsStore.Appearance,
    hazeState: HazeState?,
) {
    val v = state.vehicles.firstOrNull { it.vin == piece.vin } ?: return
    val status = state.statusFor(v) ?: return
    val (origin, icon) = piece.topic.origin()
    LiftedPiece("$origin · ${v.name}", icon, "${piece.vin}:${piece.topic}", hazeState) {
        when (piece.topic) {
            FactTopic.CHARGE -> ChargePiece(v, status, state, appearance)
            FactTopic.TWELVE_VOLT -> status.battery?.let { b ->
                b.level?.let { StatusRow("12V battery", "$it%") }
                b.health?.let { StatusRow("Health", it) }
            }
            FactTopic.DOORS -> DoorsPiece(v, status, state, vm)
            FactTopic.CLIMATE -> ClimatePiece(v, status, state, vm, appearance)
            FactTopic.ENGINE -> engineLabel(status.engine)?.let { StatusRow("Engine", it) }
            FactTopic.ODOMETER -> parseOdometerMiles(v.odometer)?.let { StatusRow("Odometer", formatDistance(it, appearance.metricDistance)) }
            FactTopic.LOCATION -> LocationPiece(v, state)
        }
    }
}

/** The hero's own readout, lifted: the big percentage, the segmented charge bar with its limit, and the range and state under it. */
@Composable
private fun ChargePiece(v: Vehicle, status: VehicleStatus, state: UiState, appearance: SettingsStore.Appearance) {
    val readout = chargeReadoutOf(status, state.hasBattery(v), state.hasFuel(v), null, appearance.metricDistance)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        RevealText(readout.pctText, style = androidx.compose.material3.MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            readout.rangeText?.let { RevealText(it, style = PrimaryTextStyle(), fontWeight = FontWeight.SemiBold) }
            MutedText(readout.statusLine)
        }
    }
    ChargeSegmentBar(
        frac = { readout.frac },
        limitPct = readout.limitPct,
        stuckAtLimit = readout.stuckAtLimit,
        charging = readout.charging,
        plugged = readout.plugged,
        modifier = Modifier.fillMaxWidth().height(ChargeBarHeight),
        collapsed = true,
    )
}

/** The lock and horn/lights buttons the car's page has, working from here, and what is open. */
@Composable
private fun DoorsPiece(v: Vehicle, status: VehicleStatus, state: UiState, vm: AppViewModel) {
    PrimaryActions(v, state, vm)
    val open = buildList {
        status.doorOpen?.openLabels()?.forEach { add("$it door") }
        status.windowOpen?.openLabels()?.forEach { add("$it window") }
        if (status.trunkOpen == true) add("trunk")
        if (status.hoodOpen == true) add("hood")
    }
    if (status.doorOpen != null || status.windowOpen != null) StatusRow("Open", if (open.isEmpty()) "All closed" else open.joinToString(", "))
    doorLockLabel(status.doorLock)?.let { StatusRow("Doors", it) }
}

/** The climate state and the one control that matters: stop it while it runs, start it smart when it does not. */
@Composable
private fun ClimatePiece(v: Vehicle, status: VehicleStatus, state: UiState, vm: AppViewModel, appearance: SettingsStore.Appearance) {
    val on = status.airCtrlOn == true
    val setpoint = status.airTemp?.value?.let { degLabel(it, appearance.useFahrenheit, status.airTemp?.unit) }
    StatusRow("Climate", if (on) "On" else "Off")
    setpoint?.let { StatusRow("Set to", it) }
    val pending = state.isPending(v.vin, "climate")
    if (on) {
        MorphActionButton(CarLabels.STOP_CLIMATE, AppIcons.Climate, { vm.stopClimate(v) }, Modifier.fillMaxWidth(), pending = pending)
    } else {
        MorphActionButton(CarLabels.SMART_CLIMATE, AppIcons.SmartClimate, { vm.startSmartClimate(v) }, Modifier.fillMaxWidth(), pending = pending)
    }
}

/** The map the Location pebble draws, and where it is as words. */
@Composable
private fun LocationPiece(v: Vehicle, state: UiState) {
    val loc = state.locations[v.vin]
    if (loc != null) {
        CarMap(loc, Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(18.dp)), deviceLocation = state.deviceLocation, interactive = false)
    }
    (state.placeNames[v.vin] ?: loc?.coordString(4))?.let { StackedRow("Parked at", it) }
}
