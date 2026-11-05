package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
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
