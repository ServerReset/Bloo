package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.CarAlerts
import com.bloo.bluelink.data.Notifications
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import com.bloo.bluelink.data.setDefaultClimatePreset
import com.bloo.bluelink.data.setLastVehicleVin
import com.bloo.bluelink.data.setSectionCollapsed
import com.bloo.bluelink.data.setVehicleOrder
import com.bloo.bluelink.data.snapshot

/** AppViewModel's car selection, pebble and trip actions, as extensions of [AppViewModel], kept out of the class so it stays readable. */

/**
 * Evaluates this car's freshly-fetched [status] against the user's alert
 * thresholds (door-open duration, engine-running duration, etc. — see
 * [CarAlerts]), posts a system notification for every alert that fires, and
 * additionally surfaces the FIRST one as an in-app snackbar message so it's
 * visible even if the app is already in the foreground (where a system
 * notification is easy to miss). Called after every successful status load.
 */
internal suspend fun AppViewModel.checkAlerts(v: Vehicle, status: VehicleStatus) {
    val alerts = CarAlerts.evaluate(settingsStore, v, status)
    alerts.forEach { Notifications.post(getApplication(), it.id, it.title, it.text, it.actions, it.channelId, it.localOnly) }
    alerts.firstOrNull()?.let { a -> _state.update { it.copy(message = a.text, messageType = "error") } }
}

// --- App PIN (device unlock PIN) -------------------------------------

// --- Garage / vehicles ----------------------------------------------

/**
 * Switch the visible car (swipe). Updates the index, and lazily loads this
 * car's status only if we don't already have it — so already-loaded cars are
 * never re-fetched on a swipe, but a car that failed to load at startup gets
 * another chance when you view it.
 */
fun AppViewModel.selectIndex(index: Int) {
    val v = _state.value.vehicles.getOrNull(index) ?: return
    // A no-op selection must not emit. UiState is threaded into every
    // pebble and is unstable, so one emission recomposes every car page
    // currently in composition -- and this is called from a snapshotFlow on
    // the pager's settledPage, which re-fires whenever the pager re-settles
    // on the car it was already showing (a wrap snap, an external select
    // that matched, a settle that never left the page). Paying three full
    // car-page rebuilds to set currentIndex to the value it already holds
    // is the worst kind of hitch: invisible work at exactly the moment the
    // user is watching the gesture finish.
    if (_currentIndex.value == index) {
        ensureStatus(v)
        return
    }
    _currentIndex.value = index
    viewModelScope.launch { settingsStore.setLastVehicleVin(v.vin) }
    ensureStatus(v)
}

/** Persist a new car display order (drag-and-drop in Settings). */
fun AppViewModel.reorderVehicles(order: List<Vehicle>) {
    _state.update { s ->
        // Keep the same CAR selected across a reorder, not the same
        // position -- selectIndex/expand always update currentIndex
        // together with vehicles, but this was the one place that moved
        // vehicles without it, so dragging a car above the currently
        // selected one silently swapped which car the detail view showed.
        val selectedVin = s.vehicles.getOrNull(_currentIndex.value)?.vin
        val newIndex = order.indexOfFirst { it.vin == selectedVin }
        if (newIndex >= 0) _currentIndex.value = newIndex
        s.copy(vehicles = order)
    }
    viewModelScope.launch {
        settingsStore.setVehicleOrder(order.map { it.vin })
        persistSnapshots(order)
    }
}

/**
 * Publish after [textFieldPublishDebounceMs] of quiet on [key], superseding any
 * publish still pending for that same key.
 *
 * This exists because three settings are edited through raw `onValueChange` text
 * fields -- licence plate, last-service miles, service interval -- and each one used
 * to run the full [persistSnapshots] fan-out on every single typed character. That
 * is a full snapshot re-encode and disk commit, then a re-read and re-decode of the
 * whole snapshot payload by every interested surface. Typing a seven-character plate
 * did all of that seven times.
 *
 * What is NOT debounced, deliberately: the `_state` update (so the field the user is
 * typing in stays responsive) and the SettingsStore write itself (so the value is
 * durable the instant it's typed, and closing the app mid-word cannot lose it). Only
 * the cross-surface publish waits, and only for as long as the user keeps typing.
 *
 * [persistSnapshots] reads `_state`, never the settings store, so a debounced publish
 * always carries the latest typed value rather than whatever was current when it was
 * scheduled.
 *
 * The map is only ever touched from the main dispatcher -- viewModelScope's default,
 * and there is no suspension point between the read and the write below -- so a plain
 * mutableMapOf is safe here. It is also never iterated, which is what made the other
 * plain map in this class a ConcurrentModificationException waiting to happen.
 */
internal fun AppViewModel.publishDebounced(key: String) {
    pendingPublishes[key]?.cancel()
    pendingPublishes[key] = viewModelScope.launch {
        delay(textFieldPublishDebounceMs)
        persistSnapshots()
        pendingPublishes.remove(key)
    }
}

/**
 * Settings cards call this exact function too, via the placeholder
 * [SettingsPseudoVehicle] (its `vin` is the only field [togglePebble] ever reads) --
 * not a separate `toggleSettingsCard` that used to duplicate this whole body under
 * [SETTINGS_CARD_VIN] by hand. Same state, same store, same persistence -- so a
 * Settings card remembers whether it was open across launches exactly the way a
 * car's pebble does, through the literal same code path rather than a lookalike.
 */
fun AppViewModel.togglePebble(v: Vehicle, section: String) {
    val key = "${v.vin}:$section"
    val collapsedNow = key !in _state.value.collapsedPebbles
    _state.update {
        it.copy(
            collapsedPebbles = if (collapsedNow) it.collapsedPebbles + key else it.collapsedPebbles - key,
        )
    }
    viewModelScope.launch { settingsStore.setSectionCollapsed(v.vin, section, collapsedNow) }
}

/** Fetch recent EV trips once per session (the Trips pebble calls this lazily). */
fun AppViewModel.loadTrips(v: Vehicle) {
    if (v.vin in _state.value.trips || _state.value.isPending(v.vin, "trips")) return
    viewModelScope.launch {
        _state.update { it.copy(pending = it.pending + "${v.vin}:trips") }
        // Only cache the result on a successful fetch -- caching emptyList()
        // on a transient failure looked identical to "genuinely no trips",
        // and since the vin's presence in the map is what gates a re-fetch
        // above, one bad network blip permanently stuck this car at "no
        // trips" for the rest of the session with no way to retry.
        // Serialize with every other repo call via the account-wide statusMutex:
        // Blue Link rejects overlapping requests ("a previous request is pending"),
        // and an unlocked trips() call could also race a concurrent 401 refresh
        // using the same stale refresh token. Every other repo.* path takes this
        // lock (loadStatus/runCommand/loadGarage/loadTrips); this
        // was the lone gap. Only the network call is inside the lock — the filter
        // and result handling stay outside, matching loadStatus's minimal scope.
        val fetched = runCatching { statusMutex.withLock { repoFor(v).trips(v) } }
            .onFailure { e -> AppLog.log("⚠ Trips for ${v.name}: ${e.message ?: "failed"}") }
            .getOrNull()
            ?.filter { (it.distance ?: 0.0) > 0 }
        _state.update {
            it.copy(
                trips = if (fetched != null) it.trips + (v.vin to fetched) else it.trips,
                pending = it.pending - "${v.vin}:trips",
            )
        }
    }
}

// beginLiveDeviceLocation / locate moved to AppViewModelCommands.kt.

// lock / unlock / flashLights / hornAndLights / stopClimate / startClimate /
// toggleClimate / startCharge / stopCharge / setChargeLimits / runCommand /
// recordRemoteAction moved to AppViewModelCommands.kt.

// --- Settings / nav --------------------------------------------------

/** Kept in sync by the garage pager's and the compact cover pager's own
 *  settle effects -- see
 *  [UiState.onSettingsPageSlot]'s own doc. Guarded the same way, so
 *  settling on the same kind of page repeatedly (two cars in a row, or
 *  two settles on the Settings slot) doesn't emit a redundant UiState
 *  update every time. */
fun AppViewModel.setOnSettingsPageSlot(value: Boolean) {
    if (_state.value.onSettingsPageSlot != value) _state.update { it.copy(onSettingsPageSlot = value) }
}

/** Set (or clear, with null) which saved preset the one-tap climate Start
 *  button runs for this car -- read back out in [bootstrapDriveSync]'s
 *  restore step into [UiState.defaultClimatePresets]. */
fun AppViewModel.setDefaultClimatePreset(vin: String, id: String?) = viewModelScope.launch {
    settingsStore.setDefaultClimatePreset(vin, id)
    // The STATE write, which was missing. UiState.defaultClimatePresets is populated
    // exactly once per process, inside bootstrapDriveSync -- which is guarded by an
    // AtomicBoolean and so never runs again. So this wrote to disk and nothing on screen
    // changed: the one-tap climate Start button kept using the OLD default for the rest of
    // the session, and the setting only appeared to take effect after a restart.
    _state.update {
        it.copy(
            defaultClimatePresets = if (id == null) {
                it.defaultClimatePresets - vin
            } else {
                it.defaultClimatePresets + (vin to id)
            },
        )
    }
}

// syncNow / setPrimaryDevice / pullFromPrimary / renameThisDevice / removeSyncedDevice /
// testSync / runDriveSyncNow moved to AppViewModelSync.kt.

/**
 * Shared wrapper for the handful of operations that should show the
 * app-wide loading spinner ([UiState.loading]) rather than a per-action
 * one: sets loading=true and clears any stale message, runs [block] inside
 * viewModelScope, and in a finally-block always clears loading=false
 * regardless of success/failure -- so a thrown exception can never leave
 * the spinner stuck on. Any exception [block] throws is caught here,
 * logged, and turned into a snackbar message instead of crashing the
 * ViewModel's coroutine scope.
 */
internal fun AppViewModel.launchBusy(block: suspend () -> Unit) {
    viewModelScope.launch {
        _state.update { it.copy(loading = true, message = null) }
        try {
            block()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val msg = e.message ?: "Something went wrong"
            AppLog.log("⚠ $msg")
            _state.update { it.copy(message = msg, messageType = "error") }
        } finally {
            _state.update { it.copy(loading = false) }
        }
    }
}
