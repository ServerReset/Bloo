package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bloo.bluelink.data.resetSyncStateForNewFile
import com.bloo.bluelink.data.performMainToMainSync
import com.bloo.bluelink.data.testSyncRoundTrip
import com.bloo.bluelink.data.exportSettingsJson
import com.bloo.bluelink.data.importSettingsJson
import com.bloo.bluelink.data.removeSyncedDevice
import com.bloo.bluelink.data.requestPullFromPrimary
import com.bloo.bluelink.data.setLastSyncError
import com.bloo.bluelink.data.setPrimaryDevice
import com.bloo.bluelink.data.setSyncDeviceName
import com.bloo.bluelink.data.setSyncUri
import com.bloo.bluelink.data.setSyncWifiOnly
import com.bloo.bluelink.data.setWatchLockTiming
import com.bloo.bluelink.data.syncFileFingerprint
import com.bloo.bluelink.data.syncUri

// --- Settings export/import and Drive auto-sync (extracted from AppViewModel) --

/**
 * Share a full settings backup (colours and palettes included) via the share sheet as a real
 * file, since most file-saving targets reject raw EXTRA_TEXT.
 */
fun AppViewModel.exportSettings(context: android.content.Context) = viewModelScope.launch {
    val json = settingsStore.exportSettingsJson()
    val uri = withContext(Dispatchers.IO) {
        runCatching {
            val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
            val file = java.io.File(dir, "bloo_settings_backup.json")
            file.writeText(json)
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }
    if (uri == null) {
        _state.update { it.copy(message = "Couldn't prepare the backup file") }
        return@launch
    }
    runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Bloo settings backup")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            android.content.Intent.createChooser(intent, "Export settings")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        AppLog.log("Settings exported")
    }.onFailure { _state.update { s -> s.copy(message = "Couldn't open the share sheet") } }
}

/** Restore a full settings backup from a user-picked JSON file. */
fun AppViewModel.importSettings(context: android.content.Context, uri: android.net.Uri) = viewModelScope.launch {
    val json = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull()
    }
    if (json == null) {
        _state.update { it.copy(message = "Couldn't read that file") }
        AppLog.log("⚠ Settings import: could not read file")
        return@launch
    }
    val error = settingsStore.importSettingsJson(json)
    AppLog.log(if (error == null) "Settings imported from backup" else "⚠ Settings import: $error")
    _state.update { it.copy(message = error ?: "Settings restored", messageType = if (error == null) "success" else "error") }
    if (error == null) {
        // Refresh loaded vehicles' local config (seats, powertrain, photo) so the restore shows immediately.
        refreshLocalCarConfig()
    }
}

/** Set up auto-sync: store a Drive URI for automatic backup on each refresh. */
fun AppViewModel.setSyncUri(uri: android.net.Uri) = viewModelScope.launch {
    val granted = runCatching {
        getApplication<android.app.Application>().contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }.isSuccess
    if (!granted) {
        // Without a PERSISTED grant the picker's temporary access dies with the process and every sync
        // fails with a SecurityException; refuse to enable sync instead.
        AppLog.log("⚠ Drive sync: couldn't get persistent access to that file")
        _state.update { it.copy(message = "Couldn't get lasting access to that file. Try picking it again", messageType = "error") }
        return@launch
    }
    AppLog.log("Drive auto-sync enabled")
    // Reset per-file sync gate state before pointing at the file: stale hash/synced-ever/lastSync/dirty
    // would block adoption and convergence. This re-arms join-adopt (synced_ever=false).
    settingsStore.resetSyncStateForNewFile()
    settingsStore.setSyncUri(uri.toString())
    _state.update { it.copy(syncUri = uri.toString()) }
    // Push right away rather than waiting for the next refresh; see runDriveSyncNow.
    runDriveSyncNow()
}

/** Disable auto-sync. */
fun AppViewModel.clearSyncUri() = viewModelScope.launch {
    settingsStore.setSyncUri(null)
    // Also drop any stale error (in-memory and persisted) so re-enabling doesn't flash it.
    settingsStore.setLastSyncError(null)
    _state.update { it.copy(syncUri = null, syncError = null) }
    AppLog.log("Drive auto-sync disabled")
}

/** Join an existing Drive sync file and set up auto-sync to it.
 *
 * Adoption goes through [SettingsStore.performMainToMainSync]'s join-adopt path, not an up-front
 * `importSettingsJson` (which routed through `editTracked` and marked every key dirty, so the
 * device never converged). Steps: confirm readable, take a persisted grant, reset gate state, run one pass. */
fun AppViewModel.importSettingsAndSync(context: android.content.Context, uri: android.net.Uri) = viewModelScope.launch {
    importSettingsAndSyncSuspend(context, uri)
}

/**
 * Suspending body of [importSettingsAndSync], so [restoreFromSyncThenContinue] can await the
 * whole join. Returns whether the join succeeded (grant obtained, pass ran), not whether the
 * file had anything to adopt.
 */
internal suspend fun AppViewModel.importSettingsAndSyncSuspend(context: android.content.Context, uri: android.net.Uri): Boolean {
    // Read once only to confirm the file is reachable; do NOT import it here.
    val readable = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.isSuccess
    }
    if (!readable) AppLog.log("⚠ Drive sync: couldn't read the picked file (will still try to enable sync)")
    val granted = runCatching {
        getApplication<android.app.Application>().contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    }.isSuccess
    if (!granted) {
        // Without a persisted grant sync fails once the process dies, so don't claim it is enabled.
        AppLog.log("⚠ Drive sync: couldn't get persistent access to that file")
        _state.update {
            it.copy(message = "Couldn't get lasting access to that file. Try picking it again", messageType = "error")
        }
        return false
    }
    // Reset per-file gate state (synced_ever cleared) so join-adopt arms, then point sync at it.
    settingsStore.resetSyncStateForNewFile()
    settingsStore.setSyncUri(uri.toString())
    _state.update { it.copy(syncUri = uri.toString(), message = "Auto-sync enabled", messageType = "success") }
    // One real pass now: join-adopts the file's settings (if any) and uploads; refreshLocalCarConfig() reflects them.
    runDriveSyncNow()
    if (_state.value.syncError == null) refreshLocalCarConfig()
    return true
}

/** Set Wi-Fi only vs any network for auto-sync. */
fun AppViewModel.setSyncWifiOnly(wifiOnly: Boolean) = viewModelScope.launch {
    AppLog.log("Drive sync: ${if (wifiOnly) "Wi-Fi only" else "any network"}")
    settingsStore.setSyncWifiOnly(wifiOnly)
    _state.update { it.copy(syncWifiOnly = wifiOnly) }
}

/** Set when a paired watch should ask for the app PIN; pushed to the watch immediately. */
fun AppViewModel.setWatchLockTiming(timing: com.bloo.bluelink.data.WatchLockTiming) {
    viewModelScope.launch {
        settingsStore.setWatchLockTiming(timing)
        _state.update { it.copy(watchLockTiming = timing) }
        com.bloo.bluelink.wear.PhoneWatchSyncService.pushNow(getApplication())
    }
}

/** Manual "Sync now": force a full Drive push/pull, available whenever sync is configured.
 *  Surfaces the outcome as a snackbar. */
fun AppViewModel.syncNow() {
    if (_state.value.syncUri == null) return
    viewModelScope.launch {
        runDriveSyncNow()
        val err = _state.value.syncError
        if (err == null) reportInfo("Synced with Drive") else reportError("Sync failed: $err")
    }
}

/** Designate [id] as the primary device (source of truth + tiebreaker); persisted locally and
 *  written to the Drive file on the next pass. */
fun AppViewModel.setPrimaryDevice(id: String) {
    viewModelScope.launch {
        settingsStore.setPrimaryDevice(id)
        _state.update { it.copy(syncPrimaryId = id) }
        runDriveSyncNow()
    }
}

/** "Pull from primary now": force this device to fully adopt the file's settings
 *  on the next pass (the primary is the source of truth), then run it. */
fun AppViewModel.pullFromPrimary() {
    if (_state.value.syncUri == null) return
    viewModelScope.launch {
        settingsStore.requestPullFromPrimary()
        runDriveSyncNow()
        val err = _state.value.syncError
        if (err == null) reportInfo("Pulled the latest settings") else reportError("Couldn't pull: $err")
    }
}

/** Rename THIS device in the sync registry. Persists locally and republishes on
 *  the next sync pass (name changes ride the registry heartbeat). */
fun AppViewModel.renameThisDevice(name: String) {
    viewModelScope.launch {
        settingsStore.setSyncDeviceName(name)
        _state.update { it.copy(syncDeviceName = name.trim()) }
        runDriveSyncNow()
    }
}

/**
 * "Kick" [id] out of the synced-devices list: a courtesy prune, not a ban (see
 * [SettingsStore.removeSyncedDevice]). Persists locally for instant UI feedback and writes the
 * removal to the Drive file on the next pass.
 */
fun AppViewModel.removeSyncedDevice(id: String) {
    viewModelScope.launch {
        settingsStore.removeSyncedDevice(id)
        _state.update { it.copy(syncDevices = it.syncDevices.filterNot { d -> d.id == id }) }
        runDriveSyncNow()
    }
}

/** Settings "Test sync" diagnostic: a non-destructive Drive round-trip (permission, read, write,
 *  verify) reported as a snackbar. Writes the file's own bytes back verbatim. */
fun AppViewModel.testSync() {
    viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { settingsStore.testSyncRoundTrip() }
        if (result.ok) reportInfo(result.message) else reportError(result.message)
    }
}

/** Runs one [SettingsStore.performMainToMainSync] pass right now and folds the outcome into
 *  [UiState]. Called right after [setSyncUri] and [importSettingsAndSync] so enabling sync
 *  pushes/pulls immediately instead of waiting for the passive refresh collector. */
internal suspend fun AppViewModel.runDriveSyncNow() {
    val outcome = withContext(Dispatchers.IO) { settingsStore.performMainToMainSync() }
    // Recompute the file biometric each pass; it is derived purely from the persisted URI.
    val fingerprint = withContext(Dispatchers.IO) { settingsStore.syncFileFingerprint() }
    if (outcome.ran) {
        if (outcome.imported) refreshLocalCarConfig()
        _state.update {
            it.copy(
                lastSyncMs = outcome.syncedAtMs,
                syncError = outcome.error,
                // A transient download failure yields an empty device list; keep the last shown list.
                syncDevices = outcome.devices.ifEmpty { it.syncDevices },
                syncPrimaryId = outcome.primaryDeviceId ?: it.syncPrimaryId,
                thisDeviceId = outcome.selfDeviceId ?: it.thisDeviceId,
                syncFileFingerprint = fingerprint,
            )
        }
    } else {
        _state.update { it.copy(syncFileFingerprint = fingerprint) }
    }
}
