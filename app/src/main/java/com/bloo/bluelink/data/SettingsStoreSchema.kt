package com.bloo.bluelink.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey

/**
 * Brings this install's settings store up to [SyncSchema]: deletes every retired key and records
 * the schema version. Raw DataStore writes, not tracked ones: this is housekeeping, not a user
 * change, so it must not mark anything dirty or trigger a sync of its own. Cheap enough to run on
 * every launch.
 */
suspend fun SettingsStore.migrateSchema() {
    val versionKey = intPreferencesKey("settings_schema")
    context.settingsDataStore.edit { prefs ->
        prefs.asMap().keys.filter { SyncSchema.isDeprecated(it.name) }.forEach { prefs.remove(it) }
        if (prefs[versionKey] != SyncSchema.VERSION) prefs[versionKey] = SyncSchema.VERSION
    }
}
