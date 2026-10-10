package com.bloo.bluelink.data

import com.bloo.bluelink.data.KiaUsaApi.Companion.API
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The Kia US API's bigger operations, as extensions of [KiaUsaApi], kept out of the class so it stays readable. */

/** Step 2b: verify the code and finish login, producing a session. */
suspend fun KiaUsaApi.verifyOtpAndComplete(
    username: String, password: String, otpCode: String,
    otpKey: String, xid: String, deviceId: String, pin: String?,
): KiaSession = withContext(Dispatchers.IO) {
    // Verify the code -> sid + rmtoken.
    val verifyReq = Request.Builder().url(API + "cmm/verifyOTP")
        .post(buildJsonObject { put("otp", otpCode) }.toString().toRequestBody(jsonMedia))
        .apiHeaders(deviceId).header("otpkey", otpKey).header("xid", xid)
        .build()
    val (interimSid, rmtoken) = raw(verifyReq).use { resp ->
        val sid = resp.header("sid")
        val rm = resp.header("rmtoken")
        if (sid == null || rm == null) throw BlueLinkException("Invalid code — please try again.", code = resp.code)
        sid to rm
    }
    // Exchange for the final session id.
    val finishReq = Request.Builder().url(API + "prof/authUser")
        .post(
            buildJsonObject {
                put("deviceKey", deviceId)
                put("deviceType", 2)
                put("userCredential", buildJsonObject { put("userId", username); put("password", password) })
            }.toString().toRequestBody(jsonMedia),
        )
        .apiHeaders(deviceId).header("sid", interimSid).header("rmtoken", rmtoken)
        .build()
    // The finish exchange can itself rotate the rmtoken; prefer a freshly-
    // issued one over the verifyOTP token when the server sends it, so a
    // server-side rotation gets persisted (mirrors authUser's silent
    // re-auth handling).
    val (finalSid, finalRmtoken) = raw(finishReq).use { resp ->
        val sid = resp.header("sid")
            ?: throw BlueLinkException(friendly(resp.code, resp.body.string()), code = resp.code)
        sid to (resp.header("rmtoken") ?: rmtoken)
    }
    KiaSession(finalSid, finalRmtoken, deviceId, pin)
}

/**
 * Map Kia's cmm/gvi payload (vehicleInfoList[0]) onto our shared
 * [VehicleStatus]. Field paths follow the community hyundai_kia_connect_api
 * (KiaUvoApiUSA._update_vehicle_properties).
 */
internal fun KiaUsaApi.parseStatus(info: JsonObject): VehicleStatus {
    val vs = info.path("lastVehicleInfo", "vehicleStatusRpt", "vehicleStatus")
    val climate = vs.path("climate")
    val heat = climate.path("heatingAccessory")
    val doors = vs.path("doorStatus")
    val seats = vs.path("seatHeaterVentState")
    val ev = vs.path("evStatus")
    val location = info.path("lastVehicleInfo", "location")
    val lat = location.path("coord", "lat").dbl()
    val lon = location.path("coord", "lon").dbl()

    // Windows: ICE cars report under windowOpen, EVs under evStatus.windowStatus.
    fun window(key: String, evKey: String): Int? =
        vs.path("windowOpen", key).int() ?: ev.path("windowStatus", evKey).int()

    val evStatus = if (ev == null) null else EvStatus(
        batteryCharge = ev.path("batteryCharge").flag(),
        batteryStatus = ev.path("batteryStatus").int(),
        batteryPlugin = ev.path("batteryPlugin").int(),
        drvDistance = run {
            val range = ev.path("drvDistance", "0", "rangeByFuel", "totalAvailableRange")
                ?: ev.path("drvDistance", "0", "rangeByFuel", "evModeRange")
            range.path("value").dbl()
                ?.let { listOf(DrvDistance(RangeByFuel(Dte(it, range.path("unit").int())))) }
                ?: emptyList()
        },
        remainTime2 = RemainTime2(
            // Current-plug estimate, then AC/DC estimates, all in minutes.
            atc = ev.path("remainChargeTime", "0", "timeInterval", "value").dbl()?.let { TimeValue(it, 1) },
            etc1 = ev.path("remainChargeTime", "0", "etc1", "value").dbl()?.let { TimeValue(it, 1) },
            etc3 = ev.path("remainChargeTime", "0", "etc3", "value").dbl()?.let { TimeValue(it, 1) },
        ).takeIf { it.atc != null || it.etc1 != null || it.etc3 != null },
    )

    return VehicleStatus(
        doorLock = vs.path("doorLock").flag(),
        airCtrlOn = climate.path("airCtrl").flag(),
        engine = vs.path("engine").flag(),
        defrost = climate.path("defrost").flag(),
        hoodOpen = doors.path("hood").flag(),
        trunkOpen = doors.path("trunk").flag(),
        doorOpen = DoorOpen(
            frontLeft = doors.path("frontLeft").int(),
            frontRight = doors.path("frontRight").int(),
            backLeft = doors.path("backLeft").int(),
            backRight = doors.path("backRight").int(),
        ),
        windowOpen = WindowOpen(
            frontLeft = window("frontLeft", "windowFL"),
            frontRight = window("frontRight", "windowFR"),
            backLeft = window("backLeft", "windowRL"),
            backRight = window("backRight", "windowRR"),
        ),
        tirePressureLamp = vs.path("tirePressure", "all").int()?.let {
            TirePressureLamp(tirePressureLampAll = it)
        },
        airTemp = climate.path("airTemp", "value").str()?.let {
            TempValue(it, climate.path("airTemp", "unit").int())
        },
        battery = vs.path("batteryStatus", "stateOfCharge").int()?.let { Battery12V(batSoc = it) },
        steerWheelHeat = heat.path("steeringWheel").int(),
        sideBackWindowHeat = heat.path("rearWindow").int(),
        sideMirrorHeat = heat.path("sideMirror").int(),
        seatHeaterVentState = if (seats == null) null else SeatHeaterVentState(
            flSeatHeatState = seats.path("flSeatHeatState").int(),
            frSeatHeatState = seats.path("frSeatHeatState").int(),
            rlSeatHeatState = seats.path("rlSeatHeatState").int(),
            rrSeatHeatState = seats.path("rrSeatHeatState").int(),
        ),
        washerFluidStatus = vs.path("washerFluidStatus").flag(),
        breakOilStatus = vs.path("breakOilStatus").flag(),
        smartKeyBatteryWarning = vs.path("smartKeyBatteryWarning").flag(),
        fuelLevel = vs.path("fuelLevel").int(),
        dte = vs.path("distanceToEmpty", "value").dbl()?.let {
            Dte(it, vs.path("distanceToEmpty", "unit").int())
        },
        dateTime = vs.path("syncDate", "utc").str(),
        evStatus = evStatus,
        vehicleLocation = if (lat != null && lon != null) {
            VehicleLocation(coord = Coord(lat, lon), time = location.path("syncDate", "utc").str())
        } else null,
    )
}

/** Start climate / remote start. Mechanism: Kia's API represents the two
 *  ends of the temperature range as the literal strings "LOW"/"HIGH"
 *  rather than accepting a numeric value outside 62-82°F, so any
 *  requested temp beyond that range gets mapped to the matching sentinel
 *  string instead of the number itself; seat heat/vent settings are only
 *  included in the body at all when at least one seat isn't OFF
 *  ([anySeat]), keeping the payload minimal when the user hasn't touched
 *  seat controls. */
suspend fun KiaUsaApi.startClimate(session: KiaSession, v: KiaVehicleSummary, req: ClimateRequest) = withContext(Dispatchers.IO) {
    val tempValue: String = when {
        req.tempF < 62 -> "LOW"
        req.tempF > 82 -> "HIGH"
        else -> req.tempF.toString()
    }
    val anySeat = listOf(req.seatFrontLeft, req.seatFrontRight, req.seatRearLeft, req.seatRearRight)
        .any { it != SeatLevel.OFF }
    val body = buildJsonObject {
        put("remoteClimate", buildJsonObject {
            put("airTemp", buildJsonObject { put("unit", 1); put("value", tempValue) })
            put("airCtrl", true)
            put("defrost", req.defrost)
            put("heatingAccessory", buildJsonObject {
                put("rearWindow", if (req.defrost) 1 else 0)
                put("sideMirror", if (req.defrost) 1 else 0)
                put("steeringWheel", if (req.steeringWheelHeat.isOn) 1 else 0)
                // Best-guess mapping, unverified against real API docs: mirrors seatSettings'
                // own inverted step scheme just below (lower step number = MORE heat), on the
                // assumption Kia's steering-wheel step follows the same convention as its seat
                // step. Confirmed-real-car feedback said this control genuinely has two
                // distinct heat levels; this is the field that already exists to carry a
                // second one (it was previously hardcoded to 1 regardless of what the app's
                // own toggle showed). If a real device reports Low/High swapped, flip this.
                put(
                    "steeringWheelStep",
                    when (req.steeringWheelHeat) {
                        WheelHeatLevel.HIGH -> 1
                        WheelHeatLevel.LOW -> 2
                        WheelHeatLevel.OFF -> 0
                    },
                )
            })
            put("ignitionOnDuration", buildJsonObject { put("unit", 4); put("value", req.durationMinutes) })
            if (anySeat) {
                put("heatVentSeat", buildJsonObject {
                    put("driverSeat", seatSettings(req.seatFrontLeft.apiValue))
                    put("passengerSeat", seatSettings(req.seatFrontRight.apiValue))
                    put("rearLeftSeat", seatSettings(req.seatRearLeft.apiValue))
                    put("rearRightSeat", seatSettings(req.seatRearRight.apiValue))
                })
            }
        })
    }
    postCommand("rems/start", session, v, body)
}
