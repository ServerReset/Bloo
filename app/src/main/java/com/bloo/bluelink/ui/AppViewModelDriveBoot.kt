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
        // One DataStore round trip for the whole bootstrap instead of ~10
        // sequential ones -- this runs on every cold start, so each extra
        // suspend read here was a small, compounding hit to how fast the
        // garage could show up.
        val snap = settingsStore.snapshot()
        com.bloo.bluelink.data.StartupTrace.markIfStarting("bootstrapDriveSync: prefs snapshot read")
        val uri = settingsStore.syncUri(snap)
        val lastSync = settingsStore.lastSyncMs(snap)
        // Restores a failure the background periodic worker hit while the
        // app was closed, so it's visible in Settings on next launch instead
        // of only ever surfacing if a foreground sync happens to fail too.
        var lastError = settingsStore.lastSyncError(snap)
        // Proactive check: don't wait for the next sync attempt to discover
        // the persisted grant is gone (revoked in system Settings, or the
        // picked Drive file/folder was deleted) -- surface it the moment
        // the app opens instead.
        if (uri != null) {
            val stillGranted = runCatching {
                getApplication<android.app.Application>().contentResolver.persistedUriPermissions.any {
                    it.uri.toString() == uri && it.isReadPermission && it.isWritePermission
                }
            }.getOrDefault(true) // Assume fine if the check itself fails; performMainToMainSync will report the real error.
            if (!stillGranted) {
                lastError = "Lost access to the Drive file. Set up sync again"
                settingsStore.setLastSyncError(lastError)
            }
        }
        val wifiOnly = settingsStore.syncWifiOnly(snap)
        val watchLockTiming = settingsStore.watchLockTiming()
        val settingsMode = settingsStore.settingsMode(snap)
        // Restore the cached device registry + primary + this-device identity so
        // Settings shows "your devices" immediately on launch, before (and even
        // without) the first live sync of the session.
        val cachedDevices = settingsStore.syncedDevices(snap)
        val cachedPrimary = settingsStore.syncPrimaryDeviceId(snap)
        // Falls back to the suspend, id-minting overload only on the rare
        // snapshot where no device id has ever been written yet.
        val myDeviceId = settingsStore.syncDeviceId(snap) ?: settingsStore.syncDeviceId()
        val myDeviceName = settingsStore.syncDeviceName(snap)
        val fileFingerprint = settingsStore.syncFileFingerprint(snap)
        // Still seeded from here for the normal cold start (vehicles are already in state by
        // the time this coroutine runs), and now ALSO from loadGarage so a first load that
        // returned nothing cannot leave it empty for the process.
        com.bloo.bluelink.data.StartupTrace.markIfStarting("seedDefaultClimatePresets: starting")
        seedDefaultClimatePresets()
        com.bloo.bluelink.data.StartupTrace.markIfStarting("seedDefaultClimatePresets: done")
        _state.update {
            it.copy(
                syncUri = uri, lastSyncMs = lastSync, syncError = lastError, syncWifiOnly = wifiOnly,
                // defaultClimatePresets is NOT set here any more -- see
                // seedDefaultClimatePresets. It is per-garage-load, and this block runs once
                // per process.
                settingsMode = settingsMode,
                syncDevices = cachedDevices, syncPrimaryId = cachedPrimary,
                thisDeviceId = myDeviceId, syncDeviceName = myDeviceName,
                watchLockTiming = watchLockTiming,
                syncFileFingerprint = fileFingerprint,
            )
        }
    }
    // Bidirectional auto-sync on refresh: download newer settings from Drive,
    // then upload our current settings (merge loop for cross-device sync).
    // The actual download/compare/import/upload sequence lives in
    // SettingsStore.performMainToMainSync().
    viewModelScope.launch {
        // True only for the very first emission below (the launch-time bootstrap pass,
        // per this block's own doc a few lines down) -- NOT for a real refresh finishing
        // later, which should still sync immediately as before.
        var firstPass = true
        _state.map { it.refreshing }.distinctUntilChanged().collect { wasRefreshing ->
            // Route through the single sync path so an imported remote is
            // actually folded into UiState (refreshLocalCarConfig), not just
            // lastSyncMs/syncError -- same handling as setSyncUri / syncNow.
            if (!wasRefreshing) {
                if (firstPass) {
                    firstPass = false
                    // Give the cold-start critical path (fetching the currently-viewed
                    // car's own status -- the fetch that gates its first-visible pebbles)
                    // a head start before this joins the queue. performMainToMainSync
                    // does real cross-process I/O (a Storage Access Framework round trip
                    // to the Drive app, possibly network-bound on Drive's end) that isn't
                    // anything the user is waiting ON at this exact moment the way the
                    // car's own status is -- reported directly, and confirmed by a real
                    // timed report: a plain, already-warm SessionStore.load() read (no
                    // I/O of its own beyond an in-memory DataStore snapshot) measured at
                    // 850ms-2.7s specifically while "Drive sync: uploaded settings" was
                    // running concurrently, with nothing else in that window. Delaying
                    // just this ONE bootstrap pass (not the dirty-key auto-push above, and
                    // not a REAL refresh finishing later) keeps settings syncing on every
                    // cold start same as before, just not competing for the first few
                    // seconds a user is actually staring at a loading screen for.
                    delay(DRIVE_SYNC_COLD_START_DELAY_MS)
                }
                runDriveSyncNow()
            }
        }
    }
    // Auto-push on ANY tracked change: every editTracked() that touches a
    // portable pref (a settings toggle, a pebble/section reorder, per-car
    // config…) appends to the dirty set, so observing it here lets sync feel
    // automatic and seamless instead of only firing on a data refresh or the
    // 2h worker. Debounced with a cancel-and-restart job so a burst of edits
    // (dragging pebbles, nudging a slider) coalesces into ONE Drive write
    // ~2s after the last change rather than hammering Drive per keystroke.
    // Only runs when sync is configured; the download-then-upload merge in
    // performMainToMainSync stays the single source of truth.
    viewModelScope.launch {
        var pushJob: kotlinx.coroutines.Job? = null
        // No .distinctUntilChanged() here: dirtyKeysFlow already dedupes, on the key set
        // AND a biometric of those keys' values. Deduping on the bare set a second time
        // would re-introduce exactly what that fixes -- re-editing one key after a failed
        // push yields an identical set, so the retry never got scheduled.
        settingsStore.dirtyKeysFlow
            .collect { dirty ->
                // Empty = nothing pending (or a sync just cleared it) — cancel any
                // scheduled push and wait for the next real change.
                if (dirty.isEmpty() || _state.value.syncUri == null) {
                    pushJob?.cancel()
                    return@collect
                }
                pushJob?.cancel()
                // viewModelScope.launch (not a bare `launch`): the collect{}
                // lambda's receiver is FlowCollector, not a CoroutineScope, so
                // the debounce job is launched on the ViewModel's own scope.
                pushJob = viewModelScope.launch {
                    kotlinx.coroutines.delay(AUTO_PUSH_DEBOUNCE_MS)
                    runDriveSyncNow()
                }
            }
    }

    // Sweep car photos no pref points at any more, once per launch. The crop screen
    // writes a fresh timestamped file each time and only overwrites the img_$vin pref,
    // so every re-crop stranded the previous full-resolution image on disk forever.
    //
    // Delayed rather than immediate: this is pure housekeeping with nothing waiting on
    // it, and launch is the one moment the process is contended (garage load, status
    // fetches, the first Drive pass, composition). Reading DataStore and stat-ing a
    // directory is cheap, but not free, and there is no reason for it to compete.
    viewModelScope.launch {
        kotlinx.coroutines.delay(PHOTO_SWEEP_DELAY_MS)
        // Retire settings that no longer exist (see SyncSchema), then sweep unreferenced photos.
        runCatching { settingsStore.migrateSchema() }
        runCatching { settingsStore.pruneOrphanPhotos() }
    }
}
