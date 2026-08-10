package com.bloo.bluelink.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// A corruption handler so a file damaged by an interrupted write/power loss resets to
// empty prefs instead of rethrowing out of every read (AppViewModel collects this at init).
private val Context.aiSummaryStore by preferencesDataStore(
    name = "bloo_ai_summaries",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * The bridge for AI summaries the WATCH asked the phone to generate.
 *
 * [WearPhoneService.runAiSummary] runs in a WearableListenerService that Play Services can
 * start with the phone UI closed, so it has no live [com.bloo.bluelink.ui.AppViewModel] to
 * write into. It used to patch only the published Data Layer extras item -- but the
 * ViewModel republishes the whole extras payload from its own `_state.aiSummaries` on the
 * next state change, which never held the watch's summary, so it was overwritten and lost
 * (and never shown on the phone either). This store is that missing channel: the service
 * writes here, the ViewModel mirrors [flow] into `_state.aiSummaries` (exactly as it already
 * does for the watch's climate draft via [ClimateSyncStore]), and the existing republish
 * then carries the correct map to the watch.
 *
 * Phone-generated summaries deliberately do NOT go through here -- they already live in
 * `_state` and were never the ones being lost; only the watch-requested ones are persisted.
 */
class AiSummaryStore(private val context: Context) {

    private val key = stringPreferencesKey("payload")
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), String.serializer())

    private fun decode(raw: String?): Map<String, String> =
        raw?.let { runCatching { json.decodeFromString(mapSerializer, it) }.getOrNull() } ?: emptyMap()

    /** Per-VIN summaries the watch has requested, decoded from JSON (empty on any fault). */
    val flow: Flow<Map<String, String>> = context.aiSummaryStore.data.map { decode(it[key]) }

    /** Merge a freshly-generated summary for [vin], reading-then-writing the stored map. */
    suspend fun put(vin: String, summary: String) {
        context.aiSummaryStore.edit { prefs ->
            prefs[key] = json.encodeToString(mapSerializer, decode(prefs[key]) + (vin to summary))
        }
    }
}
