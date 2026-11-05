package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * One charging session paid through the app (iac/orderInfo). Ported from open-blue-android, field names included. Fields the account does
 * not give are null. Not verified against a live Hyundai Canada account.
 */
data class ChargeOrder(
    val id: String?,
    val started: String?,
    val stopped: String?,
    val place: String?,
    val address: String?,
    val kwh: Double?,
    val total: Double?,
    val currency: String?,
    val card: String?,
    val status: String?,
) {
    companion object {
        private fun JsonElement.field(key: String): JsonElement? = (this as? JsonObject)?.get(key)

        fun from(e: JsonElement): ChargeOrder = ChargeOrder(
            id = e.field("ordNo").str() ?: e.field("trId").str(),
            started = e.field("startDateTime").str() ?: e.field("ordDate").str(),
            stopped = e.field("stopDateTime").str(),
            place = e.field("merchantNm").str() ?: e.field("brndNm").str() ?: e.field("ordNm").str(),
            address = listOfNotNull(e.field("addrStreet").str(), e.field("addrCity").str())
                .filter { it.isNotBlank() }.joinToString(", ").ifEmpty { null },
            kwh = e.field("totalEnergy").dbl(),
            total = e.field("totalPrice").dbl() ?: e.field("price").dbl(),
            currency = e.field("curr").str(),
            card = e.field("last4Creditcard").str()?.takeIf { it.isNotBlank() },
            status = e.field("ordStatCd").str(),
        )
    }
}

/**
 * The charging sessions paid through the app for [vehicleId] between [start] and [end] (yyyyMMdd), newest first. [profileId] is the
 * account's username, as the official app sends it.
 */
suspend fun CanadaApi.chargeOrders(
    s: CanadaSession,
    vehicleId: String,
    profileId: String,
    start: String,
    end: String,
): List<ChargeOrder> = withContext(Dispatchers.IO) {
    val body = buildJsonObject {
        put("profileId", profileId)
        put("startDate", start)
        put("endDate", end)
        put("brandIndicator", "H")
    }.toString().toRequestBody(jsonMedia)
    val req = Request.Builder().url(apiUrl + "iac/orderInfo").post(body).vehicleHeaders(s, vehicleId).build()
    val orders = call(req).path("result", "resultData") as? JsonArray ?: JsonArray(emptyList())
    orders.map { ChargeOrder.from(it) }.sortedByDescending { it.started.orEmpty() }
}

/**
 * A public charging station near the car (iac/csList), trimmed to what a list needs. Ported from open-blue-android; the paths are its
 * reader's, not verified against a live account.
 */
data class ChargingStation(
    val id: String,
    val name: String,
    val address: String?,
    val network: String?,
    val lat: Double,
    val lon: Double,
    val distanceKm: Double?,
    val available: Int?,
    val total: Int?,
    val maxKw: Double?,
    /** The chargers at the station, by their IDs; a paid charge is started at one of them. */
    val chargerIds: List<String> = emptyList(),
) {
    companion object {
        fun from(e: JsonElement): ChargingStation? {
            val id = e.path("id").str() ?: e.path("poiId").str() ?: return null
            val lat = e.path("coordinates", "center", "lat").dbl() ?: e.path("coordinates", "guidance", "lat").dbl() ?: return null
            val lon = e.path("coordinates", "center", "lon").dbl() ?: e.path("coordinates", "guidance", "lon").dbl() ?: return null
            val lang = (e.path("languageResources") as? JsonArray)?.let { list ->
                list.firstOrNull { it.path("language").str()?.startsWith("en", true) == true } ?: list.firstOrNull()
            }
            val status = (e.path("status") as? JsonArray).orEmpty()
            fun sum(key: String) = status.mapNotNull { it.path(key).intLoose() }.takeIf { it.isNotEmpty() }?.sum()
            val maxKw = (e.path("chargingPower").dbl())
                ?: ((e.path("chargers") as? JsonArray).orEmpty()).flatMap { c -> (c.path("connectors") as? JsonArray).orEmpty() }
                    .mapNotNull { it.path("chargingPower").dbl() }.maxOrNull()
            return ChargingStation(
                id = id,
                name = lang?.path("data", "name").str() ?: "Charging station",
                address = lang?.path("data", "address").str(),
                network = (lang?.path("data", "providers", "operation") as? JsonArray)?.firstOrNull()
                    ?.let { it.path("name").str() ?: it.path("brandName").str() },
                lat = lat,
                lon = lon,
                distanceKm = e.path("distance").dbl(),
                available = sum("numberOfAvailableConnectors"),
                total = sum("numberOfConnectors"),
                maxKw = maxKw?.takeIf { it > 0 },
                chargerIds = (e.path("chargers") as? JsonArray).orEmpty().mapNotNull { it.path("id").str() },
            )
        }
    }
}

/** Public chargers within [radiusKm] of ([lat], [lon]), nearest first (iac/csList). */
suspend fun CanadaApi.chargingStations(s: CanadaSession, vehicleId: String, lat: Double, lon: Double, radiusKm: Int): List<ChargingStation> =
    withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("country", "CA")
            putJsonObject("coordinate") { put("lat", lat); put("lon", lon) }
            put("radius", radiusKm)
            put("radiusUnit", "km")
            put("language", "en")
            put("size", 100)
            put("page", 0)
        }.toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url(apiUrl + "iac/csList").post(body).vehicleHeaders(s, vehicleId).build()
        val list = call(req).path("result", "chargingStations") as? JsonArray ?: JsonArray(emptyList())
        list.mapNotNull { ChargingStation.from(it) }.sortedBy { it.distanceKm ?: Double.MAX_VALUE }
    }

// --- Paid charging: start and stop a charge at a public charger, and watch it. Money is spent by startCharge, so the app only calls it
// after the person confirms the price on screen. Ported from open-blue-android; not verified against a live account.

/** The price and payment details for one charger, from iac/productInfo. [paymentId] is what the charge is started and stopped with. */
data class ChargeOffer(
    val paymentId: String,
    val productName: String?,
    val priceLines: List<String>,
    val cardId: String?,
    val cardLabel: String?,
)

/** Live numbers for a charge in progress, from iac/chargeStatus. */
data class ChargeStatus(val kwh: Double?, val powerKw: Double?, val cost: Double?, val currency: String?, val charging: Boolean?)

/** The genType the official app sends; upstream reads it from the car's service info and falls back to this value. */
private const val GEN_TYPE = "3"

private fun JsonElement?.firstItem(): JsonElement? = (this as? JsonArray)?.firstOrNull()

/** Price, payment ID and cards for charging at [chargerId] of station [poiId] (iac/productInfo). */
suspend fun CanadaApi.chargeOffer(s: CanadaSession, vehicleId: String, poiId: String, chargerId: String): ChargeOffer =
    withContext(Dispatchers.IO) {
        val product = java.util.Base64.getUrlEncoder().encodeToString(chargerId.toByteArray())
        val body = buildJsonObject {
            putJsonArray("storeInfo") {
                add(buildJsonObject {
                    put("storeId", "CHARGEPOINT")
                    put("poiId", poiId)
                    put("productId", product)
                    put("storeType", "E001")
                })
            }
            put("genType", GEN_TYPE)
        }.toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url(apiUrl + "iac/productInfo").post(body).vehicleHeaders(s, vehicleId).build()
        val r = call(req).path("result") as? JsonObject ?: throw BlueLinkException("Bluelink gave no price for this charger")
        val chargingProduct = r["storeInfo"].firstItem()?.let { (it as? JsonObject)?.get("chargingProductList").firstItem() } as? JsonObject
        val lines = ((chargingProduct?.get("priceElements") as? JsonArray).orEmpty()).flatMap { el ->
            ((el as? JsonObject)?.get("priceComponents") as? JsonArray).orEmpty().mapNotNull { pc ->
                val price = pc.path("price").dbl() ?: return@mapNotNull null
                when (pc.path("type").str()?.uppercase()) {
                    "ENERGY" -> "$%.2f per kWh".format(price)
                    "TIME" -> "$%.2f per hour".format(price)
                    "PARKING_TIME" -> "$%.2f per hour parked".format(price)
                    "FLAT" -> "$%.2f per session".format(price)
                    else -> "$%.2f %s".format(price, pc.path("type").str()?.lowercase().orEmpty())
                }
            }
        }.distinct()
        val card = r["payCardList"].firstItem()
        ChargeOffer(
            paymentId = r["paymentId"].str() ?: throw BlueLinkException("Bluelink gave no payment ID for this charger"),
            productName = chargingProduct?.get("productName").str(),
            priceLines = lines,
            cardId = card.path("cardId").str(),
            cardLabel = listOfNotNull(card.path("cardName").str(), card.path("cardNumber").str()).joinToString(" ").ifBlank { null },
        )
    }

/**
 * Starts a paid charge (iac/startCharge). Returns null when Bluelink accepted it, or the reason it gave when it did not.
 */
suspend fun CanadaApi.startCharge(s: CanadaSession, vehicleId: String, paymentId: String, poiId: String, payCardId: String?): String? =
    withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("paymentId", paymentId)
            put("storeId", "CHARGEPOINT")
            put("poiId", poiId)
            putJsonObject("orderInfo") { put("payCardId", payCardId.orEmpty()) }
            put("tncAgree", true)
            put("genType", GEN_TYPE)
        }.toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url(apiUrl + "iac/startCharge").post(body).vehicleHeaders(s, vehicleId).build()
        val result = call(req).path("result")
        val code = result.path("resultCode").intLoose()
        if (code == null || code == 0) null else result.path("errorCaseId").str() ?: result.path("resultMsg").str() ?: "Bluelink did not start the charge"
    }

/** Stops a paid charge (iac/stopCharge). Returns null when Bluelink accepted it, or the reason it gave when it did not. */
suspend fun CanadaApi.stopCharge(s: CanadaSession, vehicleId: String, paymentId: String, poiId: String): String? =
    withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("paymentId", paymentId)
            put("storeId", "CHARGEPOINT")
            put("poiId", poiId)
            put("genType", GEN_TYPE)
        }.toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url(apiUrl + "iac/stopCharge").post(body).vehicleHeaders(s, vehicleId).build()
        val result = call(req).path("result")
        val code = result.path("resultCode").intLoose()
        if (code == null || code == 0) null else result.path("errorCaseId").str() ?: result.path("resultMsg").str() ?: "Bluelink did not stop the charge"
    }

/** The live numbers for a charge in progress (iac/chargeStatus). */
suspend fun CanadaApi.chargeStatus(s: CanadaSession, vehicleId: String, vin: String, paymentId: String): ChargeStatus =
    withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("vin", vin)
            put("genType", GEN_TYPE)
            put("paymentId", paymentId)
            put("needToRefresh", false)
            put("needBWData", true)
        }.toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url(apiUrl + "iac/chargeStatus").post(body).vehicleHeaders(s, vehicleId).build()
        val r = call(req).path("result")
        ChargeStatus(
            kwh = r.path("bwStatus", "kwh").dbl(),
            powerKw = r.path("bwStatus", "power").dbl(),
            cost = r.path("bwStatus", "totalCost").dbl(),
            currency = r.path("bwStatus", "currency").str(),
            charging = r.path("evStatus", "batteryCharge").bool(),
        )
    }

// --- Trip planning: a route from the car to a destination, with the charging stops it needs (iac/rp). Ported from open-blue-android;
// not verified against a live account. Only the route's stops and totals are read here; the drawn line is not used yet.

/** A charging stop on a planned trip. Times in minutes, distances in metres, charge levels in percent. */
data class TripStop(
    val name: String,
    val network: String?,
    val lat: Double,
    val lon: Double,
    val arriveSoc: Int?,
    val targetSoc: Int?,
    val chargeMinutes: Int?,
    val distanceM: Int?,
    val powerKw: Int?,
)

/** One way to get there: its length, its time, the stops, and whether the car reaches the destination. */
data class TripRoute(
    val distanceM: Int,
    val minutes: Int,
    val stops: List<TripStop>,
    val arriveSoc: Int?,
    val reachable: Boolean,
)

/** What the route planner said. [status] 0 is a plan; anything else failed. */
data class TripPlan(val status: Int, val routes: List<TripRoute>)

/**
 * The vendor number the route planner needs, from iac/serviceInfo. [year] is the model year the account lists for the car. Upstream
 * sends the car's model code too; this sends the model name in its place, which may not match what Bluelink expects.
 */
suspend fun CanadaApi.chargeVendorNum(s: CanadaSession, vehicleId: String, model: String, year: Int?): Int = withContext(Dispatchers.IO) {
    val body = buildJsonObject {
        putJsonObject("vehicleInfo") {
            put("modelCode", model)
            put("modelYear", year?.toString().orEmpty())
            put("brand", "H")
        }
    }.toString().toRequestBody(jsonMedia)
    val req = Request.Builder().url(apiUrl + "iac/serviceInfo").post(body).vehicleHeaders(s, vehicleId).build()
    call(req).path("result", "vendorNum").intLoose() ?: 0
}

/**
 * Plans a route from ([fromLat], [fromLon]) to ([toLat], [toLon]) with charging stops: the car reaches each charger with at least
 * [chargerSoc] percent and the destination with [destinationSoc] percent. Route option 22 is what the official app always asks for.
 */
suspend fun CanadaApi.tripPlan(
    s: CanadaSession,
    vehicleId: String,
    vin: String,
    fromLat: Double,
    fromLon: Double,
    toLat: Double,
    toLon: Double,
    chargerSoc: Int,
    destinationSoc: Int,
    vendorNum: Int,
): TripPlan = withContext(Dispatchers.IO) {
    val body = buildJsonObject {
        put("version", "1.0")
        put("clientType", 6)
        put("id", vin)
        putJsonObject("origin") { put("lat", fromLat); put("lon", fromLon) }
        putJsonObject("destination") {
            putJsonArray("points") { add(buildJsonObject { put("lat", toLat); put("lon", toLon) }) }
        }
        put("evChargingStationCandidates", 0)
        putJsonArray("route") { add(buildJsonObject { put("routeOption", 22) }) }
        putJsonObject("vehicleInfo") {
            put("chargerType", 37)
            put("chargerSpeed", 7)
            put("destinationArrivalSoc", destinationSoc)
            put("chargingStationArrivalSoc", chargerSoc)
            put("genType", GEN_TYPE)
            put("vendorNum", vendorNum)
        }
    }.toString().toRequestBody(jsonMedia)
    val req = Request.Builder().url(apiUrl + "iac/rp").post(body).vehicleHeaders(s, vehicleId).build()
    val result = call(req).path("result")
    val routes = (result.path("route") as? JsonArray).orEmpty().map { rt ->
        TripRoute(
            distanceM = rt.path("totalDistance").intLoose() ?: 0,
            minutes = (rt.path("totalTime").intLoose() ?: 0) / 60,
            stops = (rt.path("evStation") as? JsonArray).orEmpty().mapNotNull { st ->
                val lat = st.path("vertex", "lat").dbl() ?: return@mapNotNull null
                val lon = st.path("vertex", "lon").dbl() ?: return@mapNotNull null
                TripStop(
                    name = st.path("poiName").str() ?: "Charging stop",
                    network = st.path("brandName").str(),
                    lat = lat,
                    lon = lon,
                    arriveSoc = st.path("pointSoc").intLoose()?.takeIf { it in 0..100 },
                    targetSoc = st.path("targetSoc").intLoose()?.takeIf { it in 0..100 },
                    chargeMinutes = st.path("chargingTime").dbl()?.let { (it / 60).toInt() },
                    distanceM = st.path("pointDistance").intLoose(),
                    powerKw = st.path("pointChargerPower").intLoose(),
                )
            },
            // 254 means unknown, as the official app shows it.
            arriveSoc = rt.path("destination", "pointSoc").intLoose()?.takeIf { it in 0..100 },
            reachable = (rt.path("reasonUnReachable").intLoose() ?: 0) == 0,
        )
    }
    TripPlan(status = result.path("status").intLoose() ?: 0, routes = routes)
}
