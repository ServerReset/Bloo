package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** One set of surround-view photos the car took, with when it took them. Ported from open-blue-android; not verified on a live car. */
data class SurroundCapture(val utcTime: String?, val jpeg: ByteArray, val imageSize: List<Int>)

/**
 * Asks the car to take a set of surround-view photos (rfc/fndmcrsvm), after the car's PIN is checked for a fresh pAuth. Returns the
 * transaction ID Bluelink gives for the request, when it gives one. The car takes the photos some time later; [lastSurroundView] reads them.
 */
suspend fun CanadaApi.requestSurroundView(s: CanadaSession, vehicleId: String, pin: String): String? = withContext(Dispatchers.IO) {
    val pAuth = pinAuth(s, vehicleId, pin)
    val req = Request.Builder().url(apiUrl + "rfc/fndmcrsvm")
        .post(ByteArray(0).toRequestBody(null))
        .commandHeaders(s, vehicleId, pAuth)
        .build()
    client.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) throw BlueLinkException("Bluelink didn't take the surround-view request (HTTP ${resp.code})", code = resp.code)
        resp.header("transactionId")
    }
}

/** The most recent surround-view photos Bluelink has stored for the car (rfc/lastmcrsvm), newest first. */
suspend fun CanadaApi.lastSurroundView(s: CanadaSession, vehicleId: String): List<SurroundCapture> = withContext(Dispatchers.IO) {
    val req = Request.Builder().url(apiUrl + "rfc/lastmcrsvm")
        .post(ByteArray(0).toRequestBody(null))
        .vehicleHeaders(s, vehicleId)
        .build()
    val locations = call(req).path("result", "svmLocations") as? JsonArray ?: JsonArray(emptyList())
    locations.mapNotNull { loc ->
        val image = loc.path("svmImage").str() ?: return@mapNotNull null
        val bytes = runCatching { java.util.Base64.getMimeDecoder().decode(image) }.getOrNull() ?: return@mapNotNull null
        SurroundCapture(
            utcTime = loc.path("utcTime").str(),
            jpeg = bytes,
            imageSize = (loc.path("imageSize") as? JsonArray).orEmpty().mapNotNull { it.intLoose() },
        )
    }.sortedByDescending { it.utcTime.orEmpty() }
}
