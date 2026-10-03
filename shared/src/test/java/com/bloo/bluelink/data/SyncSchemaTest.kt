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
}
