package com.bloo.bluelink.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.StatusCache
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.brand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import com.bloo.bluelink.data.defaultClimatePreset
import com.bloo.bluelink.data.isCarConfigured
import com.bloo.bluelink.data.lastVehicleVin
import com.bloo.bluelink.data.onboardingSeen
import com.bloo.bluelink.data.powertrain
import com.bloo.bluelink.data.snapshot
import com.bloo.bluelink.data.vehicleOrder

// --- Garage loading, cached publish, and per-car config seeding (extracted from AppViewModel) --

/**
 * Result of [perCarConfig]: a UiState transform folding in the config fields, plus the shortcut set
 * the caller needs for [com.bloo.bluelink.Shortcuts.refresh].
 */
internal class PerCarConfig(val apply: (UiState) -> UiState, val shortcutSet: Set<String>?)

/**
 * Full garage (re)load wrapped in [launchBusy]: shows [UiState.loading], and a thrown exception
 * becomes a snackbar.
 */
fun AppViewModel.loadGarage() = launchBusy { loadGarageInternal() }

/**
 * Re-entrancy guard around [loadGarageInner]: the [loadingGarage] flag (checked/set here, not
 * inside [loadGarageInner] itself so every caller goes through this one gate) makes sure only one
 * garage load runs at a time -- e.g. a login finishing and a manual pull-to-refresh landing at the
 * same moment shouldn't run two overlapping fetches of every brand's vehicle list.
 */
internal suspend fun AppViewModel.loadGarageInternal() {
    if (loadingGarage) return
    loadingGarage = true
    try {
        loadGarageInner()
    } finally {
        loadingGarage = false
    }
}

/**
 * Whether the device has a validated internet-capable network (not merely an interface that is up).
 * Separates a real API/auth failure from having no connection at all.
 */
private fun AppViewModel.isDeviceOnline(): Boolean {
    val cm = getApplication<Application>()
        .getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

/**
 * Publish the last-known garage from disk, before [loadGarageInner]'s network round trip. The fetch
 * replaces it moments later; if the fetch fails the cached garage stays.
 */
private suspend fun AppViewModel.publishCachedGarage() {
    if (_state.value.screen != Screen.Loading) return
    val cached = runCatching { snapshotStore.current() }.getOrNull() ?: return
    if (cached.vehicles.isEmpty()) return
    val cachedVehicles = cached.vehicles.map { it.toVehicle() }
    val prefs = settingsStore.snapshot()
    if (resolveScreen(cachedVehicles, prefs) != Screen.Garage) return
    val cfg = perCarConfig(cachedVehicles, prefs)
    val lastVin = settingsStore.lastVehicleVin(prefs)
    val index = cachedVehicles.indexOfFirst { it.vin == lastVin }.let { if (it < 0) 0 else it }
    val defaultPresets = cachedVehicles.associate { v ->
        v.vin to (settingsStore.defaultClimatePreset(v.vin, prefs) ?: "smart")
    }
    _state.update {
        cfg.apply(it).copy(
            vehicles = cachedVehicles,
            screen = Screen.Garage,
            garageLoadError = null,
            defaultClimatePresets = defaultPresets,
        )
    }
    _currentIndex.value = index
    AppLog.log("⚡ Cached garage shown before the network: ${cachedVehicles.size} vehicle(s)")
}

suspend fun AppViewModel.loadGarageInner() {
    logStartup("loadGarageInner: started")
    // Fire-and-forget, parallel to the vehicle fetch: refreshes the device's own location on app
    // open.
    refreshDeviceLocation()
    com.bloo.bluelink.data.StartupTrace.markIfStarting(
        "loadGarageInner: refreshDeviceLocation() dispatched (repos=${repos.size})",
    )
    // Started once per app open; no explicit stop, the subscription dies with the process.
    beginLiveDeviceLocation()
    com.bloo.bluelink.data.StartupTrace.markIfStarting("loadGarageInner: live device location started")
    // That fetch is a network round trip and it was the gate on the garage being shown at all: the
    // UI sat on Screen.Loading for its whole duration (measured 2.1s on the API 34 emulator, and
    // that is before the garage's own first composition). The list is already on disk from the last
    // session -- see publishCachedGarage's own doc.
    publishCachedGarage()
    // Merge vehicles from every signed-in brand; one brand failing must not hide the others.
    // Failures are tracked apart from "zero vehicles" so an API failure never reads as signed out.
    var lastError: String? = null
    // .toList() first: the loop suspends, and logout()/repoFor() may mutate `repos` meanwhile
    // (ConcurrentModificationException). A snapshot also keeps a mid-load sign-out's vehicles out.
    val vehiclesFetchStartedAt = System.currentTimeMillis()
    val fetched = repos.values.toList().flatMap { r ->
        runCatching {
            // Cold-start diagnostic: separates time waiting for statusMutex from r.vehicles()
            // itself.
            val lockWaitStartedAt = System.currentTimeMillis()
            statusMutex.withLock {
                val lockWaitMs = System.currentTimeMillis() - lockWaitStartedAt
                if (lockWaitMs > 200) {
                    AppLog.log("loadGarageInner: waited ${lockWaitMs}ms for statusMutex before fetching vehicles")
                }
                r.vehicles()
            }
        }.getOrElse { e ->
            // A malformed response (see ResponseFraming) would otherwise surface okio's parser text
            // -- "Expected leading [0-9a-fA-F] character but was 0x7b" -- as the user-facing
            // reason, which tells a person nothing. It is retried once inside the API layer; this
            // is the wording for when the retry also fails.
            val msg = com.bloo.bluelink.data.ResponseFraming.userMessage(e)
                ?: e.message
                ?: "Couldn't load vehicles"
            AppLog.log("⚠ $msg")
            lastError = msg
            emptyList()
        }
    }
    logStartup(
        "loadGarageInner: vehicle list fetched in " +
            "${System.currentTimeMillis() - vehiclesFetchStartedAt}ms: ${fetched.size} vehicle(s)",
    )
    if (fetched.isEmpty()) {
        // Still bootstrap Drive sync on an empty/failed cold start so the restore + persisted-grant
        // check + auto-sync collector run once this session -- bootstrapDriveSync is idempotent
        // (AtomicBoolean guard), so the non-empty path below calling it again is a no-op.
        bootstrapDriveSync()
        com.bloo.bluelink.data.StartupTrace.markIfStarting("loadGarageInner: publishing empty garage")
        _state.update {
            it.copy(
                // Keep whatever cars are already on screen (the cached garage) so the error banner
                // shows beside them rather than an empty garage.
                vehicles = it.vehicles,
                // Garage, not a separate empty screen: GarageScreen folds the status card in as a
                // pager page.
                screen = Screen.Garage,
                garageLoadError = lastError,
                garageLoadOffline = lastError != null && !isDeviceOnline(),
            )
        }
        return
    }
    // One Preferences read for every per-car setting below (including vehicleOrder). Taken here,
    // after the network work, so it is not stale if a setting changed meanwhile. See
    // SettingsStore.snapshot().
    val snapshotStartedAt = System.currentTimeMillis()
    val prefs = settingsStore.snapshot()
    val snapshotMs = System.currentTimeMillis() - snapshotStartedAt
    if (snapshotMs > 500) {
        logStartup("loadGarageInner: settingsStore.snapshot() took ${snapshotMs}ms")
    }
    val vehicles = applyOrder(fetched, settingsStore.vehicleOrder(prefs))
    // The per-car/per-tile config fields, shared with refreshLocalCarConfig via perCarConfig.
    com.bloo.bluelink.data.StartupTrace.markIfStarting("loadGarageInner: prefs snapshot + order applied, perCarConfig starting")
    val cfg = perCarConfig(vehicles, prefs)
    com.bloo.bluelink.data.StartupTrace.markIfStarting("loadGarageInner: perCarConfig done")
    // All three read the same prefs snapshot rather than re-fetching the DataStore per vehicle.
    val lastVin = settingsStore.lastVehicleVin(prefs)
    val index = vehicles.indexOfFirst { it.vin == lastVin }.let { if (it < 0) 0 else it }
    val screen = resolveScreen(vehicles, prefs)
    // Folded into this update to avoid an extra UiState emission (a full recomposition) at the
    // Loading-to-Garage flip.
    val defaultPresets = vehicles.associate { v -> v.vin to (settingsStore.defaultClimatePreset(v.vin, prefs) ?: "smart") }
    _state.update {
        // Shared config first, then the fields only the full garage load owns.
        cfg.apply(it).copy(
            vehicles = vehicles,
            screen = screen,
            garageLoadError = null,
            defaultClimatePresets = defaultPresets,
        )
    }
    // Startup breadcrumb: if a crash follows shortly after, CrashActivity's own report includes the
    // tail of AppLog, so this (and "App starting" in BlooApplication.onCreate) is what tells "did
    // this even get as far as showing the garage" apart from "crashed during the very first frame"
    // -- something the earlier per-brand/per-car error logs in this function don't cover on their
    // own since they only fire on FAILURE.
    AppLog.log("✓ Garage loaded: ${vehicles.size} vehicle(s), screen=$screen")
    val shortcutSet = cfg.shortcutSet
    // Restores the last-selected car (currentIndex lives in its own flow, so it is set alongside
    // the copy).
    _currentIndex.value = index
    // Disk write for the whole garage.
    snapshotStore.saveVehiclesKeepingStatus(vehicles.map { snapshotOf(it, null, _state.value) })
    // seedDefaultClimatePresets() is folded into the main update; bootstrapDriveSync calls it once
    // per process for the empty-vehicles cold start. One-time: start the Drive auto-sync bootstrap
    // + collector.
    bootstrapDriveSync()
    // Keep app-icon shortcuts in sync, off the main thread: ShortcutManagerCompat does a
    // synchronous binder round trip per call.
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        runCatching { com.bloo.bluelink.Shortcuts.refresh(getApplication(), vehicles, shortcutSet) }
    }
    tryRunPendingShortcut()
    // Defer status fetching while the app is (or will be) locked, so it does not jank the lock-away
    // blur.
    val currentLocked = _state.value.locked
    if (!currentLocked) {
        logStartup("loadGarageInner: fetching status for ${vehicles[index].name} (current car)")
        ensureStatus(vehicles[index], logStartupTiming = true)
        viewModelScope.launch {
            vehicles.forEachIndexed { i, v -> if (i != index) ensureStatus(v) }
        }
    } else {
        // Locked session: defer status fetching until after unlock animation completes.
        logStartup("loadGarageInner: locked, deferring status fetch until unlock")
        deferredStatusLoad = true
    }
}

/**
 * Re-reads this device's local per-car config (seat capability, powertrain, photo, license plate,
 * service intervals, pebble order) for the currently loaded vehicles and folds it straight into
 * state -- the same local reads [loadGarageInner] already does once at startup, no network call.
 */
/**
 * Decides which screen a signed-in session with [vehicles] already loaded lands on, from a fresh
 * [prefs] snapshot. [firstRunScreen] for a first-run device, [Screen.CarSetup] for any vehicle it
 * does not cover, else the garage.
 */
internal suspend fun AppViewModel.resolveScreen(
    vehicles: List<Vehicle>,
    prefs: androidx.datastore.preferences.core.Preferences,
    firstRunScreen: Screen = Screen.Onboarding,
): Screen {
    val firstRun = !settingsStore.onboardingSeen(prefs)
    val unconfiguredVins = vehicles.filter { !settingsStore.isCarConfigured(it.vin, prefs) }.map { it.vin }
    return when {
        firstRun -> firstRunScreen
        unconfiguredVins.isNotEmpty() -> Screen.CarSetup(unconfiguredVins)
        else -> Screen.Garage
    }
}
