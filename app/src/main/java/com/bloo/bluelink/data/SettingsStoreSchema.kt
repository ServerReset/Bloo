package com.bloo.bluelink.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey

/**
 * Brings this install's settings store up to [SyncSchema]: deletes every retired key and records
 * the schema version.
 */
suspend fun SettingsStore.migrateSchema() {
    val versionKey = intPreferencesKey("settings_schema")
    context.settingsDataStore.edit { prefs ->
        prefs.asMap().keys.filter { SyncSchema.isDeprecated(it.name) }.forEach { prefs.remove(it) }
        if (prefs[versionKey] != SyncSchema.VERSION) prefs[versionKey] = SyncSchema.VERSION
    }
}
