package com.bloo.wear

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bloo.bluelink.data.WearClimateState
import com.bloo.bluelink.data.WearSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.wearClimateStore by preferencesDataStore(name = "bloo_wear_climate")

/** The live climate draft mirrored between phone and watch, persisted so it
 *  survives relaunch. */
class WearClimateStore(private val context: Context) {

    private val key = stringPreferencesKey("payload")

    val flow: Flow<WearClimateState> = context.wearClimateStore.data.map { WearSync.decodeClimate(it[key]) }

    suspend fun save(raw: String) {
        context.wearClimateStore.edit { it[key] = raw }
    }
}
