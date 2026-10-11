package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncSchemaTest {
    @Test
    fun retiredSettingsNeverTravel() {
        assertTrue(SyncMerge.isDeviceLocal("show_search"))
        assertTrue(SyncMerge.isDeviceLocal("autolock_use_bt_VIN123"))
        assertTrue(SyncSchema.isDeprecated("cover_settings_hint_dismissed"))
    }

    @Test
    fun anyNewSettingSyncsByDefault() {
        assertFalse(SyncMerge.isDeviceLocal("glass_clarity"))
        assertFalse(SyncMerge.isDeviceLocal("some_future_setting"))
        assertFalse(SyncSchema.isDeprecated("theme_mode"))
    }

    @Test
    fun theSchemaVersionRecordIsDeviceLocal() {
        assertTrue(SyncMerge.isDeviceLocal("settings_schema"))
    }

    @Test
    fun runtimeNotificationFlagsNeverTravel() {
        // These were synced by accident: one device's "already sent" silenced the other's alert.
        assertTrue(SyncMerge.isDeviceLocal("notif_start_sent_5NMS33AD1KH000000"))
        assertTrue(SyncMerge.isDeviceLocal("notif_complete_sent_5NMS33AD1KH000000"))
        assertTrue(SyncMerge.isDeviceLocal("alert_door_5NMS33AD1KH000000"))
    }

    @Test
    fun perCarSettingsSyncAndAreFoundByTheCatalog() {
        listOf("img_", "plate_", "svc_last_", "svc_interval_", "ptrain_", "seat_").forEach { prefix ->
            val key = prefix + "5NMS33AD1KH000000"
            assertFalse(SyncMerge.isDeviceLocal(key), key)
            assertTrue(SettingsCatalog.familyOf(key)?.scope == SettingsCatalog.Scope.CAR, key)
        }
    }

    @Test
    fun theLongestPrefixWins() {
        assertTrue(SettingsCatalog.familyOf("autolock_device_name_VIN")?.key == "autolock_device_name_")
    }

    @Test
    fun aVinIsNormalisedAndSplitFromTheRestOfAKey() {
        assertTrue(SettingsCatalog.vinOf("svc_last_ abc123 ") == "ABC123")
        assertTrue(SettingsCatalog.vinOf("seat_abc123_sw") == "ABC123")
        assertTrue(SettingsCatalog.vinOf("theme_mode") == null)
    }

    @Test
    fun everyFamilyIsDeclaredOnce() {
        val keys = SettingsCatalog.families.map { it.scope to it.key }
        assertTrue(keys.size == keys.toSet().size)
    }

    @Test
    fun everySearchableFamilyHasWordsToBeFoundBy() {
        SettingsCatalog.searchable.forEach { assertTrue(it.keywords.isNotBlank(), it.key) }
    }

    @Test
    fun theAiSettingsRoamButOnlyTheAutoRunDefaultsOff() {
        assertFalse(SyncMerge.isDeviceLocal("ai_enabled"))
        assertFalse(SyncMerge.isDeviceLocal("ai_auto_summarize"))
    }
}
