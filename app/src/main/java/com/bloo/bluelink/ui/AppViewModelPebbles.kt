package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.CarAlerts
import com.bloo.bluelink.data.Notifications
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

/**
 * AppViewModel's car selection, pebble and trip actions, as extensions of [AppViewModel], kept out
 * of the class so it stays readable.
 */

/**
 * Evaluates [status] against the user's alert thresholds (see [CarAlerts]), posts a notification
 * per alert, and shows the first as an in-app message too. Called after every successful status
 * load.
 */
internal suspend fun AppViewModel.checkAlerts(v: Vehicle, status: VehicleStatus) {
    val alerts = CarAlerts.evaluate(settingsStore, v, status)
    alerts.forEach { Notifications.post(getApplication(), it.id, it.title, it.text, it.actions, it.channelId, it.localOnly) }
    alerts.firstOrNull()?.let { a -> _state.update { it.copy(message = a.text, messageType = "error") } }
}

// --- App PIN (device unlock PIN) -------------------------------------

// --- Garage / vehicles ----------------------------------------------

/**
 * Switch the visible car (swipe). Updates the index, and lazily loads this car's status only if we
 * don't already have it — so already-loaded cars are never re-fetched on a swipe, but a car that
 * failed to load at startup gets another chance when you view it.
 */
fun AppViewModel.selectIndex(index: Int) {
    val v = _state.value.vehicles.getOrNull(index) ?: return
    // A no-op selection must not emit: UiState is unstable, so an emission recomposes every
    // composed car page, and the pager's settledPage flow re-fires on re-settles of the same car.
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
        // Keep the same CAR selected across a reorder, not the same position -- selectIndex/expand
        // always update currentIndex together with vehicles, but this was the one place that moved
        // vehicles without it, so dragging a car above the currently selected one silently swapped
        // which car the detail view showed.
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
 * Publish after [textFieldPublishDebounceMs] of quiet on [key], superseding any pending publish for
 * that key. Raw text fields would otherwise run the full [persistSnapshots] fan-out on every
 * keystroke.
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
 * Settings cards share this function via the placeholder [SettingsPseudoVehicle] (only its `vin` is
 * read), so they persist exactly like a car's pebble.
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
        // Only cache the result on a successful fetch -- caching emptyList() on a transient failure
        // looked identical to "genuinely no trips", and since the vin's presence in the map is what
        // gates a re-fetch above, one bad network blip permanently stuck this car at "no trips" for
        // the rest of the session with no way to retry.
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

// --- Settings / nav --------------------------------------------------

/**
 * Kept in sync by the pager settle effects (see [UiState.onSettingsPageSlot]); guarded so repeated
 * settles don't emit.
 */
fun AppViewModel.setOnSettingsPageSlot(value: Boolean) {
    if (_state.value.onSettingsPageSlot != value) _state.update { it.copy(onSettingsPageSlot = value) }
}

/**
 * Set (or clear, with null) the saved preset the one-tap climate Start button runs; restored in
 * [bootstrapDriveSync] into [UiState.defaultClimatePresets].
 */
fun AppViewModel.setDefaultClimatePreset(vin: String, id: String?) = viewModelScope.launch {
    settingsStore.setDefaultClimatePreset(vin, id)
    // The STATE write, which was missing. UiState.defaultClimatePresets is populated exactly once
    // per process, inside bootstrapDriveSync -- which is guarded by an AtomicBoolean and so never
    // runs again.
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

/**
 * Runs [block] under the app-wide loading spinner ([UiState.loading]), clearing stale messages
 * first and always clearing loading afterwards. Exceptions are logged and surfaced as a snackbar
 * message.
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
