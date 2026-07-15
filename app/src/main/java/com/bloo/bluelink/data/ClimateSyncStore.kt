package com.bloo.bluelink.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// A corruption handler so a file damaged by an interrupted write/power loss
// resets to empty prefs instead of rethrowing an uncaught exception out of
// every read (AppViewModel collects this store's flow at init).
private val Context.climateSyncStore by preferencesDataStore(
    name = "bloo_climate_sync",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * The phone's copy of the live climate draft the watch publishes (two-way
 * climate sync). [WearPhoneService] writes it on `onDataChanged`; the
 * [com.bloo.bluelink.ui.AppViewModel] observes [flow] and reflects it in the UI.
 */
class ClimateSyncStore(private val context: Context) {

    private val key = stringPreferencesKey("payload")

    val flow: Flow<WearClimateState> = context.climateSyncStore.data.map { WearSync.decodeClimate(it[key]) }

    suspend fun save(raw: String) {
        context.climateSyncStore.edit { it[key] = raw }
    }
}
