package com.bloo.wear

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bloo.bluelink.data.WearClimateState
import com.bloo.bluelink.data.WearExtras
import com.bloo.bluelink.data.WearPresets
import com.bloo.bluelink.data.WearSettingsPayload
import com.bloo.bluelink.data.WearSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Generic single-key DataStore wrapper that replaces the structurally identical
 * WearSettingsStore/WearClimateStore/WearPresetsStore/WearExtrasStore.
 *
 * Usage — old call sites continue to compile unchanged:
 *   val store = WearSettingsStore(ctx)
 *   store.flow.collect { ... }
 *   store.save(rawJson)
 */
class WearSyncedStore<T> private constructor(
    private val context: Context,
    name: String,
    private val decode: (String?) -> T,
) {
    private val Context.store by preferencesDataStore(name = name)
    private val key = stringPreferencesKey("payload")

    val flow: Flow<T> = context.store.data.map { decode(it[key]) }

    suspend fun save(raw: String) {
        context.store.edit { it[key] = raw }
    }

    companion object {
        fun settings(ctx: Context) = WearSyncedStore(ctx, "bloo_wear_settings", WearSync::decodeSettings)
        fun climate(ctx: Context) = WearSyncedStore(ctx, "bloo_wear_climate", WearSync::decodeClimate)
        fun presets(ctx: Context) = WearSyncedStore(ctx, "bloo_wear_presets", WearSync::decodePresets)
        fun extras(ctx: Context) = WearSyncedStore(ctx, "bloo_wear_extras", WearSync::decodeExtras)
    }
}

/** Backward-compatible factory functions — same signatures as the old classes. */
@Suppress("FunctionName")
fun WearSettingsStore(context: Context): WearSyncedStore<WearSettingsPayload?> =
    WearSyncedStore.settings(context)

@Suppress("FunctionName")
fun WearClimateStore(context: Context): WearSyncedStore<WearClimateState> =
    WearSyncedStore.climate(context)

@Suppress("FunctionName")
fun WearPresetsStore(context: Context): WearSyncedStore<WearPresets> =
    WearSyncedStore.presets(context)

@Suppress("FunctionName")
fun WearExtrasStore(context: Context): WearSyncedStore<WearExtras> =
    WearSyncedStore.extras(context)