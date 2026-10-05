package com.bloo.bluelink.data

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
    fun seatInfo() = kotlinx.serialization.json.buildJsonObject {
        put("drvSeatHeatState", kotlinx.serialization.json.JsonPrimitive(req.seatFrontLeft.apiValue))
        put("astSeatHeatState", kotlinx.serialization.json.JsonPrimitive(req.seatFrontRight.apiValue))
        put("rlSeatHeatState", kotlinx.serialization.json.JsonPrimitive(req.seatRearLeft.apiValue))
        put("rrSeatHeatState", kotlinx.serialization.json.JsonPrimitive(req.seatRearRight.apiValue))
    }
    val isEv = v.isEv
    val gen3 = v.generation.trim() == "3"
    val path = if (isEv) "/ac/v2/evc/fatc/start" else "/ac/v2/rcs/rsc/start"
    val payload = json.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        kotlinx.serialization.json.buildJsonObject {
            if (isEv) {
                put("airCtrl", kotlinx.serialization.json.JsonPrimitive(1))
                put("airTemp", kotlinx.serialization.json.buildJsonObject {
                    put("value", kotlinx.serialization.json.JsonPrimitive(req.tempF.toString()))
                    put("unit", kotlinx.serialization.json.JsonPrimitive(1))
                })
                put("defrost", kotlinx.serialization.json.JsonPrimitive(req.defrost))
                // heating1 is a bundle-selector, not a plain boolean: the real values are 0 (all
                // off), 2 (rear window + mirrors only), 3 (steering wheel only) and 4 (steering
                // wheel + rear window + mirrors) -- confirmed across multiple independent
                // reverse-engineering efforts (bluelinky issues #139/#230,
                // hyundai_kia_connect_api's own documented heating1 fix).
                put("heating1", kotlinx.serialization.json.JsonPrimitive(if (req.steeringWheelHeat.isOn) 3 else 0))
                // Older (gen-3) EVs additionally accept duration + seat heat.
                if (gen3) {
                    put("igniOnDuration", kotlinx.serialization.json.JsonPrimitive(req.durationMinutes))
                    put("seatHeaterVentInfo", seatInfo())
                }
            } else {
                put("Ims", kotlinx.serialization.json.JsonPrimitive(0))
                put("airCtrl", kotlinx.serialization.json.JsonPrimitive(1))
                put("airTemp", kotlinx.serialization.json.buildJsonObject {
                    put("unit", kotlinx.serialization.json.JsonPrimitive(1))
                    put("value", kotlinx.serialization.json.JsonPrimitive(req.tempF.toString()))
                })
                put("defrost", kotlinx.serialization.json.JsonPrimitive(req.defrost))
                // heating1 is a bundle-selector, not a plain boolean: the real values are 0 (all
                // off), 2 (rear window + mirrors only), 3 (steering wheel only) and 4 (steering
                // wheel + rear window + mirrors) -- confirmed across multiple independent
                // reverse-engineering efforts (bluelinky issues #139/#230,
                // hyundai_kia_connect_api's own documented heating1 fix).
                put("heating1", kotlinx.serialization.json.JsonPrimitive(if (req.steeringWheelHeat.isOn) 3 else 0))
                put("igniOnDuration", kotlinx.serialization.json.JsonPrimitive(req.durationMinutes))
                put("seatHeaterVentInfo", seatInfo())
                put("username", kotlinx.serialization.json.JsonPrimitive(username))
                put("vin", kotlinx.serialization.json.JsonPrimitive(v.vin))
            }
        }
    ).toRequestBody(jsonMedia)

    val request = baseRequest(path, token, username, pin, v)
        .post(payload)
        .build()
    // One short retry clears the occasional transient 502 without bothering the user.
    callWithRetry(request)
}

/** Set EV charge target SOC for AC (plugType 1) and DC (plugType 0) in percent. */
suspend fun BlueLinkApi.setChargeTargets(
    token: String, username: String, pin: String, v: Vehicle, acPercent: Int, dcPercent: Int,
): String = execute {
    val payload = json.encodeToString(
        kotlinx.serialization.json.JsonObject.serializer(),
        kotlinx.serialization.json.buildJsonObject {
            put("targetSOClist", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.buildJsonObject {
                    put("plugType", kotlinx.serialization.json.JsonPrimitive(0))
                    put("targetSOClevel", kotlinx.serialization.json.JsonPrimitive(dcPercent))
                })
                add(kotlinx.serialization.json.buildJsonObject {
                    put("plugType", kotlinx.serialization.json.JsonPrimitive(1))
                    put("targetSOClevel", kotlinx.serialization.json.JsonPrimitive(acPercent))
                })
            })
        }
    ).toRequestBody(jsonMedia)

    val request = baseRequest("/ac/v2/evc/charge/targetsoc/set", token, username, pin, v)
        .post(payload)
        .build()
    call(request)
}
