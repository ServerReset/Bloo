package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehiclePlatform
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.lastServiceMiles
import com.bloo.bluelink.data.serviceIntervalMiles
import com.bloo.bluelink.data.setHotspots
import com.bloo.bluelink.data.setImageUrl
import com.bloo.bluelink.data.setLastServiceMiles
import com.bloo.bluelink.data.setLicensePlate
import com.bloo.bluelink.data.setPlatform
import com.bloo.bluelink.data.setPowertrain
import com.bloo.bluelink.data.setSeatFlag
import com.bloo.bluelink.data.setSectionOrder
import com.bloo.bluelink.data.setServiceIntervalMiles
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- Per-car identity, service, seats, powertrain and layout settings (extracted from AppViewModel) --

/** Set (or, with a blank string, clear) a custom car photo URL. */
fun AppViewModel.setVehicleImage(vin: String, url: String) {
    _state.update {
        it.copy(
            imageUrls = if (url.isBlank()) it.imageUrls - vin else it.imageUrls + (vin to url.trim()),
        )
    }
    viewModelScope.launch { settingsStore.setImageUrl(vin, url) }
}

fun AppViewModel.setLicensePlate(vin: String, plate: String) {
    _state.update {
        it.copy(
            licensePlates = if (plate.isBlank()) it.licensePlates - vin
            else it.licensePlates + (vin to plate.trim()),
        )
    }
    // Store write immediate (durability), cross-surface publish debounced -- see publishDebounced.
    // Still republishes rather than waiting for the next status refresh to rebuild the snapshot;
    // just not once per keypress.
    viewModelScope.launch { settingsStore.setLicensePlate(vin, plate) }
    publishDebounced("plate:$vin")
}

fun AppViewModel.setLastServiceMiles(vin: String, miles: Int?) {
    _state.update {
        it.copy(
            lastServiceMiles = if (miles == null) it.lastServiceMiles - vin
            else it.lastServiceMiles + (vin to miles),
        )
    }
    viewModelScope.launch { settingsStore.setLastServiceMiles(vin, miles) }
    publishDebounced("lastService:$vin")
}

fun AppViewModel.setServiceIntervalMiles(vin: String, miles: Int?) {
    _state.update {
        it.copy(
            serviceIntervalMiles = if (miles == null) it.serviceIntervalMiles - vin
            else it.serviceIntervalMiles + (vin to miles),
        )
    }
    viewModelScope.launch { settingsStore.setServiceIntervalMiles(vin, miles) }
    publishDebounced("serviceInterval:$vin")
}

/**
 * Toggle one seat-heater/cooler (or steering-wheel-heat) capability flag for a car. [field] is a
 * short code ("dh" = driver heat, "dc" = driver cool, "ph"/"pc" = passenger,
 * "rlh"/"rlc"/"rrh"/"rrc" = rear left/right, "sw" = steering wheel) mapped to the matching
 * [SeatConfig] property; an unrecognized code is a no-op (`else -> current`).
 */
fun AppViewModel.setSeatFlag(v: Vehicle, field: String, value: Boolean) {
    val current = _state.value.seatConfigs[v.vin] ?: SeatConfig()
    val updated = when (field) {
        "dh" -> current.copy(driverHeat = value)
        "dc" -> current.copy(driverCool = value)
        "ph" -> current.copy(passHeat = value)
        "pc" -> current.copy(passCool = value)
        "rlh" -> current.copy(rearLeftHeat = value)
        "rlc" -> current.copy(rearLeftCool = value)
        "rrh" -> current.copy(rearRightHeat = value)
        "rrc" -> current.copy(rearRightCool = value)
        "sw" -> current.copy(steeringWheel = value)
        else -> current
    }
    _state.update { it.copy(seatConfigs = it.seatConfigs + (v.vin to updated)) }
    viewModelScope.launch { settingsStore.setSeatFlag(v.vin, field, value) }
}

fun AppViewModel.setPowertrain(v: Vehicle, value: Powertrain) {
    _state.update { it.copy(powertrains = it.powertrains + (v.vin to value)) }
    viewModelScope.launch { settingsStore.setPowertrain(v.vin, value); persistSnapshots() }
}

fun AppViewModel.setPlatform(v: Vehicle, value: VehiclePlatform) {
    _state.update { it.copy(platforms = it.platforms + (v.vin to value)) }
    // persistSnapshots writes the EFFECTIVE (override-applied) generation number into the synced
    // VehicleSnapshot -- see snapshotOf.
    viewModelScope.launch { settingsStore.setPlatform(v.vin, value); persistSnapshots() }
}

/** Pin or unpin a pebble in the dual-column hot spot. */
fun AppViewModel.setHotspot(v: Vehicle, section: String) {
    _state.update {
        val current = it.hotspotSections[v.vin]
        // Toggle: if the section is already in the secondary slot, unpin it; otherwise pin it
        val updated = if (current == section) null else section
        it.copy(
            hotspotSections = if (updated == null) it.hotspotSections - v.vin else it.hotspotSections + (v.vin to updated),
        )
    }
    viewModelScope.launch { settingsStore.setHotspots(v.vin, _state.value.hotspotSections[v.vin]) }
}

/** Persist a new pebble order for a car (drag-and-drop on the card). */
fun AppViewModel.setSectionOrder(v: Vehicle, order: List<String>) {
    _state.update { it.copy(sectionOrders = it.sectionOrders + (v.vin to order)) }
    viewModelScope.launch { settingsStore.setSectionOrder(v.vin, order) }
}
