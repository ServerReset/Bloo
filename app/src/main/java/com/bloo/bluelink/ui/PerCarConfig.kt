package com.bloo.bluelink.ui

import com.bloo.bluelink.data.StartupLabels
import com.bloo.bluelink.data.StartupTrace
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.climatePresets
import com.bloo.bluelink.data.collapsedSections
import com.bloo.bluelink.data.defaultClimatePreset
import com.bloo.bluelink.data.enabledShortcuts
import com.bloo.bluelink.data.hotspots
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.autoPictureDone
import com.bloo.bluelink.data.imageUrl
import com.bloo.bluelink.data.setAutoPictureDone
import com.bloo.bluelink.data.setImageUrl
import com.bloo.bluelink.data.lastServiceMiles
import com.bloo.bluelink.data.licensePlate
import com.bloo.bluelink.data.onboardingSeen
import com.bloo.bluelink.data.platform
import com.bloo.bluelink.data.powertrain
import com.bloo.bluelink.data.seatConfig
import com.bloo.bluelink.data.sectionOrder
import com.bloo.bluelink.data.serviceIntervalMiles
import com.bloo.bluelink.data.snapshot
import com.bloo.bluelink.data.textClearPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** The value [read] finds for each vehicle that has one, keyed by VIN. */
private inline fun <T : Any> List<Vehicle>.perCar(read: (vin: String) -> T?): Map<String, T> =
    mapNotNull { v -> read(v.vin)?.let { v.vin to it } }.toMap()

/** The value [read] finds for every vehicle, keyed by VIN. */
private inline fun <T> List<Vehicle>.perCarAll(read: (vin: String) -> T): Map<String, T> = associate { it.vin to read(it.vin) }

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
    val seatConfigs = vehicles.perCarAll { settingsStore.seatConfig(it, prefs) }
    val powertrains = vehicles.perCar { settingsStore.powertrain(it, prefs) }
    val platforms = vehicles.perCar { settingsStore.platform(it, prefs) }
    val sectionOrders = vehicles.perCarAll { settingsStore.sectionOrder(it, prefs) }
    val stored = vehicles.perCar { settingsStore.imageUrl(it, prefs) }
    // A car with no picture of its own gets the bundled one for its model and paint, once, when the account tells us both. It is
    // then an ordinary choice (kept, changeable, clearable), and a cleared one is not given back.
    val auto = autoPictures(vehicles.filter { it.vin !in stored }, prefs)
    val images = stored + auto
    val textClear = vehicles.perCar { settingsStore.textClearPhoto(it, prefs) }
    val plates = vehicles.perCarAll { settingsStore.licensePlate(it, prefs) }.filterValues { it.isNotBlank() }
    val lastSvc = vehicles.perCar { settingsStore.lastServiceMiles(it, prefs) }
    val svcInterval = vehicles.perCar { settingsStore.serviceIntervalMiles(it, prefs) }
    val climatePresets = vehicles.perCarAll { settingsStore.climatePresets(it, prefs) }
    val firstRun = !settingsStore.onboardingSeen(prefs)
    // First open: all pebbles start expanded regardless of stored state.
    val collapsed = if (firstRun) emptySet()
    else vehicles.flatMap { v -> settingsStore.collapsedSections(v.vin, prefs).map { "${v.vin}:$it" } }.toSet()
    val hotspots = vehicles.perCar { settingsStore.hotspots(it, prefs) }
    val shortcutSet = settingsStore.enabledShortcuts(prefs)
    PerCarConfig(
        apply = {
            it.copy(
                seatConfigs = seatConfigs,
                powertrains = powertrains,
                platforms = platforms,
                sectionOrders = sectionOrders,
                imageUrls = images,
                textClearPhotos = textClear,
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
    StartupTrace.markIfStarting(StartupLabels.LOAD_GARAGE_PREFS_APPLIED)
    val cfg = perCarConfig(vehicles, prefs)
    StartupTrace.markIfStarting(StartupLabels.LOAD_GARAGE_PER_CAR_CONFIG_DONE)
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

/** The bundled picture for each of [vehicles] that has not been given one before and whose model and colour match one; saved as it is chosen. */
private suspend fun AppViewModel.autoPictures(
    vehicles: List<Vehicle>,
    prefs: androidx.datastore.preferences.core.Preferences,
): Map<String, String> {
    val todo = vehicles.filter { it.colour != null && !settingsStore.autoPictureDone(it.vin, prefs) }
    if (todo.isEmpty()) return emptyMap()
    val pictures = CarPictures.all(getApplication())
    val found = todo.mapNotNull { v -> matchCarPicture(v.model, v.name, v.colour, pictures)?.let { v.vin to it.url } }.toMap()
    found.forEach { (vin, url) ->
        AppLog.log("Picture chosen for a car from its model and colour: ${url.substringAfterLast('/')}")
        settingsStore.setImageUrl(vin, url)
        settingsStore.setAutoPictureDone(vin)
    }
    return found
}
