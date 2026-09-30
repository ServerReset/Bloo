package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
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

/** Outcome of a charger search. Kept distinct on purpose: "no key", "key rejected" and "couldn't
 *  reach the service" each need a different message and a different fix, and none of them is the
 *  same thing as "the search worked and nothing is nearby" ([Found] with an empty list). */
sealed interface ChargerFetch {
    data class Found(val stations: List<ChargerStation>) : ChargerFetch
    /** No API key configured; nothing was sent. */
    data object MissingKey : ChargerFetch
    /** Open Charge Map answered 401/403: the key is wrong, revoked or not yet active. */
    data object InvalidKey : ChargerFetch
    /** Network failure, a non-2xx response, or a body that would not parse. */
    data class Failed(val detail: String) : ChargerFetch
}

/**
 * Nearby EV charging stations from Open Charge Map (https://openchargemap.org), the open,
 * community-maintained charger database. Endpoint and fields per its API reference at
 * https://openchargemap.org/site/develop/api .
 *
 * A free API key is required for every request; it is sent in the `X-API-Key` header (the
 * documented alternative to a `key=` query parameter, which would leak into logs and URLs).
 * The user pastes their own key in Settings -> Map & Navigation.
 *
 * This is deliberately NOT something this app curates itself -- a charger database is exactly
 * the dataset a dedicated third party keeps current as stations open, close or change networks.
 */
object ChargerApi {

    // ignoreUnknownKeys: OCM responses carry dozens of fields we never read, and it adds more.
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Generous-but-bounded timeouts: nearby chargers are a nice-to-have layer on the map, so a
    // flaky connection gets a real chance, but a hung request still cannot block the caller.
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val ENDPOINT = "https://api.openchargemap.io/v3/poi/"

    // OCM's placeholder operator (ID 44) -- not a network worth offering as a filter chip.
    private const val UNKNOWN_OPERATOR = "(Unknown Operator)"

    @Serializable
    private data class Poi(
        @SerialName("ID") val id: Int = 0,
        @SerialName("AddressInfo") val address: Address? = null,
        @SerialName("OperatorInfo") val operator: Operator? = null,
        @SerialName("StatusType") val status: Status? = null,
        @SerialName("Connections") val connections: List<Connection>? = null,
    )

    @Serializable
    private data class Address(
        @SerialName("Title") val title: String? = null,
        @SerialName("Latitude") val latitude: Double? = null,
        @SerialName("Longitude") val longitude: Double? = null,
    )

    @Serializable
    private data class Operator(@SerialName("Title") val title: String? = null)

    @Serializable
    private data class Status(@SerialName("IsOperational") val isOperational: Boolean? = null)

    @Serializable
    private data class Connection(
        @SerialName("ConnectionType") val type: ConnectionType? = null,
        @SerialName("PowerKW") val powerKw: Double? = null,
    )

    @Serializable
    private data class ConnectionType(@SerialName("Title") val title: String? = null)

    private fun Poi.toChargerStation(): ChargerStation? {
        val addr = address ?: return null
        val lat = addr.latitude ?: return null
        val lon = addr.longitude ?: return null
        val conns = connections.orEmpty()
        return ChargerStation(
            id = id,
            name = addr.title?.takeIf { it.isNotBlank() } ?: "Charging station",
            latitude = lat,
            longitude = lon,
            network = operator?.title?.takeIf { it.isNotBlank() && it != UNKNOWN_OPERATOR },
            maxKw = conns.mapNotNull { it.powerKw?.takeIf { kw -> kw > 0 } }.maxOrNull(),
            connectorTypes = conns.mapNotNull { it.type?.title?.takeIf { t -> t.isNotBlank() } }.distinct(),
            // Only an explicit "not operational" hides a station; most POIs carry no status.
            operational = status?.isOperational != false,
        )
    }

    /** Parses an Open Charge Map `/v3/poi/` response body. Pure, so it is unit-tested. */
    internal fun parseStations(body: String): List<ChargerStation> =
        json.decodeFromString(ListSerializer(Poi.serializer()), body).mapNotNull { it.toChargerStation() }

    /** The request URL for a search; the key is deliberately not part of it. */
    internal fun searchUrl(lat: Double, lon: Double, radiusMiles: Double, maxResults: Int): String =
        "$ENDPOINT?output=json&latitude=$lat&longitude=$lon" +
            "&distance=$radiusMiles&distanceunit=Miles&maxresults=$maxResults&verbose=false"

    /**
     * Stations within [radiusMiles] of [lat]/[lon], nearest first. See [ChargerFetch] for why the
     * failure modes are separate from an empty result.
     */
    suspend fun search(
        lat: Double,
        lon: Double,
        apiKey: String?,
        radiusMiles: Double = 25.0,
        maxResults: Int = 150,
    ): ChargerFetch {
        val key = apiKey?.trim().orEmpty()
        if (key.isEmpty()) return ChargerFetch.MissingKey
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(searchUrl(lat, lon, radiusMiles, maxResults))
                    .header("X-API-Key", key)
                    .header("User-Agent", MapTiles.userAgent("Android"))
                    .get()
                    .build()
                client.newCall(request).execute().use { resp ->
                    when {
                        resp.code == 401 || resp.code == 403 -> ChargerFetch.InvalidKey
                        !resp.isSuccessful -> ChargerFetch.Failed("HTTP ${resp.code}")
                        else -> {
                            val body = resp.body?.string() ?: return@use ChargerFetch.Failed("empty response")
                            ChargerFetch.Found(parseStations(body))
                        }
                    }
                }
            }.getOrElse { ChargerFetch.Failed(it.javaClass.simpleName) }
        }
    }
}
