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
}
