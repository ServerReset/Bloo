package com.bloo.bluelink.ui

import com.bloo.bluelink.data.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- Settings export/import and Drive auto-sync (extracted from AppViewModel) --

/**
 * Share a full settings backup (includes colours and palettes) via the share
 * sheet, as a real file — not raw EXTRA_TEXT, which most file-saving targets
 * (Drive, Files, email attachments) don't accept as a share destination at
 * all, silently limiting "Export" to text-only apps and defeating the whole
 * point of producing something "Restore" can later read back in.
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
        // Refresh the already-loaded vehicles' local config (seats, powertrain,
        // photo, ...) so the UI reflects the restore immediately instead of
        // waiting for some unrelated event to trigger a full garage reload.
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
        // Without a PERSISTED grant, this session's temporary read/write
        // access from the picker intent works until the process dies, then
        // every sync attempt fails with a SecurityException forever with no
        // obvious fix in sight -- refuse to enable sync at all instead of
        // silently setting up something that's guaranteed to break later.
        AppLog.log("⚠ Drive sync: couldn't get persistent access to that file")
        _state.update { it.copy(message = "Couldn't get lasting access to that file. Try picking it again", messageType = "error") }
        return@launch
    }
    AppLog.log("Drive auto-sync enabled")
    // Reset per-file sync gate state BEFORE pointing at the (possibly new) file:
    // stale hash/synced-ever/lastSync/dirty from a previous file would block
    // adoption of and convergence with this one. This also re-arms join-adopt
    // (synced_ever=false), so if the picked file already has content (e.g. the
    // user pointed "Save to Drive" at an existing Bloo file) this device adopts
    // it; a brand-new empty file has nothing to adopt and just receives our
    // upload — either way correct.
    settingsStore.resetSyncStateForNewFile()
    settingsStore.setSyncUri(uri.toString())
    _state.update { it.copy(syncUri = uri.toString()) }
    // Push this device's settings to the file right away instead of
    // waiting for the next unrelated refresh cycle to complete -- see
    // runDriveSyncNow's doc comment for why that matters.
    runDriveSyncNow()
}

/** Disable auto-sync. */
fun AppViewModel.clearSyncUri() = viewModelScope.launch {
    settingsStore.setSyncUri(null)
    // Also drop any stale error (in-memory AND persisted) so re-enabling
    // sync later doesn't briefly show an error from the previous, now-
    // disabled setup before the first new sync attempt completes.
    settingsStore.setLastSyncError(null)
    _state.update { it.copy(syncUri = null, syncError = null) }
    AppLog.log("Drive auto-sync disabled")
}

/** Join an existing Drive sync file and set up auto-sync to it.
 *
 * Adoption now happens through [SettingsStore.performMainToMainSync]'s **join-adopt**
 * path (a device that has never synced THIS file fully adopts it as the source
 * of truth), NOT a separate up-front `importSettingsJson`. That's the actual bug
 * fix: the old explicit import routed through `editTracked`, which marked every
 * imported key dirty, so the very first sync pass then "protected" all of them
 * and the device never converged with the primary. We only need to (1) confirm
 * the file is readable, (2) take a persisted grant, (3) reset per-file gate state
 * so join-adopt arms, then (4) run one pass. */
fun AppViewModel.importSettingsAndSync(context: android.content.Context, uri: android.net.Uri) = viewModelScope.launch {
    importSettingsAndSyncSuspend(context, uri)
}

/**
 * Suspending body of [importSettingsAndSync], split out so
 * [restoreFromSyncThenContinue] can await the whole join (read, persisted
 * grant, join-adopt pass, local-config refresh) before it re-resolves
 * which screen to land on -- a plain `viewModelScope.launch` gives no way
 * to know when that's actually finished. Returns whether the join
 * succeeded (a persisted grant was obtained and the sync pass ran), not
 * whether the picked file actually had anything to adopt.
 */
internal suspend fun AppViewModel.importSettingsAndSyncSuspend(context: android.content.Context, uri: android.net.Uri): Boolean {
    // Read once purely to confirm the file is reachable; do NOT import it here.
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
        // Without a persisted grant, sync is guaranteed to start failing the
        // moment this process dies, so don't claim auto-sync is enabled.
        AppLog.log("⚠ Drive sync: couldn't get persistent access to that file")
        _state.update {
            it.copy(message = "Couldn't get lasting access to that file. Try picking it again", messageType = "error")
        }
        return false
    }
    // Reset per-file gate state so join-adopt arms for this file (synced_ever
    // cleared), then point sync at it.
    settingsStore.resetSyncStateForNewFile()
    settingsStore.setSyncUri(uri.toString())
    _state.update { it.copy(syncUri = uri.toString(), message = "Auto-sync enabled", messageType = "success") }
    // One real pass now: performMainToMainSync join-adopts the file's settings (if it
    // has any) and uploads. refreshLocalCarConfig() below reflects an adopted
    // import into the already-loaded vehicles (seats/powertrain/photo) right away.
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

/** Manual "Sync now": force a full Drive push/pull right now. Available
 *  whenever sync is configured (not just after a failure) so the user can
 *  deliberately trigger a sync without waiting for a refresh or the 2h
 *  worker tick. Surfaces the outcome as a snackbar. */
fun AppViewModel.syncNow() {
    if (_state.value.syncUri == null) return
    viewModelScope.launch {
        runDriveSyncNow()
        val err = _state.value.syncError
        if (err == null) reportInfo("Synced with Drive") else reportError("Sync failed: $err")
    }
}

/** Designate [id] as the primary device (source of truth + tiebreaker). Persists
 *  locally and writes it into the Drive file on the sync pass that follows, so
 *  the choice propagates to every other device. */
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
 * "Kick" [id] out of the synced-devices list -- see [SettingsStore.removeSyncedDevice]'s
 * own doc for why this is a courtesy prune (a device that syncs again simply
 * reappears in the list, the same way a stale one would after 90 days) rather
 * than a permanent ban. Persists locally for instant UI feedback (the row
 * disappears before any round trip completes) and writes the removal into the
 * Drive file on the sync pass that follows -- same shape as setPrimaryDevice
 * and renameThisDevice above.
 */
fun AppViewModel.removeSyncedDevice(id: String) {
    viewModelScope.launch {
        settingsStore.removeSyncedDevice(id)
        _state.update { it.copy(syncDevices = it.syncDevices.filterNot { d -> d.id == id }) }
        runDriveSyncNow()
    }
}

/** Settings "Test sync" diagnostic: runs a non-destructive end-to-end
 *  round-trip against the real Drive file (permission → read → write →
 *  verify) and reports pass/fail as a snackbar, so the user can confirm
 *  sync actually works on their device/provider in one tap. Writes the
 *  file's own bytes back verbatim, so no settings are changed. */
fun AppViewModel.testSync() {
    viewModelScope.launch {
        val result = withContext(Dispatchers.IO) { settingsStore.testSyncRoundTrip() }
        if (result.ok) reportInfo(result.message) else reportError(result.message)
    }
}

/** Runs one [SettingsStore.performMainToMainSync] pass right now and folds the
 *  outcome into [UiState]. Both [setSyncUri] and [importSettingsAndSync]
 *  used to just flip the syncUri pref and wait for the passive
 *  refreshing-transition collector in [bootstrapDriveSync] to notice --
 *  which meant "enable sync" didn't actually upload or download anything
 *  until the next unrelated data refresh happened to complete, sometimes
 *  never in the session (e.g. backgrounding right after setup). That's
 *  exactly why a second device picking the same file moments later found
 *  nothing real there yet. Calling this immediately after either flow
 *  makes "enable sync" actually push/pull data right away. */
internal suspend fun AppViewModel.runDriveSyncNow() {
    val outcome = withContext(Dispatchers.IO) { settingsStore.performMainToMainSync() }
    // Recompute the file fingerprint each pass so it appears the moment sync is
    // set up / the file is changed (it's derived purely from the persisted URI).
    val fingerprint = withContext(Dispatchers.IO) { settingsStore.syncFileFingerprint() }
    if (outcome.ran) {
        if (outcome.imported) refreshLocalCarConfig()
        _state.update {
            it.copy(
                lastSyncMs = outcome.syncedAtMs,
                syncError = outcome.error,
                // On a transient download failure the outcome carries an empty
                // device list (nothing could be read this pass) — don't blank the
                // Settings "Synced devices" list; keep whatever we last showed.
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
