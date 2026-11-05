package com.bloo.bluelink.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The US Blue Link API's bigger commands, as extensions of [BlueLinkApi], kept out of the class so
 * it stays readable.
 */

/** Start climate / remote start. Temperature is Fahrenheit for US vehicles. */
suspend fun BlueLinkApi.startClimate(
    token: String, username: String, pin: String, v: Vehicle, req: ClimateRequest,
): String = execute {
    // Body shapes mirror the community hyundai_kia_connect_api exactly. For EVs the accepted body
    // is minimal; seat-heat + duration are only honoured on generation-3 cars, and Ims/username/vin
    // are ICE-only fields.
    fun seatInfo() = buildJsonObject {
        put("drvSeatHeatState", JsonPrimitive(req.seatFrontLeft.apiValue))
        put("astSeatHeatState", JsonPrimitive(req.seatFrontRight.apiValue))
        put("rlSeatHeatState", JsonPrimitive(req.seatRearLeft.apiValue))
        put("rrSeatHeatState", JsonPrimitive(req.seatRearRight.apiValue))
    }
    val isEv = v.isEv
    val gen3 = v.generation.trim() == "3"
    val path = if (isEv) "/ac/v2/evc/fatc/start" else "/ac/v2/rcs/rsc/start"
    val payload = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            if (isEv) {
                put("airCtrl", JsonPrimitive(1))
                put("airTemp", buildJsonObject {
                    put("value", JsonPrimitive(req.tempF.toString()))
                    put("unit", JsonPrimitive(1))
                })
                put(CarCommandIds.DEFROST, JsonPrimitive(req.defrost))
                // heating1 is a bundle-selector, not a plain boolean: the real values are 0 (all
                // off), 2 (rear window + mirrors only), 3 (steering wheel only) and 4 (steering
                // wheel + rear window + mirrors) -- confirmed across multiple independent
                // reverse-engineering efforts (bluelinky issues #139/#230,
                // hyundai_kia_connect_api's own documented heating1 fix).
                put("heating1", JsonPrimitive(if (req.steeringWheelHeat.isOn) 3 else 0))
                // Older (gen-3) EVs additionally accept duration + seat heat.
                if (gen3) {
                    put("igniOnDuration", JsonPrimitive(req.durationMinutes))
                    put("seatHeaterVentInfo", seatInfo())
                }
            } else {
                put("Ims", JsonPrimitive(0))
                put("airCtrl", JsonPrimitive(1))
                put("airTemp", buildJsonObject {
                    put("unit", JsonPrimitive(1))
                    put("value", JsonPrimitive(req.tempF.toString()))
                })
                put(CarCommandIds.DEFROST, JsonPrimitive(req.defrost))
                // heating1 is a bundle-selector, not a plain boolean: the real values are 0 (all
                // off), 2 (rear window + mirrors only), 3 (steering wheel only) and 4 (steering
                // wheel + rear window + mirrors) -- confirmed across multiple independent
                // reverse-engineering efforts (bluelinky issues #139/#230,
                // hyundai_kia_connect_api's own documented heating1 fix).
                put("heating1", JsonPrimitive(if (req.steeringWheelHeat.isOn) 3 else 0))
                put("igniOnDuration", JsonPrimitive(req.durationMinutes))
                put("seatHeaterVentInfo", seatInfo())
                put("username", JsonPrimitive(username))
                put("vin", JsonPrimitive(v.vin))
            }
        }
    ).toRequestBody(jsonMedia)

    val request = baseRequest(path, token, username, pin, v)
        .post(payload)
        .build()
    // One short retry clears the occasional transient 502 without bothering the user.
    callWithRetry(request)
}

/**
 * Set EV charge target SOC for AC (plugType 1) and DC (plugType 0) in percent. Mechanism: both
 * targets are always sent together in one call - the API's targetsoc/set endpoint takes the full
 * list, so there's no way to update just one plug type's target without also re-sending the other's
 * current value.
 */
suspend fun BlueLinkApi.setChargeTargets(
    token: String, username: String, pin: String, v: Vehicle, acPercent: Int, dcPercent: Int,
): String = execute {
    val payload = json.encodeToString(
        JsonObject.serializer(),
        buildJsonObject {
            put("targetSOClist", buildJsonArray {
                add(buildJsonObject {
                    put("plugType", JsonPrimitive(0))
                    put("targetSOClevel", JsonPrimitive(dcPercent))
                })
                add(buildJsonObject {
                    put("plugType", JsonPrimitive(1))
                    put("targetSOClevel", JsonPrimitive(acPercent))
                })
            })
        }
    ).toRequestBody(jsonMedia)

    val request = baseRequest("/ac/v2/evc/charge/targetsoc/set", token, username, pin, v)
        .post(payload)
        .build()
    call(request)
}

// --- Surround view (US): the car's latest composite, and a request for a fresh capture. Reference client: svm/getSVMDetails and
// svm/findMyCarSVM. The capture takes a while; BlueLinkRepository polls the latest composite until its time changes.

/** The car's latest surround-view composite and when it was taken, or null when Bluelink has none. */
suspend fun BlueLinkApi.svmLatest(token: String, username: String, pin: String, v: Vehicle): SurroundCapture? = execute {
    val request = baseRequest("/ac/v2/svm/getSVMDetails", token, username, pin, v).get().build()
    val root = BlooJson.parseToJsonElement(call(request)).jsonObject
    val detail = root["svmDetails"]?.jsonArray?.firstOrNull()?.jsonObject?.get("svmDetail")?.jsonObject
        ?: return@execute null
    val image = detail["svmImage"]?.jsonPrimitive?.contentOrNull ?: return@execute null
    val jpeg = runCatching { java.util.Base64.getMimeDecoder().decode(image) }.getOrNull() ?: return@execute null
    SurroundCapture(
        utcTime = detail["gpsDetail"]?.jsonObject?.get("time")?.jsonPrimitive?.contentOrNull,
        jpeg = jpeg,
        imageSize = emptyList(),
    )
}

/** Asks the car for a fresh surround-view capture (svm/findMyCarSVM). Returns when Bluelink accepts the request. */
suspend fun BlueLinkApi.svmRequest(token: String, username: String, pin: String, v: Vehicle): Unit = execute {
    val body = buildJsonObject {
        put("vin", JsonPrimitive(v.vin))
        put("username", JsonPrimitive(username))
        put("gen", JsonPrimitive(v.generation))
        put("blueLinkServicePin", JsonPrimitive(pin))
    }.toString().toRequestBody(jsonMedia)
    val request = baseRequest("/ac/v2/svm/findMyCarSVM", token, username, pin, v).post(body).build()
    call(request)
    Unit
}
