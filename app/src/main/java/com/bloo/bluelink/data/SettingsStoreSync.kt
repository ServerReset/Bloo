package com.bloo.bluelink.data

import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import com.bloo.uicommon.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import com.bloo.bluelink.ioScope
import kotlinx.coroutines.async

// --- Drive main-to-main sync engine (extracted from SettingsStore) --

suspend fun SettingsStore.resetSyncStateForNewFile() {
    context.settingsDataStore.edit {
        it.remove(stringPreferencesKey(PrefKeys.SYNC_LAST_HASH))
        it.remove(booleanPreferencesKey(PrefKeys.SYNC_SYNCED_EVER))
        it.remove(stringPreferencesKey(PrefKeys.SYNC_LAST_MS))
        it.remove(stringPreferencesKey(PrefKeys.SYNC_DIRTY_KEYS))
        it.remove(booleanPreferencesKey(PrefKeys.SYNC_PULL_PRIMARY))
        it.remove(stringPreferencesKey(PrefKeys.SYNC_DEVICES_CACHE))
        it.remove(stringPreferencesKey(PrefKeys.SYNC_PRIMARY_CACHE))
        it.remove(stringPreferencesKey(PrefKeys.SYNC_PRIMARY_PENDING))
        it.remove(stringPreferencesKey(PrefKeys.SYNC_PENDING_REMOVED_DEVICE_IDS))
        // Drop the cached file id too - the new file has its own (or will mint one).
        it.remove(stringPreferencesKey(PrefKeys.SYNC_FILE_ID))
    }
}

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

/**
 * A cheap "did another device write the file?" check for the foreground poll: one metadata query, no
 * download. The file's modified time is read in the PROVIDER's clock and compared with what this
 * device last wrote or read there, so our own uploads never look like a change. A provider that
 * reports no modified time is checked by content hash instead, at most every 30 seconds. False when
 * there is no sync file.
 */
suspend fun SettingsStore.syncFileChangedRemotely(): Boolean {
    // One read of the store for the whole check (this runs on every foreground poll tick), so the three facts also agree.
    val prefs = context.settingsDataStore.data.first()
    val uri = syncUri(prefs) ?: return false
    val parsed = uri.toUri()
    val modified = kotlinx.coroutines.withContext(Dispatchers.IO) { providerLastModifiedMs(parsed) }
    val now = android.os.SystemClock.elapsedRealtime()
    val due = modified == null && SyncPoll.contentPeekDue(now, lastContentPeekMs)
    if (due) lastContentPeekMs = now
    // The decision itself is SyncPoll.changed (unit-tested); this only supplies the facts. The file is
    // downloaded only if the provider gave no modified time and a peek is due.
    val remoteHash = suspend {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching { readDriveText(parsed) }.onFailure { rethrowUnlessTimeout(it) }.getOrNull()
        }?.substringAfter('\n', "")?.takeIf { it.isNotBlank() }?.let { SyncMerge.parseMeta(it)?.hash }
    }
    val hash = if (due) remoteHash() else null
    return SyncPoll.changed(modified, lastSyncMs(prefs), due, { hash }, syncLastHash(prefs))
}

/** When [syncFileChangedRemotely] last downloaded the file to compare hashes (providers with no modified time). */
private var lastContentPeekMs = 0L

/**
 * Writes the sync body to the Drive file and reads it back: read-write-truncate, flushed to disk before the descriptor closes (a plain
 * "wt" stream can close before the provider has the bytes), then verified against what was written. Throws when the write did not land.
 */
private suspend fun SettingsStore.writeDriveFile(parsed: android.net.Uri, body: String) {
    withDriveRetry {
        kotlinx.coroutines.withTimeout(DRIVE_IO_TIMEOUT_MS) {
            context.contentResolver.openFileDescriptor(parsed, "rwt")?.use { pfd ->
                val out = java.io.FileOutputStream(pfd.fileDescriptor)
                out.write(body.toByteArray())
                out.flush()
                pfd.fileDescriptor.sync()
            } ?: error("Couldn't open the Drive file for writing")
            val verify = context.readUriText(parsed)
            if (verify != body) error("Upload didn't verify: the Drive file doesn't match what was written")
        }
    }
}

/**
 * Whether the shared device registry has a real change to write: this device's own entry changed beyond its last-seen stamp, its heartbeat
 * has fallen due, or another device's entry changed. Pure, so it is easy to read and to test.
 */
private fun registryChangedSince(
    remoteDevices: List<SyncMerge.SyncDevice>,
    outcomeDevices: List<SyncMerge.SyncDevice>,
    self: SyncMerge.SyncDevice,
    now: Long,
): Boolean {
    val remoteSelf = remoteDevices.firstOrNull { it.id == self.id }
    val selfChanged = remoteSelf == null || remoteSelf.copy(lastSeenMs = self.lastSeenMs) != self
    val heartbeatDue = remoteSelf == null || now - remoteSelf.lastSeenMs >= REGISTRY_HEARTBEAT_MS
    val othersChanged = outcomeDevices.filterNot { it.id == self.id } != remoteDevices.filterNot { it.id == self.id }
    return selfChanged || heartbeatDue || othersChanged
}

/**
 * Reads the sync file from Drive, with the timeout and retry. Returns the text (null when the read failed) and the message for a failed read.
 * The read closes its stream: readText() alone would leak a file descriptor to the Drive provider on every sync pass.
 */
private suspend fun SettingsStore.readDriveFile(parsed: android.net.Uri): Pair<String?, String?> {
    var error: String? = null
    val content = runCatching {
        withDriveRetry { readDriveText(parsed) }
    }.onFailure {
        rethrowUnlessTimeout(it)
        error = if (it is TimeoutCancellationException) "Timed out reading the Drive file" else it.message ?: "Couldn't read the Drive file"
    }.getOrNull()
    return content to error
}

/**
 * After a verified upload: the bookkeeping that records this pass's content as what the file now holds (the modified time, the dirty keys it
 * carried, the content hash, the registry and primary caches, and the pending primary and removals it wrote). Only a pass whose upload landed reaches it.
 */
private suspend fun SettingsStore.recordUploadedPass(
    parsed: android.net.Uri,
    now: Long,
    uploadedDirtyKeys: Set<String>,
    localHash: String,
    outcomeDevices: List<SyncMerge.SyncDevice>,
    primaryToWrite: String?,
    pendingPrimary: String?,
    pendingRemovedIds: Set<String>,
) {
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

/**
 * The import half of a pass: decides whether the file's settings should come in (the change gate, or the first sync after an update), and imports them
 * as a full adopt or a protected merge. Returns whether anything was imported. The gate, not a flag, is what stops a file re-importing itself.
 */
private suspend fun SettingsStore.importRemoteSettings(
    remoteJson: String?,
    remoteHasContent: Boolean,
    remoteHash: String?,
    remoteTs: Long,
    pullPrimary: Boolean,
): Boolean {
    // Nothing to import without content: the same answer the gate below gives, read once here for the calls that need a String.
    val json = remoteJson ?: return false
    // Adopt-mode + import-gate decision.
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
            adoptSettingsJson(json)
        } else {
            mergeSettingsJson(json, protect = dirtyKeys())
        }
        if (imported) {
            AppLog.log(if (fullAdopt) "Drive sync: adopted settings from file" else "Drive sync: imported newer settings")
            // Record the content we just took, so this pass's own state matches the file and the
            // next pass's gate is a no-op (no self-reimport).
            if (remoteHash != null) setSyncLastHash(remoteHash)
            setSyncSyncedEver(true)
        }
    }
    return imported
}

/**
 * The end of a pass: the error is persisted (not just returned), so a failure from the background periodic worker -- which has no live
 * ViewModel to update -- still shows in Settings next time the app is opened. Returns what the caller reads back.
 */
private suspend fun SettingsStore.finishSyncPass(
    error: String?,
    imported: Boolean,
    uploaded: Boolean,
    now: Long,
    outcomeDevices: List<SyncMerge.SyncDevice>,
    primaryToWrite: String?,
): SettingsStore.MainToMainSyncOutcome {
    setLastSyncError(error)
    return SettingsStore.MainToMainSyncOutcome(
        ran = true, imported = imported, uploaded = uploaded, syncedAtMs = if (uploaded) now else lastSyncMs(),
        error = error, devices = outcomeDevices, primaryDeviceId = primaryToWrite, selfDeviceId = syncDeviceId(),
    )
}

/**
 * One full bidirectional Drive-sync pass: download the file at [syncUri] (if configured), import it
 * when it's newer than our last sync (by the file's real last-modified time, falling back to a
 * timestamp embedded in the file for providers that don't expose one), then upload our current
 * settings with a fresh timestamp.
 */
suspend fun SettingsStore.performMainToMainSync(
    /** The watch connected to THIS phone right now, if any: it is published in the registry, attached to this phone. */
    connectedWatchId: String? = null,
    connectedWatchName: String? = null,
): SettingsStore.MainToMainSyncOutcome = mainToMainSyncMutex.withLock {
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
    val (remoteContent, downloadError) = readDriveFile(parsed)
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

    val pullPrimary = syncPullPrimary()
    val syncedEver = syncSyncedEver()
    val fullAdopt = pullPrimary || !syncedEver
    val imported = importRemoteSettings(remoteJson, remoteHasContent, remoteHash, remoteTs, pullPrimary)
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
    // download) - the UI still updates from what we read.
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
        val self = selfSyncDevice(now)
        // This phone's connected watch, as the registry should hold it: attached to this phone, its "last seen" kept for the
        // heartbeat (so an unchanged watch is not a registry change on every poll). A watch this phone no longer has drops out.
        val watchEntry = connectedWatchId?.takeIf { it.isNotBlank() }?.let { id ->
            val wid = "watch:$id"
            val prior = remoteDevices.firstOrNull { it.id == wid }
            SyncMerge.SyncDevice(
                id = wid, name = connectedWatchName.orEmpty(), kind = SyncMerge.KIND_WATCH, hostId = self.id,
                lastSeenMs = if (prior != null && now - prior.lastSeenMs < REGISTRY_HEARTBEAT_MS) prior.lastSeenMs else now,
            )
        }
        val registry = remoteDevices.filterNot { it.isWatch && it.hostId == self.id && it.id != watchEntry?.id } + listOfNotNull(watchEntry)
        outcomeDevices = SyncMerge.mergeDevices(registry, self, now)
        // A pass that found the file "changed" but has nothing of its OWN to say must not rewrite it.
        // The foreground poll runs every few seconds, and our own upload advances the file's
        // modified time -- which the very next poll then reads as a remote change. An unconditional
        // upload here therefore became a Drive write every ~10 seconds, forever, feeding itself.
        // Only a pass with dirty keys, a registry/primary change, a first-ever sync, or a file we
        // have not written yet actually uploads; everything else is a no-op read.
        // This device's own entry is stamped "seen now" on every pass, so comparing the whole registry always reads as changed and
        // every poll rewrote the file. Only a REAL change counts: another device's entry, this device's own name/model/version, or
        // the heartbeat falling due (so a quiet device still reads as active to the others).
        val registryChanged = registryChangedSince(remoteDevices, outcomeDevices, self, now)
        val primaryChanged = primaryToWrite != remoteMeta?.primaryDeviceId
        val fileAlreadyCarriesUs = remoteHasContent && remoteHash != null && remoteHash == syncLastHash()
        val nothingToUpload = !SyncPoll.shouldUpload(
            dirtyKeys = uploadedDirtyKeys,
            registryChanged = registryChanged,
            primaryChanged = primaryChanged,
            fullAdopt = fullAdopt,
            fileAlreadyCarriesUs = fileAlreadyCarriesUs,
        )
        // A file that has content this version cannot read as a Bloo sync file (a plain exported backup with no sync
        // header, or one written by a NEWER Bloo) is left alone: the upload half used to overwrite it with this
        // device's own settings, destroying the backup or the newer format's data. Only an empty file is written to.
        val remoteUnrecognised = remoteJson != null && !remoteHasContent
        if (remoteUnrecognised) {
            uploaded = false
            uploadError = "The Drive file isn't a Bloo sync file this version can read, so it was left as it is."
            AppLog.log("⚠ Drive sync: remote file not recognised; not overwriting it")
        } else if (nothingToUpload) {
            uploaded = false
            // The file already carries our content: record the revision we just saw, so the next
            // poll's change check is a no-op instead of re-reading the file every few seconds. When Drive does not
            // report a modified time, the check itself is the sync: it is recorded as now, not left at the last upload
            // (which read as "Synced 15 hr ago" after every clean pass).
            setLastSyncMs(fileModifiedMs ?: now)
        } else {
            // Snapshot the portable content ONCE (post-import): its SHA-256 is both the change gate
            // written into the file AND, being computed over the exact prefs/photos we upload,
            // guarantees the file's `_hash` matches its own content.
            val prefsSnapshot = context.settingsDataStore.data.first()
            val prefsMap: Map<String, Any> = prefsSnapshot.toNameMap()
            val photos = SyncPhotos.encode(prefsSnapshot).mapValues { it.value.content }
            // ONE set, used by both the hash and the body -- see portableContentHash's param doc for
            // why passing it to only one of them corrupts the change gate.
            val carriedTombstones = remoteJson?.let { SyncMerge.parseRemoved(it) } ?: emptySet()
            val localHash = SyncMerge.portableContentHash(prefsMap, uploadedDirtyKeys, photos, priorRemoved = carriedTombstones)
            val driveBody = SyncMerge.buildExportForMainToMain(
                prefs = prefsMap,
                dirtyKeys = uploadedDirtyKeys,
                photos = photos,
                hash = localHash,
                primaryDeviceId = primaryToWrite,
                selfDevice = self,
                knownDevices = registry,
                nowMs = now,
                fileId = resolvedFileId,
                // Carry the remote file's OWN tombstones forward.
                priorRemoved = carriedTombstones,
            )
            val body = "$now\n$driveBody"
            // Re-read the file just before writing. If it changed since this pass read it, another phone wrote a newer copy:
            // overwriting it would drop that phone's update, so this pass waits and merges the newer copy on the next one.
            val fileChangedMidPass = context.readUriText(parsed) != remoteContent
            if (fileChangedMidPass) {
                AppLog.log("Drive sync: the file changed during this pass; not overwriting it, the next pass merges the newer copy")
                uploadError = "The Drive file changed during sync; it will retry."
            }
            uploaded = !fileChangedMidPass && runCatching {
                writeDriveFile(parsed, body)
                AppLog.log("Drive sync: uploaded settings to ${parsed.lastPathSegment}")
                true
            }.onFailure {
                uploadError = if (it is TimeoutCancellationException) "Timed out writing the Drive file" else it.message ?: "Couldn't write the Drive file"
                AppLog.log("⚠ Drive sync: upload failed: ${it.message}")
            }.getOrElse { false }
            if (uploaded) recordUploadedPass(parsed, now, uploadedDirtyKeys, localHash, outcomeDevices, primaryToWrite, pendingPrimary, pendingRemovedIds)
        }
    }
    val error = uploadError ?: downloadError?.takeIf { remoteContent == null }
    return finishSyncPass(error, imported, uploaded, now, outcomeDevices, primaryToWrite)
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
    if (!granted) return SettingsStore.SyncTestResult(false, "Lost access to the Drive file. Set up sync again.")
    // Serialize with real syncs so the read-then-write-back can't interleave with a concurrent
    // performMainToMainSync writing different content.
    return mainToMainSyncMutex.withLock {
        // 2. Read current bytes (an empty/new file reads as "" or null).
        val current = runCatching {
            withDriveRetry { readDriveText(parsed) }
        }.getOrElse { e ->
            rethrowUnlessTimeout(e)
            val why = if (e is TimeoutCancellationException) "timed out reading" else (e.message ?: "couldn't read")
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
                    val readBack = context.readUriText(parsed)
                    readBack == payload
                }
            }
        }.getOrElse { e ->
            val why = if (e is TimeoutCancellationException) "timed out writing" else (e.message ?: "write failed")
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
 * Reads the Drive file, waiting at most [DRIVE_IO_TIMEOUT_MS]. The provider's read blocks its thread and cannot be interrupted, and a
 * withTimeout wrapped straight around a blocking call never fires (it only acts when the block next suspends, which a blocking read never
 * does). So the read runs on its own, and this waits for it with a timeout that does fire. A read that hangs is left to finish or fail on
 * its own; nothing waits for it. Only reads go this way: an abandoned write could land after a retry's, so writes stay as they are.
 */
private suspend fun SettingsStore.readDriveText(parsed: android.net.Uri): String? {
    val read = driveReads.async { context.readUriText(parsed) }
    try {
        return kotlinx.coroutines.withTimeout(DRIVE_IO_TIMEOUT_MS) { read.await() }
    } finally {
        read.cancel()
    }
}

/** Where [readDriveText]'s reads run, apart from their callers, so a hung read never holds one up. */
private val driveReads = ioScope()

/** In a catch-all around a Drive read: our own timeout is a failure to report, but the caller being cancelled is not. */
private fun rethrowUnlessTimeout(e: Throwable) {
    if (e is CancellationException && e !is TimeoutCancellationException) throw e
}

/**
 * Runs [block] once, and if it throws, once more after a short delay -- a single retry absorbs the
 * kind of momentary blip (Drive app still waking up, a dropped packet) that would otherwise fail an
 * entire sync pass outright.
 */
private suspend fun <T> SettingsStore.withDriveRetry(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    if (e is TimeoutCancellationException) {
        kotlinx.coroutines.delay(1000)
        block()
    } else {
        throw e
    }
} catch (e: Exception) {
    kotlinx.coroutines.delay(1000)
    block()
}

/** How long this device may go without rewriting its "last seen" in the shared registry (kept under the "Active now" window). */
private const val REGISTRY_HEARTBEAT_MS = 5L * 60 * 1000
