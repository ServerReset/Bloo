package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

/** The EU service's climate and charge commands and the vehicle list, as extensions of [EuApi], kept out of the class so it stays readable. */

// --- Vehicles ------------------------------------------------------------

suspend fun EuApi.vehicles(session: EuSession): List<EuVehicleSummary> = withContext(Dispatchers.IO) {
    val req = Request.Builder().url(spa + "vehicles").get().authHeaders(session, 0).build()
    val list = call(req).path("resMsg", "vehicles") as? JsonArray ?: JsonArray(emptyList())
    list.mapNotNull { e ->
        val o = e.obj() ?: return@mapNotNull null
        val id = o["vehicleId"]?.str() ?: return@mapNotNull null
        val type = o["type"]?.str()?.uppercase(Locale.US)
        EuVehicleSummary(
            id = id,
            name = o["nickname"]?.str() ?: o["vehicleName"]?.str() ?: id.takeLast(6),
            model = o["vehicleName"]?.str() ?: "Car",
            vin = o["vin"]?.str() ?: id,
            isEv = type == "EV" || type == "PHEV" || type == "PE",
            ccs2 = o["ccuCCS2ProtocolSupport"]?.intLoose() ?: 0,
        )
    }
}

suspend fun EuApi.stopClimate(session: EuSession, v: EuVehicleSummary, controlToken: String) =

/**
 * Start climate / pre-conditioning. Temperature arrives as Fahrenheit
 * ([ClimateRequest.tempF]) and is sent as a Celsius half-degree. Body shape
 * from ApiImplType1's ccs2 temperature start.
 *
 * The seat states carry the user's actual settings now; they were pinned to
 * 0 (off), so a European owner could set seat heat in the app and the car
 * would never receive it. The encoding is [SeatLevel.apiValue] -- the same
 * 0 / 3-5 cool / 6-8 heat scale BlueLinkApi already posts as
 * `drvSeatHeatState` -- and the payload's SHAPE is unchanged, which is what
 * keeps this low-risk: every key here was already being sent and verified
 * against a live car, only the values were fixed at zero. If EU climate
 * starts failing, this pair of lines is the thing to put back.
 *
 * `drvSeatLoc` and the driver/passenger mapping are derived together from
 * [deviceDriveSide], because they have to agree: the payload names the two
 * front seats by ROLE while Bloo names them by SIDE, so on a right-hand-drive
 * car the driver's seat is the front RIGHT one. Sending "L" while mapping the
 * driver to the left seat is self-consistent and was correct for every market
 * Bloo supported before Europe; sending it to a car in Britain would put the
 * driver's heat setting on the empty passenger seat.
 */
suspend fun EuApi.startClimate(
    session: EuSession, v: EuVehicleSummary, controlToken: String, req: ClimateRequest,
) {
    val celsius = Math.round((req.tempF - 32) * 5.0 / 9.0 * 2) / 2.0
    val driveSide = deviceDriveSide()
    val driverSeat =
        if (driveSide == DriveSide.RIGHT) req.seatFrontRight else req.seatFrontLeft
    val passengerSeat =
        if (driveSide == DriveSide.RIGHT) req.seatFrontLeft else req.seatFrontRight
    val cmd = buildJsonObject {
        put("command", "start")
        put("ignitionDuration", req.durationMinutes)
        put("strgWhlHeating", if (req.steeringWheelHeat.isOn) 1 else 0)
        put("hvacTempType", 1)
        put("hvacTemp", celsius)
        put("sideRearMirrorHeating", 0)
        put("drvSeatLoc", driveSide.ccs2Code)
        put("seatClimateInfo", buildJsonObject {
            // Front pair by ROLE, so it flips with the drive side. The rear
            // pair is named by side in the payload too (rl/rr), so those map
            // straight across and never swap.
            put("drvSeatClimateState", driverSeat.apiValue)
            put("psgSeatClimateState", passengerSeat.apiValue)
            put("rrSeatClimateState", req.seatRearRight.apiValue)
            put("rlSeatClimateState", req.seatRearLeft.apiValue)
        })
        put("tempUnit", "C")
        put("windshieldFrontDefogState", req.defrost)
    }
    control(session, v, controlToken, "temperature", cmd)
}

/** Set AC (plugType 1) and DC (plugType 0) charge target SOC percentages, via
 *  the v1 `.../charge/target` endpoint. Unlike lock/climate this authenticates
 *  with the plain access token (NOT the PIN control token) — the reference's
 *  set_charge_limits uses the authenticated headers, and the control token 403s. */
suspend fun EuApi.setChargeTargets(
    session: EuSession, v: EuVehicleSummary, acPercent: Int, dcPercent: Int,
) = withContext(Dispatchers.IO) {
    val body = buildJsonObject {
        put("targetSOClist", buildJsonArray {
            add(buildJsonObject { put("plugType", 0); put("targetSOClevel", dcPercent) })
            add(buildJsonObject { put("plugType", 1); put("targetSOClevel", acPercent) })
        })
    }.toString().toRequestBody(jsonMedia)
    val req = Request.Builder().url(spa + "vehicles/${v.id}/charge/target")
        .post(body).authHeaders(session, v.ccs2).build()
    call(req)
    Unit
}
