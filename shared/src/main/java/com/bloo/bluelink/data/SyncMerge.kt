package com.bloo.bluelink.data

import kotlinx.serialization.Serializable
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
 *
 * ## Two export shapes
 * - [buildExport] — the **portable** shape (`prefs`/`photos`/`_removed` only). It is
 *   what the manual "export settings to a file" feature produces (a file the user
 *   may share/email), and it is also the exact byte-content the change-detection
 *   [portableContentHash] is computed over. It carries **no device metadata**.
 * - [buildExportForDrive] — the portable shape **plus** the Drive-sync-only keys
 *   (`_hash`, `_primaryDeviceId`, `_writerDeviceId`, `devices`). Used only for the
 *   Drive file, never for the shareable export, so device names/ids never leak.
 *
 * ## The `_hash` change gate (why not a timestamp / sequence counter)
 * The old import gate compared the Drive file's last-modified time against a
 * locally-stored wall-clock — fragile under cross-device clock skew (and useless
 * when a provider exposes no modified-time). A monotonic `_seq += 1` per upload
 * would instead ping-pong forever (each device re-uploads on every clean pass,
 * climbing the counter and re-importing the other's echo). A **content hash** of
 * the portable content is skew-immune AND self-detects a no-op sync: identical
 * content → identical hash → nothing to import and nothing new to write. All new
 * keys are additive top-level fields, so [BACKUP_VERSION] stays 1 and an older
 * client (which ignores them on read, and drops them when it rewrites the file)
 * still interoperates — the new client just falls back to the timestamp gate when
 * `_hash` is absent.
 */
object SyncMerge {

    /** The settings-backup format version. The format is a flat key-value bag, so
     *  an older client reading a newer backup is normally fine (unknown keys are
     *  ignored); bump this only if a future change stops being purely additive, so
     *  old clients can detect and refuse a newer format instead of misreading it.
     *  The `_hash`/`devices`/`_primaryDeviceId`/`_writerDeviceId` keys are additive
     *  (ignored by old clients), so they do NOT warrant a version bump. */
    const val BACKUP_VERSION = 1

    /** Preference keys that describe THIS device's own Drive-sync wiring (the
     *  content:// URI it was granted, its last-sync bookkeeping, its Wi-Fi-only
     *  preference, its local dirty set, and its sync-identity/registry bookkeeping)
     *  — never portable, so never exported, imported, or merged. */
    val DEVICE_LOCAL_KEYS = setOf(
        "sync_uri", "sync_last_ms", "sync_last_error", "sync_wifi", "sync_dirty_keys",
        // Sync identity + hash-gate + registry bookkeeping (all per-device, never travel):
        "sync_device_id", "sync_device_name", "sync_last_hash", "sync_synced_ever",
        "sync_devices_cache", "sync_pull_primary", "sync_primary_cache", "sync_file_id",
        // A primary designation made on THIS device and not yet uploaded. Emphatically
        // device-local: it is a one-shot write intent, and letting it roam would hand every
        // other device the same intent and restart the tug-of-war it exists to end.
        "sync_primary_pending",
        // Whether THIS device installs updates silently via Shizuku — a device-local
        // capability (Shizuku may not be present elsewhere), so it must not roam.
        "seamless_install_shizuku",
        // Which car is on screen RIGHT NOW. Transient per-device view state, not a setting:
        // it is written through editTracked on every car swipe, so it was dirty-tracked,
        // auto-pushed to Drive, and then roamed -- changing the car on the phone yanked the
        // tablet to the same car, and every swipe cost a sync round trip.
        "last_vehicle_vin",
    )

    /** Per-VIN keys whose NAMES carry a dynamic suffix (the VIN), so they can't be
     *  listed exactly in [DEVICE_LOCAL_KEYS] but are just as device-local and must
     *  never travel. All are transient per-device RUNTIME state, not settings:
     *   - `alert_*`      : CarAlerts "already fired this episode" flags — importing a
     *                      peer's true flag would suppress THIS device's own
     *                      independent door/engine/service notification.
     *   - `door_since_*` / `engine_since_*` / `unlocked_since_*` : the wall-clock
     *                      timestamp an open/running/unlocked episode began — comparing
     *                      it against another device's clock domain makes the
     *                      elapsed-time threshold fire early/late.
     *   - `tile_refreshed_*` : the per-car tile live-refresh throttle stamp — a peer's
     *                      stamp would wrongly suppress this device's own refresh.
     *  Excluding them also keeps the portable content hash stable across alert/refresh
     *  ticks (otherwise every 30-min alert poll churned the hash and forced a re-upload). */
    private val DEVICE_LOCAL_PREFIXES = listOf(
        // unlocked_since_* was missing here while all three of its siblings were
        // listed. It is the same thing: Notifications sets it to
        // System.currentTimeMillis() on first observing an unlocked car and then tests
        // `now - since > unlockedMinutes * 60_000`, so a peer's clock-domain stamp
        // makes the "unlocked for N minutes" alert fire early or late -- and it churns
        // the content hash on every lock-state change, costing a Drive round trip per
        // alert tick. The companion alert_unlocked_* WAS covered, by "alert_", which is
        // probably why the gap went unnoticed. prefs.unlocked defaults on, so this is
        // the common configuration rather than an edge case.
        "alert_", "door_since_", "engine_since_", "unlocked_since_", "tile_refreshed_",
        // live_dismissed_* : "the user swiped THIS device's live charging bar away for
        //                  the current charging session". Dismissing a notification on
        //                  a phone says nothing about whether a tablet should show one,
        //                  and roaming it would suppress the bar on a device the user
        //                  never touched. Also cleared and re-set constantly during a
        //                  charge, so exporting it would churn the content hash.
        "live_dismissed_",
    )

    /** Whether [name] is device-local (exact key or dynamic per-VIN prefix) and so must
     *  never be exported, imported, merged, or folded into the content hash. */
    fun isDeviceLocal(name: String): Boolean =
        name in DEVICE_LOCAL_KEYS || DEVICE_LOCAL_PREFIXES.any { name.startsWith(it) }

    /** A device that syncs this Drive file, as recorded in the file's `devices`
     *  registry. Purely informational (drives the phone's "your devices" list and
     *  the "primary" designation); never affects the settings merge itself. All
     *  fields default so a partial/older entry decodes leniently rather than
     *  throwing — an entry with a blank [id] is dropped on merge. */
    @Serializable
    data class SyncDevice(
        val id: String = "",
        val name: String = "",
        val model: String = "",
        val appVersion: String = "",
        val lastSeenMs: Long = 0L,
    )

    /** Registry entries not seen for this long are pruned on merge, so a
     *  factory-reset/retired device doesn't linger in the list forever. 90 days
     *  is comfortably longer than any normal "I didn't open that device" gap. */
    const val DEVICE_RETENTION_MS = 90L * 24 * 60 * 60 * 1000

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

    /** The Drive-sync-only metadata parsed out of a file's top-level keys, kept
     *  separate from the [MergePlan] (which is only the portable prefs/tombstones).
     *  [hash] is null when the file predates the hash gate (old client, or the
     *  header/marker case) — the caller then falls back to the timestamp gate. */
    data class SyncMeta(
        val hash: String?,
        val primaryDeviceId: String?,
        val writerDeviceId: String?,
        val devices: List<SyncDevice>,
        /** A stable id for the FILE ITSELF, written into the content so every device
         *  reads the SAME value — unlike a SAF content:// URI, which the OS assigns
         *  differently per device for the same Drive file (the reason a URI hash
         *  showed mismatched codes on two phones that ARE on one file). Minted once
         *  by whichever device first writes it, then preserved by all. Null on a
         *  file that predates this field. */
        val fileId: String?,
    )

    // Same Json config SettingsStore's backupJson used: pretty-printed output so
    // the exported file is human-readable, unknown keys ignored on decode.
    private val backupJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // --- Portable export (prefs/photos/_removed only — safe to share) ----------

    /** The `prefs` object shared by [buildExport] and [buildExportForDrive]:
     *  skips [DEVICE_LOCAL_KEYS] and local-file `img_` paths (a "/"-prefixed String
     *  path is meaningless on another device — only the photos channel carries
     *  local photos), and types each value as a JSON boolean/string (anything else
     *  coerced via toString()). */
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

    /** `_removed` tombstones: dirty keys that no longer exist in [prefs] and aren't
     *  device-local, so other devices converge on the deletion instead of
     *  resurrecting the key. */
    private fun tombstones(prefs: Map<String, Any>, dirtyKeys: Set<String>): Set<String> =
        (dirtyKeys - prefs.keys.toSet()).filterNotTo(LinkedHashSet()) { isDeviceLocal(it) }

    /** Builds the base backup root (`_format`/`_version`/`prefs`/`photos`/`_removed`),
     *  then lets [extra] add any additional top-level keys (the Drive-only metadata).
     *  [buildExport] passes an empty [extra] so its output is exactly the historical
     *  portable shape. */
    private inline fun buildRoot(
        prefs: Map<String, Any>,
        dirtyKeys: Set<String>,
        photos: Map<String, String>,
        priorRemoved: Set<String>,
        extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        val entries = portablePrefsObject(prefs)
        // Local tombstones UNIONED with the ones already in the remote file.
        //
        // Without the union a tombstone lived for exactly ONE upload. `tombstones()` reads the
        // current dirty set, and a successful upload clears it -- so the very next push, triggered
        // by any unrelated edit, rebuilt the body from an empty dirty set and omitted `_removed`
        // entirely. Any peer that had not synced inside that single window still held the key,
        // re-uploaded it, and the deletion was undone on the device that made it. Plates, car
        // photos, service history, weather location, climate presets: every setter that REMOVES a
        // key rather than blanking it.
        //
        // Filtered so carrying forward cannot make a deletion permanent or smuggle a local key:
        //  - a key PRESENT in prefs wins over its own stale tombstone, so re-adding a value works
        //    (otherwise re-setting a plate would be undone on the next sync, forever);
        //  - device-local keys can never travel, even via a hand-edited `_removed`.
        //
        // Known tradeoff, stated rather than hidden: the tombstone set grows with the number of
        // keys ever deleted and has no TTL. Trimming it needs a per-tombstone timestamp, which
        // this format has nowhere to put, and an unbounded-but-tiny list of dead key NAMES is a
        // far better failure than resurrecting a user's deleted data.
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
     * The **portable** export (identical output to the historical `buildExport`):
     * `prefs`/`photos`/`_removed` only, no device metadata. Used by the manual
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
     * The **Drive** export: the portable content plus the Drive-sync-only metadata.
     * [hash] should be [portableContentHash] of the same prefs/dirtyKeys/photos.
     * The `devices` registry is [mergeDevices]`(knownDevices, selfDevice, nowMs)` so
     * this device's own entry is upserted and stale peers pruned; peers are
     * otherwise preserved. [primaryDeviceId] is omitted when null.
     */
    fun buildExportForDrive(
        prefs: Map<String, Any>,
        dirtyKeys: Set<String>,
        photos: Map<String, String>,
        hash: String,
        primaryDeviceId: String?,
        selfDevice: SyncDevice,
        knownDevices: List<SyncDevice>,
        nowMs: Long,
        // The file's own stable id, written into the content so every device shows
        // the SAME File ID for one Drive file (a per-device SAF URI can't). The
        // caller passes the remote file's id if present, else a freshly-minted one.
        fileId: String,
        /** Tombstones already advertised by the remote file, carried forward so a deletion
         *  survives longer than one upload -- see [buildRoot]. */
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
     * A **canonical, order-independent** SHA-256 of the portable content
     * (prefs + tombstones + photos), so two devices with identical logical settings
     * produce the identical hash regardless of DataStore map iteration order (which
     * is NOT guaranteed stable across devices). This is the change-detection signal:
     * remote `_hash` != our last-seen hash ⇒ import; our new hash == remote ⇒ no-op
     * (write only a registry heartbeat, don't churn the file).
     *
     * Entries are sorted by key and joined with ASCII control separators — a
     * unit-separator (0x1F) between a key and its value, a record-separator (0x1E)
     * between entries, and a group-separator (0x1D) between the prefs / tombstones /
     * photos sections. None can appear in a key name or a stored value, so
     * "a"->"bc" and "ab"->"c" can't collide.
     */
    fun portableContentHash(
        prefs: Map<String, Any>,
        dirtyKeys: Set<String>,
        photos: Map<String, String> = emptyMap(),
        /** Must be the SAME set passed to [buildExportForDrive]. The hash is documented as being
         *  computed over the exact content uploaded, and `_removed` is part of that content -- so
         *  once tombstones are carried forward, omitting them here would leave `_hash` describing
         *  a file that no longer exists. Callers that pass one and not the other break the
         *  invariant silently. */
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
        // Same union+filter as buildRoot, so the hash and the body agree by construction.
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

    /** Union [remote] with [self] by device id (self's entry replaces its own prior
     *  copy; other devices are preserved), then prune entries whose [SyncDevice.lastSeenMs]
     *  is older than [retentionMs] before [nowMs] — except [self], which is always
     *  kept. Blank-id entries are dropped. */
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
     * Parse the Drive-only metadata out of a file's top-level keys. Returns null
     * only when [json] isn't a valid JSON object at all; otherwise every field is
     * best-effort ([hash] null when absent/blank so the caller uses the timestamp
     * fallback; malformed `devices` entries dropped; a bad `_primaryDeviceId`/
     * `_writerDeviceId` → null). Never throws on a hand-edited or version-skewed file.
     */
    fun parseMeta(json: String): SyncMeta? {
        val root = runCatching { backupJson.parseToJsonElement(json) as? JsonObject }.getOrNull() ?: return null
        // The hash (SHA-256 hex) and the device ids (UUID strings) are ALWAYS JSON
        // strings when we write them. Require an actual JSON string primitive: a bare
        // number like `123` is still a JsonPrimitive whose `content` is "123", so
        // without the isString guard a hand-edited/foreign `"_writerDeviceId": 123`
        // would be wrongly accepted as an id rather than treated as malformed → null.
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
     * Just the `_removed` list from a backup, for carrying tombstones forward.
     *
     * Separate from [parseBackup] deliberately: performDriveSync needs this on the UPLOAD half,
     * which runs even when the import half was skipped (nothing newer, or an unreadable prefs
     * block). Going through parseBackup would tie the two together and lose the tombstones in
     * exactly the passes that still have to republish them. Never throws.
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
            if (isDeviceLocal(name)) return@forEach
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
        val removes = removed.filterNotTo(LinkedHashSet()) { isDeviceLocal(it) }
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
