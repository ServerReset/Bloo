package com.bloo.bluelink.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Pure, Context-free core of the Drive-sync backup format: builds the export JSON and decodes
 * a backup into the DataStore mutations a merge or import applies. JVM-testable; the Android
 * side (DataStore, photos, dirty keys) stays in SettingsStore.
 *
 * Two export shapes:
 * - [buildExport]: the portable shape (`prefs`/`photos`/`_removed`, no device metadata). Used
 *   for the shareable file and as the content [portableContentHash] covers.
 * - [buildExportForMainToMain]: portable plus Drive-only keys (`_hash`, `_primaryDeviceId`,
 *   `_writerDeviceId`, `devices`). Never shared, so device names/ids do not leak.
 *
 * Change gate: a content hash of the portable content is clock-skew-immune and makes a no-op
 * sync self-detecting (a sequence counter would ping-pong). The extra keys are additive, so
 * [BACKUP_VERSION] stays 1; without `_hash` callers fall back to the timestamp gate.
 */
object SyncMerge {

    /**
     * The settings-backup format version. The format is a flat key-value bag, so an older client
     * reading a newer backup is normally fine (unknown keys are ignored); bump this only if a
     * future change stops being purely additive, so old clients can detect and refuse a newer
     * format instead of misreading it.
     */
    const val BACKUP_VERSION = 1

    /**
     * Preference keys that describe THIS device's own Drive-sync wiring (the content:// URI it was
     * granted, its last-sync bookkeeping, its Wi-Fi-only preference, its local dirty set, and its
     * sync-identity/registry bookkeeping) — never portable, so never exported, imported, or merged.
     */
    val DEVICE_LOCAL_KEYS = setOf(
        "sync_uri", "sync_last_ms", "sync_last_error", "sync_wifi", "sync_dirty_keys",
        // Sync identity + hash-gate + registry bookkeeping (all per-device, never travel):
        "sync_device_id", "sync_device_name", "sync_last_hash", "sync_synced_ever",
        "sync_devices_cache", "sync_pull_primary", "sync_primary_cache", "sync_file_id",
        // A primary designation made here and not yet uploaded: a one-shot write intent that
        // must not roam.
        "sync_primary_pending",
        // Whether THIS device installs updates silently via Shizuku — a device-local capability
        // (Shizuku may not be present elsewhere), so it must not roam.
        "seamless_install_shizuku",
        // Which car is on screen right now: transient view state, must not roam or cost a sync per swipe.
        "last_vehicle_vin",
        // Which version of the settings schema this install last migrated to (see [SyncSchema]).
        "settings_schema",
    )

    /** Per-VIN key prefixes (dynamic suffix) that are device-local runtime state and never travel:
     *  - `alert_*`: "already fired" flags; a peer's flag would suppress this device's notification.
     *  - `door_since_*` / `engine_since_*` / `unlocked_since_*`: wall-clock episode starts that are
     *    meaningless in another device's clock domain.
     *  - `tile_refreshed_*`: per-car refresh throttle stamps.
     *  Excluding them also keeps the content hash stable across alert/refresh ticks. */
    private val DEVICE_LOCAL_PREFIXES = listOf(
        // Includes unlocked_since_* (same clock-domain reasoning as its siblings).
        "alert_", "door_since_", "engine_since_", "unlocked_since_", "tile_refreshed_",
        // live_dismissed_* : "the user swiped THIS device's live charging bar away for the current
        // charging session". Dismissing a notification on a phone says nothing about whether a
        // tablet should show one, and roaming it would suppress the bar on a device the user never
        // touched.
        "live_dismissed_",
    )

    /** Whether [name] is device-local (exact key or per-VIN prefix): never exported, imported, merged or hashed. */
    fun isDeviceLocal(name: String): Boolean =
        name in DEVICE_LOCAL_KEYS || DEVICE_LOCAL_PREFIXES.any { name.startsWith(it) } ||
            // A retired setting never travels either: not exported, not imported, not in the hash.
            SyncSchema.isDeprecated(name)

    /** A device that syncs this Drive file, per the file's `devices` registry. Informational
     *  (the "your devices" list and "primary" designation); never affects the merge. Defaults
     *  keep partial entries decodable; a blank [id] is dropped on merge. */
    @Serializable
    data class SyncDevice(
        val id: String = "",
        val name: String = "",
        val model: String = "",
        val appVersion: String = "",
        val lastSeenMs: Long = 0L,
        /** "watch" for a Wear OS companion, "phone" otherwise; a watch cannot be the source
         *  of truth. Absent (null) entries read as "phone". */
        val kind: String = KIND_PHONE,
    ) {
        val isWatch: Boolean get() = kind == KIND_WATCH
    }

    const val KIND_PHONE = "phone"
    const val KIND_WATCH = "watch"

    /** Registry entries unseen for this long are pruned on merge. */
    const val DEVICE_RETENTION_MS = 90L * 24 * 60 * 60 * 1000

    /**
     * The decoded set of mutations a merge/import should apply, with no DataStore involved.
     * [stringPuts] are written under a string key, [boolPuts] under a boolean key, and every name
     * in [removes] is deleted (under both key types on the Android side, since this app mixes
     * string/boolean prefs under one name).
     */
    data class MergePlan(
        val stringPuts: Map<String, String>,
        val boolPuts: Map<String, Boolean>,
        val removes: Set<String>,
    )

    /**
     * The Drive-sync-only metadata parsed out of a file's top-level keys, kept separate from the
     * [MergePlan] (which is only the portable prefs/tombstones). [hash] is null when the file
     * predates the hash gate (old client, or the header/marker case) — the caller then falls back
     * to the timestamp gate.
     */
    data class SyncMeta(
        val hash: String?,
        val primaryDeviceId: String?,
        val writerDeviceId: String?,
        val devices: List<SyncDevice>,
        /** A stable id for the FILE ITSELF, written into the content so every device reads the
         *  same value (a SAF content:// URI differs per device). Null on files predating it. */
        val fileId: String?,
    )

    // Pretty-printed so the exported file is human-readable; unknown keys ignored on decode.
    private val backupJson = BlooBackupJson

    // --- Portable export (prefs/photos/_removed only — safe to share) ----------

    /**
     * The `prefs` object shared by [buildExport] and [buildExportForMainToMain]: skips
     * [DEVICE_LOCAL_KEYS] and local-file `img_` paths (a "/"-prefixed String path is meaningless on
     * another device — only the photos channel carries local photos), and types each value as a
     * JSON boolean/string (anything else coerced via toString()).
     */
    private fun portablePrefsObject(prefs: Map<String, Any>): JsonObject = buildJsonObject {
        prefs.forEach { (name, value) ->
            if (isDeviceLocal(name)) return@forEach
            if (name.startsWith("img_") && value is String && value.startsWith("/")) return@forEach
            when (value) {
                is Boolean -> put(name, JsonPrimitive(value))
                is String -> put(name, JsonPrimitive(value))
                else -> put(name, JsonPrimitive(value.toString()))
            }
        }
    }

    /** `_removed` tombstones: dirty keys gone from [prefs] and not device-local, so other devices
     *  converge on the deletion. */
    private fun tombstones(prefs: Map<String, Any>, dirtyKeys: Set<String>): Set<String> =
        (dirtyKeys - prefs.keys.toSet()).filterNotTo(LinkedHashSet()) { isDeviceLocal(it) }

    /**
     * Builds the base backup root (`_format`/`_version`/`prefs`/`photos`/`_removed`), then lets
     * [extra] add any additional top-level keys (the Drive-only metadata). [buildExport] passes an
     * empty [extra] so its output is exactly the historical portable shape.
     */
    private inline fun buildRoot(
        prefs: Map<String, Any>,
        dirtyKeys: Set<String>,
        photos: Map<String, String>,
        priorRemoved: Set<String>,
        extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        val entries = portablePrefsObject(prefs)
        // Local tombstones are unioned with those already in the remote file: the dirty set is
        // cleared after each upload, so without the union a deletion would survive one push only
        // and a lagging peer would resurrect the key. A key present in prefs wins over its stale
        // tombstone, and device-local keys never travel. The set has no TTL (no per-tombstone
        // timestamp in the format); a tiny list of dead key names beats resurrecting deleted data.
        val removed = (tombstones(prefs, dirtyKeys) + priorRemoved)
            .filterNotTo(LinkedHashSet()) { it in prefs.keys || isDeviceLocal(it) }
        return buildJsonObject {
            put("_format", JsonPrimitive("bloo-settings"))
            put("_version", JsonPrimitive(BACKUP_VERSION))
            put("prefs", entries)
            if (photos.isNotEmpty()) {
                put("photos", buildJsonObject { photos.forEach { (vin, b64) -> put(vin, JsonPrimitive(b64)) } })
            }
            if (removed.isNotEmpty()) put("_removed", buildJsonArray { removed.forEach { add(JsonPrimitive(it)) } })
            extra()
        }
    }

    /**
     * The portable export: `prefs`/`photos`/`_removed` only, no device metadata. Used for the
     * share-to-file feature and as the content [portableContentHash] hashes.
     */
    fun buildExport(
        prefs: Map<String, Any>,
        dirtyKeys: Set<String>,
        photos: Map<String, String> = emptyMap(),
        /** Tombstones already advertised by the file being replaced -- see [buildRoot]. */
        priorRemoved: Set<String> = emptySet(),
    ): String =
        backupJson.encodeToString(
            JsonObject.serializer(),
            buildRoot(prefs, dirtyKeys, photos, priorRemoved) {},
        )

    /**
     * The Drive export: portable content plus Drive-only metadata. [hash] should be
     * [portableContentHash] of the same inputs. `devices` is [mergeDevices] (self upserted, stale
     * peers pruned). [primaryDeviceId] is omitted when null.
     */
    fun buildExportForMainToMain(
        prefs: Map<String, Any>,
        dirtyKeys: Set<String>,
        photos: Map<String, String>,
        hash: String,
        primaryDeviceId: String?,
        selfDevice: SyncDevice,
        knownDevices: List<SyncDevice>,
        nowMs: Long,
        // The file's own stable id; the caller passes the remote file's id if present, else a new one.
        fileId: String,
        /** Tombstones advertised by the remote file, carried forward -- see [buildRoot]. */
        priorRemoved: Set<String> = emptySet(),
    ): String {
        val devices = mergeDevices(knownDevices, selfDevice, nowMs)
        val root = buildRoot(prefs, dirtyKeys, photos, priorRemoved) {
            put("_hash", JsonPrimitive(hash))
            put("_fileId", JsonPrimitive(fileId))
            if (primaryDeviceId != null) put("_primaryDeviceId", JsonPrimitive(primaryDeviceId))
            put("_writerDeviceId", JsonPrimitive(selfDevice.id))
            put("devices", buildJsonArray {
                devices.forEach { d ->
                    add(buildJsonObject {
                        put("id", JsonPrimitive(d.id))
                        put("name", JsonPrimitive(d.name))
                        put("model", JsonPrimitive(d.model))
                        put("appVersion", JsonPrimitive(d.appVersion))
                        put("lastSeenMs", JsonPrimitive(d.lastSeenMs))
                    })
                }
            })
        }
        return backupJson.encodeToString(JsonObject.serializer(), root)
    }

    /**
     * A canonical, order-independent SHA-256 of the portable content (prefs + tombstones + photos),
     * so identical logical settings hash identically regardless of map iteration order.
     * Remote `_hash` != last-seen hash means import; equal means no-op.
     *
     * Entries are sorted and joined with ASCII control separators (0x1F key/value, 0x1E entry,
     * 0x1D section) that cannot appear in keys or values, so "a"->"bc" and "ab"->"c" cannot collide.
     */
    fun portableContentHash(
        prefs: Map<String, Any>,
        dirtyKeys: Set<String>,
        photos: Map<String, String> = emptyMap(),
        /** Must be the SAME set passed to [buildExportForMainToMain]: `_removed` is part of the
         *  uploaded content, so omitting it would leave `_hash` describing a different file. */
        priorRemoved: Set<String> = emptySet(),
    ): String {
        val us = Char(31) // unit separator: between a key and its value
        val rs = Char(30) // record separator: between entries
        val gs = Char(29) // group separator: between sections
        val sb = StringBuilder()
        prefs.entries
            .asSequence()
            .filterNot { isDeviceLocal(it.key) }
            .filterNot { it.key.startsWith("img_") && it.value is String && (it.value as String).startsWith("/") }
            .sortedBy { it.key }
            .forEach { sb.append(it.key).append(us).append(it.value.toString()).append(rs) }
        sb.append(gs)
        // Same union+filter as buildRoot, so the hash and the body agree.
        (tombstones(prefs, dirtyKeys) + priorRemoved)
            .filterNot { it in prefs.keys || isDeviceLocal(it) }
            .sorted()
            .forEach { sb.append(it).append(rs) }
        sb.append(gs)
        photos.entries.sortedBy { it.key }.forEach { sb.append(it.key).append(us).append(it.value).append(rs) }
        return sha256Hex(sb.toString())
    }

    private fun sha256Hex(s: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    // --- Device registry -------------------------------------------------------

    /**
     * Union [remote] with [self] by device id (self's entry replaces its own prior copy; other
     * devices are preserved), then prune entries whose [SyncDevice.lastSeenMs] is older than
     * [retentionMs] before [nowMs] — except [self], which is always kept. Blank-id entries are
     * dropped.
     */
    fun mergeDevices(
        remote: List<SyncDevice>,
        self: SyncDevice,
        nowMs: Long,
        retentionMs: Long = DEVICE_RETENTION_MS,
    ): List<SyncDevice> {
        val byId = LinkedHashMap<String, SyncDevice>()
        remote.forEach { if (it.id.isNotBlank()) byId[it.id] = it }
        if (self.id.isNotBlank()) byId[self.id] = self
        val cutoff = nowMs - retentionMs
        return byId.values.filter { it.id == self.id || it.lastSeenMs >= cutoff }
    }

    // --- Decode ----------------------------------------------------------------

    /**
     * Parse the Drive-only metadata from a file's top-level keys. Returns null only when [json]
     * is not a JSON object; otherwise fields are best-effort (blank/malformed values become null
     * or are dropped). Never throws on a hand-edited or version-skewed file.
     */
    fun parseMeta(json: String): SyncMeta? {
        val root = runCatching { backupJson.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
        // Require a real JSON string: a bare number like `123` is a JsonPrimitive too and must read as malformed.
        fun stringField(name: String): String? =
            (root[name] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        val hash = stringField("_hash")
        val primary = stringField("_primaryDeviceId")
        val writer = stringField("_writerDeviceId")
        val fileId = stringField("_fileId")
        val devices = (root["devices"] as? JsonArray)?.mapNotNull { el ->
            runCatching { backupJson.decodeFromJsonElement(SyncDevice.serializer(), el) }.getOrNull()
                ?.takeIf { it.id.isNotBlank() }
        } ?: emptyList()
        return SyncMeta(hash = hash, primaryDeviceId = primary, writerDeviceId = writer, devices = devices, fileId = fileId)
    }

    /**
     * Just the `_removed` list, for carrying tombstones forward. Separate from [parseBackup]
     * because the upload half runs even when the import half was skipped. Never throws.
     */
    fun parseRemoved(json: String): Set<String> = runCatching {
        val root = backupJson.parseToJsonElement(json) as? JsonObject ?: return emptySet()
        (root["_removed"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?.filterNot { isDeviceLocal(it) }
            ?.toSet()
            ?: emptySet()
    }.getOrDefault(emptySet())

    /**
     * Shared decode for import and merge. Returns null on invalid JSON, a wrong `_format`, a
     * newer `_version` than [BACKUP_VERSION], or no `prefs`. JSON strings and bare numbers (stored
     * as string prefs) go to [MergePlan.stringPuts], bare booleans to [MergePlan.boolPuts],
     * `_removed` to [MergePlan.removes]; [DEVICE_LOCAL_KEYS] are excluded everywhere.
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
            if (isDeviceLocal(name)) return@forEach
            val prim = element as? JsonPrimitive ?: return@forEach
            when {
                // A real JSON string -> a string pref.
                prim.isString -> stringPuts[name] = prim.content
                // A bare JSON boolean -> a boolean pref.
                prim.booleanOrNull != null -> boolPuts[name] = prim.booleanOrNull!!
                // Anything else (a bare number): numeric prefs are stored as strings.
                else -> stringPuts[name] = prim.content
            }
        }
        // A key present in prefs wins over its own tombstone (as in buildRoot): appliers run
        // puts then removes, so the remove would otherwise undo the put.
        val removes = removed.filterNotTo(LinkedHashSet()) {
            isDeviceLocal(it) || it in stringPuts || it in boolPuts
        }
        return MergePlan(stringPuts, boolPuts, removes)
    }

    /**
     * Like [parseBackup], but additionally drops every key in [guarded] from the puts and the
     * removes — the protect + live-dirty logic in the automatic merge: a key changed locally since
     * our last sync (and not yet uploaded) must keep its current local value and must not be
     * tombstoned by the incoming file.
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
