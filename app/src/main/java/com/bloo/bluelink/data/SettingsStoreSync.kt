package com.bloo.bluelink.data

import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

// --- Drive main-to-main sync engine (extracted from SettingsStore) --

suspend fun SettingsStore.resetSyncStateForNewFile() {
    context.settingsDataStore.edit {
        it.remove(stringPreferencesKey("sync_last_hash"))
        it.remove(booleanPreferencesKey("sync_synced_ever"))
        it.remove(stringPreferencesKey("sync_last_ms"))
        it.remove(stringPreferencesKey("sync_dirty_keys"))
        it.remove(booleanPreferencesKey("sync_pull_primary"))
        it.remove(stringPreferencesKey("sync_devices_cache"))
        it.remove(stringPreferencesKey("sync_primary_cache"))
        it.remove(stringPreferencesKey("sync_primary_pending"))
        it.remove(stringPreferencesKey("sync_pending_removed_device_ids"))
        // Drop the cached file id too — the new file has its own (or will mint one).
        it.remove(stringPreferencesKey("sync_file_id"))
    }
}

/**
 * One full bidirectional Drive-sync pass: download the file at [syncUri] (if configured), import it
 * when it's newer than our last sync (by the file's real last-modified time, falling back to a
 * timestamp embedded in the file for providers that don't expose one), then upload our current
 * settings with a fresh timestamp.
 */
/**
 * The last-modified time a Storage Access Framework document reports, in epoch millis, or null when
 * the URI is not a document URI, the provider returns nothing, or the query throws.
 * performMainToMainSync reads this in two places -- the download gate and the upload's self-write
 * guard -- to compare in the PROVIDER's clock domain rather than the device's, which is what keeps
 * the sync skew-safe and free of self-reimport.
 */
private fun SettingsStore.providerLastModifiedMs(parsed: android.net.Uri): Long? = runCatching {
    if (android.provider.DocumentsContract.isDocumentUri(context, parsed)) {
        context.contentResolver.query(
            parsed, arrayOf(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null, null, null,
        )?.use { if (it.moveToFirst()) it.getLong(0).takeIf { ts -> ts > 0 } else null }
    } else null
}.getOrNull()

suspend fun SettingsStore.performMainToMainSync(): SettingsStore.MainToMainSyncOutcome = mainToMainSyncMutex.withLock {
    // The periodic worker and the auto-sync-on-refresh collector can both fire within moments of
    // each other with no coordination otherwise -- this mutex makes them run one at a time instead
    // of racing to read/merge/upload the same Drive file.
    val uri = syncUri() ?: return@withLock SettingsStore.MainToMainSyncOutcome(ran = false, imported = false, uploaded = false, syncedAtMs = lastSyncMs())
    if (syncWifiOnly()) {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val wifi = cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
        if (!wifi) {
            AppLog.log("⚠ Drive sync: skipped (Wi-Fi only, on cellular)")
            return@withLock SettingsStore.MainToMainSyncOutcome(ran = false, imported = false, uploaded = false, syncedAtMs = lastSyncMs())
        }
    }
    val parsed = uri.toUri()
    // Without this, that device's first post-update pass would see !syncedEver and full-adopt its
    // OWN file, discarding any not-yet-uploaded local edits.
    if (!syncSyncedEver() && lastSyncMs() > 0L) setSyncSyncedEver(true)
    // Check the file's actual last-modified time from Drive.
    val fileModifiedMs = providerLastModifiedMs(parsed)
    // Download: read the existing file from Drive.
    var downloadError: String? = null
    val remoteContent = runCatching {
        withDriveRetry {
            kotlinx.coroutines.withTimeout(DRIVE_IO_TIMEOUT_MS) {
                // .use{} closes the InputStream (and its ParcelFileDescriptor); readText() alone
                // does NOT close, leaking an FD to the Drive SAF provider on every sync pass. Still
                // evaluates to String?.
                context.contentResolver.openInputStream(parsed)?.use { it.bufferedReader().readText() }
            }
        }
    }.onFailure {
        downloadError = if (it is kotlinx.coroutines.TimeoutCancellationException) "Timed out reading the Drive file" else it.message ?: "Couldn't read the Drive file"
    }.getOrNull()
    val remoteJson = remoteContent?.substringAfter('\n', "")?.takeIf { it.isNotBlank() }
    val remoteTs = fileModifiedMs ?: (remoteContent?.substringBefore('\n')?.toLongOrNull() ?: 0L)
    // The Drive-only metadata (content hash, primary, device registry). Absent fields → null/empty
    // (an old-client file, or the header/marker case).
    val remoteMeta = remoteJson?.let { SyncMerge.parseMeta(it) }
    val remoteHash = remoteMeta?.hash
    val remoteHasContent = remoteJson != null && SyncMerge.parseBackup(remoteJson) != null
    // Resolve the file's content-based id: the remote file's own id wins (so every device converges
    // on it), else what we've cached, else mint a fresh one (this device is the first to stamp the
    // file).
    val cachedFileId = syncFileId()
    val resolvedFileId = remoteMeta?.fileId ?: cachedFileId ?: java.util.UUID.randomUUID().toString()
    if (resolvedFileId != cachedFileId) setSyncFileId(resolvedFileId)

    // Adopt-mode + import-gate decision.
    val pullPrimary = syncPullPrimary()
    val syncedEver = syncSyncedEver()
    // Change gate: prefer the content HASH (skew-immune, and it self-detects a no-op so two devices
    // don't ping-pong re-imports); fall back to the file's modified-time only when the file
    // predates the hash (an un-updated client last wrote it and dropped our additive keys).
    val gatePassed = if (remoteHash != null) remoteHash != syncLastHash() else remoteTs > lastSyncMs()
    // Every other pass is a normal field-level protected merge.
    val fullAdopt = pullPrimary || !syncedEver
    val shouldImport = remoteHasContent && (pullPrimary || !syncedEver || gatePassed)
    var imported = false
    // No `&& remoteJson != null` here: remoteHasContent (folded into shouldImport above) already
    // requires it, and K2 proves it -- the extra check was dead code.
    if (shouldImport) {
        imported = if (fullAdopt) {
            adoptSettingsJson(remoteJson)
        } else {
            mergeSettingsJson(remoteJson, protect = dirtyKeys())
        }
        if (imported) {
            AppLog.log(if (fullAdopt) "Drive sync: adopted settings from file" else "Drive sync: imported newer settings")
            // Record the content we just took, so this pass's own state matches the file and the
            // next pass's gate is a no-op (no self-reimport).
            if (remoteHash != null) setSyncLastHash(remoteHash)
            setSyncSyncedEver(true)
        }
    }
    // `!syncedEver` right above doesn't have this problem -- it's derived from persisted state, not
    // a flag this function clears itself, so a failed pass naturally retries it next time; this
    // one-shot flag needs the same self-healing property.
    if (pullPrimary && downloadError == null) setSyncPullPrimary(false)

    val now = System.currentTimeMillis()
    var uploadError: String? = null
    val uploaded: Boolean
    // Devices queued locally for removal (see removeSyncedDevice's own doc) are filtered out of
    // whatever the file itself says HERE, once, so every use of the remote registry below -- the
    // immediate outcome, the merge, and the upload body -- agrees on the same filtered list rather
    // than three separate reads that could drift if this pref changed between them.
    val pendingRemovedIds = syncPendingRemovedDeviceIds()
    val remoteDevices = (remoteMeta?.devices ?: emptyList()).filterNot { it.id in pendingRemovedIds }
    // Best-available registry/primary for the outcome even if the upload half doesn't run (failed
    // download) — the UI still updates from what we read.
    var outcomeDevices: List<SyncMerge.SyncDevice> = remoteDevices
    // Precedence, and the order matters: an un-uploaded designation made HERE wins, then whatever
    // the file says, and only then this device's cached copy.
    val pendingPrimary = syncPrimaryPending()
    val primaryToWrite: String? = pendingPrimary ?: remoteMeta?.primaryDeviceId ?: syncPrimaryDeviceId()
    // Gate the ENTIRE upload block (dirty snapshot, body build, write/verify, and the
    // lastSyncMs/dirty-clear bookkeeping) on a clean download.
    if (downloadError != null) {
        uploaded = false
    } else {
        // Snapshot the dirty set that this upload body actually carries, taken right before the
        // body is built.
        val uploadedDirtyKeys = dirtyKeys()
        // Snapshot the portable content ONCE (post-import): its SHA-256 is both the change gate
        // written into the file AND, being computed over the exact prefs/photos we upload,
        // guarantees the file's `_hash` matches its own content.
        val prefsSnapshot = context.settingsDataStore.data.first()
        val prefsMap: Map<String, Any> = prefsSnapshot.asMap().entries.associate { it.key.name to it.value }
        val photos = SyncPhotos.encode(prefsSnapshot).mapValues { it.value.content }
        // ONE set, used by both the hash and the body -- see portableContentHash's param doc for
        // why passing it to only one of them corrupts the change gate.
        val carriedTombstones = remoteJson?.let { SyncMerge.parseRemoved(it) } ?: emptySet()
        val localHash = SyncMerge.portableContentHash(
            prefsMap, uploadedDirtyKeys, photos, priorRemoved = carriedTombstones,
        )
        val self = selfSyncDevice(now)
        outcomeDevices = SyncMerge.mergeDevices(remoteDevices, self, now)
        val driveBody = SyncMerge.buildExportForMainToMain(
            prefs = prefsMap,
            dirtyKeys = uploadedDirtyKeys,
            photos = photos,
            hash = localHash,
            primaryDeviceId = primaryToWrite,
            selfDevice = self,
            knownDevices = remoteDevices,
            nowMs = now,
            fileId = resolvedFileId,
            // Carry the remote file's OWN tombstones forward.
            priorRemoved = carriedTombstones,
        )
        val body = "$now\n$driveBody"
        uploaded = runCatching {
            withDriveRetry {
                kotlinx.coroutines.withTimeout(DRIVE_IO_TIMEOUT_MS) {
                    context.contentResolver.openOutputStream(parsed, "wt")?.use { it.write(body.toByteArray()) }
                        ?: error("Couldn't open the Drive file for writing")
                    val verify = context.contentResolver.openInputStream(parsed)?.use { it.bufferedReader().readText() }
                    if (verify != body) error("Upload didn't verify: the Drive file doesn't match what was written")
                }
            }
            AppLog.log("Drive sync: uploaded settings")
            true
        }.onFailure {
            uploadError = if (it is kotlinx.coroutines.TimeoutCancellationException) "Timed out writing the Drive file" else it.message ?: "Couldn't write the Drive file"
            AppLog.log("⚠ Drive sync: upload failed: ${it.message}")
        }.getOrElse { false }
        if (uploaded) {
            // Keep the wall-clock lastSyncMs advancing IN PARALLEL with the hash gate: it's the
            // fallback gate for a file an un-updated client overwrote (dropping `_hash`), so it
            // must stay current or the fallback breaks exactly when it's needed.
            val uploadedModifiedMs = providerLastModifiedMs(parsed)
            setLastSyncMs(uploadedModifiedMs ?: now)
            // Clear ONLY the keys this upload body actually carried, not the whole set -- an edit
            // made after the body snapshot (setters don't hold mainToMainSyncMutex) is still
            // pending and must stay dirty so a later remote import can't overwrite it.
            clearDirtyKeys(uploadedDirtyKeys)
            // The content-hash self-write guard: next pass reads this exact hash back and the gate
            // is a no-op (mirrors the lastSyncMs self-guard).
            setSyncLastHash(localHash)
            setSyncSyncedEver(true)
            // Cache the registry + primary for offline Settings display.
            setSyncedDevicesCache(outcomeDevices)
            setSyncPrimaryCache(primaryToWrite)
            if (pendingPrimary != null) setSyncPrimaryPending(null)
            // Same reasoning, same place: a kicked device is only truly gone once THIS upload --
            // the one that actually wrote a registry without it -- has verifiably landed.
            if (pendingRemovedIds.isNotEmpty()) {
                setSyncPendingRemovedDeviceIds(syncPendingRemovedDeviceIds() - pendingRemovedIds)
            }
        }
    }
    val error = uploadError ?: downloadError?.takeIf { remoteContent == null }
    // Persisted (not just returned) so a failure from the background periodic worker -- which has
    // no live ViewModel/UiState to update -- still shows up in Settings next time the app is
    // opened, instead of silently only ever reaching AppLog.
    setLastSyncError(error)
    return SettingsStore.MainToMainSyncOutcome(
        ran = true, imported = imported, uploaded = uploaded, syncedAtMs = if (uploaded) now else lastSyncMs(),
        error = error,
        devices = outcomeDevices,
        primaryDeviceId = primaryToWrite,
        selfDeviceId = syncDeviceId(),
    )
}

/**
 * A non-destructive end-to-end self-test of the Drive round-trip, for the Settings "Test sync"
 * button.
 */
suspend fun SettingsStore.testSyncRoundTrip(): SettingsStore.SyncTestResult {
    val uri = syncUri() ?: return SettingsStore.SyncTestResult(false, "Drive sync isn't set up yet.")
    val parsed = uri.toUri()
    // 1. Confirm we still hold a persisted read+write grant for this file.
    val granted = runCatching {
        context.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == uri && it.isReadPermission && it.isWritePermission
        }
    }.getOrDefault(false)
    if (!granted) {
        return SettingsStore.SyncTestResult(false, "Lost access to the Drive file. Set up sync again.")
    }
    // Serialize with real syncs so the read-then-write-back can't interleave with a concurrent
    // performMainToMainSync writing different content.
    return mainToMainSyncMutex.withLock {
        // 2. Read current bytes (an empty/new file reads as "" or null).
        val current = runCatching {
            withDriveRetry {
                kotlinx.coroutines.withTimeout(DRIVE_IO_TIMEOUT_MS) {
                    context.contentResolver.openInputStream(parsed)?.use { it.bufferedReader().readText() }
                }
            }
        }.getOrElse { e ->
            val why = if (e is kotlinx.coroutines.TimeoutCancellationException) "timed out reading" else (e.message ?: "couldn't read")
            return@withLock SettingsStore.SyncTestResult(false, "Couldn't read the Drive file ($why).")
        }
        // Write the SAME bytes back so user content is unchanged; only a genuinely empty file gets
        // a throwaway marker (overwritten by the next real sync's upload).
        val payload = current?.takeIf { it.isNotEmpty() } ?: "bloo-sync-test"
        // 3. Truncate-write + 4. verify, exactly as performMainToMainSync does.
        val verified = runCatching {
            withDriveRetry {
                kotlinx.coroutines.withTimeout(DRIVE_IO_TIMEOUT_MS) {
                    context.contentResolver.openOutputStream(parsed, "wt")?.use { it.write(payload.toByteArray()) }
                        ?: error("couldn't open for writing")
                    val readBack = context.contentResolver.openInputStream(parsed)?.use { it.bufferedReader().readText() }
                    readBack == payload
                }
            }
        }.getOrElse { e ->
            val why = if (e is kotlinx.coroutines.TimeoutCancellationException) "timed out writing" else (e.message ?: "write failed")
            return@withLock SettingsStore.SyncTestResult(false, "Couldn't write the Drive file ($why).")
        }
        if (verified) {
            SettingsStore.SyncTestResult(true, "Drive sync is working: read, wrote and verified the file successfully.")
        } else {
            SettingsStore.SyncTestResult(false, "The write didn't verify: the provider may be dropping or truncating writes.")
        }
    }
}

/**
 * Runs [block] once, and if it throws, once more after a short delay -- a single retry absorbs the
 * kind of momentary blip (Drive app still waking up, a dropped packet) that would otherwise fail an
 * entire sync pass outright.
 */
private suspend fun <T> SettingsStore.withDriveRetry(block: suspend () -> T): T = try {
    block()
} catch (e: kotlinx.coroutines.CancellationException) {
    if (e is kotlinx.coroutines.TimeoutCancellationException) {
        kotlinx.coroutines.delay(1000)
        block()
    } else {
        throw e
    }
} catch (e: Exception) {
    kotlinx.coroutines.delay(1000)
    block()
}
