package com.bloo.bluelink.ui

import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import com.bloo.bluelink.data.climatePresets
import com.bloo.bluelink.data.collapsedSections
import com.bloo.bluelink.data.defaultClimatePreset
import com.bloo.bluelink.data.enabledShortcuts
import com.bloo.bluelink.data.hotspots
import com.bloo.bluelink.data.imageUrl
import com.bloo.bluelink.data.lastServiceMiles
import com.bloo.bluelink.data.licensePlate
import com.bloo.bluelink.data.onboardingSeen
import com.bloo.bluelink.data.platform
import com.bloo.bluelink.data.powertrain
import com.bloo.bluelink.data.seatConfig
import com.bloo.bluelink.data.sectionOrder
import com.bloo.bluelink.data.serviceIntervalMiles
import com.bloo.bluelink.data.snapshot

/**
 * Reads this device's per-car / per-tile config for [vehicles] from one [prefs] snapshot and returns a UiState
 * transform folding them in, plus the resolved shortcut set (needed outside the copy to re-push launcher shortcuts).
 * `firstRun` yields an empty collapsed set, so all pebbles start expanded on first open.
 */
// Runs on Dispatchers.Default: decoding each car's preset list must not run on the main thread during the Loading -> Garage frame.
internal suspend fun AppViewModel.perCarConfig(
    vehicles: List<Vehicle>,
    prefs: androidx.datastore.preferences.core.Preferences,
): PerCarConfig = withContext(Dispatchers.Default) {
    val seatConfigs = vehicles.associate { it.vin to settingsStore.seatConfig(it.vin, prefs) }
    val powertrains = vehicles.mapNotNull { v -> settingsStore.powertrain(v.vin, prefs)?.let { v.vin to it } }.toMap()
    val platforms = vehicles.mapNotNull { v -> settingsStore.platform(v.vin, prefs)?.let { v.vin to it } }.toMap()
    val sectionOrders = vehicles.associate { it.vin to settingsStore.sectionOrder(it.vin, prefs) }
    val images = vehicles.mapNotNull { v -> settingsStore.imageUrl(v.vin, prefs)?.let { v.vin to it } }.toMap()
    val plates = vehicles.associate { it.vin to settingsStore.licensePlate(it.vin, prefs) }.filterValues { it.isNotBlank() }
    val lastSvc = vehicles.mapNotNull { v -> settingsStore.lastServiceMiles(v.vin, prefs)?.let { v.vin to it } }.toMap()
    val svcInterval = vehicles.mapNotNull { v -> settingsStore.serviceIntervalMiles(v.vin, prefs)?.let { v.vin to it } }.toMap()
    val climatePresets = vehicles.associate { it.vin to settingsStore.climatePresets(it.vin, prefs) }
    val firstRun = !settingsStore.onboardingSeen(prefs)
    // First open: all pebbles start expanded regardless of stored state.
    val collapsed = if (firstRun) emptySet()
    else vehicles.flatMap { v -> settingsStore.collapsedSections(v.vin, prefs).map { "${v.vin}:$it" } }.toSet()
    val hotspots = vehicles.mapNotNull { v -> settingsStore.hotspots(v.vin, prefs)?.let { v.vin to it } }.toMap()
    val shortcutSet = settingsStore.enabledShortcuts(prefs)
    PerCarConfig(
        apply = {
            it.copy(
                seatConfigs = seatConfigs,
                powertrains = powertrains,
                platforms = platforms,
                sectionOrders = sectionOrders,
                imageUrls = images,
                licensePlates = plates,
                lastServiceMiles = lastSvc,
                serviceIntervalMiles = svcInterval,
                climatePresets = climatePresets,
                collapsedPebbles = collapsed,
                hotspotSections = hotspots,
                shortcutSet = shortcutSet,
            )
        },
        shortcutSet = shortcutSet,
    )
}


internal suspend fun AppViewModel.refreshLocalCarConfig() {
    // One Preferences read for every per-car setting; see SettingsStore.snapshot().
    val prefs = settingsStore.snapshot()
    val vehicles = _state.value.vehicles
    if (vehicles.isEmpty()) return
    com.bloo.bluelink.data.StartupTrace.markIfStarting("loadGarageInner: prefs snapshot + order applied, perCarConfig starting")
    val cfg = perCarConfig(vehicles, prefs)
    com.bloo.bluelink.data.StartupTrace.markIfStarting("loadGarageInner: perCarConfig done")
    _state.update { cfg.apply(it) }
    // Also re-push launcher shortcuts so an imported shortcut-set change reaches the app-icon menu.
    com.bloo.bluelink.Shortcuts.refresh(getApplication(), vehicles, cfg.shortcutSet)
}


/**
 * Seeds [UiState.defaultClimatePresets] from the current vehicle list.
 *
 * Runs per garage load, not inside the once-per-process [bootstrapDriveSync], which can run before any vehicle exists.
 */
internal suspend fun AppViewModel.seedDefaultClimatePresets() {
    val vehicles = _state.value.vehicles
    if (vehicles.isEmpty()) return
    // One Preferences read, not one per car: this is on the cold-start critical path.
    val prefs = settingsStore.snapshot()
    val presets = vehicles.associate { v ->
        v.vin to (settingsStore.defaultClimatePreset(v.vin, prefs) ?: "smart")
    }
    _state.update { it.copy(defaultClimatePresets = presets) }
}
