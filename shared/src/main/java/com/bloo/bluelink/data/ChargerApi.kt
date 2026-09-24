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
 * Nearby EV charging stations from Open Charge Map (https://openchargemap.org), a
 * free, community-maintained global charger database -- deliberately NOT something
 * this app curates or maintains itself, the same reasoning [WeatherApi]'s own doc
 * gives for Open-Meteo: a nearby-charger list is exactly the kind of thing a
 * dedicated third party already does well (and keeps current as stations open, close
 * or change networks) that this app has no business trying to replicate.
 *
 * NOT key-less, unlike [WeatherApi]/[MapTiles] -- OCM now requires a per-caller API
 * key on every `/poi` request (a query param or an `X-API-Key` header), confirmed
 * against its own OpenAPI spec after an early version of this client shipped without
 * one and every search silently came back empty (a bare 401, previously swallowed by
 * collapsing every failure to an empty list -- see [nearby]'s own doc for why that
 * shape changed). This app has no business embedding ONE key for every install to
 * share, either -- OCM's own rate limits are per key, and a single shared key split
 * across however many people run this app would starve fast. Callers pass whatever
 * the user entered in Settings (null/blank = try anonymously anyway, since OCM's own
 * docs don't say a keyless request is hard-rejected everywhere, just that a key is
 * how you get a real per-caller rate limit).
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
    private data class Poi(
        val ID: Int = 0,
        val AddressInfo: AddressInfo? = null,
        val OperatorInfo: OperatorInfo? = null,
        val Connections: List<Connection>? = null,
        val StatusType: StatusType? = null,
    )

    @Serializable
    private data class AddressInfo(
        val Title: String? = null,
        val Latitude: Double? = null,
        val Longitude: Double? = null,
    )

    @Serializable
    private data class OperatorInfo(val Title: String? = null)

    @Serializable
    private data class Connection(
        val PowerKW: Double? = null,
        val ConnectionType: ConnectionType? = null,
    )

    @Serializable
    private data class ConnectionType(val Title: String? = null)

    @Serializable
    private data class StatusType(val IsOperational: Boolean? = null)

    private fun Poi.toStation(): ChargerStation? {
        val lat = AddressInfo?.Latitude ?: return null
        val lon = AddressInfo.Longitude ?: return null
        val connections = Connections.orEmpty()
        return ChargerStation(
            id = ID,
            name = AddressInfo.Title?.takeIf { it.isNotBlank() } ?: "Charging station",
            latitude = lat,
            longitude = lon,
            network = OperatorInfo?.Title?.takeIf { it.isNotBlank() },
            maxKw = connections.mapNotNull { it.PowerKW }.maxOrNull(),
            connectorTypes = connections.mapNotNull { it.ConnectionType?.Title?.takeIf { t -> t.isNotBlank() } }.distinct(),
            operational = StatusType?.IsOperational ?: true,
        )
    }

    /**
     * Fetch stations within [radiusMiles] of [lat]/[lon]. Returns null on any actual
     * failure (network/IO exception, a non-2xx response -- including the 401 an
     * invalid/missing [apiKey] gets -- or malformed JSON), and only ever an empty
     * list for a genuine "the search worked, nothing is nearby" result. This is
     * DELIBERATELY not the same collapse-everything-to-one-result shape
     * [WeatherApi.fetch] uses: weather silently falling back to "no reading" is a
     * minor, low-stakes UI gap, but a bare auth failure collapsing to the same empty
     * list a real zero-result search returns is exactly the bug an earlier version
     * of this function had -- "0 chargers nearby" shown for a dense urban search,
     * reported directly, that was actually a silently-swallowed 401 for lack of an
     * API key. Callers can now tell the two apart and say so.
     *
     * `compact=false` (keeps the OperatorInfo/ConnectionType reference objects this
     * needs for network/connector names, instead of OCM's default of collapsing them
     * to bare IDs) and `verbose=false` (drops comments/media/user-submission metadata
     * this has no use for) together keep the response small without losing anything
     * the filter UI or the map pin actually reads.
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
                val url = "https://api.openchargemap.io/v3/poi/" +
                    "?output=json&latitude=$lat&longitude=$lon" +
                    "&distance=$radiusMiles&distanceunit=Miles" +
                    "&maxresults=$maxResults&compact=false&verbose=false"
                val builder = Request.Builder()
                    .url(url)
                    .header("User-Agent", MapTiles.userAgent("Android"))
                if (!apiKey.isNullOrBlank()) builder.header("X-API-Key", apiKey)
                client.newCall(builder.get().build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val body = resp.body?.string() ?: return@use null
                    json.decodeFromString(ListSerializer(Poi.serializer()), body).mapNotNull { it.toStation() }
                }
            }.getOrNull()
        }
}
