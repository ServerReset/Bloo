package com.bloo.bluelink.data

import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The EU service's bigger operations -- sign-in, the vehicle list, climate and charge commands, and
 * turning the CCS2 status tree into a [VehicleStatus] -- as extensions of [EuApi], kept out of the
 * class so it stays readable.
 */

/**
 * Headless IDPConnect sign-in: authorize (seed cookies) -> fetch RSA cert -> RSA-encrypt the
 * password -> POST the sign-in form and read the auth `code` from the 302 redirect -> exchange the
 * code for tokens.
 */
suspend fun EuApi.login(username: String, password: String, deviceId: String, pin: String?): EuSession =
    withContext(Dispatchers.IO) {
        // OneApp/CCI sign-in -- see [loginCci].
        loginCci(username, password, deviceId, pin)
    }

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

/**
 * Maps the CCS2 `state.Vehicle` tree onto the shared [VehicleStatus] model, using the dot-paths the
 * reference's get_child_value reads (confirmed against KiaUvoApiEU/ApiImplType1).
 */
internal fun EuApi.parseStatus(vh: JsonObject): VehicleStatus {
    val green = vh["Green"] as? JsonObject
    val cabin = vh["Cabin"] as? JsonObject
    val body = vh["Body"] as? JsonObject
    val drivetrain = vh["Drivetrain"] as? JsonObject
    val chassis = vh["Chassis"] as? JsonObject
    val electronics = vh["Electronics"] as? JsonObject

    val soc = green.path("BatteryManagement", "BatteryRemain", "Ratio").dbl()?.toInt()
    // NOT ChargingDoor.State -- that is the charge-port DOOR's own open/closed flag, confirmed
    // against the reference this file is ported from (ApiImplType1.py): `charging_door_state in [0,
    // 2] -> door closed, == 1 -> door open`, nothing to do with whether a cable is actually
    // connected.
    val plug = green.path("ChargingInformation", "ConnectorFastening", "State").intLoose()
        ?.let { if (it != 0) 1 else 0 }
    val chargeRemain = green.path("ChargingInformation", "Charging", "RemainTime").dbl()
    val rangeKm = (drivetrain.path("FuelSystem", "DTE", "Total")
        ?: drivetrain.path("FuelSystem", "DTE", "EV")).dbl()

    val evStatus = if (green == null) null else EvStatus(
        batteryStatus = soc,
        // TRUE or unknown, never a definite false derived from a time estimate. This was
        // `chargeRemain?.let { it > 0.0 }`, which turns a missing or zero RemainTime into "the car
        // told us it stopped charging".
        batteryCharge = if (chargeRemain != null && chargeRemain > 0.0) true else null,
        batteryPlugin = plug,
        drvDistance = rangeKm?.kmToMi()?.let { listOf(DrvDistance(RangeByFuel(Dte(it, 3)))) } ?: emptyList(),
        remainTime2 = chargeRemain?.let { RemainTime2(atc = TimeValue(it, 1)) },
        reservChargeInfos = run {
            val ac = green.path("ChargingInformation", "TargetSoC", "Standard").intLoose()
            val dc = green.path("ChargingInformation", "TargetSoC", "Quick").intLoose()
            if (ac == null && dc == null) null
            else ReservChargeInfos(
                listOfNotNull(
                    dc?.let { TargetSOC(plugType = 0, targetSOClevel = it) },
                    ac?.let { TargetSOC(plugType = 1, targetSOClevel = it) },
                ),
            )
        },
    )

    val door1 = cabin.path("Door", "Row1") as? JsonObject
    val door2 = cabin.path("Door", "Row2") as? JsonObject
    val win1 = cabin.path("Window", "Row1") as? JsonObject
    val win2 = cabin.path("Window", "Row2") as? JsonObject

    // CCS2 per-door "Lock" is inverted: 0 = locked, 1 = unlocked (the reference reads `not
    // bool(Lock)`). The car is locked only when ALL present doors report Lock == 0.
    val doorLocks = listOfNotNull(
        door1.path("Driver", "Lock").intLoose(),
        door1.path("Passenger", "Lock").intLoose(),
        door2.path("Left", "Lock").intLoose(),
        door2.path("Right", "Lock").intLoose(),
    )

    return VehicleStatus(
        doorLock = if (doorLocks.isEmpty()) null else doorLocks.all { it == 0 },
        engine = vh.path("DrivingReady").flag(),
        trunkOpen = body.path("Trunk", "Open").flag(),
        hoodOpen = body.path("Hood", "Open").flag(),
        defrost = body.path("Windshield", "Front", "Defog", "State").intLoose()?.let { it == 1 },
        doorOpen = if (door1 == null && door2 == null) null else DoorOpen(
            frontLeft = door1.path("Driver", "Open").intLoose(),
            frontRight = door1.path("Passenger", "Open").intLoose(),
            backLeft = door2.path("Left", "Open").intLoose(),
            backRight = door2.path("Right", "Open").intLoose(),
        ),
        windowOpen = if (win1 == null && win2 == null) null else WindowOpen(
            frontLeft = win1.path("Driver", "Open").intLoose(),
            frontRight = win1.path("Passenger", "Open").intLoose(),
            backLeft = win2.path("Left", "Open").intLoose(),
            backRight = win2.path("Right", "Open").intLoose(),
        ),
        dte = rangeKm?.let { Dte(it.kmToMi(), 3) },
        battery = normalizeBattery12V(
            electronics.path("Battery", "Level").intLoose(),
            electronics.path("Battery", "SensorReliability").intLoose(),
        )?.let { Battery12V(batSoc = it) },
        evStatus = evStatus,
        dateTime = vh.path("Date").str(),
        tirePressureLamp = (chassis.path("Axle") as? JsonObject)?.let {
            TirePressureLamp(tirePressureLampAll = chassis.path("Axle", "Tire", "PressureLow").intLoose())
        },
    )
}

suspend fun EuApi.stopClimate(session: EuSession, v: EuVehicleSummary, controlToken: String) =
    control(session, v, controlToken, "temperature", buildJsonObject { put("command", "stop") })

/**
 * Start climate / pre-conditioning. Temperature arrives as Fahrenheit ([ClimateRequest.tempF]) and
 * is sent as a Celsius half-degree. Body shape from ApiImplType1's ccs2 temperature start.
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
            // Front pair by ROLE, so it flips with the drive side. The rear pair is named by side
            // in the payload too (rl/rr), so those map straight across and never swap.
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

/**
 * Set AC (plugType 1) and DC (plugType 0) charge target SOC percentages, via the v1
 * `.../charge/target` endpoint.
 */
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
