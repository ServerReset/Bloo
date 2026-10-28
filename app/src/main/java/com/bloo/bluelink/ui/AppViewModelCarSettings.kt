package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehiclePlatform
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
import com.bloo.bluelink.data.setTextClearPhoto
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** This per-car map with [vin]'s entry set to [value], or dropped when [value] is null. */
private fun <V> Map<String, V>.withEntry(vin: String, value: V?): Map<String, V> = if (value == null) this - vin else this + (vin to value)

// --- Per-car identity, service, seats, powertrain and layout settings (extracted from AppViewModel) --

/** Set (or, with a blank string, clear) a custom car photo URL. */
fun AppViewModel.setVehicleImage(vin: String, url: String) {
    _state.update {
        it.copy(imageUrls = it.imageUrls.withEntry(vin, url.trim().ifBlank { null }))
    }
    persist { setImageUrl(vin, url) }
}

fun AppViewModel.setLicensePlate(vin: String, plate: String) {
    _state.update {
        it.copy(licensePlates = it.licensePlates.withEntry(vin, plate.trim().ifBlank { null }))
    }
    // Store write immediate (durability), cross-surface publish debounced -- see publishDebounced.
    // Still republishes rather than waiting for the next status refresh to rebuild the snapshot;
    // just not once per keypress.
    persist { setLicensePlate(vin, plate) }
    publishDebounced("plate:$vin")
}

fun AppViewModel.setLastServiceMiles(vin: String, miles: Int?) {
    _state.update {
        it.copy(lastServiceMiles = it.lastServiceMiles.withEntry(vin, miles))
    }
    persist { setLastServiceMiles(vin, miles) }
    publishDebounced("lastService:$vin")
}

fun AppViewModel.setServiceIntervalMiles(vin: String, miles: Int?) {
    _state.update {
        it.copy(serviceIntervalMiles = it.serviceIntervalMiles.withEntry(vin, miles))
    }
    persist { setServiceIntervalMiles(vin, miles) }
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
    _state.update { it.copy(seatConfigs = it.seatConfigs.withCar(v.vin, updated)) }
    persist { setSeatFlag(v.vin, field, value) }
}

fun AppViewModel.setPowertrain(v: Vehicle, value: Powertrain) {
    _state.update { it.copy(powertrains = it.powertrains.withCar(v.vin, value)) }
    viewModelScope.launch { settingsStore.setPowertrain(v.vin, value); persistSnapshots() }
}

fun AppViewModel.setPlatform(v: Vehicle, value: VehiclePlatform) {
    _state.update { it.copy(platforms = it.platforms.withCar(v.vin, value)) }
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
        it.copy(hotspotSections = it.hotspotSections.withEntry(v.vin, updated))
    }
    persist { setHotspots(v.vin, _state.value.hotspotSections[v.vin]) }
}

/** Persist a new pebble order for a car (drag-and-drop on the card). */
fun AppViewModel.setSectionOrder(v: Vehicle, order: List<String>) {
    _state.update { it.copy(sectionOrders = it.sectionOrders.withCar(v.vin, order)) }
    persist { setSectionOrder(v.vin, order) }
}

/** Whether this car's name and charge sit clear of its photo, rather than over it. */
fun AppViewModel.setTextClearPhoto(v: Vehicle, value: Boolean) {
    _state.update { it.copy(textClearPhotos = it.textClearPhotos.withCar(v.vin, value)) }
    persist { setTextClearPhoto(v.vin, value) }
}
