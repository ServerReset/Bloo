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
 * Exports every app preference as one portable JSON backup, keeping value types (string or
 * boolean). Credentials live elsewhere and are never included. Car photos ([encodeSyncPhotos])
 * go in a separate "photos" object, since a local `img_$vin` path means nothing on another device.
 */
suspend fun SettingsStore.exportSettingsJson(): String {
    val prefs = context.settingsDataStore.data.first()
    // Key/tombstone selection and typing live in the pure [SyncMerge.buildExport]; this only snapshots
    // Preferences into a map and base64-encodes photos (needs Bitmap).
    val prefsMap: Map<String, Any> = prefs.asMap().entries.associate { it.key.name to it.value }
    val photos = SyncPhotos.encode(prefs).mapValues { it.value.content }
    // Dirty set read from the snapshot already held.
    return SyncMerge.buildExport(prefsMap, prefs.dirtyKeySet(), photos)
}

/**
 * Restores settings from a backup produced by [exportSettingsJson], overwriting matching keys.
 * Returns an error message, or null on success. Uses [editTracked] so restored values are pushed
 * by the next Drive sync; photo paths join the same mutation.
 */
suspend fun SettingsStore.importSettingsJson(json: String): String? {
    val root = runCatching { backupJson.parseToJsonElement(json).jsonObject }
        .getOrElse { return "Invalid settings file" }
    // `as?` casts, never `?.jsonPrimitive`: those accessors throw on a wrong kind, and these guards
    // vet a hand-editable file whose `_format`/`prefs` may be the wrong type.
    if ((root["_format"] as? JsonPrimitive)?.contentOrNull != "bloo-settings") {
        return "Not a Bloo settings backup"
    }
    val version = (root["_version"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1
    if (version > BACKUP_VERSION) {
        return "This backup was made with a newer version of Bloo. Update the app first"
    }
    if ((root["prefs"] as? JsonObject) == null) return "Settings file has no data"
    // Key typing and tombstones come from [SyncMerge.parseBackup] (device-local keys excluded); non-null after the guards above.
    val plan = SyncMerge.parseBackup(json) ?: return "Settings file has no data"
    val photoPaths = SyncPhotos.apply(context, root["photos"] as? JsonObject)
    editTracked { mut ->
        plan.stringPuts.forEach { (name, value) -> mut[stringPreferencesKey(name)] = value }
        plan.boolPuts.forEach { (name, value) -> mut[booleanPreferencesKey(name)] = value }
        photoPaths.forEach { (vin, path) -> mut[stringPreferencesKey("img_$vin")] = path }
        // Propagate deletions: a key tombstoned on the source device is removed here too (both key
        // types, since this file mixes string/boolean prefs under the same name) so a deletion
        // converges instead of the key resurrecting from this device's stale copy.
        // DEVICE_LOCAL_KEYS are already excluded by parseBackup.
        plan.removes.forEach { name ->
            mut.remove(stringPreferencesKey(name))
            mut.remove(booleanPreferencesKey(name))
        }
    }
    return null
}

/**
 * Deletes car photos on disk that no preference points at any more (each re-crop writes a fresh
 * `cars/car_<vin>_<millis>.<ext>` and only the `img_$vin` pref is overwritten).
 *
 * `img_$vin` is the only pref holding a local photo path, so an unreferenced file is unreachable;
 * deciding by that set (not by the vehicle list) cannot destroy data when a brand's fetch fails.
 * [MIN_ORPHAN_AGE_MS] guards a file written just before its pref lands.
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
 * Merges a Drive-downloaded settings file into local prefs for the automatic bidirectional sync:
 * keys in [protect] (changed locally since the last sync, not yet uploaded) keep their local value,
 * all others come from remote. Unlike [importSettingsJson] it skips [editTracked] so accepted remote
 * values are not re-marked as local changes. Returns whether anything was applied.
 */
internal suspend fun SettingsStore.mergeSettingsJson(json: String, protect: Set<String>): Boolean {
    // Validate up front: a bad remote file returns false (the upload half still proceeds) and logs a newer format.
    val root = runCatching { backupJson.parseToJsonElement(json).jsonObject }.getOrNull() ?: return false
    if ((root["_format"] as? JsonPrimitive)?.contentOrNull != "bloo-settings") return false
    val version = (root["_version"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1
    if (version > BACKUP_VERSION) {
        // Newer format: skip the import half this round and wait for an update.
        AppLog.log("⚠ Drive sync: remote backup is a newer format ($version > $BACKUP_VERSION), skipping import")
        return false
    }
    if ((root["prefs"] as? JsonObject) == null) return false
    // Photos are guarded by `protect` plus every photo this device chose itself: once uploaded, the key
    // leaves the dirty set, and a peer's lossy 640px JPEG transport copy would overwrite the original
    // (the orphan sweep would then delete it). Only non-synced-filename photos are protected, so genuine
    // peer updates still land. adoptSettingsJson protects all local img keys for the same reason.
    val ownPhotoKeys = context.settingsDataStore.data.first().asMap()
        .filterKeys { it.name.startsWith("img_") }
        .filterValues { v ->
            (v as? String)?.let { it.startsWith("/") && !it.endsWith("_synced.jpg") } == true
        }
        .keys.map { it.name }.toSet()
    val photoPaths = SyncPhotos.apply(context, root["photos"] as? JsonObject, protect + ownPhotoKeys)
    context.settingsDataStore.edit { mut ->
        // Re-read the dirty set from this transaction's live prefs: a local edit between the snapshot and
        // this block (setters hold no sync mutex) must not be clobbered. Live-dirty keys are treated like `protect`.
        val liveDirty = mut.dirtyKeySet()
        val guarded = protect + liveDirty
        // Non-null after the guards above; the return is defensive.
        val plan = SyncMerge.mergePlan(json, guarded) ?: return@edit
        plan.stringPuts.forEach { (name, value) -> mut[stringPreferencesKey(name)] = value }
        plan.boolPuts.forEach { (name, value) -> mut[booleanPreferencesKey(name)] = value }
        // Same race as `liveDirty`: setImageUrl (via editTracked, no mutex) can land after the snapshot, and
        // overwriting it would also leave it unmarked for re-upload.
        photoPaths.forEach { (vin, path) ->
            if ("img_$vin" !in guarded) mut[stringPreferencesKey("img_$vin")] = path
        }
        // Propagate deletions from the remote file, but never remove a key we're protecting
        // (locally changed since our last sync, or live-dirty within this transaction) or a
        // device-local key -- mergePlan already dropped both from removes; both key types removed
        // since names are shared.
        plan.removes.forEach { name ->
            mut.remove(stringPreferencesKey(name))
            mut.remove(booleanPreferencesKey(name))
        }
    }
    return true
}

/**
 * Full-adopt: applies EVERY portable key unguarded (the file wins, even over the local dirty set)
 * and clears the dirty set, for a device joining a sync or an explicit "pull from primary". Local car
 * photos stay protected so a join does not replace the user's own. Clearing the dirty set stops
 * adopted values being re-uploaded as local changes.
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
    // Protect the user's own local car photos across a join-adopt.
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
        // Clear the dirty set in the SAME transaction: the adopted values are the file's, not
        // pending local changes, so they must not be re-uploaded as edits (and must not protect
        // themselves on the next merge).
        mut.remove(stringPreferencesKey("sync_dirty_keys"))
    }
    return true
}
