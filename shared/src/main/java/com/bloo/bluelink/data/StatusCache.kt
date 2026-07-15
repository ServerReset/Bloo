package com.bloo.bluelink.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// A corruption handler so a file damaged by an interrupted write/power loss
// resets to empty prefs instead of rethrowing an uncaught exception out of
// every read (this cache is read at cold start, before any network call returns).
private val Context.statusCacheStore by preferencesDataStore(
    name = "bloo_status_cache",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Serializable
private data class CachePayload(
    val statuses: Map<String, VehicleStatus> = emptyMap(),
    val locations: Map<String, GeoLocation> = emptyMap(),
    val placeNames: Map<String, String> = emptyMap(),
    val fetched: Map<String, Long> = emptyMap(),
)

/**
 * On-disk cache of the last fetched status/location per VIN, so the UI can show
 * stale-but-useful data (locks, parked/driving, last location) immediately on a
 * cold start, until a command is sent or the user refreshes.
 */
class StatusCache(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("payload")

    data class Cached(
        val statuses: Map<String, VehicleStatus>,
        val locations: Map<String, GeoLocation>,
        val placeNames: Map<String, String>,
        val fetched: Map<String, Long>,
    )

    suspend fun load(): Cached {
        val raw = context.statusCacheStore.data.first()[key]
        val p = raw?.let { runCatching { json.decodeFromString(CachePayload.serializer(), it) }.getOrNull() }
            ?: CachePayload()
        return Cached(p.statuses, p.locations, p.placeNames, p.fetched)
    }

    suspend fun save(
        statuses: Map<String, VehicleStatus>,
        locations: Map<String, GeoLocation>,
        placeNames: Map<String, String>,
        fetched: Map<String, Long>,
    ) {
        context.statusCacheStore.edit {
            it[key] = json.encodeToString(
                CachePayload.serializer(),
                CachePayload(statuses, locations, placeNames, fetched),
            )
        }
    }
}
