package com.bloo.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

// DataStore requires exactly ONE instance per file for the whole process, so the
// delegates must live at top level. Holding them inside WearSyncedStore instances
// crashes with "There are multiple DataStores active for the same file" as soon as
// a ViewModel collector and a listener-service writer coexist.
private val Context.wearSettingsStore by preferencesDataStore(name = "bloo_wear_settings")
private val Context.wearClimateStore by preferencesDataStore(name = "bloo_wear_climate")
private val Context.wearPresetsStore by preferencesDataStore(name = "bloo_wear_presets")
private val Context.wearExtrasStore by preferencesDataStore(name = "bloo_wear_extras")

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
    private val store: DataStore<Preferences>,
    private val decode: (String?) -> T,
) {
    private val key = stringPreferencesKey("payload")

    val flow: Flow<T> = store.data.map { decode(it[key]) }

    suspend fun save(raw: String) {
        store.edit { it[key] = raw }
    }

    companion object {
        fun settings(ctx: Context) = WearSyncedStore(ctx.wearSettingsStore, WearSync::decodeSettings)
        fun climate(ctx: Context) = WearSyncedStore(ctx.wearClimateStore, WearSync::decodeClimate)
        fun presets(ctx: Context) = WearSyncedStore(ctx.wearPresetsStore, WearSync::decodePresets)
        fun extras(ctx: Context) = WearSyncedStore(ctx.wearExtrasStore, WearSync::decodeExtras)
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
