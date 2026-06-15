package com.bloo.wear

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bloo.bluelink.data.WearExtras
import com.bloo.bluelink.data.WearSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wearExtrasStore by preferencesDataStore(name = "bloo_wear_extras")

/** Phone-synced extras (weather, car photos, AI summaries), kept on the watch. */
class WearExtrasStore(private val context: Context) {

    private val key = stringPreferencesKey("payload")

    val flow: Flow<WearExtras> = context.wearExtrasStore.data.map { WearSync.decodeExtras(it[key]) }

    suspend fun save(raw: String) {
        context.wearExtrasStore.edit { it[key] = raw }
    }
}
