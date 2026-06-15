package com.bloo.wear

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bloo.bluelink.data.WearSettingsPayload
import com.bloo.bluelink.data.WearSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wearSettingsStore by preferencesDataStore(name = "bloo_wear_settings")

/** Persists the phone-synced appearance + preferences so the watch theme is
 *  applied instantly on launch and survives reboots. */
class WearSettingsStore(private val context: Context) {

    private val key = stringPreferencesKey("payload")

    val flow: Flow<WearSettingsPayload?> =
        context.wearSettingsStore.data.map { WearSync.decodeSettings(it[key]) }

    suspend fun save(raw: String) {
        context.wearSettingsStore.edit { it[key] = raw }
    }
}
