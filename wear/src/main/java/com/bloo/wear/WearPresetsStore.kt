package com.bloo.wear

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bloo.bluelink.data.WearPresets
import com.bloo.bluelink.data.WearSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wearPresetsStore by preferencesDataStore(name = "bloo_wear_presets")

/** Phone-synced climate presets, kept on the watch so they survive relaunch. */
class WearPresetsStore(private val context: Context) {

    private val key = stringPreferencesKey("payload")

    val flow: Flow<WearPresets> = context.wearPresetsStore.data.map { WearSync.decodePresets(it[key]) }

    suspend fun save(raw: String) {
        context.wearPresetsStore.edit { it[key] = raw }
    }
}
