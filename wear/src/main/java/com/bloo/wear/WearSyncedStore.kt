package com.bloo.wear

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bloo.bluelink.data.WearClimateState
import com.bloo.bluelink.data.WearExtras
import com.bloo.bluelink.data.WearPresets
import com.bloo.bluelink.data.WearSettingsPayload
import com.bloo.bluelink.data.WearSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers

// ─────────────────────────────────────────────────────────────────────────────
//  Phone-synced mirror
//
//  The watch's local, durable copy of the four state blobs the phone pushes over
//  the Wearable Data Layer: settings, presets, climate and extras. Each is a
//  single JSON string decoded through the FROZEN :shared WearSync codecs, so the
//  on-disk representation and the decode/default semantics stay identical to
//  whatever the phone produced.
//
//  Ownership of the two halves of the round-trip:
//    - WearListenerService.onDataChanged → WearStateWriter.persist*  → save(raw)
//    - WearViewModel collectors                                      → flow
//  (WearViewModel also writes presets back via save() after a local edit so the
//  optimistic change survives a process death before the phone re-pushes.)
//
//  DataStore requires exactly ONE delegate per file for the whole process, so
//  the four delegates live at top level. Holding them inside WearSyncedStore
//  instances crashes with "There are multiple DataStores active for the same
//  file" the moment a ViewModel collector and a listener-service writer coexist.
//
//  Each delegate gets a corruption handler so a file damaged by an interrupted
//  write / power loss resets to empty prefs instead of rethrowing an uncaught
//  exception out of every read — these back live tiles/complications, which must
//  never throw. A missing/empty payload decodes to the codec's own default
//  (WearSettingsPayload → null; the rest → their no-arg data class).
// ─────────────────────────────────────────────────────────────────────────────

private val corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
private val Context.wearSettingsStore by preferencesDataStore(name = "bloo_wear_settings", corruptionHandler = corruptionHandler)
private val Context.wearClimateStore by preferencesDataStore(name = "bloo_wear_climate", corruptionHandler = corruptionHandler)
private val Context.wearPresetsStore by preferencesDataStore(name = "bloo_wear_presets", corruptionHandler = corruptionHandler)
private val Context.wearExtrasStore by preferencesDataStore(name = "bloo_wear_extras", corruptionHandler = corruptionHandler)

/**
 * Generic single-key DataStore wrapper for one phone-synced blob of type [T].
 *
 * A single raw JSON string is stored under the "payload" key and decoded on read
 * via the [decode] function (one of the frozen [WearSync] codecs). This collapses
 * four structurally identical stores into one type; the [Companion] factories and
 * the backward-compatible top-level `Wear*Store(ctx)` functions below give each
 * blob its correctly-typed handle.
 *
 * Call sites are unchanged from the original per-class stores:
 *   val store = WearSettingsStore(ctx)
 *   store.flow.collect { ... }
 *   store.save(rawJson)
 */
class WearSyncedStore<T> private constructor(
    private val store: DataStore<Preferences>,
    private val decode: (String?) -> T,
) {
    private val key = stringPreferencesKey("payload")

    /**
     * Reactive decoded view; emits a fresh [T] on every [save] to this store.
     *
     * flowOn, because [decode] is a JSON deserialization and DataStore only guarantees that the
     * FILE read is off the main thread -- a map{} transform runs in the collector's context.
     * Every collector here is a viewModelScope.launch, which is Main.immediate, so the whole
     * settings/presets/climate/extras payload was being parsed on the UI thread of a watch.
     * That is the same defect the phone's SettingsStore had, on the one device in this project
     * that genuinely cannot absorb it.
     */
    val flow: Flow<T> = store.data.map { decode(it[key]) }.flowOn(Dispatchers.Default)

    /** Persist the raw JSON [raw] as produced by the phone / a WearSync encoder. */
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

// Backward-compatible factory functions — same names and signatures as the old
// standalone store classes, so every call site keeps compiling unchanged.

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
