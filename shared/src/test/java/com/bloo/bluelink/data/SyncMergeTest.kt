package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [SyncMerge], the extracted Drive-sync backup core. These
 * pin the merge/export/tombstone semantics the sync audit cared about so a future
 * refactor of SettingsStore can't silently break them. No Android, no DataStore.
 *
 * kotlin.test.assertNotNull RETURNS the non-null value, so `val plan =
 * assertNotNull(...)` both asserts and gives a non-null binding to dereference.
 */
class SyncMergeTest {

    // 1. export -> parse round-trip preserves value TYPES: a real string pref and a
    //    numeric-looking string pref both come back as string puts; a boolean pref
    //    comes back as a boolean put.
    @Test
    fun exportParseRoundTripPreservesTypes() {
        val json = SyncMerge.buildExport(
            prefs = mapOf(
                "theme_mode" to "DARK",   // string pref
                "notify_service" to true, // boolean pref (bare JSON boolean)
                "ui_scale" to "1.2",      // numeric-looking value stored AS a string
            ),
            dirtyKeys = emptySet(),
        )
        val plan = assertNotNull(SyncMerge.parseBackup(json))
        assertEquals("DARK", plan.stringPuts["theme_mode"])
        assertEquals("1.2", plan.stringPuts["ui_scale"])
        assertEquals(true, plan.boolPuts["notify_service"])
        // A boolean pref must NOT leak into stringPuts, and vice-versa.
        assertFalse(plan.boolPuts.containsKey("theme_mode"))
        assertFalse(plan.stringPuts.containsKey("notify_service"))
    }

    // A bare JSON number (never produced by buildExport, but a hand-edited/foreign
    // file can carry one) decodes via the numeric fallback into a STRING put, since
    // every numeric pref in this app is stored as a string.
    @Test
    fun bareNumberDecodesAsStringPut() {
        val json = """{"_format":"bloo-settings","_version":1,"prefs":{"notify_door_min":5}}"""
        val plan = assertNotNull(SyncMerge.parseBackup(json))
        assertEquals("5", plan.stringPuts["notify_door_min"])
        assertFalse(plan.boolPuts.containsKey("notify_door_min"))
    }

    // 2. Tombstone: a key in dirtyKeys but NOT present in prefs becomes a _removed
    //    entry and parses into `removes`; a key that IS present does not.
    @Test
    fun tombstoneForDirtyKeyMissingFromPrefs() {
        val json = SyncMerge.buildExport(
            prefs = mapOf("kept" to "value"),
            dirtyKeys = setOf("kept", "deleted"),
        )
        val plan = assertNotNull(SyncMerge.parseBackup(json))
        assertTrue(plan.removes.contains("deleted"))
        assertFalse(plan.removes.contains("kept"))
        assertEquals("value", plan.stringPuts["kept"])
    }

    // 3. Device-local keys (sync_uri etc.) are never exported and never appear in a
    //    parsed plan, even if a hand-edited backup tries to smuggle them in.
    @Test
    fun deviceLocalKeysExcludedFromExportAndParse() {
        // Never exported.
        val exported = SyncMerge.buildExport(
            prefs = mapOf("sync_uri" to "content://drive/abc", "theme_mode" to "DARK"),
            dirtyKeys = emptySet(),
        )
        assertFalse(exported.contains("sync_uri"))
        val plan = assertNotNull(SyncMerge.parseBackup(exported))
        assertFalse(plan.stringPuts.containsKey("sync_uri"))

        // Never accepted from a foreign file, in either prefs or _removed.
        val smuggled = """
            {"_format":"bloo-settings","_version":1,
             "prefs":{"sync_uri":"content://evil","theme_mode":"LIGHT"},
             "_removed":["sync_wifi"]}
        """.trimIndent()
        val plan2 = assertNotNull(SyncMerge.parseBackup(smuggled))
        assertFalse(plan2.stringPuts.containsKey("sync_uri"))
        assertFalse(plan2.removes.contains("sync_wifi"))
        assertEquals("LIGHT", plan2.stringPuts["theme_mode"])
    }

    // 4. A local-file img_ path ("/...") is excluded from exported prefs (it travels
    //    only via the base64 photos channel); a remote img_ URL is kept in prefs.
    @Test
    fun localImgPathExcludedRemoteImgKept() {
        val json = SyncMerge.buildExport(
            prefs = mapOf(
                "img_LOCALVIN" to "/data/user/0/com.bloo/files/cars/car.jpg",
                "img_REMOTEVIN" to "https://example.com/car.png",
            ),
            dirtyKeys = emptySet(),
        )
        val plan = assertNotNull(SyncMerge.parseBackup(json))
        assertFalse(plan.stringPuts.containsKey("img_LOCALVIN"))
        assertEquals("https://example.com/car.png", plan.stringPuts["img_REMOTEVIN"])
    }

    // The optional photos map is embedded and round-trips through the JSON (the
    // base64 encoding itself is done Android-side and passed in as strings).
    @Test
    fun photosMapEmbeddedInExport() {
        val json = SyncMerge.buildExport(
            prefs = mapOf("theme_mode" to "DARK"),
            dirtyKeys = emptySet(),
            photos = mapOf("VIN123" to "QUJD"), // base64 for "ABC"
        )
        assertTrue(json.contains("photos"))
        assertTrue(json.contains("QUJD"))
    }

    // 5. guarded/protect: mergePlan drops a guarded key from BOTH puts and removes,
    //    while leaving every other key intact.
    @Test
    fun mergePlanDropsGuardedKeysFromPutsAndRemoves() {
        val json = """
            {"_format":"bloo-settings","_version":1,
             "prefs":{"guardedStr":"remote","guardedBool":true,"free":"ok"},
             "_removed":["guardedRemoved","freeRemoved"]}
        """.trimIndent()
        val plan = assertNotNull(
            SyncMerge.mergePlan(json, guarded = setOf("guardedStr", "guardedBool", "guardedRemoved")),
        )
        // Guarded keys are gone from puts...
        assertFalse(plan.stringPuts.containsKey("guardedStr"))
        assertFalse(plan.boolPuts.containsKey("guardedBool"))
        // ...and from removes.
        assertFalse(plan.removes.contains("guardedRemoved"))
        // Non-guarded keys survive untouched.
        assertEquals("ok", plan.stringPuts["free"])
        assertTrue(plan.removes.contains("freeRemoved"))
    }

    // mergePlan with an empty guarded set is identical to parseBackup.
    @Test
    fun mergePlanEmptyGuardedEqualsParseBackup() {
        val json = SyncMerge.buildExport(
            prefs = mapOf("a" to "x", "b" to true),
            dirtyKeys = setOf("a", "gone"),
        )
        val parsed = SyncMerge.parseBackup(json)
        val merged = SyncMerge.mergePlan(json, guarded = emptySet())
        assertEquals(parsed, merged)
    }

    // 6. Version / format / invalid-JSON guards all return null.
    @Test
    fun parseBackupRejectsNewerVersion() {
        val json = """{"_format":"bloo-settings","_version":${SyncMerge.BACKUP_VERSION + 1},"prefs":{"a":"b"}}"""
        assertNull(SyncMerge.parseBackup(json))
    }

    @Test
    fun parseBackupRejectsWrongFormat() {
        val json = """{"_format":"not-bloo","_version":1,"prefs":{"a":"b"}}"""
        assertNull(SyncMerge.parseBackup(json))
    }

    @Test
    fun parseBackupRejectsInvalidJson() {
        assertNull(SyncMerge.parseBackup("this is not json {{{"))
        assertNull(SyncMerge.parseBackup(""))
        // A valid JSON object but with no prefs object is also rejected.
        assertNull(SyncMerge.parseBackup("""{"_format":"bloo-settings","_version":1}"""))
    }

    // The current version is accepted (guard is strictly-greater-than).
    @Test
    fun parseBackupAcceptsCurrentVersion() {
        val json = """{"_format":"bloo-settings","_version":${SyncMerge.BACKUP_VERSION},"prefs":{"a":"b"}}"""
        assertNotNull(SyncMerge.parseBackup(json))
    }

    // 7. Re-added key: a key present in prefs is NOT tombstoned even when it also
    //    appears in dirtyKeys (it was deleted then re-added before this export).
    @Test
    fun reAddedKeyIsNotTombstoned() {
        val json = SyncMerge.buildExport(
            prefs = mapOf("readded" to "freshValue"),
            dirtyKeys = setOf("readded"),
        )
        val plan = assertNotNull(SyncMerge.parseBackup(json))
        assertFalse(plan.removes.contains("readded"))
        assertEquals("freshValue", plan.stringPuts["readded"])
    }

    // --- Content-hash gate + device registry (the two-device convergence fix) ---

    private val dev = SyncMerge.SyncDevice(id = "uuid-A", name = "Adi's S24", model = "SM-S921", appVersion = "1.0", lastSeenMs = 1000L)

    // 8. buildExportForDrive parses back to the SAME MergePlan as a plain portable
    //    file — the additive Drive-only keys (_hash/devices/_primaryDeviceId/
    //    _writerDeviceId) never leak into stringPuts/boolPuts/removes.
    @Test
    fun driveExportParsesLikePortable() {
        val prefs = mapOf("theme_mode" to "DARK", "notify_service" to true, "ui_scale" to "1.2")
        val portable = SyncMerge.buildExport(prefs, emptySet())
        val drive = SyncMerge.buildExportForDrive(
            prefs = prefs, dirtyKeys = emptySet(), photos = emptyMap(),
            hash = SyncMerge.portableContentHash(prefs, emptySet()),
            primaryDeviceId = "uuid-A", selfDevice = dev, knownDevices = emptyList(), nowMs = 5000L,
            fileId = "file-xyz",
        )
        assertEquals(SyncMerge.parseBackup(portable), SyncMerge.parseBackup(drive))
        // Drive-only keys are NOT present in the portable share export.
        assertFalse(portable.contains("_hash"))
        assertFalse(portable.contains("_primaryDeviceId"))
        assertFalse(portable.contains("_fileId"))
        assertFalse(portable.contains("uuid-A"))
        assertFalse(portable.contains("SM-S921"))
        // ...but ARE present in the Drive export, and the file id round-trips.
        assertTrue(drive.contains("_hash"))
        assertTrue(drive.contains("uuid-A"))
        assertEquals("file-xyz", SyncMerge.parseMeta(drive)?.fileId)
    }

    // 9. Old-client / hashless file: parseMeta returns null hash → caller falls back
    //    to the timestamp gate; parseBackup still works.
    @Test
    fun hashlessFileFallsBack() {
        val json = SyncMerge.buildExport(mapOf("a" to "b"), emptySet())
        val meta = assertNotNull(SyncMerge.parseMeta(json))
        assertNull(meta.hash)
        assertNull(meta.primaryDeviceId)
        assertTrue(meta.devices.isEmpty())
        assertNotNull(SyncMerge.parseBackup(json))
    }

    // 10. Malformed metadata never throws: blank/object/wrong-type _hash, bad
    //     _primaryDeviceId, non-array + partial devices all degrade to null/empty.
    @Test
    fun malformedMetaIsResilient() {
        val bad = """
            {"_format":"bloo-settings","_version":1,"prefs":{"a":"b"},
             "_hash":"", "_primaryDeviceId":{"nope":1}, "_writerDeviceId":123,
             "devices":{"not":"an array"}}
        """.trimIndent()
        val meta = assertNotNull(SyncMerge.parseMeta(bad))
        assertNull(meta.hash)              // blank → null
        assertNull(meta.primaryDeviceId)   // object → null
        assertNull(meta.writerDeviceId)    // number → null (not a string primitive)
        assertTrue(meta.devices.isEmpty()) // non-array → empty
        // A devices ARRAY with one blank-id entry and one good entry: blank dropped.
        val mixed = """
            {"_format":"bloo-settings","_version":1,"prefs":{"a":"b"},"_hash":"h",
             "devices":[{"id":"","name":"ghost"},{"id":"real","name":"Phone"}]}
        """.trimIndent()
        val meta2 = assertNotNull(SyncMerge.parseMeta(mixed))
        assertEquals("h", meta2.hash)
        assertEquals(listOf("real"), meta2.devices.map { it.id })
        // Invalid JSON → null.
        assertNull(SyncMerge.parseMeta("not json {{{"))
    }

    // 11. Change gate + no-ping-pong: identical portable content → identical hash
    //     (regardless of map order); different content → different hash. This is the
    //     regression guard for the seq-counter ping-pong the review caught.
    @Test
    fun contentHashIsStableAndOrderIndependent() {
        val a = SyncMerge.portableContentHash(linkedMapOf("theme_mode" to "DARK", "ui_scale" to "1.2"), emptySet())
        // Same logical content, different insertion order → SAME hash.
        val b = SyncMerge.portableContentHash(linkedMapOf("ui_scale" to "1.2", "theme_mode" to "DARK"), emptySet())
        assertEquals(a, b)
        // A real change → different hash.
        val c = SyncMerge.portableContentHash(linkedMapOf("theme_mode" to "LIGHT", "ui_scale" to "1.2"), emptySet())
        assertTrue(a != c)
        // Device-local keys don't affect the hash (they never travel).
        val d = SyncMerge.portableContentHash(
            linkedMapOf("theme_mode" to "DARK", "ui_scale" to "1.2", "sync_uri" to "content://x", "sync_device_id" to "zzz"),
            emptySet(),
        )
        assertEquals(a, d)
    }

    // 12. Simulated A→B→A convergence: once both devices carry the same portable
    //     content, the hash stabilizes — a device seeing remoteHash == its own last
    //     hash has nothing to import (proves the loop terminates).
    @Test
    fun twoDeviceHashConverges() {
        val shared = linkedMapOf("theme_mode" to "DARK", "notify_service" to true)
        val hashA = SyncMerge.portableContentHash(shared, emptySet())
        // B adopts the same content and computes its own hash independently.
        val hashB = SyncMerge.portableContentHash(linkedMapOf("notify_service" to true, "theme_mode" to "DARK"), emptySet())
        assertEquals(hashA, hashB) // → B's next pass sees remoteHash==localHash → no re-import, no re-upload churn.
    }

    // 13. Registry union + prune: self upserted (replacing its stale copy), peers
    //     preserved, no dupes, entries older than retention dropped (self kept).
    @Test
    fun mergeDevicesUnionsAndPrunes() {
        val now = 1_000_000L
        val retention = 100_000L
        val staleId = SyncMerge.SyncDevice(id = "old", name = "Retired", lastSeenMs = now - retention - 1)
        val freshPeer = SyncMerge.SyncDevice(id = "peer", name = "Pixel", lastSeenMs = now - 10)
        val selfStale = SyncMerge.SyncDevice(id = "self", name = "old name", lastSeenMs = 0L) // stale copy in file
        val selfNow = SyncMerge.SyncDevice(id = "self", name = "new name", lastSeenMs = now)
        val merged = SyncMerge.mergeDevices(
            remote = listOf(staleId, freshPeer, selfStale),
            self = selfNow, nowMs = now, retentionMs = retention,
        )
        val ids = merged.map { it.id }
        assertTrue("peer" in ids)          // fresh peer preserved
        assertTrue("self" in ids)          // self kept
        assertFalse("old" in ids)          // stale peer pruned
        assertEquals(1, ids.count { it == "self" }) // no dupe
        assertEquals("new name", merged.first { it.id == "self" }.name) // self upserted, not the stale copy
    }

    // 14. New device-local keys never travel: not exported, and rejected from a
    //     smuggled prefs/_removed.
    @Test
    fun newDeviceLocalKeysNeverTravel() {
        val exported = SyncMerge.buildExport(
            prefs = mapOf(
                "sync_device_id" to "uuid", "sync_device_name" to "Phone", "sync_last_hash" to "h",
                "sync_synced_ever" to true, "sync_devices_cache" to "[]", "sync_pull_primary" to true,
                "theme_mode" to "DARK",
            ),
            dirtyKeys = emptySet(),
        )
        assertFalse(exported.contains("sync_device_id"))
        assertFalse(exported.contains("sync_devices_cache"))
        val smuggled = """
            {"_format":"bloo-settings","_version":1,
             "prefs":{"sync_device_id":"evil","sync_last_hash":"x","theme_mode":"LIGHT"},
             "_removed":["sync_synced_ever"]}
        """.trimIndent()
        val plan = assertNotNull(SyncMerge.parseBackup(smuggled))
        assertFalse(plan.stringPuts.containsKey("sync_device_id"))
        assertFalse(plan.stringPuts.containsKey("sync_last_hash"))
        assertFalse(plan.removes.contains("sync_synced_ever"))
        assertEquals("LIGHT", plan.stringPuts["theme_mode"])
    }

    // 15. A prefs entry literally NAMED like a top-level protocol key is a normal
    //     put and doesn't confuse parseMeta's top-level reads.
    @Test
    fun prefNamedLikeProtocolKeyIsNormalPut() {
        // Note: "_hash" as a genuine pref would be an odd key, but prove isolation.
        val json = """
            {"_format":"bloo-settings","_version":1,"_hash":"realtophash",
             "prefs":{"_writerDeviceId":"iamapref","theme_mode":"DARK"}}
        """.trimIndent()
        val plan = assertNotNull(SyncMerge.parseBackup(json))
        assertEquals("iamapref", plan.stringPuts["_writerDeviceId"]) // pref entry survives as a put
        val meta = assertNotNull(SyncMerge.parseMeta(json))
        assertEquals("realtophash", meta.hash)          // top-level _hash read from root
        assertNull(meta.writerDeviceId)                 // no top-level _writerDeviceId (it was inside prefs)
    }
}

/**
 * Pins which preference keys are allowed to roam between devices.
 *
 * The classification is a real correctness boundary, not a style choice, and
 * it is easy to get wrong when adding a setting: forget it and a genuine
 * preference silently stops syncing; over-match a prefix and per-device
 * runtime state starts travelling, which is worse -- importing a peer's
 * "alert already fired" flag suppresses this device's own notifications, and
 * importing a peer's episode-start timestamp compares clock domains and makes
 * elapsed-time thresholds fire early or late.
 *
 * Separate class from SyncMergeTest so a failure here reads as "the sync
 * boundary moved", not "the merge algorithm broke".
 */
class SyncDeviceLocalTest {

    @Test
    fun `user settings roam between devices`() {
        // Every notification preference is a genuine user choice and must
        // travel -- including the live-charging bar, whose key would
        // otherwise be silently device-only.
        listOf(
            "notify_service", "notify_door", "notify_door_min",
            "notify_running", "notify_running_min",
            "notify_unlocked", "notify_unlocked_min",
            "notify_charging",
            // A representative spread of the other portable settings.
            "theme_mode", "unit_system", "settings_mode",
        ).forEach {
            assertFalse(SyncMerge.isDeviceLocal(it), "$it should sync between devices")
        }
    }

    @Test
    fun `per-device state never roams`() {
        listOf(
            // Sync plumbing: pointing another device at this device's file, or
            // adopting its identity, corrupts the whole scheme.
            "sync_uri", "sync_device_id", "sync_last_hash", "sync_synced_ever",
            // Device capability, not preference: Shizuku may not exist elsewhere.
            "seamless_install_shizuku",
            // Per-VIN runtime state, matched by prefix rather than exact name.
            "alert_door_KMHXX", "door_since_KMHXX", "engine_since_KMHXX",
            "tile_refreshed_KMHXX",
            // "user swiped THIS device's live charging bar away this session" --
            // dismissing a notification on a phone says nothing about what a tablet
            // should show, and it flips constantly during a charge, so exporting it
            // would churn the portable content hash too.
            "live_dismissed_KMHXX",
        ).forEach {
            assertTrue(SyncMerge.isDeviceLocal(it), "$it must never leave this device")
        }
    }

    @Test
    fun `device-local prefixes don't over-match real settings`() {
        // "alert_" is a prefix rule, so a future setting merely STARTING with
        // a similar word must not be swept up by it. These are the near-misses
        // that would be easy to introduce without noticing.
        listOf(
            "alerts_enabled", "doorbell", "engine_type", "tiles_order",
            // "live_dismissed_" is a prefix too, so a real setting about live updates
            // must not be swept into device-local and silently stop syncing.
            "live_updates_enabled", "livecharge_style",
        ).forEach {
            assertFalse(SyncMerge.isDeviceLocal(it), "$it was wrongly treated as device-local")
        }
    }
}
