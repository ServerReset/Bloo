package com.bloo.bluelink.data

import kotlinx.serialization.json.JsonObject

/** The EU service's bigger operations -- sign-in and turning the CCS2 status tree into a [VehicleStatus] -- as extensions of [EuApi], kept out of the class so it stays readable. */

/**
 * Headless IDPConnect sign-in: authorize (seed cookies) -> fetch RSA cert ->
 * RSA-encrypt the password -> POST the sign-in form and read the auth `code`
 * from the 302 redirect -> exchange the code for tokens. Ported verbatim in
 * shape from KiaUvoApiEU._login_with_password.
 */
suspend fun EuApi.login(username: String, password: String, deviceId: String, pin: String?): EuSession =

/**
 * Maps the CCS2 `state.Vehicle` tree onto the shared [VehicleStatus] model,
 * using the dot-paths the reference's get_child_value reads (confirmed against
 * KiaUvoApiEU/ApiImplType1). Everything reads defensively so a firmware that
 * omits a field yields a missing value, never a crash.
 */
internal fun EuApi.parseStatus(vh: JsonObject): VehicleStatus {
    val green = vh["Green"] as? JsonObject
    val cabin = vh["Cabin"] as? JsonObject
    val body = vh["Body"] as? JsonObject
    val drivetrain = vh["Drivetrain"] as? JsonObject
    val chassis = vh["Chassis"] as? JsonObject
    val electronics = vh["Electronics"] as? JsonObject

    val soc = green.path("BatteryManagement", "BatteryRemain", "Ratio").dbl()?.toInt()
    // NOT ChargingDoor.State -- that is the charge-port DOOR's own open/closed flag,
    // confirmed against the reference this file is ported from (ApiImplType1.py):
    // `charging_door_state in [0, 2] -> door closed, == 1 -> door open`, nothing to
    // do with whether a cable is actually connected. Using it here meant popping the
    // port door open with nothing plugged in read as "Plugged in (DC fast)" -- enabling
    // the start/stop-charge button and showing the DC limit pill on a car with no
    // cable attached at all, and the reverse on any car whose door auto-closes over an
    // inserted cable.
    //
    // ConnectorFastening.State is the reference's actual plug-detection field (it's
    // the LAST of two candidate assignments to ev_battery_is_plugged_in there, so it's
    // the one that wins). It is only ever a plugged/unplugged bool, though -- neither
    // this field nor anything else in the reference distinguishes AC from DC once
    // connected, so [EvStatus.batteryPlugin]'s AC/DC label is approximated as DC (1)
    // whenever a cable is present rather than genuinely known. That is a real
    // limitation, not a guess dressed up as one: Bloo's own AC-vs-DC UI presents this
    // as fact, so it can misname an AC session, but that is a strictly smaller error
    // than the previous one -- it never claims "unplugged" while charging, or
    // "plugged in" while it is not.
    val plug = green.path("ChargingInformation", "ConnectorFastening", "State").intLoose()
        ?.let { if (it != 0) 1 else 0 }
    val chargeRemain = green.path("ChargingInformation", "Charging", "RemainTime").dbl()
    val rangeKm = (drivetrain.path("FuelSystem", "DTE", "Total")
        ?: drivetrain.path("FuelSystem", "DTE", "EV")).dbl()

    val evStatus = if (green == null) null else EvStatus(
        batteryStatus = soc,
        // TRUE or unknown, never a definite false derived from a time estimate.
        //
        // This was `chargeRemain?.let { it > 0.0 }`, which turns a missing or zero
        // RemainTime into "the car told us it stopped charging". Notifications.LiveCharge.sync
        // documents that its `charging = false` means exactly that and never "we don't know",
        // and it acts on it: it cancels the live charging notification, clears the dismissal
        // flag, and LiveChargePollWorker ends its 5-minute chain. Meanwhile the snapshot
        // keeps charging = true through `evStatus?.batteryCharge ?: charging`, so the
        // snapshot-driven UI still shows a green ring and TOGGLE_CHARGE resolves to
        // CHARGE_OFF -- surfaces disagreeing, all from one parse.
        //
        // A remaining-time estimate is evidence of charging when present and positive, and
        // evidence of nothing at all otherwise: cars stop reporting it near the top of a
        // charge, and CCS2 payloads omit it entirely. null flows correctly through
        // VehicleSnapshot.merged's `?:` as "no new information", which is the honest answer.
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

    // CCS2 per-door "Lock" is inverted: 0 = locked, 1 = unlocked (the
    // reference reads `not bool(Lock)`). The car is locked only when ALL
    // present doors report Lock == 0.
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
