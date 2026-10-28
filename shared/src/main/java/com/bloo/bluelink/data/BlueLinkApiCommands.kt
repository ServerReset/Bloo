package com.bloo.bluelink.data

import kotlinx.serialization.json.JsonObject
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
                put("defrost", JsonPrimitive(req.defrost))
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
                put("defrost", JsonPrimitive(req.defrost))
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
