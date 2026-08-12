package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher

/**
 * A signed-in Hyundai Bluelink Europe (CCAPI / "CCS2") session. [deviceId] is the
 * `ccsp-device-id` from device registration and must stay stable across refreshes;
 * [pin] is the account service PIN, required to mint the short-lived control token
 * every command needs (the EU analogue of Canada's `pAuth`). [accessToken] is the
 * bare token — headers prepend "Bearer ".
 */
data class EuSession(
    val accessToken: String,
    val refreshToken: String?,
    val deviceId: String,
    val pin: String?,
)

/** A Europe-account vehicle summary. [id] is the CCAPI `vehicleId`; [ccs2] is the
 *  car's `ccuCCS2ProtocolSupport` flag, echoed back in the `Ccuccs2protocolsupport`
 *  header and used to pick the v2/ccs2 endpoints (non-zero for E-GMP cars). */
data class EuVehicleSummary(
    val id: String,
    val name: String,
    val model: String,
    val vin: String,
    val isEv: Boolean,
    val ccs2: Int,
)

/**
 * Client for Hyundai Bluelink Europe on the CCAPI platform ("CCS2" — E-GMP /
 * 2023+ cars). One shared API shape that Kia Connect EU and Genesis EU also ride
 * (different host/client/login-form host only), like the three Canada brands
 * share [CanadaApi]; only Hyundai EU is wired today via [Brand.isEurope].
 *
 * Ported from the Apache-2.0 hyundai_kia_connect_api (KiaUvoApiEU + the CCS2
 * ApiImplType1). The current EU sign-in is the "IDPConnect" OAuth2 flow: it
 * fetches an RSA public key, encrypts the password with it, posts the sign-in
 * form to the identity host (idpconnect-eu.hyundai.com), reads the authorization
 * code from the 302 redirect, then exchanges it for tokens. Opaque constants
 * ([EuStamp], [Brand.clientSecret]) are the Hyundai EU values from that source.
 */
class EuApi(private val brand: Brand) {

    init {
        require(brand.isEurope) { "EuApi requires a Europe brand, got $brand" }
    }

    private val host get() = brand.host
    private val userApi get() = "${brand.baseUrl}/api/v1/user/"
    private val spa get() = "${brand.baseUrl}/api/v1/spa/"
    private val spaV2 get() = "${brand.baseUrl}/api/v2/spa/"
    private val serviceId get() = brand.clientId
    private val clientSecret get() = brand.clientSecret

    // Hyundai EU sign-in form / identity host and OAuth redirect target. When
    // Kia/Genesis EU are added these become brand-keyed (idpconnect-eu.kia.com,
    // redirect_uri .../oauth2/redirect for Kia).
    private val loginFormHost get() = "https://idpconnect-eu.hyundai.com"
    private val redirectUri get() = userApi + "oauth2/token"

    companion object {
        private const val USER_AGENT_OKHTTP = "okhttp/3.12.0"
        // The IDPConnect authorize endpoint 400s without the "_CCS_APP_AOS" suffix.
        private const val USER_AGENT_IDP =
            "Mozilla/5.0 (Linux; Android 4.1.1; Galaxy Nexus Build/JRO03C) AppleWebKit/535.19 " +
                "(KHTML, like Gecko) Chrome/18.0.1025.166 Mobile Safari/535.19_CCS_APP_AOS"

        /** A fresh device id (persisted; the ccsp-device-id is derived per login). */
        fun newDeviceId(): String = UUID.randomUUID().toString()

        // Force-refresh polling: the car reports asynchronously (~20s live), so
        // poll /latest this many times at this interval (≈20s cap) waiting for the
        // snapshot timestamp to advance. Kept small for EU's strict rate limits.
        private const val REFRESH_POLLS = 5
        private const val REFRESH_POLL_INTERVAL_MS = 4000L

        private val sharedJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

        private val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            // BASIC level logs the request/response line only (no bodies), so the
            // password / PIN / tokens in auth bodies are never written to the log.
            .addInterceptor(
                HttpLoggingInterceptor { line -> AppLog.log(line) }.apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
            .build()
    }

    private val json get() = sharedJson
    private val jsonMedia = "application/json;charset=UTF-8".toMediaType()
    private val client: OkHttpClient get() = sharedClient

    // The CCAPI stamp binds to the request time in Unix SECONDS (see EuStamp).
    private fun nowStamp(): String = EuStamp.generate(unixSeconds = System.currentTimeMillis() / 1000)

    // --- Headers -------------------------------------------------------------

    /** CCAPI service headers every prd.eu-ccapi call needs (pre- or post-auth). */
    private fun Request.Builder.apiHeaders(): Request.Builder = this
        .header("Content-Type", "application/json;charset=UTF-8")
        .header("ccsp-service-id", serviceId)
        .header("ccsp-application-id", EuStamp.APP_ID)
        .header("Stamp", nowStamp())
        .header("Host", host)
        .header("Connection", "Keep-Alive")
        .header("Accept-Encoding", "gzip")
        .header("User-Agent", USER_AGENT_OKHTTP)

    /** [apiHeaders] plus the bearer access token, device id and CCS2-support flag. */
    private fun Request.Builder.authHeaders(session: EuSession, ccs2: Int): Request.Builder =
        apiHeaders()
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("ccsp-device-id", session.deviceId)
            .header("Ccuccs2protocolsupport", ccs2.toString())

    /** [authHeaders] with the PIN-derived control token in both Authorization and
     *  AuthorizationCCSP — CCS2 control endpoints authenticate on the control
     *  token, not the plain access token. [controlToken] already carries "Bearer ". */
    private fun Request.Builder.commandHeaders(session: EuSession, ccs2: Int, controlToken: String): Request.Builder =
        authHeaders(session, ccs2)
            .header("Authorization", controlToken)
            .header("AuthorizationCCSP", controlToken)

    // --- Auth ----------------------------------------------------------------

    /**
     * Registers this device with the CCAPI push channel and returns the
     * `ccsp-device-id` all authenticated calls carry. Bloo doesn't use CCAPI push
     * — it only needs the device id the register call mints from a generated push
     * registration id. Ported from KiaUvoApiEU._get_device_id (no auth token).
     */
    suspend fun register(): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("pushRegId", UUID.randomUUID().toString().replace("-", "").take(64))
            put("pushType", "GCM")
            put("uuid", UUID.randomUUID().toString())
        }.toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url(spa + "notifications/register").post(body).apiHeaders().build()
        call(req).path("resMsg", "deviceId").str()
            ?: throw BlueLinkException("Europe device registration failed")
    }

    /**
     * Headless IDPConnect sign-in: authorize (seed cookies) -> fetch RSA cert ->
     * RSA-encrypt the password -> POST the sign-in form and read the auth `code`
     * from the 302 redirect -> exchange the code for tokens. Ported verbatim in
     * shape from KiaUvoApiEU._login_with_password.
     */
    suspend fun login(username: String, password: String, deviceId: String, pin: String?): EuSession =
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
            val authorizeUrl = "$loginFormHost/auth/api/v2/user/oauth2/authorize" +
                "?response_type=code&client_id=$serviceId&redirect_uri=$redirectUri&lang=en&state=ccsp&country=de"
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

    /** Exchange the refresh token for a fresh access token (no re-login). */
    suspend fun refresh(session: EuSession): EuSession = withContext(Dispatchers.IO) {
        val refresh = session.refreshToken
            ?: throw BlueLinkException("Session expired — please sign in again", code = 401)
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", serviceId)
            .add("client_secret", clientSecret)
            .build()
        val req = Request.Builder().url("$loginFormHost/auth/api/v2/user/oauth2/token")
            .header("User-Agent", USER_AGENT_IDP).post(form).build()
        val root = call(req)
        val access = root.path("access_token").str()
            ?: throw BlueLinkException("Session expired — please sign in again", code = 401)
        session.copy(accessToken = access, refreshToken = root.path("refresh_token").str() ?: refresh)
    }

    /**
     * Mint the short-lived control token every command needs by verifying the PIN
     * (PUT user/pin). Not cached here — [EuRepository] caches it and refetches on a
     * 401, like [CanadaRepository] does with `pAuth`. Returns the value already
     * prefixed "Bearer " for the Authorization header.
     */
    suspend fun controlToken(session: EuSession, pin: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("deviceId", session.deviceId); put("pin", pin) }
            .toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url(userApi + "pin?token=")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Host", host)
            .header("Accept-Encoding", "gzip")
            .header("User-Agent", USER_AGENT_OKHTTP)
            .put(body).build()
        val token = call(req).path("controlToken").str()
            ?: throw BlueLinkException("Incorrect service PIN")
        if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    // --- Vehicles ------------------------------------------------------------

    suspend fun vehicles(session: EuSession): List<EuVehicleSummary> = withContext(Dispatchers.IO) {
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
                ccs2 = o["ccuCCS2ProtocolSupport"]?.int() ?: 0,
            )
        }
    }

    // --- Status / location ---------------------------------------------------

    /**
     * Latest CCS2 vehicle state (carstatus is on the v1 SPA API; only the ccs2
     * control commands are on v2).
     *
     * When [refresh] is false this just reads the cached `/latest` snapshot — fast,
     * but it lags the car (right after a command it still shows the old state).
     *
     * When [refresh] is true it does a genuine force-refresh: GET the no-`/latest`
     * "wake" endpoint, then poll `/latest` until its snapshot timestamp (`Date`)
     * advances past the pre-wake value — the car reports asynchronously (~20s
     * live), so this is the only way to see a post-command state. It returns as
     * soon as fresh data arrives and is capped at ~20s so the spinner can't hang;
     * if the car stays silent it falls back to the last snapshot. Kept to a
     * handful of requests to respect EU's strict rate limits.
     */
    suspend fun status(session: EuSession, v: EuVehicleSummary, refresh: Boolean): VehicleStatus? =
        withContext(Dispatchers.IO) {
            val base = spa + "vehicles/${v.id}/ccs2/carstatus"
            fun readLatest(): JsonObject? =
                call(Request.Builder().url("$base/latest").get().authHeaders(session, v.ccs2).build())
                    .path("resMsg", "state", "Vehicle") as? JsonObject

            if (!refresh) return@withContext readLatest()?.let { parseStatus(it) }

            val before = readLatest()
            val beforeDate = before.path("Date").str()
            // Wake the car (best-effort — the wake returns an async envelope, not state).
            runCatching { call(Request.Builder().url(base).get().authHeaders(session, v.ccs2).build()) }
            repeat(REFRESH_POLLS) {
                delay(REFRESH_POLL_INTERVAL_MS)
                val now = readLatest()
                if (now != null && now.path("Date").str() != beforeDate) return@withContext parseStatus(now)
            }
            (before ?: readLatest())?.let { parseStatus(it) }
        }

    /** Last-known parked GPS via the dedicated `/location/park` endpoint (returns
     *  `resMsg.coord.lat/lon`) — the CCS2 carstatus snapshot doesn't reliably
     *  carry a position. Non-rate-limited (parked position), access-token auth. */
    suspend fun location(session: EuSession, v: EuVehicleSummary): GeoLocation? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(spa + "vehicles/${v.id}/location/park")
            .get().authHeaders(session, v.ccs2).build()
        val loc = call(req).path("resMsg") ?: return@withContext null
        val lat = loc.path("coord", "lat").dbl()
        val lon = loc.path("coord", "lon").dbl()
        if (lat != null && lon != null) GeoLocation(lat, lon, loc.path("speed", "value").dbl()) else null
    }

    /**
     * Maps the CCS2 `state.Vehicle` tree onto the shared [VehicleStatus] model,
     * using the dot-paths the reference's get_child_value reads (confirmed against
     * KiaUvoApiEU/ApiImplType1). Everything reads defensively so a firmware that
     * omits a field yields a missing value, never a crash.
     */
    private fun parseStatus(vh: JsonObject): VehicleStatus {
        val green = vh["Green"] as? JsonObject
        val cabin = vh["Cabin"] as? JsonObject
        val body = vh["Body"] as? JsonObject
        val drivetrain = vh["Drivetrain"] as? JsonObject
        val chassis = vh["Chassis"] as? JsonObject
        val electronics = vh["Electronics"] as? JsonObject

        val soc = green.path("BatteryManagement", "BatteryRemain", "Ratio").dbl()?.toInt()
        val plug = green.path("ChargingDoor", "State").int()
        val chargeRemain = green.path("ChargingInformation", "Charging", "RemainTime").dbl()
        val rangeKm = (drivetrain.path("FuelSystem", "DTE", "Total")
            ?: drivetrain.path("FuelSystem", "DTE", "EV")).dbl()

        val evStatus = if (green == null) null else EvStatus(
            batteryStatus = soc,
            batteryCharge = chargeRemain?.let { it > 0.0 },
            batteryPlugin = plug,
            drvDistance = rangeKm?.kmToMi()?.let { listOf(DrvDistance(RangeByFuel(Dte(it, 3)))) } ?: emptyList(),
            remainTime2 = chargeRemain?.let { RemainTime2(atc = TimeValue(it, 1)) },
            reservChargeInfos = run {
                val ac = green.path("ChargingInformation", "TargetSoC", "Standard").int()
                val dc = green.path("ChargingInformation", "TargetSoC", "Quick").int()
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
            door1.path("Driver", "Lock").int(),
            door1.path("Passenger", "Lock").int(),
            door2.path("Left", "Lock").int(),
            door2.path("Right", "Lock").int(),
        )

        return VehicleStatus(
            doorLock = if (doorLocks.isEmpty()) null else doorLocks.all { it == 0 },
            engine = vh.path("DrivingReady").flag(),
            trunkOpen = body.path("Trunk", "Open").flag(),
            hoodOpen = body.path("Hood", "Open").flag(),
            defrost = body.path("Windshield", "Front", "Defog", "State").int()?.let { it == 1 },
            doorOpen = if (door1 == null && door2 == null) null else DoorOpen(
                frontLeft = door1.path("Driver", "Open").int(),
                frontRight = door1.path("Passenger", "Open").int(),
                backLeft = door2.path("Left", "Open").int(),
                backRight = door2.path("Right", "Open").int(),
            ),
            windowOpen = if (win1 == null && win2 == null) null else WindowOpen(
                frontLeft = win1.path("Driver", "Open").int(),
                frontRight = win1.path("Passenger", "Open").int(),
                backLeft = win2.path("Left", "Open").int(),
                backRight = win2.path("Right", "Open").int(),
            ),
            dte = rangeKm?.let { Dte(it.kmToMi(), 3) },
            battery = electronics.path("Battery", "Level").int()?.let { Battery12V(batSoc = it) },
            evStatus = evStatus,
            dateTime = vh.path("Date").str(),
            tirePressureLamp = (chassis.path("Axle") as? JsonObject)?.let {
                TirePressureLamp(tirePressureLampAll = chassis.path("Axle", "Tire", "PressureLow").int())
            },
        )
    }

    // --- Commands ------------------------------------------------------------
    // CCS2 lock/charge/climate: POST to the ccs2 control endpoints with the control token.
    // Charge target is a v1 endpoint. Bodies ported from ApiImplType1.

    suspend fun lock(session: EuSession, v: EuVehicleSummary, controlToken: String) =
        control(session, v, controlToken, "door", buildJsonObject { put("command", "close") })

    suspend fun unlock(session: EuSession, v: EuVehicleSummary, controlToken: String) =
        control(session, v, controlToken, "door", buildJsonObject { put("command", "open") })

    suspend fun startCharge(session: EuSession, v: EuVehicleSummary, controlToken: String) =
        control(session, v, controlToken, "charge", buildJsonObject { put("command", "start") })

    suspend fun stopCharge(session: EuSession, v: EuVehicleSummary, controlToken: String) =
        control(session, v, controlToken, "charge", buildJsonObject { put("command", "stop") })

    suspend fun stopClimate(session: EuSession, v: EuVehicleSummary, controlToken: String) =
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
    suspend fun startClimate(
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
            put("strgWhlHeating", if (req.steeringWheelHeat) 1 else 0)
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
    suspend fun setChargeTargets(
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

    private suspend fun control(
        session: EuSession, v: EuVehicleSummary, controlToken: String, path: String, cmd: JsonObject,
    ) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(spaV2 + "vehicles/${v.id}/ccs2/control/$path")
            .post(cmd.toString().toRequestBody(jsonMedia))
            .commandHeaders(session, v.ccs2, controlToken).build()
        call(req)
        Unit
    }

    // --- Plumbing ------------------------------------------------------------

    /** RSA-PKCS1v1.5-encrypt [password] with the JWK public key ([nB64Url]/[eB64Url]
     *  are base64url modulus/exponent), returning lowercase hex — matching the
     *  reference's `cipher.encrypt(pw).hex()`. */
    private fun rsaEncryptHex(password: String, nB64Url: String, eB64Url: String): String {
        fun decodeUrl(s: String): ByteArray {
            val padded = s + "=".repeat((4 - s.length % 4) % 4)
            return Base64.getUrlDecoder().decode(padded)
        }
        val key = KeyFactory.getInstance("RSA").generatePublic(
            RSAPublicKeySpec(BigInteger(1, decodeUrl(nB64Url)), BigInteger(1, decodeUrl(eB64Url))),
        )
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(password.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    /** Runs [request] on [httpClient] and returns the parsed JSON body. Throws on
     *  non-2xx (401 -> [EuRepository] refreshes + retries) and on an in-band
     *  `retCode == "F"` error. The failing method+path is included in the message. */
    private fun call(request: Request, httpClient: OkHttpClient = this.client): JsonElement =
        httpClient.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            val root = if (text.isBlank()) JsonObject(emptyMap())
            else runCatching { json.parseToJsonElement(text) }.getOrNull() ?: JsonObject(emptyMap())
            val where = "${request.method} ${request.url.encodedPath}"
            // In-band CCAPI status (resCode/resMsg) can accompany EITHER a 2xx or a
            // 4xx HTTP status. Codes from the reference's _check_response_for_errors.
            val resCode = root.path("resCode").str()
            val resMsg = root.path("resMsg").str()

            // 4004 "Duplicate request": an identical command is already being
            // processed server-side (e.g. a command fired right after a refresh, or
            // a double-tap) — the request DID land, so treat it as an accepted
            // no-op whatever the HTTP status, and never retry (that just duplicates
            // again). This is what makes the spurious "Duplicate request" error and
            // the retry-driven refresh-on-error go away.
            if (resCode == "4004") {
                AppLog.log("$where: duplicate request — already accepted, ignoring")
                return@use root
            }

            // Only genuine token/device expiry retries (mapped to 401). 7501 = auth,
            // 4002 = bad deviceId, or an explicit "token expired" message. A plain
            // HTTP 401 counts too. Everything else is a terminal error (no retry) —
            // notably we do NOT treat a bare 403 as retryable.
            val expired = resp.code == 401 || resCode == "7501" || resCode == "4002" ||
                (resMsg?.contains("token", true) == true && resMsg.contains("expired", true))

            if (!resp.isSuccessful || root.path("retCode").str() == "F") {
                val msg = resMsg ?: friendly(resp.code, text)
                AppLog.log("ERROR ${resp.code} $where: $msg (resCode $resCode)")
                throw BlueLinkException("$msg [$where]", code = if (expired) 401 else resp.code)
            }
            root
        }

    private fun friendly(code: Int, body: String): String {
        val msg = runCatching {
            json.parseToJsonElement(body).obj()?.let { it["resMsg"] ?: it.path("error", "message") }?.str()
        }.getOrNull()
        return msg?.takeIf { it.isNotBlank() } ?: "Europe request failed (HTTP $code)"
    }

    // --- JSON helpers (identical convention to CanadaApi/KiaUsaApi) -----------

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
    private fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.doubleOrNull
    private fun JsonElement?.flag(): Boolean? =
        (this as? JsonPrimitive)?.let { it.booleanOrNull ?: it.intOrNull?.let { v -> v != 0 } }

    /** CCS2 reports distance in km; Bloo stores miles everywhere and converts to
     *  km only at display time (see FormatUtils.formatDistance) — normalise here. */
    private fun Double.kmToMi(): Double = this * 0.621371

    private fun JsonElement?.path(vararg keys: String): JsonElement? {
        var cur: JsonElement? = this
        for (k in keys) {
            cur = when (cur) {
                is JsonObject -> cur[k]
                is JsonArray -> k.toIntOrNull()?.let { cur.getOrNull(it) }
                else -> null
            }
            if (cur == null) return null
        }
        return cur
    }
}
