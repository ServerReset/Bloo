package com.bloo.bluelink.data

import android.content.Context
import androidx.core.net.toUri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock

// --- Drive main-to-main sync engine (extracted from SettingsStore) --

/** Reset all per-file sync gate state — MUST be called when the sync target URI
 *  changes, or stale hash/synced-ever/lastSync/dirty from the OLD file would
 *  block adoption of and convergence with the NEW file. */
suspend fun SettingsStore.resetSyncStateForNewFile() {
    context.settingsDataStore.edit {
        it.remove(stringPreferencesKey("sync_last_hash"))
        it.remove(booleanPreferencesKey("sync_synced_ever"))
        it.remove(stringPreferencesKey("sync_last_ms"))
        it.remove(stringPreferencesKey("sync_dirty_keys"))
        it.remove(booleanPreferencesKey("sync_pull_primary"))
        it.remove(stringPreferencesKey("sync_devices_cache"))
        it.remove(stringPreferencesKey("sync_primary_cache"))
        // The pending designation too: it named a primary for the OLD file's device
        // registry, and re-asserting it against a different file's registry is exactly
        // the stale-state bug this function exists to prevent.
        it.remove(stringPreferencesKey("sync_primary_pending"))
        // Same reasoning as the pending primary above: a removal intent named a
        // device id in the OLD file's own registry, which means nothing against a
        // different file's.
        it.remove(stringPreferencesKey("sync_pending_removed_device_ids"))
        // Drop the cached file id too — the new file has its own (or will mint
        // one). Keeping the old id would show a stale/mismatched File ID.
        it.remove(stringPreferencesKey("sync_file_id"))
    }
}

/**
 * One full bidirectional Drive-sync pass: download the file at [syncUri] (if
 * configured), import it when it's newer than our last sync (by the file's
 * real last-modified time, falling back to a timestamp embedded in the file
 * for providers that don't expose one), then upload our current settings with
 * a fresh timestamp.
 *
 * This is the ONE place this logic lives — it used to be duplicated between
 * the auto-sync-on-refresh collector and the on-demand "Sync now" request,
 * which is exactly how a bug (this device's own Drive URI leaking into the
 * portable export) existed in two copies at once.
 */
/**
 * The last-modified time a Storage Access Framework document reports, in epoch millis, or
 * null when the URI is not a document URI, the provider returns nothing, or the query throws.
 *
 * performMainToMainSync reads this in two places -- the download gate and the upload's
 * self-write guard -- to compare in the PROVIDER's clock domain rather than the device's,
 * which is what keeps the sync skew-safe and free of self-reimport. The two reads were
 * byte-for-byte identical; this is that query, once. Never throws (runCatching), because a
 * flaky Drive provider must degrade to "unknown time", not crash a sync pass.
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
    // The periodic worker and the auto-sync-on-refresh collector can both fire
    // within moments of each other with no coordination otherwise -- this mutex
    // makes them run one at a time
    // instead of racing to read/merge/upload the same Drive file.
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
    // Installed-base migration seed: `sync_synced_ever` is a brand-new key, so
    // it's false on every device that ALREADY synced this file under the old
    // (mtime-only) scheme. Without this, that device's first post-update pass
    // would see !syncedEver and full-adopt its OWN file, discarding any
    // not-yet-uploaded local edits. A device that has ever recorded a lastSyncMs
    // for this file is NOT a fresh joiner — mark it synced so it takes the normal
    // protected-merge path, not join-adopt.
    if (!syncSyncedEver() && lastSyncMs() > 0L) setSyncSyncedEver(true)
    // Check the file's actual last-modified time from Drive.
    val fileModifiedMs = providerLastModifiedMs(parsed)
    // Download: read the existing file from Drive.
    var downloadError: String? = null
    // withTimeout, not just runCatching -- a stalled SAF/DocumentsProvider
    // call (Drive app backgrounded, flaky network) previously had no
    // bound at all and could hang this coroutine indefinitely while still
    // holding mainToMainSyncMutex, blocking every other sync path (the worker,
    // the refresh collector) until it resolved.
    // withDriveRetry: one immediate retry so a single transient blip
    // (momentary network hiccup, Drive app briefly waking up) doesn't
    // force waiting for the periodic worker's own backoff or the next
    // unrelated refresh -- this pass is often the ONLY one that runs
    // right after the user enables sync, so it needs to actually land.
    val remoteContent = runCatching {
        withDriveRetry {
            kotlinx.coroutines.withTimeout(DRIVE_IO_TIMEOUT_MS) {
                // .use{} closes the InputStream (and its ParcelFileDescriptor);
                // readText() alone does NOT close, leaking an FD to the Drive
                // SAF provider on every sync pass. Still evaluates to String?.
                context.contentResolver.openInputStream(parsed)?.use { it.bufferedReader().readText() }
            }
        }
    }.onFailure {
        downloadError = if (it is kotlinx.coroutines.TimeoutCancellationException) "Timed out reading the Drive file" else it.message ?: "Couldn't read the Drive file"
    }.getOrNull()
    val remoteJson = remoteContent?.substringAfter('\n', "")?.takeIf { it.isNotBlank() }
    val remoteTs = fileModifiedMs ?: (remoteContent?.substringBefore('\n')?.toLongOrNull() ?: 0L)
    // The Drive-only metadata (content hash, primary, device registry). Absent
    // fields → null/empty (an old-client file, or the header/marker case).
    val remoteMeta = remoteJson?.let { SyncMerge.parseMeta(it) }
    val remoteHash = remoteMeta?.hash
    val remoteHasContent = remoteJson != null && SyncMerge.parseBackup(remoteJson) != null
    // Resolve the file's content-based id: the remote file's own id wins (so
    // every device converges on it), else what we've cached, else mint a fresh
    // one (this device is the first to stamp the file). Cache it so the File ID
    // shown in Settings is stable + identical across devices on this file.
    // Read the cached file id ONCE (was read twice back-to-back with nothing
    // writing between — each read is a full DataStore snapshot collect).
    val cachedFileId = syncFileId()
    val resolvedFileId = remoteMeta?.fileId ?: cachedFileId ?: java.util.UUID.randomUUID().toString()
    if (resolvedFileId != cachedFileId) setSyncFileId(resolvedFileId)

    // Adopt-mode + import-gate decision.
    val pullPrimary = syncPullPrimary()
    val syncedEver = syncSyncedEver()
    // Change gate: prefer the content HASH (skew-immune, and it self-detects a
    // no-op so two devices don't ping-pong re-imports); fall back to the file's
    // modified-time only when the file predates the hash (an un-updated client
    // last wrote it and dropped our additive keys).
    val gatePassed = if (remoteHash != null) remoteHash != syncLastHash() else remoteTs > lastSyncMs()
    // A device that has never synced THIS file, or an explicit "pull from
    // primary", FULLY adopts the file as source of truth (this is the fix for
    // "my other phone won't pull the primary's settings" — the old protected
    // merge kept a joining device's huge dirty set and adopted almost nothing).
    // Every other pass is a normal field-level protected merge.
    val fullAdopt = pullPrimary || !syncedEver
    val shouldImport = remoteHasContent && (pullPrimary || !syncedEver || gatePassed)
    var imported = false
    // No `&& remoteJson != null` here: remoteHasContent (folded into
    // shouldImport above) already requires it, and K2 proves it -- the extra
    // check was dead code.
    if (shouldImport) {
        imported = if (fullAdopt) {
            adoptSettingsJson(remoteJson)
        } else {
            // Protect anything WE'VE changed locally but haven't uploaded yet —
            // read the dirty set before this pass touches anything, so a merge
            // import can't accidentally protect keys it's about to import itself.
            mergeSettingsJson(remoteJson, protect = dirtyKeys())
        }
        if (imported) {
            AppLog.log(if (fullAdopt) "Drive sync: adopted settings from file" else "Drive sync: imported newer settings")
            // Record the content we just took, so this pass's own state matches
            // the file and the next pass's gate is a no-op (no self-reimport).
            if (remoteHash != null) setSyncLastHash(remoteHash)
            setSyncSyncedEver(true)
        }
    }
    // The pull-from-primary lever is one-shot: consume it once there was a REAL
    // chance to adopt, whether or not anything actually needed adopting -- so a
    // later normal merge doesn't keep re-adopting. That is NOT the same as
    // consuming it unconditionally: if this pass's download failed
    // (downloadError != null, remoteJson stayed null), there was never a real
    // chance -- shouldImport was false only because we couldn't read the file,
    // not because there was nothing to pull. Clearing the flag anyway silently
    // drops the user's explicit "pull from primary" request: the very next
    // sync (the periodic worker, hours later, or a plain "Sync now") would run
    // an ordinary protected merge instead, with nothing telling the user their
    // request never actually happened. `!syncedEver` right above doesn't have
    // this problem -- it's derived from persisted state, not a flag this
    // function clears itself, so a failed pass naturally retries it next time;
    // this one-shot flag needs the same self-healing property.
    if (pullPrimary && downloadError == null) setSyncPullPrimary(false)

    val now = System.currentTimeMillis()
    var uploadError: String? = null
    val uploaded: Boolean
    // Devices queued locally for removal (see removeSyncedDevice's own doc) are
    // filtered out of whatever the file itself says HERE, once, so every use of
    // the remote registry below -- the immediate outcome, the merge, and the
    // upload body -- agrees on the same filtered list rather than three separate
    // reads that could drift if this pref changed between them.
    val pendingRemovedIds = syncPendingRemovedDeviceIds()
    val remoteDevices = (remoteMeta?.devices ?: emptyList()).filterNot { it.id in pendingRemovedIds }
    // Best-available registry/primary for the outcome even if the upload half
    // doesn't run (failed download) — the UI still updates from what we read.
    var outcomeDevices: List<SyncMerge.SyncDevice> = remoteDevices
    // Precedence, and the order matters: an un-uploaded designation made HERE wins, then
    // whatever the file says, and only then this device's cached copy.
    //
    // It used to read `syncPrimaryDeviceId() ?: remoteMeta?.primaryDeviceId` -- the local
    // CACHE ahead of the file. But that cache is also where every pass stores the value it
    // just wrote (see setSyncPrimaryCache below), so "the primary I once saw" was
    // indistinguishable from "the primary I am asking for", and each device re-asserted
    // its own copy forever. The primary could therefore never be MOVED: designate the
    // tablet on the tablet, it uploads primary=tablet, then the phone's next pass reads
    // that, ignores it in favour of its own cached primary=phone, and writes it back. Both
    // devices sit there each believing it is primary, and the user's choice silently
    // reverts. A pending intent is one-shot, so the file converges after one pass.
    val pendingPrimary = syncPrimaryPending()
    val primaryToWrite: String? = pendingPrimary ?: remoteMeta?.primaryDeviceId ?: syncPrimaryDeviceId()
    // Never write on a failed read: a download error means we couldn't see
    // the remote file's real contents this pass, so uploading now would
    // truncate-overwrite whatever is actually there with our local state --
    // a last-write-wins clobber of another device's possibly-newer settings.
    // Gate the ENTIRE upload block (dirty snapshot, body build, write/verify,
    // and the lastSyncMs/dirty-clear bookkeeping) on a clean download.
    // First sync is unaffected: a missing/empty Drive file reads with
    // remoteContent==null/empty WITHOUT setting downloadError, so
    // downloadError==null still permits the initial upload.
    if (downloadError != null) {
        uploaded = false
    } else {
        // Snapshot the dirty set that this upload body actually carries, taken
        // right before the body is built. Only these keys may be cleared on
        // success -- a key edited AFTER this point (setters don't take
        // mainToMainSyncMutex, so a local edit can land mid-upload) isn't reflected
        // in `body`, so it must keep its dirty flag or a later remote import
        // could silently overwrite the un-uploaded value.
        val uploadedDirtyKeys = dirtyKeys()
        // Snapshot the portable content ONCE (post-import): its SHA-256 is both
        // the change gate written into the file AND, being computed over the
        // exact prefs/photos we upload, guarantees the file's `_hash` matches
        // its own content. Encoding the photos once here (not twice) keeps this
        // cheap despite the base64 work.
        val prefsSnapshot = context.settingsDataStore.data.first()
        val prefsMap: Map<String, Any> = prefsSnapshot.asMap().entries.associate { it.key.name to it.value }
        val photos = SyncPhotos.encode(prefsSnapshot).mapValues { it.value.content }
        // ONE set, used by both the hash and the body -- see portableContentHash's param
        // doc for why passing it to only one of them corrupts the change gate.
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
            // Carry the remote file's OWN tombstones forward. Without this a `_removed`
            // entry lived for exactly one upload: it is derived from the dirty set, and
            // clearDirtyKeys empties that on success, so the next push -- any unrelated edit,
            // ~2s later -- rebuilt the body without it. A peer that had not synced inside
            // that single window still held the deleted key, re-uploaded it, and the deletion
            // was undone on the device that made it.
            //
            // Read straight from remoteJson rather than through parseBackup, because this
            // runs on the UPLOAD half, which happens even when the import half was skipped
            // (nothing newer, or an unreadable prefs block) -- exactly the passes that still
            // have to keep republishing the tombstone.
            priorRemoved = carriedTombstones,
        )
        val body = "$now\n$driveBody"
        uploaded = runCatching {
            withDriveRetry {
                kotlinx.coroutines.withTimeout(DRIVE_IO_TIMEOUT_MS) {
                    context.contentResolver.openOutputStream(parsed, "wt")?.use { it.write(body.toByteArray()) }
                        ?: error("Couldn't open the Drive file for writing")
                    // Verify the write actually landed instead of trusting that
                    // close() completing without throwing means the bytes are really
                    // there -- some document providers can silently truncate or drop
                    // a buffered write under low storage or an interrupted upload,
                    // which previously would have reported success, advanced
                    // lastSyncMs, and cleared the dirty set for data that was never
                    // actually saved.
                    val verify = context.contentResolver.openInputStream(parsed)?.use { it.bufferedReader().readText() }
                    if (verify != body) error("Upload didn't verify — the Drive file doesn't match what was written")
                }
            }
            AppLog.log("Drive sync: uploaded settings")
            true
        }.onFailure {
            uploadError = if (it is kotlinx.coroutines.TimeoutCancellationException) "Timed out writing the Drive file" else it.message ?: "Couldn't write the Drive file"
            AppLog.log("⚠ Drive sync: upload failed: ${it.message}")
        }.getOrElse { false }
        // Only claim "last synced" when the upload actually landed --
        // bumping it on a failure previously made the UI show "Last synced
        // just now" right next to "Sync failed", with no way to tell sync
        // had never succeeded.
        if (uploaded) {
            // Keep the wall-clock lastSyncMs advancing IN PARALLEL with the hash
            // gate: it's the fallback gate for a file an un-updated client
            // overwrote (dropping `_hash`), so it must stay current or the
            // fallback breaks exactly when it's needed. Re-read the file's
            // last-modified so the fallback compares in the provider's clock
            // domain (no self-reimport, skew-safe), same as before.
            val uploadedModifiedMs = providerLastModifiedMs(parsed)
            setLastSyncMs(uploadedModifiedMs ?: now)
            // Clear ONLY the keys this upload body actually carried, not the
            // whole set -- an edit made after the body snapshot (setters don't
            // hold mainToMainSyncMutex) is still pending and must stay dirty so a
            // later remote import can't overwrite it.
            clearDirtyKeys(uploadedDirtyKeys)
            // The content-hash self-write guard: next pass reads this exact hash
            // back and the gate is a no-op (mirrors the lastSyncMs self-guard).
            setSyncLastHash(localHash)
            setSyncSyncedEver(true)
            // Cache the registry + primary for offline Settings display.
            setSyncedDevicesCache(outcomeDevices)
            setSyncPrimaryCache(primaryToWrite)
            // Consume the one-shot designation -- but ONLY now, inside the successful-
            // upload branch. Clearing it any earlier (on read, or on a failed upload)
            // would drop the user's choice on the floor without it ever reaching the
            // file; from the next pass on, the file's own value governs.
            if (pendingPrimary != null) setSyncPrimaryPending(null)
            // Same reasoning, same place: a kicked device is only truly gone once
            // THIS upload -- the one that actually wrote a registry without it --
            // has verifiably landed. Set difference, not a blanket clear, in case a
            // fresh removal was requested from Settings while this pass was in
            // flight (removeSyncedDevice writes directly, without this function's
            // own mutex).
            if (pendingRemovedIds.isNotEmpty()) {
                setSyncPendingRemovedDeviceIds(syncPendingRemovedDeviceIds() - pendingRemovedIds)
            }
        }
    }
    val error = uploadError ?: downloadError?.takeIf { remoteContent == null }
    // Persisted (not just returned) so a failure from the background
    // periodic worker -- which has no live ViewModel/UiState to update --
    // still shows up in Settings next time the app is opened, instead of
    // silently only ever reaching AppLog.
    setLastSyncError(error)
    return SettingsStore.MainToMainSyncOutcome(
        // Match what was actually persisted above: report the OLD synced
        // time on total failure, not "now", so a caller that copies this
        // straight into UI state (AppViewModel does) can't show "synced
        // just now" next to a sync-failed error.
        ran = true, imported = imported, uploaded = uploaded, syncedAtMs = if (uploaded) now else lastSyncMs(),
        error = error,
        devices = outcomeDevices,
        primaryDeviceId = primaryToWrite,
        selfDeviceId = syncDeviceId(),
    )
}

/**
 * A non-destructive end-to-end self-test of the Drive round-trip, for the
 * Settings "Test sync" button. Exercises the EXACT provider path
 * [performMainToMainSync] relies on — persisted permission, read, truncate-write,
 * write-verify, read-back — against the user's real configured file, but
 * writes the file's own current bytes back VERBATIM so nothing the user has
 * is changed. (A brand-new/empty file is written with a harmless one-line
 * marker that the very next real sync overwrites.)
 *
 * This is the honest answer to "does Drive sync actually work on THIS device
 * with THIS provider," which can't be proven by reading code alone: it
 * catches a lost/He-revoked permission grant, a provider that rejects the
 * "wt" truncate mode, or one that silently drops a write — the real-world
 * failure modes. It never touches the settings DataStore, never advances
 * lastSyncMs, and never clears the dirty set, so it's side-effect-free
 * beyond re-writing identical bytes.
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
        return SettingsStore.SyncTestResult(false, "Lost access to the Drive file — set up sync again.")
    }
    // Serialize with real syncs so the read-then-write-back can't interleave
    // with a concurrent performMainToMainSync writing different content.
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
        // Write the SAME bytes back so user content is unchanged; only a
        // genuinely empty file gets a throwaway marker (overwritten by the
        // next real sync's upload).
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
            SettingsStore.SyncTestResult(true, "Drive sync is working — read, wrote and verified the file successfully.")
        } else {
            SettingsStore.SyncTestResult(false, "The write didn't verify — the provider may be dropping or truncating writes.")
        }
    }
}

/** Runs [block] once, and if it throws, once more after a short delay --
 *  a single retry absorbs the kind of momentary blip (Drive app still
 *  waking up, a dropped packet) that would otherwise fail an entire sync
 *  pass outright. Real cancellation (the coroutine's own job being
 *  cancelled, NOT our own [DRIVE_IO_TIMEOUT_MS] timeout) is rethrown
 *  immediately instead of being swallowed into a pointless retry. */
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
