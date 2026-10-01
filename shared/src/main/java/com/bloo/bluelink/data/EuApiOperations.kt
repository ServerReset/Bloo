package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import com.bloo.bluelink.data.EuApi.Companion.USER_AGENT_IDP
import com.bloo.bluelink.data.EuApi.Companion.sharedClient

/** The EU service's bigger operations -- sign-in, the vehicle list, climate and charge commands, and turning the CCS2 status tree into a [VehicleStatus] -- as extensions of [EuApi], kept out of the class so it stays readable. */

/**
 * Headless IDPConnect sign-in: authorize (seed cookies) -> fetch RSA cert ->
 * RSA-encrypt the password -> POST the sign-in form and read the auth `code`
 * from the 302 redirect -> exchange the code for tokens. Ported verbatim in
 * shape from KiaUvoApiEU._login_with_password.
 */
suspend fun EuApi.login(username: String, password: String, deviceId: String, pin: String?): EuSession =
    withContext(Dispatchers.IO) {
        // One cookie jar shared across the handshake; two clients over it that
        // differ only in redirect-following (signin must NOT follow, so its 302
        // Location — carrying the code — is readable).
        val store = mutableListOf<Cookie>()
        val jar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                store.removeAll { e -> cookies.any { it.name == e.name } }
                store.addAll(cookies)
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = store.toList()
        }
        val follow = sharedClient.newBuilder().cookieJar(jar).followRedirects(true).build()
        val noFollow = sharedClient.newBuilder().cookieJar(jar).followRedirects(false).build()

        fun idp(url: String) = Request.Builder().url(url).header("User-Agent", USER_AGENT_IDP)

        // 1. authorize — seed IDP session cookies (follows redirect to login form).
        //
        // The country was pinned to "de" for every user, which is right for
        // exactly one of the thirty-odd markets this region serves.
        // euLoginCountry sends the device's own country instead, but only
        // when it is one the region actually serves -- so a German owner
        // with a US-English phone still sends "de" rather than a country
        // this IDP has never heard of, and nobody who can sign in today
        // stops being able to.
        val authorizeUrl = "$loginFormHost/auth/api/v2/user/oauth2/authorize" +
            "?response_type=code&client_id=$serviceId&redirect_uri=$redirectUri" +
            "&lang=en&state=ccsp&country=${euLoginCountry()}"
        follow.newCall(idp(authorizeUrl).get().build()).execute().close()

        // 2. RSA public key (JWK) for password encryption.
        val certRoot = call(idp("$loginFormHost/auth/api/v1/accounts/certs").get().build(), follow)
        val jwk = certRoot.path("retValue") as? JsonObject
            ?: throw BlueLinkException("Europe sign-in: could not fetch the login key")
        val kid = jwk.path("kid").str().orEmpty()
        val encryptedPw = rsaEncryptHex(
            password,
            jwk.path("n").str() ?: throw BlueLinkException("Europe sign-in: bad login key"),
            jwk.path("e").str() ?: throw BlueLinkException("Europe sign-in: bad login key"),
        )

        // 3. signin — form POST, do NOT follow the redirect; pull code from Location.
        val signinForm = FormBody.Builder()
            .add("client_id", serviceId)
            .add("encryptedPassword", "true")
            .add("password", encryptedPw)
            .add("redirect_uri", redirectUri)
            .add("scope", "")
            .add("nonce", "")
            .add("state", "ccsp")
            .add("username", username)
            .add("connector_session_key", "")
            .add("kid", kid)
            .add("_csrf", "")
            .build()
        val location = noFollow.newCall(
            idp("$loginFormHost/auth/account/signin").post(signinForm).build(),
        ).execute().use { resp ->
            if (resp.code != 302) {
                throw BlueLinkException(
                    "Europe sign-in failed (HTTP ${resp.code}) — check your Bluelink email and password",
                    code = resp.code,
                )
            }
            resp.header("location").orEmpty()
        }
        val code = Regex("[?&]code=([^&]+)").find(location)?.groupValues?.get(1)
            ?: throw BlueLinkException(
                if (location.contains("authorization", true))
                    "Bluelink needs a one-time consent in the official app/website first, then try again."
                else "Europe sign-in was rejected — check your Bluelink email and password.",
            )

        // 4. exchange code -> tokens (form; client_secret sent as a field).
        val tokenForm = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri)
            .add("client_id", serviceId)
            .add("client_secret", clientSecret)
            .build()
        val tokenRoot = call(
            idp("$loginFormHost/auth/api/v2/user/oauth2/token").post(tokenForm).build(), follow,
        )
        val access = tokenRoot.path("access_token").str()
            ?: throw BlueLinkException("Europe sign-in failed to obtain an access token")
        EuSession(access, tokenRoot.path("refresh_token").str(), deviceId, pin)
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

suspend fun EuApi.stopClimate(session: EuSession, v: EuVehicleSummary, controlToken: String) =
    control(session, v, controlToken, "temperature", buildJsonObject { put("command", "stop") })

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
