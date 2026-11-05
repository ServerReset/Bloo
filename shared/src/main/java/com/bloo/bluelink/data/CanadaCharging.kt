package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
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
