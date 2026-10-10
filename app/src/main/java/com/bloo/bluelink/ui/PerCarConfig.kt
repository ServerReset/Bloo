package com.bloo.bluelink.ui

import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.Vehicle
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
 * Reads this device's per-car / per-tile config for [vehicles] from one [prefs] snapshot and
 * returns a UiState transform folding them in, plus the resolved shortcut set (needed outside the
 * copy to re-push launcher shortcuts).
 */
// Dispatchers.Default, same reasoning as SettingsStore.appearance's own .flowOn(Default):
// climatePresets(vin, prefs) below JSON-decodes each car's saved preset list, and this whole
// function runs right on the Loading -> Garage transition frame (loadGarageInner) or a
// settings-import refresh -- exactly the "decode ran on the main thread while the first frame was
// trying to draw" cost that fix already called out elsewhere.
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

/**
 * Re-reads this device's local per-car config (seat capability, powertrain, photo, license plate,
 * service intervals, pebble order) for the currently loaded vehicles and folds it straight into
 * state -- the same local reads [loadGarageInner] already does once at startup, no network call.
 */
internal suspend fun AppViewModel.refreshLocalCarConfig() {
    // One Preferences read for every per-car setting; see SettingsStore.snapshot().
    val prefs = settingsStore.snapshot()
    val vehicles = _state.value.vehicles
    if (vehicles.isEmpty()) return
    com.bloo.bluelink.data.StartupTrace.markIfStarting("loadGarageInner: prefs snapshot + order applied, perCarConfig starting")
    val cfg = perCarConfig(vehicles, prefs)
    com.bloo.bluelink.data.StartupTrace.markIfStarting("loadGarageInner: perCarConfig done")
    _state.update { cfg.apply(it) }
    // Quick-tile / shortcut changes must also re-push the launcher shortcuts, exactly as
    // loadGarageInner does, so an imported shortcut-set change is reflected in the app-icon
    // long-press menu and not just in-app.
    com.bloo.bluelink.Shortcuts.refresh(getApplication(), vehicles, cfg.shortcutSet)
}

/**
 * Seeds [UiState.defaultClimatePresets] from the current vehicle list. Runs per garage load, not
 * inside the once-per-process [bootstrapDriveSync], which can run before any vehicle exists.
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
