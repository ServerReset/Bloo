package com.bloo.bluelink.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.migrateSchema
import com.bloo.bluelink.data.pruneOrphanPhotos
import com.bloo.bluelink.data.lastSyncError
import com.bloo.bluelink.data.lastSyncMs
import com.bloo.bluelink.data.setLastSyncError
import com.bloo.bluelink.data.settingsMode
import com.bloo.bluelink.data.snapshot
import com.bloo.bluelink.data.syncDeviceId
import com.bloo.bluelink.data.syncDeviceName
import com.bloo.bluelink.data.syncFileFingerprint
import com.bloo.bluelink.data.syncPrimaryDeviceId
import com.bloo.bluelink.data.syncUri
import com.bloo.bluelink.data.syncWifiOnly
import com.bloo.bluelink.data.syncedDevices
import com.bloo.bluelink.data.watchLockTiming

// --- Cold-start Drive sync bootstrap (extracted from AppViewModel) --

internal fun AppViewModel.bootstrapDriveSync() {
    if (!driveSyncBootstrapped.compareAndSet(false, true)) return
    com.bloo.bluelink.data.StartupTrace.markIfStarting("bootstrapDriveSync: entered")
    // Restore auto-sync Drive URI and last sync timestamp from preferences.
    viewModelScope.launch {
        val snap = settingsStore.snapshot()
        com.bloo.bluelink.data.StartupTrace.markIfStarting("bootstrapDriveSync: prefs snapshot read")
        val uri = settingsStore.syncUri(snap)
        val lastSync = settingsStore.lastSyncMs(snap)
        var lastError = settingsStore.lastSyncError(snap)
        if (uri != null) {
            val stillGranted = runCatching {
                getApplication<android.app.Application>().contentResolver.persistedUriPermissions.any {
                    it.uri.toString() == uri && it.isReadPermission && it.isWritePermission
                }
            }.getOrDefault(true)
            if (!stillGranted) {
                lastError = "Lost access to the Drive file. Set up sync again"
                settingsStore.setLastSyncError(lastError)
            }
        }
        val wifiOnly = settingsStore.syncWifiOnly(snap)
        val watchLockTiming = settingsStore.watchLockTiming()
        val settingsMode = settingsStore.settingsMode(snap)
        // Restore the cached device registry + primary + this-device identity so Settings shows
        // "your devices" immediately on launch, before (and even without) the first live sync of
        // the session.
        val cachedDevices = settingsStore.syncedDevices(snap)
        val cachedPrimary = settingsStore.syncPrimaryDeviceId(snap)
        // Falls back to the suspend, id-minting overload only on the rare snapshot where no device
        // id has ever been written yet.
        val myDeviceId = settingsStore.syncDeviceId(snap) ?: settingsStore.syncDeviceId()
        val myDeviceName = settingsStore.syncDeviceName(snap)
        val fileFingerprint = settingsStore.syncFileFingerprint(snap)
        com.bloo.bluelink.data.StartupTrace.markIfStarting("seedDefaultClimatePresets: starting")
        seedDefaultClimatePresets()
        com.bloo.bluelink.data.StartupTrace.markIfStarting("seedDefaultClimatePresets: done")
        _state.update {
            it.copy(
                syncUri = uri, lastSyncMs = lastSync, syncError = lastError, syncWifiOnly = wifiOnly,
                // defaultClimatePresets is NOT set here any more -- see seedDefaultClimatePresets.
                // It is per-garage-load, and this block runs once per process.
                settingsMode = settingsMode,
                syncDevices = cachedDevices, syncPrimaryId = cachedPrimary,
                thisDeviceId = myDeviceId, syncDeviceName = myDeviceName,
                watchLockTiming = watchLockTiming,
                syncFileFingerprint = fileFingerprint,
            )
        }
    }
    // Bidirectional auto-sync on refresh: download newer settings from Drive, then upload our
    // current settings (merge loop for cross-device sync).
    viewModelScope.launch {
        // True only for the very first emission below (the launch-time bootstrap pass, per this
        // block's own doc a few lines down) -- NOT for a real refresh finishing later, which should
        // still sync immediately as before.
        var firstPass = true
        _state.map { it.refreshing }.distinctUntilChanged().collect { wasRefreshing ->
            // Route through the single sync path so an imported remote is actually folded into
            // UiState (refreshLocalCarConfig), not just lastSyncMs/syncError -- same handling as
            // setSyncUri / syncNow.
            if (!wasRefreshing) {
                if (firstPass) {
                    firstPass = false
                    // Delaying just this ONE bootstrap pass (not the dirty-key auto-push above, and
                    // not a REAL refresh finishing later) keeps settings syncing on every cold
                    // start same as before, just not competing for the first few seconds a user is
                    // actually staring at a loading screen for.
                    delay(DRIVE_SYNC_COLD_START_DELAY_MS)
                }
                runDriveSyncNow()
            }
        }
    }
    // Auto-push on ANY tracked change: every editTracked() that touches a portable pref (a settings
    // toggle, a pebble/section reorder, per-car config…) appends to the dirty set, so observing it
    // here lets sync feel automatic and seamless instead of only firing on a data refresh or the 2h
    // worker.
    viewModelScope.launch {
        var pushJob: kotlinx.coroutines.Job? = null
        // No .distinctUntilChanged() here: dirtyKeysFlow already dedupes, on the key set AND a
        // biometric of those keys' values.
        settingsStore.dirtyKeysFlow
            .collect { dirty ->
                // Empty = nothing pending (or a sync just cleared it) — cancel any scheduled push
                // and wait for the next real change.
                if (dirty.isEmpty() || _state.value.syncUri == null) {
                    pushJob?.cancel()
                    return@collect
                }
                pushJob?.cancel()
                // viewModelScope.launch (not a bare `launch`): the collect{} lambda's receiver is
                // FlowCollector, not a CoroutineScope, so the debounce job is launched on the
                // ViewModel's own scope.
                pushJob = viewModelScope.launch {
                    kotlinx.coroutines.delay(AUTO_PUSH_DEBOUNCE_MS)
                    runDriveSyncNow()
                }
            }
    }

    // Sweep car photos no pref points at any more, once per launch.
    viewModelScope.launch {
        kotlinx.coroutines.delay(PHOTO_SWEEP_DELAY_MS)
        runCatching { settingsStore.migrateSchema() }
        runCatching { settingsStore.pruneOrphanPhotos() }
    }
}
