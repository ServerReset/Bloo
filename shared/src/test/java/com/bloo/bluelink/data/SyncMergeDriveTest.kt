package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure-JVM tests for [SyncMerge]'s Drive-specific surface: the portable content hash that gates
 * uploads, the device registry, parseMeta's resilience, and the shape of the Drive export.
 * See [SyncMergeTest] for the portable export/parse/tombstone semantics.
 */
class SyncMergeDriveTest {

    // 6d. The Drive export carries them too, not just the portable one -- performMainToMainSync uses
    //     buildExportForMainToMain, so a fix that only reached buildExport would fix nothing in
    //     practice.
    @Test
    fun driveExportAlsoCarriesPriorTombstones() {
        val json = SyncMerge.buildExportForMainToMain(
            prefs = mapOf("kept" to "value"),
            dirtyKeys = emptySet(),
            photos = emptyMap(),
            hash = "h",
            primaryDeviceId = null,
            selfDevice = dev,
            knownDevices = emptyList(),
            nowMs = 5000L,
            fileId = "file-1",
            priorRemoved = setOf("svc_last_VIN1"),
        )
        val plan = assertNotNull(SyncMerge.parseBackup(json))
        assertTrue(plan.removes.contains("svc_last_VIN1"))
    }

    // --- Content-hash gate + device registry (the two-device convergence fix) ---

    private val dev = SyncMerge.SyncDevice(id = "uuid-A", name = "Adi's S24", model = "SM-S921", appVersion = "1.0", lastSeenMs = 1000L)

    // 8. buildExportForMainToMain parses back to the SAME MergePlan as a plain portable
    //    file — the additive Drive-only keys (_hash/devices/_primaryDeviceId/
    //    _writerDeviceId) never leak into stringPuts/boolPuts/removes.
    @Test
    fun driveExportParsesLikePortable() {
        val prefs = mapOf("theme_mode" to "DARK", "notify_service" to true, "ui_scale" to "1.2")
        val portable = SyncMerge.buildExport(prefs, emptySet())
        val drive = SyncMerge.buildExportForMainToMain(
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
