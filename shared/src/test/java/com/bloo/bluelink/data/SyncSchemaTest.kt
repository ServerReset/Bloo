package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

    @Test
    fun describeReadsAStoredValueInPlainLanguage() {
        fun fam(key: String) = SettingsCatalog.families.first { it.key == key }
        assertTrue(SettingsCatalog.describe(fam("columns_flipped"), true) == "On")
        assertTrue(SettingsCatalog.describe(fam("weather_label"), "San Jose") == "San Jose")
        assertTrue(SettingsCatalog.describe(fam("weather_label"), "") == "Not set")
        assertTrue(SettingsCatalog.describe(fam("vehicle_order"), "A,B,C") == "3 items")
        assertTrue(SettingsCatalog.describe(fam("vehicle_order"), "") == "None")
        assertTrue(SettingsCatalog.describe(fam("img_"), "/files/cars/x.jpg") == "Set")
        assertTrue(SettingsCatalog.describe(fam("img_"), null) == "Not set")
    }

    @Test
    fun theSettingsWithoutAControlAreTheOnesSearchShowsReadOnly() {
        val readOnly = SettingsCatalog.searchable.filter { !it.control }.map { it.key }
        assertTrue("img_" in readOnly && "vehicle_order" in readOnly && "custom_palettes" in readOnly)
        // These gained real controls in search (and so can be changed by a plain-language request).
        assertFalse("columns_flipped" in readOnly || "color_palette" in readOnly || "settings_mode" in readOnly)
        assertFalse("haptics_enabled" in readOnly)
    }

    @Test
    fun whatSearchShowsIsWhatSyncCarries() {
        // The one pipeline's invariant: a setting a person can search for is a setting that syncs, and a
        // device-local one is never offered. Device-local families are exactly the ones that never travel.
        SettingsCatalog.searchable.forEach {
            val sample = if (it.scope == SettingsCatalog.Scope.CAR) it.key + "5NMS33AD1KH000000" else it.key
            assertFalse(SyncMerge.isDeviceLocal(sample), "searchable but device-local: $sample")
        }
        SettingsCatalog.families.filter { !it.syncs }.forEach {
            assertFalse(it.searchable, "device-local but searchable: ${it.key}")
            val sample = if (it.scope == SettingsCatalog.Scope.CAR) it.key + "5NMS33AD1KH000000" else it.key
            assertTrue(SyncMerge.isDeviceLocal(sample), sample)
        }
    }

    @Test
    fun everySearchableSettingSurvivesAnExportAndImport() {
        // One store, two readers: build a store holding every searchable setting (per-car ones for two
        // cars, one of them lower-case as a stray API might return), send it through the Drive export
        // and back, and every key must arrive intact -- search and sync see the same data.
        val vins = listOf("5NMS33AD1KH000000", "kmhl14ja1pa000001")
        val store = LinkedHashMap<String, Any>()
        SettingsCatalog.searchable.forEach { f ->
            val sample: Any = if (f.kind == SettingsCatalog.Kind.TOGGLE) true else "value-${f.key}"
            if (f.scope == SettingsCatalog.Scope.CAR) vins.forEach { store[f.key + it] = sample } else store[f.key] = sample
        }
        val plan = assertNotNull(SyncMerge.parseBackup(SyncMerge.buildExport(prefs = store, dirtyKeys = emptySet())))
        store.forEach { (key, value) ->
            val arrived: Any? = plan.boolPuts[key] ?: plan.stringPuts[key]
            assertTrue(arrived == value, "lost in sync: $key")
        }
    }
}
