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
 * Reads this device's 17 local per-car / per-tile config values for [vehicles] from one
 * [prefs] snapshot and returns a UiState transform that folds them into `copy()`, plus the
 * resolved shortcut set (the one value a caller needs OUTSIDE the copy, to re-push launcher
 * shortcuts).
 *
 * This block was duplicated byte-for-byte between [loadGarageInner] and
 * [refreshLocalCarConfig] -- all 17 vals with identical right-hand sides. The comment at the
 * old refreshLocalCarConfig copy recorded the exact bug that duplication caused: fields it
 * had OMITTED (pebble visibility, collapse, hotspots, tile config, shortcuts) wrote DataStore
 * on a sync but never reached the running UiState, so "hid a pebble / moved a Quick-tile,
 * synced, nothing changed". Two copies is how one falls behind the other; there is now one.
 *
 * `firstRun` -> empty collapsed set is preserved (all pebbles start expanded on first open),
 * and callers layer their own distinct fields (loadGarageInner adds vehicles/screen/
 * garageLoadError; refreshLocalCarConfig adds nothing) on top of the returned transform.
 */
// Dispatchers.Default, same reasoning as SettingsStore.appearance's own .flowOn(Default):
// climatePresets(vin, prefs) below JSON-decodes each car's saved preset list, and this whole
// function runs right on the Loading -> Garage transition frame (loadGarageInner) or a
// settings-import refresh -- exactly the "decode ran on the main thread while the first
// frame was trying to draw" cost that fix already called out elsewhere. Everything else here
// is a pure, already-in-memory Preferences read (no real suspension), so hopping dispatchers
// once for the whole function costs one context switch, not one per getter.
suspend fun AppViewModel.perCarConfig(
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
    // On first open all pebbles start expanded regardless of any stored state.
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
    // ONE Preferences read for every per-car setting below, instead of one per
    // getter per car. See SettingsStore.snapshot().
    val prefs = settingsStore.snapshot()
    val vehicles = _state.value.vehicles
    if (vehicles.isEmpty()) return
    com.bloo.bluelink.data.StartupTrace.markIfStarting("loadGarageInner: prefs snapshot + order applied, perCarConfig starting")
    val cfg = perCarConfig(vehicles, prefs)
    com.bloo.bluelink.data.StartupTrace.markIfStarting("loadGarageInner: perCarConfig done")
    _state.update { cfg.apply(it) }
    // Quick-tile / shortcut changes must also re-push the launcher shortcuts,
    // exactly as loadGarageInner does, so an imported shortcut-set change is
    // reflected in the app-icon long-press menu and not just in-app.
    com.bloo.bluelink.Shortcuts.refresh(getApplication(), vehicles, cfg.shortcutSet)
}


/**
 * One-time Drive-sync bootstrap: restore the saved sync URI / settings-mode /
 * last-sync-time / per-car default climate presets, then start the
 * bidirectional auto-sync collector (download-then-upload whenever a refresh
 * settles). Guarded by [driveSyncBootstrapped] so calling this more than once
 * (the garage can reload after a re-login) never starts a second collector.
 *
 * This used to be spliced into the middle of [loadStatus] — which runs on
 * every single vehicle status fetch — so every manual refresh started a
 * brand-new, permanent `_state.refreshing` collector that itself did a full
 * Drive download + merge + upload. None of those collectors ever completed,
 * so a long session accumulated an unbounded pile of them, and each later
 * refresh fired ALL of them at once: redundant network calls and concurrent
 * writes to the same Drive file racing each other.
 */
/**
 * Seeds [UiState.defaultClimatePresets] from the CURRENT vehicle list.
 *
 * Lives outside [bootstrapDriveSync] because it is per-GARAGE-LOAD work, and that function
 * is once-per-PROCESS (an AtomicBoolean). The two were the same block, and the empty/failed
 * cold-start path calls bootstrapDriveSync BEFORE any vehicle exists -- deliberately, so the
 * sync collector still starts. That consumed the guard, so this map was computed from an
 * empty list and the later call on the real load was a documented no-op: every car's default
 * climate preset silently fell back to "smart" for the whole process, ignoring what the user
 * had chosen, until an app restart whose first garage load happened to succeed.
 *
 * Two different lifetimes had been given one guard. Splitting them is the fix.
 */
internal suspend fun AppViewModel.seedDefaultClimatePresets() {
    val vehicles = _state.value.vehicles
    if (vehicles.isEmpty()) return
    // ONE Preferences read, not one suspend data.first() per car -- this runs right on
    // the cold-start critical path (called from loadGarageInner immediately after the
    // Loading -> Garage flip), the exact "N getters x N cars" shape SettingsStore.snapshot()
    // exists to eliminate everywhere else in this file.
    val prefs = settingsStore.snapshot()
    val presets = vehicles.associate { v ->
        v.vin to (settingsStore.defaultClimatePreset(v.vin, prefs) ?: "smart")
    }
    _state.update { it.copy(defaultClimatePresets = presets) }
}
