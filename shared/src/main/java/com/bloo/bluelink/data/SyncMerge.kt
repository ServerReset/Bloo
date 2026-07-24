package com.bloo.bluelink.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Pure, Context-free core of the Drive-sync backup format: build the export JSON,
 * and decode a backup JSON into the exact set of DataStore mutations a merge or
 * import should apply. This is the logic that used to live inline in
 * [com.bloo.bluelink.data] `SettingsStore.exportSettingsJson` /
 * `importSettingsJson` / `mergeSettingsJson`, lifted out so it can be unit-tested
 * on a plain JVM with no Android Context/DataStore/Bitmap.
 *
 * Everything here operates only on JSON strings and plain maps/sets. The Android
 * side (reading DataStore into a map, base64-encoding photos from Bitmaps,
 * applying a [MergePlan] to DataStore, dirty-key tracking) stays in SettingsStore.
 */
object SyncMerge {

    /** The settings-backup format version. The format is a flat key-value bag, so
     *  an older client reading a newer backup is normally fine (unknown keys are
     *  ignored); bump this only if a future change stops being purely additive, so
     *  old clients can detect and refuse a newer format instead of misreading it. */
    const val BACKUP_VERSION = 1

    /** Preference keys that describe THIS device's own Drive-sync wiring (the
     *  content:// URI it was granted, its last-sync bookkeeping, its Wi-Fi-only
     *  preference, and its local dirty set) — never portable, so never exported,
     *  imported, or merged. */
    val DEVICE_LOCAL_KEYS = setOf("sync_uri", "sync_last_ms", "sync_last_error", "sync_wifi", "sync_dirty_keys")

    /**
     * The decoded set of mutations a merge/import should apply, with no DataStore
     * involved. [stringPuts] are written under a string key, [boolPuts] under a
     * boolean key, and every name in [removes] is deleted (under both key types on
     * the Android side, since this app mixes string/boolean prefs under one name).
     */
    data class MergePlan(
        val stringPuts: Map<String, String>,
        val boolPuts: Map<String, Boolean>,
        val removes: Set<String>,
    )

    // Same Json config SettingsStore's backupJson used: pretty-printed output so
    // the exported file is human-readable, unknown keys ignored on decode.
    private val backupJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

    /**
     * Pure version of `exportSettingsJson`. [prefs] is a plain snapshot of every
     * preference (String/Boolean values; anything else is coerced to its
     * toString()), [dirtyKeys] is the set of keys changed locally since the last
     * upload, and [photos] is an optional `{vin -> base64 JPEG}` map (the Android
     * side encodes the Bitmaps; this just embeds the strings).
     *
     * Skips [DEVICE_LOCAL_KEYS] and local-file `img_` values (a "/"-prefixed
     * String path is meaningless on another device — only the photos channel
     * carries local photos). `_removed` tombstones are the dirty keys that no
     * longer exist in [prefs] and aren't device-local, so other devices converge
     * on the deletion instead of resurrecting the key.
     */
    fun buildExport(prefs: Map<String, Any>, dirtyKeys: Set<String>, photos: Map<String, String> = emptyMap()): String {
        val entries = buildJsonObject {
            prefs.forEach { (name, value) ->
                if (name in DEVICE_LOCAL_KEYS) return@forEach
                if (name.startsWith("img_") && value is String && value.startsWith("/")) return@forEach
                when (value) {
                    is Boolean -> put(name, JsonPrimitive(value))
                    is String -> put(name, JsonPrimitive(value))
                    else -> put(name, JsonPrimitive(value.toString()))
                }
            }
        }
        val presentNames = prefs.keys.toSet()
        val removed = (dirtyKeys - presentNames - DEVICE_LOCAL_KEYS)
        val root = buildJsonObject {
            put("_format", JsonPrimitive("bloo-settings"))
            put("_version", JsonPrimitive(BACKUP_VERSION))
            put("prefs", entries)
            if (photos.isNotEmpty()) {
                put("photos", buildJsonObject { photos.forEach { (vin, b64) -> put(vin, JsonPrimitive(b64)) } })
            }
            if (removed.isNotEmpty()) put("_removed", buildJsonArray { removed.forEach { add(JsonPrimitive(it)) } })
        }
        return backupJson.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * The shared decode used by both `importSettingsJson` and `mergeSettingsJson`:
     * returns null on invalid JSON, a wrong/absent `_format`, a `_version` newer
     * than [BACKUP_VERSION], or an absent `prefs` object (mirroring every guard in
     * the original). Otherwise decodes `prefs` into [MergePlan.stringPuts] (real
     * JSON strings, plus the numeric-fallback: a bare number is stored as a string
     * pref in this app) versus [MergePlan.boolPuts] (bare JSON booleans), and
     * `_removed` into [MergePlan.removes] — excluding [DEVICE_LOCAL_KEYS] from both
     * puts and removes.
     */
    fun parseBackup(json: String): MergePlan? {
        val root = runCatching { backupJson.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
        if ((root["_format"] as? JsonPrimitive)?.contentOrNull != "bloo-settings") return null
        val version = (root["_version"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 1
        if (version > BACKUP_VERSION) return null
        val prefs = root["prefs"] as? JsonObject ?: return null
        val removed = (root["_removed"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

        val stringPuts = LinkedHashMap<String, String>()
        val boolPuts = LinkedHashMap<String, Boolean>()
        prefs.forEach { (name, element) ->
            if (name in DEVICE_LOCAL_KEYS) return@forEach
            val prim = element as? JsonPrimitive ?: return@forEach
            when {
                // A real JSON string (e.g. "DARK", "true") → keep as a string pref.
                prim.isString -> stringPuts[name] = prim.content
                // A bare JSON boolean → a boolean pref (notifications, alerts, …).
                prim.booleanOrNull != null -> boolPuts[name] = prim.booleanOrNull!!
                // Anything else (a bare number) — every numeric pref is stored as a
                // string, so coerce it back to one.
                else -> stringPuts[name] = prim.content
            }
        }
        val removes = removed.filterTo(LinkedHashSet()) { it !in DEVICE_LOCAL_KEYS }
        return MergePlan(stringPuts, boolPuts, removes)
    }

    /**
     * Like [parseBackup], but additionally drops every key in [guarded] from the
     * puts and the removes — the protect + live-dirty logic in the automatic merge:
     * a key changed locally since our last sync (and not yet uploaded) must keep
     * its current local value and must not be tombstoned by the incoming file.
     */
    fun mergePlan(json: String, guarded: Set<String>): MergePlan? {
        val base = parseBackup(json) ?: return null
        if (guarded.isEmpty()) return base
        return MergePlan(
            stringPuts = base.stringPuts.filterKeys { it !in guarded },
            boolPuts = base.boolPuts.filterKeys { it !in guarded },
            removes = base.removes.filterTo(LinkedHashSet()) { it !in guarded },
        )
    }
}
