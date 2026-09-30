package com.bloo.bluelink.data

import android.graphics.Bitmap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

// --- Settings backup export/import/merge (extracted from SettingsStore) --

/**
 * Export every app preference (theme, colours and custom palettes, weather,
 * notifications, tiles, per-car config…) as one portable JSON backup. Values
 * keep their type (string or boolean) so a re-import restores them exactly.
 * Note: account credentials live in a separate store and are never included.
 *
 * Per-car photos ([encodeSyncPhotos]) are embedded as a separate top-level
 * "photos" object rather than folded into "prefs" like everything else --
 * an `img_$vin` pref pointing at a local file path used to sync as just
 * that path string, which meant nothing on a second device (no such file
 * there), so a synced photo silently never actually appeared anywhere but
 * the device it was set on.
 */
suspend fun SettingsStore.exportSettingsJson(): String {
    val prefs = context.settingsDataStore.data.first()
    // Everything that decides WHICH keys/tombstones travel and how each value
    // is encoded lives in the pure, unit-tested [SyncMerge.buildExport]: the
    // DEVICE_LOCAL_KEYS skip, the local-file img_ path skip, the boolean/
    // string/coerced-toString typing, and the `_removed` tombstone set
    // (dirty keys no longer present, minus device-local). This method only
    // does the two Android-bound things buildExport can't: snapshot the typed
    // Preferences into a plain map, and base64-encode local car photos (which
    // needs android.graphics.Bitmap — see [encodeSyncPhotos]).
    val prefsMap: Map<String, Any> = prefs.asMap().entries.associate { it.key.name to it.value }
    val photos = SyncPhotos.encode(prefs).mapValues { it.value.content }
    // Read the dirty set from the snapshot we already hold (was a second
    // .data.first() via dirtyKeys()) — same value, one fewer collect.
    return SyncMerge.buildExport(prefsMap, prefs.dirtyKeySet(), photos)
}

/**
 * Restore settings from a backup produced by [exportSettingsJson], overwriting
 * any matching keys. Returns an error message on failure, or null on success.
 * Uses [editTracked] — a manual restore is a deliberate local change, so if
 * this device also has Drive auto-sync configured, the restored values are
 * the ones the next sync should push out, not silently discard. Embedded
 * photos ([applySyncPhotos]) are written to local storage first (plain
 * suspend file IO, not a DataStore edit), then their resulting `img_$vin`
 * paths are folded into the SAME editTracked mutation as the rest of the
 * prefs, so they're marked dirty for re-upload exactly like everything else.
 */
suspend fun SettingsStore.importSettingsJson(json: String): String? {
    val root = runCatching { backupJson.parseToJsonElement(json).jsonObject }
        .getOrElse { return "Invalid settings file" }
    // `as? JsonPrimitive` / `as? JsonObject`, never `?.jsonPrimitive` / `?.jsonObject`.
    // The kotlinx accessors THROW IllegalArgumentException when the element is not of
    // that kind, and these twelve guards (three functions × four keys) exist precisely
    // to vet a HAND-EDITABLE, version-skewed file — the one place where `_format`
    // plausibly arrives as an object, or `prefs` as an array. Throwing out of a function
    // documented to *return an error message* (and out of two documented to return
    // false) turned "this is not a Bloo backup" into a crash. [SyncMerge.parseBackup]
    // already vets the identical keys with safe casts and promises "never throws on a
    // hand-edited or version-skewed file"; these disagreed with it.
    if ((root["_format"] as? JsonPrimitive)?.contentOrNull != "bloo-settings") {
        return "Not a Bloo settings backup"
    }
    val version = (root["_version"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1
    if (version > BACKUP_VERSION) {
        return "This backup was made with a newer version of Bloo — update the app first"
    }
    if ((root["prefs"] as? JsonObject) == null) return "Settings file has no data"
    // Which keys to put (by type) and which to tombstone is decided by the
    // pure, unit-tested [SyncMerge.parseBackup]: real JSON strings and bare
    // numbers become string prefs, bare booleans become boolean prefs, and
    // `_removed` becomes the remove set — all with DEVICE_LOCAL_KEYS excluded.
    // The four guards above already validated format/version/prefs, so this is
    // non-null; the ?: keeps the same "no data" message defensively.
    val plan = SyncMerge.parseBackup(json) ?: return "Settings file has no data"
    val photoPaths = SyncPhotos.apply(context, root["photos"] as? JsonObject)
    editTracked { mut ->
        plan.stringPuts.forEach { (name, value) -> mut[stringPreferencesKey(name)] = value }
        plan.boolPuts.forEach { (name, value) -> mut[booleanPreferencesKey(name)] = value }
        photoPaths.forEach { (vin, path) -> mut[stringPreferencesKey("img_$vin")] = path }
        // Propagate deletions: a key tombstoned on the source device is removed
        // here too (both key types, since this file mixes string/boolean prefs
        // under the same name) so a deletion converges instead of the key
        // resurrecting from this device's stale copy. DEVICE_LOCAL_KEYS are
        // already excluded by parseBackup.
        plan.removes.forEach { name ->
            mut.remove(stringPreferencesKey(name))
            mut.remove(booleanPreferencesKey(name))
        }
    }
    return null
}

/**
 * Delete car photos on disk that no preference points at any more.
 *
 * The crop screen writes `cars/car_<vin>_<millis>.<ext>` -- a FRESH timestamped name
 * every time -- and only overwrites the `img_$vin` pref. So every re-crop left the
 * previous file behind forever. Ten passes at the crop slider on one car is ten
 * full-resolution images, none of them reachable. (applySyncPhotos writes a fixed
 * `car_<vin>_synced.jpg` instead, which is why it does not leak; its path is stored in
 * the same pref, so it is correctly seen as referenced here.)
 *
 * Safe by construction, and deliberately NOT the per-VIN pref garbage collection the
 * same leak invites. `img_$vin` is the ONLY preference that holds a local photo path
 * (a `photo_$vin` Wear DataMap asset key, since removed with the watch, was not a pref),
 * so a file absent from that set cannot be displayed by anything -- there is no code
 * path that could reach it. Crucially this makes the decision independent of the
 * VEHICLE LIST: purging prefs for "cars that disappeared" would risk destroying a
 * user's plate, service history and presets whenever one brand's fetch failed and its
 * cars merely looked absent. This asks a question that cannot be wrong instead.
 *
 * [MIN_ORPHAN_AGE_MS] guards the one race: a file written by the crop screen
 * microseconds before its pref write lands. Nothing else in the app writes here.
 */
suspend fun SettingsStore.pruneOrphanPhotos(): Int = withContext(Dispatchers.IO) {
    val dir = java.io.File(context.filesDir, "cars")
    if (!dir.isDirectory) return@withContext 0
    val referenced = context.settingsDataStore.data.first().asMap()
        .filterKeys { it.name.startsWith("img_") }
        .values.filterIsInstance<String>()
        .filter { it.startsWith("/") }
        .toSet()
    val cutoff = System.currentTimeMillis() - MIN_ORPHAN_AGE_MS
    var freed = 0
    dir.listFiles()?.forEach { f ->
        if (!f.isFile || f.absolutePath in referenced || f.lastModified() > cutoff) return@forEach
        if (runCatching { f.delete() }.getOrDefault(false)) freed++
    }
    if (freed > 0) AppLog.log("Cleaned up $freed orphaned car photo(s)")
    freed
}

/**
 * Merge a Drive-downloaded settings file into local prefs for the AUTOMATIC
 * bidirectional sync: every key in [protect] (changed locally since our own
 * last successful sync, and not yet uploaded) keeps its current local value;
 * every other key is taken from remote. Unlike [importSettingsJson] this does
 * NOT go through [editTracked] — accepting a remote value must not re-mark
 * that key as a pending local change, or it would never finish converging.
 * Returns whether anything was actually applied.
 */
internal suspend fun SettingsStore.mergeSettingsJson(json: String, protect: Set<String>): Boolean {
    // Validate format/version up front for the merge-specific behaviour a bad
    // remote file needs: return false (don't apply anything, but let the upload
    // half of the pass proceed), and log the newer-format case. parseBackup
    // below applies the same guards, but doing them here keeps the AppLog line
    // and the distinct "skip import, keep syncing" semantics intact.
    val root = runCatching { backupJson.parseToJsonElement(json).jsonObject }.getOrNull() ?: return false
    if ((root["_format"] as? JsonPrimitive)?.contentOrNull != "bloo-settings") return false
    val version = (root["_version"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1
    if (version > BACKUP_VERSION) {
        // A newer device wrote this — rather than misapply a format we don't
        // recognize, skip the import half this round (the upload half still
        // runs normally) and wait for this device to be updated.
        AppLog.log("⚠ Drive sync: remote backup is a newer format ($version > $BACKUP_VERSION), skipping import")
        return false
    }
    if ((root["prefs"] as? JsonObject) == null) return false
    // Photos are guarded by the pre-pass `protect` snapshot (an img_$vin changed locally
    // since the last sync keeps its local file), PLUS every photo this device chose itself.
    //
    // `protect` alone was not enough, and the gap destroyed originals. Once a device has
    // successfully uploaded its own photo, clearDirtyKeys drops img_$vin from the dirty set
    // -- so on the very next pass the key is no longer protected. A peer that imported the
    // photo re-uploads it, this device's hash gate opens, and applySyncPhotos then writes the
    // peer's TRANSPORT copy over the originating device's own pref.
    //
    // The transport copy is lossy by design: encodeSyncPhotos re-encodes to a 640px JPEG at
    // quality 78. CropScreen goes out of its way to save an alpha source as a 1080px PNG
    // ("Preserve transparency ... so the background stays see-through"), and JPEG cannot
    // carry alpha at all. So the device that chose a transparent PNG ended up displaying a
    // flattened, black-backgrounded, twice-compressed 640px JPEG of it.
    //
    // And it was unrecoverable rather than merely wrong, because of pruneOrphanPhotos, which
    // I added earlier on this branch: once img_$vin points at car_$vin_synced.jpg, the
    // original crop is referenced by nothing and the sweep deletes it. Before that sweep
    // existed the original at least survived on disk. A leak-fix turned a degradation into
    // data loss -- worth remembering as a class of mistake, not just this instance.
    //
    // Scoped so a genuine peer update still lands: only photos whose path is NOT the synced
    // filename are protected. A device whose photo already came from sync keeps accepting
    // newer synced photos; a device that chose its own original never has it overwritten by
    // a re-encode of itself. adoptSettingsJson already protected ALL local img keys and says
    // why -- this is the same reasoning, one import path later.
    val ownPhotoKeys = context.settingsDataStore.data.first().asMap()
        .filterKeys { it.name.startsWith("img_") }
        .filterValues { v ->
            (v as? String)?.let { it.startsWith("/") && !it.endsWith("_synced.jpg") } == true
        }
        .keys.map { it.name }.toSet()
    val photoPaths = SyncPhotos.apply(context, root["photos"] as? JsonObject, protect + ownPhotoKeys)
    context.settingsDataStore.edit { mut ->
        // Re-read the dirty set from THIS transaction's live prefs, not just the
        // protect snapshot taken before the pass started: a local edit that
        // landed between that snapshot and this edit block (setters don't hold
        // mainToMainSyncMutex) would otherwise be clobbered by the incoming remote
        // value. Treat those live-dirty keys exactly like protect -- skip
        // writing and skip removing them. [SyncMerge.mergePlan] applies the
        // guarded drop (and the DEVICE_LOCAL_KEYS exclusion, and value typing)
        // purely on the prefs/tombstones; photos are handled above.
        val liveDirty = mut.dirtyKeySet()
        val guarded = protect + liveDirty
        // parseBackup already succeeded on the guards above, so mergePlan is
        // non-null here; ?: return@edit is a defensive no-op.
        val plan = SyncMerge.mergePlan(json, guarded) ?: return@edit
        plan.stringPuts.forEach { (name, value) -> mut[stringPreferencesKey(name)] = value }
        plan.boolPuts.forEach { (name, value) -> mut[booleanPreferencesKey(name)] = value }
        // photoPaths was decided from the PRE-PASS protect+ownPhotoKeys snapshot,
        // taken before this edit block opened -- the same gap `liveDirty` above
        // exists to close for prefs. setImageUrl (the crop screen's write path)
        // goes through editTracked and holds no mutex, so it can land between
        // that snapshot and here; without this filter its fresh img_$vin would
        // be silently overwritten by the older remote photo, AND -- since that
        // overwrite bypasses editTracked -- the key wouldn't even be marked
        // dirty afterward, so the next sync push wouldn't re-upload the correct
        // local photo either. Same guard the prefs above already get.
        photoPaths.forEach { (vin, path) ->
            if ("img_$vin" !in guarded) mut[stringPreferencesKey("img_$vin")] = path
        }
        // Propagate deletions from the remote file, but never remove a key we're
        // protecting (locally changed since our last sync, or live-dirty within
        // this transaction) or a device-local key -- mergePlan already dropped
        // both from removes; both key types removed since names are shared.
        plan.removes.forEach { name ->
            mut.remove(stringPreferencesKey(name))
            mut.remove(booleanPreferencesKey(name))
        }
    }
    return true
}

/**
 * Full-adopt the file as the source of truth: apply EVERY portable key
 * unguarded (ignoring even the local dirty set) and clear the dirty set, so a
 * device joining an existing sync — or an explicit "pull from primary" — takes
 * the file's settings wholesale instead of protecting its own pre-join values.
 * This is the fix for the reported bug: the old code only ever ran the
 * protected [mergeSettingsJson], and a previously-used joining device's dirty
 * set covered ~every key, so it adopted almost nothing.
 *
 * Distinct from [mergeSettingsJson] on two points: (1) it does NOT re-add
 * live-dirty to a guarded set (there is no guarding — the file wins); (2) photos
 * are still PROTECTED (`protect = all local img_ keys`) so a join doesn't
 * silently replace the user's own car photos with the primary's, while every
 * other pref fully adopts. Clears the dirty set at the end so the adopted values
 * aren't immediately re-uploaded as "local changes".
 */
internal suspend fun SettingsStore.adoptSettingsJson(json: String): Boolean {
    val root = runCatching { backupJson.parseToJsonElement(json).jsonObject }.getOrNull() ?: return false
    if ((root["_format"] as? JsonPrimitive)?.contentOrNull != "bloo-settings") return false
    val version = (root["_version"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1
    if (version > BACKUP_VERSION) {
        AppLog.log("⚠ Drive sync: remote backup is a newer format ($version > $BACKUP_VERSION), skipping adopt")
        return false
    }
    if ((root["prefs"] as? JsonObject) == null) return false
    // Protect the user's own local car photos across a join-adopt (only these).
    val localImgKeys = context.settingsDataStore.data.first().asMap().keys
        .map { it.name }.filter { it.startsWith("img_") }.toSet()
    val photoPaths = SyncPhotos.apply(context, root["photos"] as? JsonObject, protect = localImgKeys)
    // Unguarded plan: the file wins for every portable pref/tombstone.
    val plan = SyncMerge.parseBackup(json) ?: return false
    context.settingsDataStore.edit { mut ->
        plan.stringPuts.forEach { (name, value) -> mut[stringPreferencesKey(name)] = value }
        plan.boolPuts.forEach { (name, value) -> mut[booleanPreferencesKey(name)] = value }
        photoPaths.forEach { (vin, path) -> mut[stringPreferencesKey("img_$vin")] = path }
        plan.removes.forEach { name ->
            mut.remove(stringPreferencesKey(name))
            mut.remove(booleanPreferencesKey(name))
        }
        // Clear the dirty set in the SAME transaction: the adopted values are
        // the file's, not pending local changes, so they must not be re-uploaded
        // as edits (and must not protect themselves on the next merge).
        mut.remove(stringPreferencesKey("sync_dirty_keys"))
    }
    return true
}
