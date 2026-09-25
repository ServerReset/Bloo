package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * One EV charging station near a point, normalised from Open Charge Map's response.
 * [maxKw] is the fastest connector this station offers (null if the API reported no
 * power figures for any of them) -- the map/filter UI treats a station as meeting a
 * "50kW+" filter, say, if ANY of its plugs can deliver that, not only if all of them
 * can.
 */
data class ChargerStation(
    val id: Int,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    /** The charging network operating this station (e.g. "Tesla", "ChargePoint
     *  Network", "EVgo Network"), or null if Open Charge Map has no operator on
     *  file for it. Whatever OCM's own community data calls it -- this app does
     *  not maintain its own list of networks/brands to normalise against. */
    val network: String?,
    val maxKw: Double?,
    /** Distinct connector type names (e.g. "CCS (Type 1)", "CHAdeMO", "Tesla
     *  (NACS)"), in whatever order Open Charge Map returned them. */
    val connectorTypes: List<String>,
    /** False only when OCM explicitly reports this station as non-operational
     *  (temporarily out of service, planned, or removed) -- an unknown status
     *  defaults to true, since most POIs simply don't carry one and hiding them
     *  by default would make the map look far sparser than it actually is. */
    val operational: Boolean,
)

/** User-set narrowing for which fetched [ChargerStation]s actually show on the map.
 *  [minKw] of 0 means "any speed"; an empty [networks] means "any network" -- both
 *  are the natural "no filter applied yet" defaults. */
data class ChargerFilters(
    val minKw: Int = 0,
    val networks: Set<String> = emptySet(),
)

/** Whether this station passes [filters]. A station with no [ChargerStation.maxKw]
 *  on file fails any real minimum-speed filter (there's no basis to claim it meets
 *  one), but always passes the "any speed" (0) filter -- same reasoning for
 *  [ChargerStation.network] against a non-empty network filter. */
fun ChargerStation.matches(filters: ChargerFilters): Boolean {
    if (filters.minKw > 0 && (maxKw == null || maxKw < filters.minKw)) return false
    if (filters.networks.isNotEmpty() && (network == null || network !in filters.networks)) return false
    return true
}

/**
 * Nearby EV charging stations from ChargingNear.me (https://chargingnear.me), a
 * free charger database with excellent US coverage and clear API key management.
 * The API is simple, responsive, and well-documented.
 *
 * Requires an API key (Bearer token in Authorization header). Users can get one
 * instantly at https://chargingnear.me/developers by creating a free account.
 * Free tier: 100 requests/day. Paid tier: $99/month for 5,000 requests/day.
 *
 * This is deliberately NOT something this app curates itself -- a charger database
 * is exactly the kind of specialized dataset a dedicated third party does well
 * (keeping current as stations open, close or change networks). Callers pass
 * the user's API key from Settings (null/blank = error message directing to
 * settings, since the API requires authentication).
 */
object ChargerApi {

    // ignoreUnknownKeys so OCM adding response fields later doesn't break parsing;
    // isLenient for the same minor-JSON-quirk tolerance WeatherApi's own client uses.
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // A dedicated client, generous-but-bounded timeouts -- same reasoning as
    // WeatherApi's own: nearby chargers are a nice-to-have layer on the map, so a
    // flaky connection gets a real chance rather than failing fast, but a hung
    // request still can't block the caller indefinitely.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Serializable
    private data class Station(
        val id: Int = 0,
        val name: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val network: String? = null,
        val connectorTypes: List<String>? = null,
        val maxPower: Double? = null,
        val isOperational: Boolean? = null,
    )

    @Serializable
    private data class StationsResponse(
        val data: List<Station>? = null,
    )

    private fun Station.toChargerStation(): ChargerStation? {
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        return ChargerStation(
            id = id,
            name = name?.takeIf { it.isNotBlank() } ?: "Charging station",
            latitude = lat,
            longitude = lon,
            network = network?.takeIf { it.isNotBlank() },
            maxKw = maxPower,
            connectorTypes = connectorTypes?.filter { it.isNotBlank() }?.distinct() ?: emptyList(),
            operational = isOperational ?: true,
        )
    }

    /**
     * Fetch stations within ~25 miles of [lat]/[lon] via ChargingNear.me API.
     * Returns null on any actual failure (network/IO exception, a non-2xx response
     * including 401 for invalid/missing [apiKey], or malformed JSON), and only
     * an empty list for a genuine "search worked, nothing nearby" result.
     *
     * This distinction matters: a bare auth failure should show "add API key in
     * Settings", not "0 chargers nearby". Earlier versions collapsed both to empty
     * and hid the real problem.
     *
     * [apiKey] is required; ChargingNear.me's API requires Bearer auth. Free tier:
     * 100 requests/day (sufficient for typical usage). Get a key at
     * https://chargingnear.me/developers
     */
    suspend fun nearby(
        lat: Double,
        lon: Double,
        apiKey: String?,
        radiusMiles: Double = 25.0,
        maxResults: Int = 150,
    ): List<ChargerStation>? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://api.chargingnear.me/v1/stations/nearest" +
                    "?latitude=$lat&longitude=$lon&limit=$maxResults"
                val builder = Request.Builder()
                    .url(url)
                    .header("User-Agent", MapTiles.userAgent("Android"))
                if (!apiKey.isNullOrBlank()) {
                    builder.header("Authorization", "Bearer $apiKey")
                }
                client.newCall(builder.get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string() ?: return@use null
                    val response = json.decodeFromString(StationsResponse.serializer(), body)
                    response.data?.mapNotNull { it.toChargerStation() } ?: emptyList()
                }
            }.getOrNull()
        }
}
