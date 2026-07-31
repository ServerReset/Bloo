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

// Wire format persisted as a single JSON string under one DataStore key. Everything
// is keyed by VIN (the map keys), so one payload can hold every vehicle on the
// account at once. All fields default to empty so decoding an old/partial payload
// (or the corruption-handler's emptyPreferences() fallback) still parses cleanly.
@Serializable
private data class CachePayload(
    val statuses: Map<String, VehicleStatus> = emptyMap(),
    val locations: Map<String, GeoLocation> = emptyMap(),
    val placeNames: Map<String, String> = emptyMap(), // reverse-geocoded label per VIN, cached to avoid re-geocoding
    val fetched: Map<String, Long> = emptyMap(), // epoch-millis timestamp of when each VIN's data was fetched
)

/**
 * On-disk cache of the last fetched status/location per VIN, so the UI can show
 * stale-but-useful data (locks, parked/driving, last location) immediately on a
 * cold start, until a command is sent or the user refreshes.
 */
class StatusCache(private val context: Context) {

    // ignoreUnknownKeys lets old cache files survive future field additions without
    // crashing decode; encodeDefaults ensures every field is always written so a
    // reader on an older app version (or after a rollback) still finds all keys present.
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val key = stringPreferencesKey("payload")

    data class Cached(
        val statuses: Map<String, VehicleStatus>,
        val locations: Map<String, GeoLocation>,
        val placeNames: Map<String, String>,
        val fetched: Map<String, Long>,
    )

    /**
     * Reads the single DataStore entry, decodes it from JSON, and unpacks it into a
     * [Cached]. Takes only the first emitted value from the DataStore Flow (`.first()`)
     * since this is a one-shot read, not an ongoing subscription. If the key is absent
     * (first run) or decoding fails for any reason (corrupt/incompatible JSON), falls
     * back to an all-empty [CachePayload] via `runCatching { }.getOrNull() ?: CachePayload()`
     * rather than throwing, so a bad cache never blocks app startup.
     */
    suspend fun load(): Cached {
        val raw = context.statusCacheStore.data.first()[key]
        val p = raw?.let { runCatching { json.decodeFromString(CachePayload.serializer(), it) }.getOrNull() }
            ?: CachePayload()
        return Cached(p.statuses, p.locations, p.placeNames, p.fetched)
    }

    /**
     * Serializes the given maps into one [CachePayload] JSON blob and writes it as a
     * single atomic DataStore edit (DataStore's `edit` runs the whole block as one
     * transaction), replacing whatever was previously stored under [key] wholesale —
     * there is no per-VIN partial update, callers must pass the full merged state.
     */
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

    /**
     * Wipes the cache entirely. Called on full sign-out: the last-known car GPS,
     * lock/charge state, and reverse-geocoded place names are account-derived
     * telemetry that must not survive logout (they persist as plaintext JSON and
     * are re-loaded into the UI on the next cold start otherwise). Removing the key
     * makes the next [load] fall back to an all-empty [CachePayload].
     */
    suspend fun clear() {
        context.statusCacheStore.edit { it.remove(key) }
    }
}
