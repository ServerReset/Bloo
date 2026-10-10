package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
            // unlocked_since_ was absent from both the list and this test, so the
            // test could not have caught it. Its sibling timestamps were here.
            "unlocked_since_KMHXX",
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

    // A hand-edited backup that lists the SAME key in both `prefs` and `_removed` must keep the
    // present value, not delete it. Every applier runs the puts then the removes, so without the
    // reader dropping such a key from `removes`, the put would be immediately undone -- the exact
    // opposite of buildRoot's own "a present value wins over its stale tombstone" rule, which it
    // applies on the WRITE side. Bloo's own exports never dual-list; a hand-edited file can.
    @Test
    fun keyInBothPrefsAndRemovedKeepsTheValue() {
        val json = """
            {"_format":"bloo-settings","_version":1,
             "prefs":{"plate_ABC":"7XYZ123","notify_charging":true},
             "_removed":["plate_ABC","notify_charging","genuinely_gone"]}
        """.trimIndent()
        val plan = assertNotNull(SyncMerge.parseBackup(json))
        // The two keys present in prefs win -- kept as puts, dropped from removes.
        assertEquals("7XYZ123", plan.stringPuts["plate_ABC"])
        assertEquals(true, plan.boolPuts["notify_charging"])
        assertFalse(plan.removes.contains("plate_ABC"), "a key present in prefs must not also be removed")
        assertFalse(plan.removes.contains("notify_charging"), "a key present in boolPuts must not also be removed")
        // A key ONLY in _removed is still a genuine tombstone.
        assertTrue(plan.removes.contains("genuinely_gone"))
    }
}
