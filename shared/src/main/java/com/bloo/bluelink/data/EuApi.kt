package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
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
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A signed-in Hyundai Bluelink Europe (CCAPI / "CCS2") session. [deviceId] is the
 * `ccsp-device-id` obtained from device registration and must stay stable across
 * refreshes; [pin] is the account service PIN, required to mint the short-lived
 * control token every command needs (the EU analogue of Canada's `pAuth`).
 */
data class EuSession(
    val accessToken: String,
    val refreshToken: String?,
    val deviceId: String,
    val pin: String?,
)

/** A Europe-account vehicle summary. [id] is the CCAPI `vehicleId`, used in the
 *  `/spa/vehicles/{id}/...` path on every vehicle-scoped call. */
data class EuVehicleSummary(
    val id: String,
    val name: String,
    val model: String,
    val vin: String,
    val isEv: Boolean,
)

/**
 * Client for Hyundai Bluelink Europe on the CCAPI platform (the "CCS2" protocol
 * used by E-GMP / 2023+ cars — Ioniq 5/6, etc.). One shared API shape that Kia
 * Connect EU and Genesis EU also ride (different host + client only), exactly
 * like the three Canada brands share [CanadaApi]; only Hyundai EU is wired today
 * via [Brand.isEurope].
 *
 * Ported from the Apache-2.0 community project hyundai_kia_connect_api
 * (github.com/Hyundai-Kia-Connect/hyundai_kia_connect_api — HyundaiBlueLinkApiEU
 * + the CCS2 ApiImplType1 state mapping). Two categories are deliberately marked
 * for live confirmation rather than fabricated, because the repo's rule is real
 * data only and a wrong value fails silently at the server:
 *
 *  - **Opaque constants** ([EuStamp.CFB]/[EuStamp.APP_ID], [Brand.clientSecret]):
 *    rotate with Hyundai's app; fill from the reference project's const.py.
 *  - **CCS2 wire specifics** (the exact `state.Vehicle.*` field paths in
 *    [parseStatus] and the control-command bodies): ported best-effort and must
 *    be validated/adjusted against ONE real `carstatus/latest` capture from the
 *    owner's car. Each is flagged inline with `CCS2-CONFIRM`.
 */
class EuApi(private val brand: Brand) {

    init {
        require(brand.isEurope) { "EuApi requires a Europe brand, got $brand" }
    }

    private val apiUrl get() = "${brand.baseUrl}/api/v1/"
    private val spaV2 get() = "${brand.baseUrl}/api/v2/spa/vehicles/"
    private val host get() = brand.host
    private val serviceId get() = brand.clientId          // ccsp-service-id / oauth client_id
    private val clientSecret get() = brand.clientSecret

    companion object {
        // Matches the reference EU client's User-Agent (the CCAPI is picky).
        private const val USER_AGENT = "okhttp/3.12.0"
        // CCAPI OAuth redirect target; the sign-in step returns a redirectUrl to
        // this with the authorization `code` as a query param.
        private const val REDIRECT_PATH = "api/v1/user/oauth2/redirect"

        /** A fresh device id; persisted (the ccsp-device-id is bound to it). */
        fun newDeviceId(): String = UUID.randomUUID().toString()

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

    // --- Headers -----------------------------------------------------------

    /** Headers every CCAPI call needs, authenticated or not. */
    private fun Request.Builder.apiHeaders(): Request.Builder = this
        .header("Content-Type", "application/json;charset=UTF-8")
        .header("Accept", "application/json, text/plain, */*")
        .header("Accept-Language", "en-US,en;q=0.9")
        .header("User-Agent", USER_AGENT)
        .header("Host", host)
        .header("Connection", "keep-alive")
        .header("ccsp-service-id", serviceId)
        .header("ccsp-application-id", EuStamp.APP_ID)
        .header("Stamp", nowStamp())

    /** [apiHeaders] plus the OAuth bearer token + device id every authenticated call needs. */
    private fun Request.Builder.authHeaders(session: EuSession): Request.Builder =
        apiHeaders()
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("ccsp-device-id", session.deviceId)

    /** [authHeaders] with the PIN-derived control token swapped into Authorization —
     *  CCS2 control endpoints authenticate the command with the control token, not
     *  the plain access token. */
    private fun Request.Builder.commandHeaders(session: EuSession, controlToken: String): Request.Builder =
        apiHeaders()
            .header("Authorization", controlToken)
            .header("ccsp-device-id", session.deviceId)

    // --- Auth ----------------------------------------------------------------

    /**
     * Registers this device with the CCAPI push channel and returns the
     * `ccsp-device-id` all later authenticated calls carry. A generated push
     * registration id is fine — Bloo doesn't use CCAPI push, it only needs the
     * device id the register call mints. Ported from HyundaiBlueLinkApiEU._device_id.
     */
    suspend fun register(): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("pushRegId", UUID.randomUUID().toString().replace("-", "").take(64))
            put("pushType", "GCM")
            put("uuid", UUID.randomUUID().toString())
        }.toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url(apiUrl + "spa/notifications/register").post(body).apiHeaders().build()
        call(req).path("resMsg", "deviceId").str()
            ?: throw BlueLinkException("Europe device registration failed")
    }

    /**
     * Full OAuth2 sign-in: seed cookies via authorize, set language, post the
     * credentials, pull the authorization `code` out of the returned redirect
     * URL, then exchange it for access/refresh tokens (HTTP Basic with the EU
     * client credentials). Returns a session carrying [deviceId]/[pin] for later
     * command auth. Ported from HyundaiBlueLinkApiEU.login.
     */
    suspend fun login(username: String, password: String, deviceId: String, pin: String?): EuSession =
        withContext(Dispatchers.IO) {
            // The OAuth handshake spans several requests that share session cookies
            // (JSESSIONID etc.), so it runs on a client with its own in-memory
            // cookie jar — the shared client keeps none. Mirrors the reference
            // client's use of a single requests.Session for the whole flow.
            val http = clientWithCookies()

            // 1. authorize — returns an HTML/redirect page (NOT json); we only run
            //    it to seed the session cookies, so execute + discard the body.
            val authorizeUrl = apiUrl + "user/oauth2/authorize?response_type=code&state=ccsp&client_id=" +
                serviceId + "&redirect_uri=" + brand.baseUrl + "/" + REDIRECT_PATH + "&lang=en"
            exec(http, Request.Builder().url(authorizeUrl).get().apiHeaders().build())

            // 2. language (CCAPI expects this before signin); response body unused.
            val langBody = buildJsonObject { put("lang", "en") }.toString().toRequestBody(jsonMedia)
            exec(http, Request.Builder().url(apiUrl + "user/language").post(langBody).apiHeaders().build())

            // 3. signin -> { redirectUrl: ".../redirect?code=<AUTH_CODE>&..." }
            val signinBody = buildJsonObject { put("email", username); put("password", password) }
                .toString().toRequestBody(jsonMedia)
            val signinRoot = call(
                Request.Builder().url(apiUrl + "user/signin").post(signinBody).apiHeaders().build(), http,
            )
            val redirectUrl = signinRoot.path("redirectUrl").str()
                ?: throw BlueLinkException("Europe sign-in failed — check your Bluelink email and password")
            val code = redirectUrl.substringAfter("code=", "").substringBefore("&").ifBlank {
                throw BlueLinkException("Europe sign-in did not return an authorization code")
            }

            // 4. exchange code -> tokens (HTTP Basic: base64("<serviceId>:<clientSecret>"))
            val basic = Base64.getEncoder()
                .encodeToString("$serviceId:$clientSecret".toByteArray(Charsets.UTF_8))
            val tokenForm = ("grant_type=authorization_code&redirect_uri=" + brand.baseUrl + "/" + REDIRECT_PATH +
                "&code=" + code)
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val tokenReq = Request.Builder().url(apiUrl + "user/oauth2/token").post(tokenForm)
                .apiHeaders().header("Authorization", "Basic $basic")
                .header("Content-Type", "application/x-www-form-urlencoded").build()
            val tokenRoot = call(tokenReq, http)
            val access = tokenRoot.path("access_token").str()
                ?: throw BlueLinkException("Europe sign-in failed to obtain an access token")
            // Store the bare token; authHeaders() adds the "Bearer " prefix itself.
            EuSession(
                accessToken = access,
                refreshToken = tokenRoot.path("refresh_token").str(),
                deviceId = deviceId,
                pin = pin,
            )
        }

    /** Exchange the refresh token for a fresh access token (no re-login / password). */
    suspend fun refresh(session: EuSession): EuSession = withContext(Dispatchers.IO) {
        val refresh = session.refreshToken
            ?: throw BlueLinkException("Session expired — please sign in again", code = 401)
        val basic = Base64.getEncoder().encodeToString("$serviceId:$clientSecret".toByteArray(Charsets.UTF_8))
        val form = ("grant_type=refresh_token&redirect_uri=" + brand.baseUrl + "/" + REDIRECT_PATH +
            "&refresh_token=" + refresh)
            .toRequestBody("application/x-www-form-urlencoded".toMediaType())
        val req = Request.Builder().url(apiUrl + "user/oauth2/token").post(form)
            .apiHeaders().header("Authorization", "Basic $basic")
            .header("Content-Type", "application/x-www-form-urlencoded").build()
        val root = call(req)
        val access = root.path("access_token").str()
            ?: throw BlueLinkException("Session expired — please sign in again", code = 401)
        session.copy(accessToken = access, refreshToken = root.path("refresh_token").str() ?: refresh)
    }

    /**
     * Mint the short-lived control token every command needs, by verifying the
     * account PIN. Not cached here — [EuRepository] caches it and re-fetches on a
     * 401, the same way [CanadaRepository] handles `pAuth`. Returns the
     * Authorization value to send verbatim (CCAPI returns it already typed).
     */
    suspend fun controlToken(session: EuSession, pin: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("deviceId", session.deviceId); put("pin", pin) }
            .toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url(apiUrl + "user/pin").put(body).authHeaders(session).build()
        val root = call(req)
        val token = root.path("controlToken").str()
            ?: throw BlueLinkException("Incorrect service PIN")
        // CCAPI returns the bare token; the control endpoints want it prefixed "Bearer ".
        if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    // --- Vehicles ------------------------------------------------------------

    suspend fun vehicles(session: EuSession): List<EuVehicleSummary> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${brand.baseUrl}/api/v1/spa/vehicles").get().authHeaders(session).build()
        val list = call(req).path("resMsg", "vehicles") as? JsonArray ?: JsonArray(emptyList())
        list.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o["vehicleId"]?.str() ?: return@mapNotNull null
            EuVehicleSummary(
                id = id,
                name = o["nickname"]?.str() ?: o["vehicleName"]?.str() ?: id.takeLast(6),
                model = listOfNotNull(o["year"]?.str(), o["vehicleName"]?.str()).joinToString(" ").ifBlank { "Car" },
                vin = o["vin"]?.str() ?: id,
                // CCS2 EVs report a "type" of "EV" (or "PHEV"); ICE reports "GN".
                isEv = o["type"]?.str()?.uppercase(Locale.US)?.let { it == "EV" || it == "PHEV" } ?: true,
            )
        }
    }

    // --- Status / location ---------------------------------------------------

    /** Latest CCS2 vehicle state. [refresh] forces a fresh read from the car
     *  (the `/ccs2/carstatus` path) vs the cached `/ccs2/carstatus/latest`. */
    suspend fun status(session: EuSession, v: EuVehicleSummary, refresh: Boolean): VehicleStatus? =
        withContext(Dispatchers.IO) {
            val path = if (refresh) "${v.id}/ccs2/carstatus" else "${v.id}/ccs2/carstatus/latest"
            val req = Request.Builder().url(spaV2 + path).get().authHeaders(session).build()
            val state = call(req).path("resMsg", "state", "Vehicle") as? JsonObject ?: return@withContext null
            parseStatus(state)
        }

    /** Last-known GPS from the CCS2 state's Location block (no separate call). */
    suspend fun location(session: EuSession, v: EuVehicleSummary): GeoLocation? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(spaV2 + "${v.id}/ccs2/carstatus/latest").get().authHeaders(session).build()
        val loc = call(req).path("resMsg", "state", "Vehicle", "Location") as? JsonObject ?: return@withContext null
        val lat = loc.path("GeoCoord", "Latitude").dbl()
        val lon = loc.path("GeoCoord", "Longitude").dbl()
        if (lat != null && lon != null) GeoLocation(lat, lon, loc.path("Speed", "Value").dbl()) else null
    }

    /**
     * Maps the CCS2 `state.Vehicle` tree onto the shared [VehicleStatus] model.
     *
     * CCS2-CONFIRM: the field paths below are ported from the reference project's
     * CCS2 state mapping and are the single thing that MUST be checked against one
     * real `carstatus/latest` capture from the owner's E-GMP car — casing and
     * nesting differ between firmware versions. Everything reads defensively
     * (null when absent), so a wrong path yields a missing field, never a crash.
     */
    private fun parseStatus(vh: JsonObject): VehicleStatus {
        val green = vh["Green"] as? JsonObject
        val cabin = vh["Cabin"] as? JsonObject
        val body = vh["Body"] as? JsonObject
        val drivetrain = vh["Drivetrain"] as? JsonObject
        val chassis = vh["Chassis"] as? JsonObject
        val electronics = vh["Electronics"] as? JsonObject

        val soc = green.path("BatteryManagement", "BatteryRemain", "Ratio").int()
        val plugged = green.path("ChargingInformation", "ConnectorFastening", "State").int()
        val charging = green.path("ChargingInformation", "Charging", "RemainTime").dbl()
        val rangeKm = drivetrain.path("FuelSystem", "DTE", "Total").dbl()

        val evStatus = if (green == null) null else EvStatus(
            batteryStatus = soc,
            batteryCharge = charging?.let { it > 0.0 },
            batteryPlugin = plugged,
            drvDistance = rangeKm?.kmToMi()?.let {
                listOf(DrvDistance(RangeByFuel(Dte(it, 3)))) // unit 3 = km on the wire; value stored as mi
            } ?: emptyList(),
            remainTime2 = charging?.let { RemainTime2(atc = TimeValue(it, 1)) },
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

        // Doors: CCS2 nests per row/seat with an Open flag (0 closed / 1 open).
        val door = cabin.path("Door", "Row1") as? JsonObject
        val doorRear = cabin.path("Door", "Row2") as? JsonObject
        val doorOpen = if (door == null && doorRear == null) null else DoorOpen(
            frontLeft = door.path("Driver", "Open").int(),
            frontRight = door.path("Passenger", "Open").int(),
            backLeft = doorRear.path("Left", "Open").int(),
            backRight = doorRear.path("Right", "Open").int(),
        )
        val window = cabin.path("Window", "Row1") as? JsonObject
        val windowRear = cabin.path("Window", "Row2") as? JsonObject
        val windowOpen = if (window == null && windowRear == null) null else WindowOpen(
            frontLeft = window.path("Driver", "Open").int(),
            frontRight = window.path("Passenger", "Open").int(),
            backLeft = windowRear.path("Left", "Open").int(),
            backRight = windowRear.path("Right", "Open").int(),
        )

        return VehicleStatus(
            // Lock is reported at the driver door in CCS2 (1 = locked). CCS2-CONFIRM.
            doorLock = door.path("Driver", "Lock").flag(),
            airCtrlOn = cabin.path("HVAC", "Row1", "Driver", "Blower", "SpeedLevel").int()?.let { it > 0 },
            // No meaningful engine-on concept for an E-GMP EV; left unset.
            engine = null,
            trunkOpen = body.path("Trunk", "Open").flag(),
            hoodOpen = body.path("Hood", "Open").flag(),
            doorOpen = doorOpen,
            windowOpen = windowOpen,
            dte = rangeKm?.let { Dte(it.kmToMi(), 3) },
            battery = electronics.path("Battery", "Level").int()?.let { Battery12V(batSoc = it) },
            evStatus = evStatus,
            dateTime = vh.path("Date").str(),
            tirePressureLamp = (chassis.path("Axle") as? JsonObject)?.let {
                TirePressureLamp(
                    tirePressureLampAll = chassis.path("Axle", "Tire", "PressureLow").int(),
                )
            },
            fuelLevel = drivetrain.path("FuelSystem", "FuelLevel").int(),
        )
    }

    // --- Commands ------------------------------------------------------------
    // CCS2-CONFIRM: control endpoint paths + bodies are ported best-effort and
    // must be validated against the car. Each posts a small command object to a
    // /ccs2/control/* endpoint with the control token in Authorization.

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

    /** Set AC (plugType 1) and DC (plugType 0) charge target SOC percentages.
     *  Unlike the other commands this is a v1 endpoint (`.../charge/target`, NOT
     *  ccs2/control) taking the full targetSOClist in one request — ported from
     *  the reference EU client. CCS2-CONFIRM. */
    suspend fun setChargeTargets(
        session: EuSession, v: EuVehicleSummary, controlToken: String, acPercent: Int, dcPercent: Int,
    ) = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("targetSOClist", buildJsonArray {
                add(buildJsonObject { put("plugType", 0); put("targetSOClevel", dcPercent) })
                add(buildJsonObject { put("plugType", 1); put("targetSOClevel", acPercent) })
            })
        }.toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url("${brand.baseUrl}/api/v1/spa/vehicles/${v.id}/charge/target")
            .post(body).commandHeaders(session, controlToken).build()
        call(req)
        Unit
    }

    /** Start climate / pre-conditioning. Temperature arrives as Fahrenheit
     *  ([ClimateRequest.tempF]) and is sent as a Celsius half-degree. Body shape
     *  ported from the reference EU client's ccs2/control/temperature payload;
     *  the per-seat `seatClimateInfo` block is intentionally omitted (best-effort
     *  temperature + defrost + steering-wheel only). CCS2-CONFIRM. */
    suspend fun startClimate(
        session: EuSession, v: EuVehicleSummary, controlToken: String, req: ClimateRequest,
    ) {
        val celsius = Math.round((req.tempF - 32) * 5.0 / 9.0 * 2) / 2.0
        val cmd = buildJsonObject {
            put("command", "start")
            put("ignitionDuration", req.durationMinutes)
            put("strgWhlHeating", if (req.steeringWheelHeat) 1 else 0)
            put("hvacTempType", 1)
            put("hvacTemp", celsius)
            put("tempUnit", "C")
            put("windshieldFrontDefogState", req.defrost)
        }
        control(session, v, controlToken, "temperature", cmd)
    }

    private suspend fun control(
        session: EuSession, v: EuVehicleSummary, controlToken: String, path: String, cmd: JsonObject,
    ) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(spaV2 + "${v.id}/ccs2/control/$path")
            .post(cmd.toString().toRequestBody(jsonMedia))
            .commandHeaders(session, controlToken).build()
        call(req)
        Unit
    }

    // --- Plumbing ------------------------------------------------------------

    /** Runs [request] and returns the parsed JSON body. Throws on non-2xx; the
     *  CCAPI signals an expired token with 401, which [EuRepository] catches to
     *  refresh + retry. A successful HTTP status can still carry an in-band
     *  `retCode == "F"` error (resCode/resMsg), surfaced here as an exception. */
    /** A client that carries session cookies for the multi-step OAuth handshake.
     *  Reuses the shared dispatcher/pool/logging interceptor but adds a private
     *  in-memory cookie jar, so cookies never leak across logins or brands. */
    private fun clientWithCookies(): OkHttpClient {
        val store = mutableListOf<Cookie>()
        val jar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                store.removeAll { existing -> cookies.any { it.name == existing.name } }
                store.addAll(cookies)
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> = store.toList()
        }
        return sharedClient.newBuilder().cookieJar(jar).build()
    }

    /** Execute a request purely for its side effects (cookies), discarding the
     *  body — used for the authorize/language steps that return HTML, not JSON.
     *  Still throws on a non-2xx so a broken handshake surfaces early. */
    private fun exec(client: OkHttpClient, request: Request) {
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val msg = friendly(resp.code, resp.body?.string().orEmpty())
                AppLog.log("ERROR ${resp.code} ${request.method} ${request.url.encodedPath}: $msg")
                throw BlueLinkException(msg, code = resp.code)
            }
        }
    }

    private fun call(request: Request, client: OkHttpClient = this.client): JsonElement =
        client.newCall(request).execute().use { resp ->
        val text = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
            val msg = friendly(resp.code, text)
            AppLog.log("ERROR ${resp.code} ${request.method} ${request.url.encodedPath}: $msg")
            throw BlueLinkException(msg, code = resp.code)
        }
        val root = if (text.isBlank()) JsonObject(emptyMap()) else parseJson(text, resp.code)
        val retCode = root.path("retCode").str()
        if (retCode == "F") {
            val err = root.path("resCode").str()
            val msg = root.path("resMsg").str() ?: "Europe request failed (${err ?: "?"})"
            // 4004/4005-family = token invalid/expired -> surface as 401 so the
            // repository refreshes and retries. CCS2-CONFIRM the exact codes.
            val expired = err != null && (err.startsWith("400") || err == "4004" || err == "4005")
            AppLog.log("ERROR ${request.method} ${request.url.encodedPath}: $msg (code $err)")
            throw BlueLinkException(msg, code = if (expired) 401 else resp.code)
        }
        root
    }

    private fun friendly(code: Int, body: String): String {
        val msg = runCatching {
            json.parseToJsonElement(body).obj()?.let { it["resMsg"] ?: it.path("error", "message") }?.str()
        }.getOrNull()
        return msg?.takeIf { it.isNotBlank() } ?: "Europe request failed (HTTP $code)"
    }

    private fun parseJson(text: String, code: Int): JsonElement =
        runCatching { json.parseToJsonElement(text) }
            .getOrElse { throw BlueLinkException(friendly(code, text), code = code) }

    // --- JSON helpers (identical convention to CanadaApi/KiaUsaApi) -----------

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
    private fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.doubleOrNull
    private fun JsonElement?.flag(): Boolean? =
        (this as? JsonPrimitive)?.let { it.booleanOrNull ?: it.intOrNull?.let { v -> v != 0 } }

    /** CCS2 reports distance in km; Bloo stores distance as miles everywhere and
     *  converts to km only at display time (see FormatUtils.formatDistance), so
     *  normalise at the parse boundary — same as CanadaApi does. */
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
