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

/** Result of [perCarConfig]: a UiState transform folding in the 17 config fields, plus the
 *  shortcut set the caller needs separately for [com.bloo.bluelink.Shortcuts.refresh]. */
internal class PerCarConfig(val apply: (UiState) -> UiState, val shortcutSet: Set<String>?)


/** Public entry point for a full garage (re)load, wrapped in [launchBusy]
 *  so [UiState.loading] shows and any thrown exception becomes a snackbar. */
fun AppViewModel.loadGarage() = launchBusy { loadGarageInternal() }


/** Re-entrancy guard around [loadGarageInner]: the [loadingGarage] flag
 *  (checked/set here, not inside [loadGarageInner] itself so every caller
 *  goes through this one gate) makes sure only one garage load runs at a
 *  time -- e.g. a login finishing and a manual pull-to-refresh landing at
 *  the same moment shouldn't run two overlapping fetches of every brand's
 *  vehicle list. `@Volatile` because this can be read/written from
 *  different coroutines dispatched onto different threads. */
internal suspend fun AppViewModel.loadGarageInternal() {
    if (loadingGarage) return
    loadingGarage = true
    try {
        loadGarageInner()
    } finally {
        loadingGarage = false
    }
}


/** Whether the device currently has a validated internet-capable network --
 *  not just "some network interface is up", which is also true mid-captive-
 *  portal or on a link with no actual internet behind it. Used to tell a real
 *  API/auth failure (garageLoadError while genuinely online) apart from the
 *  device simply having no connection at all, so the status card
 *  GarageScreen folds in for a zero-vehicle account only shows the plain
 *  "no connection" copy for the latter. */
private fun AppViewModel.isDeviceOnline(): Boolean {
    val cm = getApplication<Application>()
        .getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}


/**
 * Publish the last-known garage from disk, before [loadGarageInner]'s network round trip.
 *
 * The vehicle LIST is a network fetch, and it used to gate the garage being shown at all:
 * a returning user watched Screen.Loading for the whole round trip. [SnapshotStore]
 * already holds every car's identity from the last session, and [StatusCache]'s own
 * restore (a separate launch) fills in their statuses, so there is nothing to wait for.
 * The fetch in [loadGarageInner] replaces this with fresh data a moment later; if it
 * fails, the cached garage stays (the better failure mode: last-known cars plus the
 * error banner, not an empty garage).
 *
 * Only fires on the cold-start path (screen still Loading) and only when the resolved
 * screen is Garage, so a first run or a car still needing its powertrain/seats set up
 * keeps going through Onboarding/CarSetup exactly as before.
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
    // Fire-and-forget, in parallel with the vehicle fetch below (its own
    // viewModelScope.launch, not awaited here) -- refreshes the DEVICE's own
    // last-known location on every cold start/app open. See refreshDeviceLocation's
    // own doc; this is the "whenever the app... opened" half of that, refreshStatus
    // covers "whenever the app refreshed".
    refreshDeviceLocation()
    com.bloo.bluelink.data.StartupTrace.markIfStarting(
        "loadGarageInner: refreshDeviceLocation() dispatched (repos=${repos.size})",
    )
    // "Every minute or two while inside the app" -- reported directly, correcting
    // the previous every-5-seconds design: started once per app open (this
    // ViewModel's own lifetime is "inside the app"; there is no explicit stop,
    // the same way refreshDeviceLocation's own one-shot fires have never needed
    // one -- Android tears the subscription down with the process). See
    // beginLiveDeviceLocation's own doc.
    beginLiveDeviceLocation()
    com.bloo.bluelink.data.StartupTrace.markIfStarting("loadGarageInner: live device location started")
    // Show the last-known garage NOW, before the vehicle-list fetch below. That fetch is
    // a network round trip and it was the gate on the garage being shown at all: the UI
    // sat on Screen.Loading for its whole duration (measured 2.1s on the API 34
    // emulator, and that is before the garage's own first composition). The list is
    // already on disk from the last session -- see publishCachedGarage's own doc.
    publishCachedGarage()
    // Merge vehicles from every signed-in brand; one brand failing shouldn't
    // hide the others. Track failures separately from "this account
    // genuinely has zero vehicles" -- collapsing both into the same empty
    // list used to make a network/API failure display as "Not signed in"
    // or "No vehicles found", which looked like the app had silently
    // signed the user out rather than telling them what actually happened.
    var lastError: String? = null
    // .toList() FIRST, so the iteration below runs over a snapshot rather than
    // the live map. It suspends inside the loop -- statusMutex.withLock plus a
    // network round trip per brand -- and every suspension hands the main thread
    // to another coroutine that may mutate `repos`: logout() calls
    // repos.remove(brand), and signing into a new brand makes repoFor() insert
    // one. Confining the map to the main thread is not enough to make iterating
    // it safe when the loop body suspends; that is exactly how single-threaded
    // coroutine code still earns a ConcurrentModificationException. Signing out
    // of one account while the garage was still loading another's cars was
    // enough to do it.
    //
    // A snapshot is also the behaviour this wants: a brand signed out mid-load
    // should not have its half-fetched vehicles land in the merged list, and a
    // brand signed in mid-load gets its own reload from logout()/login()'s own
    // path anyway.
    val vehiclesFetchStartedAt = System.currentTimeMillis()
    val fetched = repos.values.toList().flatMap { r ->
        runCatching {
            // Cold-start diagnostic: isolates how long THIS call spent merely waiting
            // for statusMutex (held by some other concurrent account/status call, if
            // anything) from how long r.vehicles() itself actually took once it had the
            // lock -- a real device log showed loadGarageInner's own overall vehicle-list
            // timer running ~1.5s longer than every timed sub-step inside r.vehicles()
            // (dispatch onto IO, store.load(), the network body read) summed together,
            // and mutex contention was one of the untimed gaps that could hide in.
            val lockWaitStartedAt = System.currentTimeMillis()
            statusMutex.withLock {
                val lockWaitMs = System.currentTimeMillis() - lockWaitStartedAt
                if (lockWaitMs > 200) {
                    AppLog.log("loadGarageInner: waited ${lockWaitMs}ms for statusMutex before fetching vehicles")
                }
                r.vehicles()
            }
        }.getOrElse { e ->
            // A malformed response (see ResponseFraming) would otherwise surface okio's
            // parser text -- "Expected leading [0-9a-fA-F] character but was 0x7b" -- as the
            // user-facing reason, which tells a person nothing. It is retried once inside the
            // API layer; this is the wording for when the retry also fails.
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
        // Still bootstrap Drive sync on an empty/failed cold start so the
        // restore + persisted-grant check + auto-sync collector run once this
        // session -- bootstrapDriveSync is idempotent (AtomicBoolean guard),
        // so the non-empty path below calling it again is a no-op.
        bootstrapDriveSync()
        com.bloo.bluelink.data.StartupTrace.markIfStarting("loadGarageInner: publishing empty garage")
        _state.update {
            it.copy(
                // Whatever cars are already on screen -- the cached garage published
                // above, if any -- rather than blanking them: a failed fetch should
                // leave the last-known cars visible with the error banner, not replace
                // a perfectly good garage with an empty one. On a genuine first run
                // this is still empty, so the status card is unchanged.
                vehicles = it.vehicles,
                // Garage, not a separate empty screen -- GarageScreen folds
                // the "no connection"/"not signed in"/"no vehicles" status
                // card in as a page of its own pager instead.
                screen = Screen.Garage,
                garageLoadError = lastError,
                garageLoadOffline = lastError != null && !isDeviceOnline(),
            )
        }
        return
    }
    // ONE Preferences read for every per-car setting below (including vehicleOrder,
    // right after), instead of one read per getter -- vehicleOrder used to be its own
    // extra data.first() taken separately, one more round trip sitting directly ahead
    // of the very snapshot meant to eliminate this class of cost on the cold-start
    // critical path. See SettingsStore.snapshot().
    //
    // Taken HERE and not at the top of the function, deliberately: everything above
    // this is network work that can take seconds, and a snapshot read before it would
    // be stale by the time it was used if the user changed a setting meanwhile.
    // Timed like SessionStore.load() elsewhere in this file's own history: that one
    // turned out to take 850ms-2.9s on this same account for no visible reason, and
    // this is the same shape of call (a suspend Preferences DataStore read) sitting
    // directly between "vehicle list fetched" and "fetching status for" with nothing
    // to explain a multi-second gap between them if this is where it goes too.
    val snapshotStartedAt = System.currentTimeMillis()
    val prefs = settingsStore.snapshot()
    val snapshotMs = System.currentTimeMillis() - snapshotStartedAt
    if (snapshotMs > 500) {
        logStartup("loadGarageInner: settingsStore.snapshot() took ${snapshotMs}ms")
    }
    val vehicles = applyOrder(fetched, settingsStore.vehicleOrder(prefs))
    // The 16 per-car/per-tile config fields, shared with refreshLocalCarConfig via
    // perCarConfig so the two can't drift. firstRun's empty-collapsed rule lives inside it.
    com.bloo.bluelink.data.StartupTrace.markIfStarting("loadGarageInner: prefs snapshot + order applied, perCarConfig starting")
    val cfg = perCarConfig(vehicles, prefs)
    com.bloo.bluelink.data.StartupTrace.markIfStarting("loadGarageInner: perCarConfig done")
    // All three read the SAME prefs snapshot taken just above (the Preferences-taking
    // overloads), not their own suspend re-fetch of the DataStore -- isCarConfigured in
    // particular used to be one full data.first() round trip PER VEHICLE, sequentially, on
    // the cold-start critical path (every one of them returning fields off the identical
    // snapshot). See snapshot()'s own doc for why this is the pattern every per-car/global
    // getter here is meant to be paired with.
    val lastVin = settingsStore.lastVehicleVin(prefs)
    val index = vehicles.indexOfFirst { it.vin == lastVin }.let { if (it < 0) 0 else it }
    val screen = resolveScreen(vehicles, prefs)
    // Folded into this SAME update rather than seedDefaultClimatePresets()'s own
    // separate _state.update a few lines down (see that function's remaining call
    // site inside bootstrapDriveSync for why it still exists there) -- this was one
    // of three back-to-back UiState emissions landing in the first couple of frames
    // right as Screen.Loading flips to Screen.Garage, each one a full recomposition
    // pass over the newly-visible screen (UiState is diffed by its generated
    // equals(), so any one changed field invalidates every pebble taking the whole
    // object). vehicles/prefs are already both in scope here, so there's no reason
    // this needs its own trip through the StateFlow at all on the cold-start path.
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
    // Startup breadcrumb: if a crash follows shortly after, CrashActivity's own report
    // includes the tail of AppLog, so this (and "App starting" in BlooApplication.onCreate)
    // is what tells "did this even get as far as showing the garage" apart from "crashed
    // during the very first frame" -- something the earlier per-brand/per-car error logs
    // in this function don't cover on their own since they only fire on FAILURE.
    AppLog.log("✓ Garage loaded: ${vehicles.size} vehicle(s), screen=$screen")
    val shortcutSet = cfg.shortcutSet
    // Restores the last-selected car. This used to ride along inside the
    // copy() above; it lives in its own flow now (see currentIndex), so it
    // has to be set alongside rather than within.
    _currentIndex.value = index
    // The disk write for the whole garage. MOVED to here from just after `applyOrder`
    // above, and the move IS a fix.
    //
    // Why saveVehiclesKeepingStatus and not saveVehicles: snapshotOf(v, null) knows every
    // car's identity and nothing about its state, and saveVehicles replaces the payload
    // wholesale -- it used to blank percent, range, lock, charge, climate, engine,
    // location and fetchedAt for every car on disk on every cold start, login and
    // pull-to-refresh, and fetchedAt = 0 tripped the stale gate on the way.
    // persistSnapshots() gets the same result by passing the in-memory status cache;
    // deliberately NOT copied here, because that cache is restored on its own
    // viewModelScope.launch and whether it has landed by now is a race. The store carries
    // the values forward from disk inside its own edit transaction, which has no window.
    //
    // Why it had to move DOWN here:
    //
    // snapshotOf() reads `_state.value.hasBattery(v)`, which reads the `powertrains` map
    // that the _state.update just above is what populates. Run BEFORE it -- where this
    // line used to be -- every car was written to disk with hasBattery = false, and
    // keepingStatusOf carries the STATUS fields forward but not this one, so the false
    // stuck.
    //
    // Every surface fed from that same file read the wrong powertrain, so an EV got its
    // gauge labelled "Fuel" and its battery-only actions dropped.
    // persistSnapshots() later repairs it -- but only when a status fetch actually returns
    // something, because it sits inside `s?.let`. With cars asleep (null status) or the
    // network down, the wrong value stood for the whole session.
    //
    // Still saveVehiclesKeepingStatus, and still snapshotOf(it, null): the store carries
    // the status fields forward inside its own edit transaction, which has no race
    // against the separately-launched cache restore. That reasoning was already right.
    snapshotStore.saveVehiclesKeepingStatus(vehicles.map { snapshotOf(it, null, _state.value) })
    // seedDefaultClimatePresets() used to be called here too, on EVERY non-empty garage
    // load -- folded into the main _state.update above instead (vehicles/prefs were
    // already in scope there, so it's the identical computation, just written into the
    // same emission instead of a second one), so this path no longer pays for a second
    // UiState emission. bootstrapDriveSync's own call to the same function, a few lines
    // into the coroutine below, is unrelated to this and untouched: it runs once per
    // PROCESS (guarded), specifically to cover a cold start whose very first garage
    // load returned nothing at all (the empty-vehicles branch, an early return above
    // this point) --
    // this fold does not affect that path since it never reaches this line either.
    // One-time: start the Drive auto-sync bootstrap + collector.
    bootstrapDriveSync()
    // Keep the app-icon long-press shortcuts in sync with the current cars. Dispatched
    // off the main thread: ShortcutManagerCompat does a synchronous binder round trip per
    // call, and this lands on the very frame the garage first draws -- it has no ordering
    // relationship with the shortcut ROUTING below, which reads the intent that launched
    // this Activity, not the shortcut list.
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        runCatching { com.bloo.bluelink.Shortcuts.refresh(getApplication(), vehicles, shortcutSet) }
    }
    // Run any shortcut that was tapped before the garage finished loading.
    tryRunPendingShortcut()
    // Set the defer flag if the app is locked (or will be locked shortly by maybeRelock).
    // This gates status fetching below -- if true, status fetches are deferred until
    // unlocked() is called, avoiding recomposition jank that overlaps the lock-away
    // blur animation. Check the current lock state from the updated state above.
    val currentLocked = _state.value.locked
    if (!currentLocked) {
        // Unlocked session: fetch status now (no lock animation to worry about).
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
 * Re-reads this device's local per-car config (seat capability, powertrain,
 * photo, license plate, service intervals, pebble order) for the currently
 * loaded vehicles and folds it straight into state -- the same local reads
 * [loadGarageInner] already does once at startup, no network call. A
 * restored/imported settings backup only ever writes to [settingsStore]
 * directly; without this, the already-composed UI (onboarding mid-flow, a
 * Settings screen already open) kept showing whatever it loaded before the
 * import, until some unrelated event happened to trigger a full reload.
 */
/**
 * Decides which screen a signed-in session with [vehicles] already loaded should land
 * on, from a fresh [prefs] snapshot: [firstRunScreen] for a genuinely first-run device,
 * then [Screen.CarSetup] for any vehicle that first-run screen doesn't cover (a car
 * added since, or one a partial restore didn't configure), else straight to the garage.
 *
 * Shared by [loadGarageInner] (a fresh vehicle fetch) and [restoreFromSyncThenContinue]
 * (an import that can flip onboarding_seen/isCarConfigured without any new vehicle fetch at
 * all). First run is the onboarding deck either way: its restore card is where a second
 * phone brings its setup in.
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
