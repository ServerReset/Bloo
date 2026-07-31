package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A signed-in Hyundai/Genesis/Kia Canada session. [pin] is the account's
 * service PIN (needed to obtain [CanadaApi.pinAuth] before any command);
 * [deviceId] must stay stable across logins — the 90-day "remembered device"
 * (mfaYn) is bound to it, and re-sending a fresh one forces a new OTP.
 */
data class CanadaSession(
    val accessToken: String,
    val refreshToken: String?,
    val deviceId: String,
    val pin: String?,
)

/** A Canada-account vehicle summary. [id] is the API's own `vehicleId`, sent
 *  back as the `vehicleId` header on every vehicle-scoped call — unlike Kia
 *  US there is no separate session-scoped "vinkey" to refresh. [year] drives
 *  which of the two temperature lookup tables [CanadaApi] hex-encodes against
 *  (see [CanadaApi.tempToHex]). */
data class CanadaVehicleSummary(
    val id: String,
    val name: String,
    val model: String,
    val vin: String,
    val year: Int?,
    val isEv: Boolean,
)

/** Outcome of step 1 of Canada sign-in: a ready session (device already
 *  remembered from a prior 90-day mfaYn grant), or an MFA challenge that must
 *  be completed with [CanadaApi.sendOtp] + [CanadaApi.verifyOtpAndComplete].
 *  Canada only offers email delivery (no SMS option, unlike Kia US). */
sealed interface CanadaAuth {
    data class LoggedIn(val session: CanadaSession) : CanadaAuth
    data class OtpRequired(val userInfoUuid: String, val email: String?) : CanadaAuth
}

/**
 * Client for the Hyundai/Genesis/Kia Canada telematics backend — one shared
 * API shape across all three brands (same client_id/client_secret, same
 * endpoints, differing only by host), and a completely different backend
 * from both the US Hyundai/Genesis "HATA" API ([BlueLinkApi]) and Kia's
 * separate US backend ([KiaUsaApi]).
 *
 * Ported from the community hyundai_kia_connect_api project's KiaUvoApiCA
 * (github.com/Hyundai-Kia-Connect/hyundai_kia_connect_api,
 * hyundai_kia_connect_api/KiaUvoApiCA.py) and its temperature-hex helpers in
 * utils.py (get_index_into_hex_temp/get_hex_temp_into_index). Two things
 * that project's own code leaves brand-specific and unconfirmed by this
 * port:
 *  - The EV9-specific "remoteControl"-wrapped climate body (other EVs use
 *    "hvacInfo") isn't special-cased here; all EVs use the "hvacInfo" shape.
 *  - The per-seat heat/vent command encoding (`drvSeatOptCmd` et al) is not
 *    documented anywhere in the reference project at the time of this port,
 *    so seat commands are sent as a best-effort 0(off)/1(low)/2(med)/3(high)
 *    heat-only scale and may not work correctly — leaving every seat at Off
 *    avoids the field being sent at all.
 */
class CanadaApi(private val brand: Brand) {

    init {
        require(brand.isCanada) { "CanadaApi requires a Canada brand, got $brand" }
    }

    private val apiUrl get() = "${brand.baseUrl}/"
    private val host get() = brand.host
    private val clientId get() = brand.clientId
    private val clientSecret get() = brand.clientSecret

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/130.0.0.0 Mobile Safari/537.36"
        private const val MFA_API_CODE = "0107"

        /** A fresh, stable device id (persist it; the 90-day mfaYn grant is bound to it). */
        fun newDeviceId(): String = UUID.randomUUID().toString().uppercase(Locale.US)

        // Process-wide, same reasoning as BlueLinkApi/KiaUsaApi's shared pair —
        // this class is constructed per call on hot command paths.
        private val sharedJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

        private val sharedClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor { line -> AppLog.log(line) }.apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
            .build()

        // Celsius half-degree lookup tables the hex-encoded setpoint indexes
        // into — pre-2020 model-year vehicles report a narrower range. Mirrors
        // KiaUvoApiCA's temperature_range_c_old/temperature_range_c_new exactly.
        private val TEMP_RANGE_OLD: List<Double> = (32..63).map { it * 0.5 }
        private val TEMP_RANGE_NEW: List<Double> = (28..63).map { it * 0.5 }
        private const val TEMP_RANGE_MODEL_YEAR = 2020
    }

    private val json get() = sharedJson
    private val jsonMedia = "application/json;charset=UTF-8".toMediaType()
    private val client: OkHttpClient get() = sharedClient

    // --- Headers -----------------------------------------------------------

    /** Headers every call needs, authenticated or not. */
    private fun Request.Builder.apiHeaders(deviceId: String): Request.Builder = this
        .header("Content-Type", "application/json;charset=UTF-8")
        .header("Accept", "application/json, text/plain, */*")
        .header("Accept-Language", "en-CA,en-US;q=0.8,en;q=0.5,fr;q=0.3")
        .header("User-Agent", USER_AGENT)
        .header("from", "CWP")
        .header("offset", gmtOffsetHours())
        .header("language", "0")
        .header("Origin", "https://$host")
        .header("Referer", "https://$host/login")
        .header("client_id", clientId)
        .header("client_secret", clientSecret)
        .header("Deviceid", Base64.getEncoder().encodeToString(deviceId.toByteArray(Charsets.UTF_8)))

    /** [apiHeaders] plus the access token every authenticated call needs. */
    private fun Request.Builder.authHeaders(session: CanadaSession): Request.Builder =
        apiHeaders(session.deviceId).header("accessToken", session.accessToken)

    /** [authHeaders] plus which car a vehicle-scoped call applies to. */
    private fun Request.Builder.vehicleHeaders(session: CanadaSession, vehicleId: String): Request.Builder =
        authHeaders(session).header("vehicleId", vehicleId)

    /** [vehicleHeaders] plus the PIN-derived auth token a command needs. */
    private fun Request.Builder.commandHeaders(session: CanadaSession, vehicleId: String, pAuth: String): Request.Builder =
        vehicleHeaders(session, vehicleId).header("pAuth", pAuth).header("from", "SPA")

    // --- Auth ----------------------------------------------------------------

    /** Step 1: username/password. Returns a session directly if this device is
     *  still within its 90-day mfaYn grant, otherwise an MFA challenge. */
    suspend fun authUser(username: String, password: String, deviceId: String, pin: String?): CanadaAuth =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject { put("loginId", username); put("password", password) }
                .toString().toRequestBody(jsonMedia)
            val req = Request.Builder().url(apiUrl + "v2/login").post(body).apiHeaders(deviceId).build()
            val root = call(req)
            val token = root.path("result", "token")
            val accessToken = token.path("accessToken").str()
            if (accessToken != null) {
                return@withContext CanadaAuth.LoggedIn(
                    CanadaSession(accessToken, token.path("refreshToken").str(), deviceId, pin),
                )
            }
            // Device isn't remembered -- resolve where the OTP can be delivered.
            val selBody = buildJsonObject { put("mfaApiCode", MFA_API_CODE); put("userAccount", username) }
                .toString().toRequestBody(jsonMedia)
            val selReq = Request.Builder().url(apiUrl + "mfa/selverifmeth").post(selBody).apiHeaders(deviceId).build()
            val selRoot = call(selReq)
            val uuid = selRoot.path("result", "userInfoUuid").str()
                ?: throw BlueLinkException("Canada sign-in failed: no verification method offered")
            val email = (selRoot.path("result", "emailList") as? JsonArray)?.firstOrNull()?.str()
                ?: selRoot.path("result", "userAccount").str()
                ?: username
            CanadaAuth.OtpRequired(uuid, email)
        }

    /** Step 2a: email the one-time code. Returns the otpKey [verifyOtpAndComplete] needs. */
    suspend fun sendOtp(userInfoUuid: String, email: String, deviceId: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("otpMethod", "E")
            put("mfaApiCode", MFA_API_CODE)
            put("userAccount", email)
            put("userPhone", "")
            put("userInfoUuid", userInfoUuid)
        }.toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url(apiUrl + "mfa/sendotp").post(body).apiHeaders(deviceId).build()
        call(req).path("result", "otpKey").str()
            ?: throw BlueLinkException("Couldn't send the verification code — please try again.")
    }

    /** Step 2b: verify the code and mint a session (remembering this device for 90 days). */
    suspend fun verifyOtpAndComplete(
        username: String, email: String, otpKey: String, otpCode: String, deviceId: String, pin: String?,
    ): CanadaSession = withContext(Dispatchers.IO) {
        val validateBody = buildJsonObject {
            put("otpNo", otpCode); put("userAccount", username); put("otpKey", otpKey); put("mfaApiCode", MFA_API_CODE)
        }.toString().toRequestBody(jsonMedia)
        val validateReq = Request.Builder().url(apiUrl + "mfa/validateotp").post(validateBody).apiHeaders(deviceId).build()
        val validateRoot = call(validateReq)
        val validationKey = validateRoot.path("result", "otpValidationKey").str()
            ?: validateRoot.path("result", "validationKey").str()
            ?: validateRoot.path("result", "otpKey").str()
            ?: throw BlueLinkException("Invalid code — please try again.")

        val genBody = buildJsonObject {
            put("userAccount", username); put("otpEmail", email); put("mfaApiCode", MFA_API_CODE)
            put("otpValidationKey", validationKey); put("mfaYn", "Y")
        }.toString().toRequestBody(jsonMedia)
        val genReq = Request.Builder().url(apiUrl + "mfa/genmfatkn").post(genBody).apiHeaders(deviceId).build()
        val genRoot = call(genReq)
        val token = genRoot.path("result", "token")
        val accessToken = token.path("accessToken").str()
            ?: throw BlueLinkException("Sign-in failed after verifying the code — please try again.")
        CanadaSession(accessToken, token.path("refreshToken").str(), deviceId, pin)
    }

    // --- Vehicles / PIN auth --------------------------------------------------

    /** Every vehicle registered on this account. */
    suspend fun vehicles(session: CanadaSession): List<CanadaVehicleSummary> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(apiUrl + "vhcllst")
            .post(ByteArray(0).toRequestBody(null)).authHeaders(session).build()
        val list = call(req).path("result", "vehicles") as? JsonArray ?: JsonArray(emptyList())
        list.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o["vehicleId"]?.str() ?: return@mapNotNull null
            CanadaVehicleSummary(
                id = id,
                name = o["nickName"]?.str() ?: o["modelName"]?.str() ?: id.takeLast(6),
                model = listOfNotNull(o["modelYear"]?.str(), o["modelName"]?.str()).joinToString(" ").ifBlank { "Car" },
                vin = o["vin"]?.str() ?: id,
                year = o["modelYear"]?.str()?.toIntOrNull(),
                // fuelKindCode's exact enumeration isn't documented in the reference
                // project; 4 matches the "pure EV" code Kia US's own fuelType field
                // uses (see KiaUsaApi.toVehicle), a reasonable best-effort guess
                // pending a real Canada EV account to confirm against.
                isEv = o["fuelKindCode"]?.int() == 4,
            )
        }
    }

    /** Get the PIN-derived auth token every command (lock/unlock/climate/charge/
     *  location) needs, by re-verifying the account's service PIN against this
     *  specific vehicle. Not cached here -- callers (CanadaRepository) cache it
     *  per vehicle and re-fetch on a 401. */
    suspend fun pinAuth(session: CanadaSession, vehicleId: String, pin: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("pin", pin) }.toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url(apiUrl + "vrfypin").post(body).vehicleHeaders(session, vehicleId).build()
        call(req).path("result", "pAuth").str()
            ?: throw BlueLinkException("Incorrect service PIN")
    }

    // --- Status / location -----------------------------------------------------

    /** Cached (or, if [refresh], freshly-woken) status. */
    suspend fun status(session: CanadaSession, v: CanadaVehicleSummary, refresh: Boolean): VehicleStatus? =
        withContext(Dispatchers.IO) {
            val path = if (refresh) "rltmvhclsts" else "lstvhclsts"
            val req = Request.Builder().url(apiUrl + path)
                .post(ByteArray(0).toRequestBody(null)).vehicleHeaders(session, v.id).build()
            val statusObj = call(req).path("result", "status") as? JsonObject ?: return@withContext null
            parseStatus(statusObj)
        }

    /** Last-known GPS fix, gated behind the account's service PIN like every
     *  other vehicle-scoped Canada command. */
    suspend fun location(session: CanadaSession, v: CanadaVehicleSummary, pAuth: String): GeoLocation? =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject { put("pin", session.pin.orEmpty()) }.toString().toRequestBody(jsonMedia)
            val req = Request.Builder().url(apiUrl + "fndmcr").post(body)
                .commandHeaders(session, v.id, pAuth).build()
            val root = call(req)
            val lat = root.path("result", "coord", "lat").dbl()
            val lon = root.path("result", "coord", "lon").dbl()
            if (lat != null && lon != null) GeoLocation(lat, lon) else null
        }

    /**
     * Maps `result.status` onto the shared [VehicleStatus] model. Field names
     * here match [VehicleStatus]'s own almost exactly (both ultimately trace
     * back to the same community reverse-engineering lineage), so this walks
     * the tree directly rather than via kotlinx.serialization decode, keeping
     * the same defensive per-field null handling as [KiaUsaApi.parseStatus]
     * for the fields whose shape does differ (evStatus.drvDistance/remainTime2).
     */
    private fun parseStatus(vs: JsonObject): VehicleStatus {
        val ev = vs["evStatus"] as? JsonObject
        val evStatus = if (ev == null) null else EvStatus(
            batteryCharge = ev["batteryCharge"].flag(),
            batteryStatus = ev["batteryStatus"].int(),
            batteryPlugin = (ev["batteryPlugin"] ?: vs["batteryPlugin"]).int(),
            drvDistance = run {
                val range = vs.path("drvDistance", "0", "rangeByFuel", "totalAvailableRange")
                    ?: vs.path("drvDistance", "0", "rangeByFuel", "evModeRange")
                range?.path("value").dbl()?.kmToMi()
                    ?.let { listOf(DrvDistance(RangeByFuel(Dte(it, range.path("unit").int())))) }
                    ?: emptyList()
            },
            remainTime2 = RemainTime2(
                atc = ev.path("remainTime2", "atc", "value").dbl()?.let { TimeValue(it, 1) },
                etc1 = ev.path("remainTime2", "etc1", "value").dbl()?.let { TimeValue(it, 1) },
                etc3 = ev.path("remainTime2", "etc3", "value").dbl()?.let { TimeValue(it, 1) },
            ).takeIf { it.atc != null || it.etc1 != null || it.etc3 != null },
        )
        return VehicleStatus(
            doorLock = vs["doorLock"].flag(),
            airCtrlOn = vs["airCtrlOn"].flag(),
            engine = vs["engine"].flag(),
            acc = vs["acc"].flag(),
            trunkOpen = vs["trunkOpen"].flag(),
            hoodOpen = vs["hoodOpen"].flag(),
            defrost = vs["defrost"].flag(),
            doorOpen = (vs["doorOpen"] as? JsonObject)?.let {
                DoorOpen(it["frontLeft"].int(), it["frontRight"].int(), it["backLeft"].int(), it["backRight"].int())
            },
            windowOpen = (vs["windowOpen"] as? JsonObject)?.let {
                WindowOpen(it["frontLeft"].int(), it["frontRight"].int(), it["backLeft"].int(), it["backRight"].int())
            },
            tirePressureLamp = (vs["tirePressureLamp"] as? JsonObject)?.let {
                TirePressureLamp(
                    tirePressureLampAll = it["tirePressureLampAll"].int(),
                    tirePressureLampFL = it["tirePressureLampFL"].int(),
                    tirePressureLampFR = it["tirePressureLampFR"].int(),
                    tirePressureLampRL = it["tirePressureLampRL"].int(),
                    tirePressureLampRR = it["tirePressureLampRR"].int(),
                )
            },
            // Canada reports distance in km (the CA app has no imperial option),
            // but the rest of Bloo treats every Dte/RangeByFuel value as miles
            // internally, converting to km only at display time based on the
            // user's own unit preference (see formatDistance) -- so this needs
            // to be normalized to miles right here at the parse boundary, or a
            // metric-mode user sees the km figure re-multiplied by 1.609 on top
            // of an already-km number (reported by a user: Bluelink said 263 km,
            // Bloo showed 423 km -- 263 * 1.609 ≈ 423).
            dte = (vs["dte"] as? JsonObject)?.let { Dte(it["value"].dbl()?.kmToMi(), it["unit"].int()) },
            airTemp = (vs["airTemp"] as? JsonObject)?.let { TempValue(it["value"]?.str(), it["unit"].int()) },
            battery = (vs["battery"] as? JsonObject)?.let { Battery12V(batSoc = it["batSoc"].int()) },
            evStatus = evStatus,
            dateTime = vs.path("lastStatusDate").str(),
            steerWheelHeat = vs["steerWheelHeat"].int(),
            sideBackWindowHeat = vs["sideBackWindowHeat"].int(),
            sideMirrorHeat = vs["sideMirrorHeat"].int(),
            seatHeaterVentState = (vs["seatHeaterVentState"] as? JsonObject)?.let {
                SeatHeaterVentState(
                    it["flSeatHeatState"].int(), it["frSeatHeatState"].int(),
                    it["rlSeatHeatState"].int(), it["rrSeatHeatState"].int(),
                )
            },
            lowFuelLight = vs["lowFuelLight"].flag(),
            washerFluidStatus = vs["washerFluidStatus"].flag(),
            breakOilStatus = vs["breakOilStatus"].flag(),
            smartKeyBatteryWarning = vs["smartKeyBatteryWarning"].flag(),
            fuelLevel = vs["fuelLevel"].int(),
            tirePressure = vs["tirePressureLamp"].let { (it as? JsonObject)?.get("tirePressureLampAll").int() }
                ?.let { TirePressure(all = it) },
        )
    }

    // --- Commands --------------------------------------------------------------

    suspend fun lock(session: CanadaSession, v: CanadaVehicleSummary, pAuth: String) =
        pinCommand("drlck", session, v, pAuth)

    suspend fun unlock(session: CanadaSession, v: CanadaVehicleSummary, pAuth: String) =
        pinCommand("drulck", session, v, pAuth)

    suspend fun stopClimate(session: CanadaSession, v: CanadaVehicleSummary, pAuth: String) =
        pinCommand(if (v.isEv) "evc/rfoff" else "rmtstp", session, v, pAuth)

    suspend fun startCharge(session: CanadaSession, v: CanadaVehicleSummary, pAuth: String) =
        pinCommand("evc/rcstrt", session, v, pAuth)

    suspend fun stopCharge(session: CanadaSession, v: CanadaVehicleSummary, pAuth: String) =
        pinCommand("evc/rcstp", session, v, pAuth)

    /** Set EV charge target SOC for AC (plugType 1) and DC (plugType 0) percent. */
    suspend fun setChargeTargets(
        session: CanadaSession, v: CanadaVehicleSummary, pAuth: String, acPercent: Int, dcPercent: Int,
    ) = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("tsoc", buildJsonArray {
                add(buildJsonObject { put("plugType", 0); put("level", dcPercent) })
                add(buildJsonObject { put("plugType", 1); put("level", acPercent) })
            })
            put("pin", session.pin.orEmpty())
        }.toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url(apiUrl + "evc/setsoc").post(body).commandHeaders(session, v.id, pAuth).build()
        call(req)
        Unit
    }

    /** Start climate / remote start. Temperature comes in as Fahrenheit
     *  ([ClimateRequest.tempF], the shared UI's unit) and is converted to the
     *  nearest Celsius half-degree, then hex-encoded — see [tempToHex]. */
    suspend fun startClimate(
        session: CanadaSession, v: CanadaVehicleSummary, pAuth: String, req: ClimateRequest,
    ) = withContext(Dispatchers.IO) {
        val hexTemp = tempToHex(req.tempF, v.year)
        val anySeat = listOf(req.seatFrontLeft, req.seatFrontRight, req.seatRearLeft, req.seatRearRight)
            .any { it != SeatLevel.OFF }
        fun climateFields() = buildJsonObject {
            put("airCtrl", 1)
            put("defrost", req.defrost)
            put("heating1", if (req.steeringWheelHeat) 1 else 0)
            put("igniOnDuration", req.durationMinutes)
            put("airTemp", buildJsonObject {
                put("value", hexTemp)
                put("unit", 0)
                put("hvacTempType", if (v.isEv) 1 else 0)
            })
            // Best-effort heat-only scale (0 off .. 3 high) -- see class doc:
            // the real per-seat command encoding isn't documented anywhere in
            // the reference project this was ported from.
            if (anySeat) {
                fun cmd(level: SeatLevel) = if (level.isHeat) (level.apiValue - 5) else 0
                put("seatHeaterVentCMD", buildJsonObject {
                    put("drvSeatOptCmd", cmd(req.seatFrontLeft))
                    put("astSeatOptCmd", cmd(req.seatFrontRight))
                    put("rlSeatOptCmd", cmd(req.seatRearLeft))
                    put("rrSeatOptCmd", cmd(req.seatRearRight))
                })
            }
        }
        val body = if (v.isEv) {
            buildJsonObject { put("pin", session.pin.orEmpty()); put("hvacInfo", climateFields()) }
        } else {
            buildJsonObject { put("setting", climateFields()); put("pin", session.pin.orEmpty()) }
        }
        val path = if (v.isEv) "evc/rfon" else "rmtstrt"
        val request = Request.Builder().url(apiUrl + path)
            .post(body.toString().toRequestBody(jsonMedia)).commandHeaders(session, v.id, pAuth).build()
        call(request)
        Unit
    }

    /** Convert a Fahrenheit setpoint to the API's zero-padded-hex-plus-"H"
     *  index encoding. Mirrors KiaUvoApiCA's get_index_into_hex_temp exactly:
     *  the nearest half-degree Celsius value's *index* into the model-year's
     *  lookup table, hex-formatted as e.g. "0AH". */
    private fun tempToHex(tempF: Int, modelYear: Int?): String {
        val celsius = (tempF - 32) * 5.0 / 9.0
        val table = if ((modelYear ?: TEMP_RANGE_MODEL_YEAR) >= TEMP_RANGE_MODEL_YEAR) TEMP_RANGE_NEW else TEMP_RANGE_OLD
        val rounded = Math.round(celsius * 2) / 2.0
        val clamped = rounded.coerceIn(table.first(), table.last())
        val index = table.indices.minByOrNull { kotlin.math.abs(table[it] - clamped) } ?: 0
        return Integer.toHexString(index).padStart(2, '0').uppercase(Locale.US) + "H"
    }

    /** Shared shape for the no-extra-body PIN-gated commands (lock/unlock/
     *  stop-climate/start-stop-charge): every one of these still needs the
     *  account's PIN in the body per the reference project, just with no
     *  other fields alongside it. */
    private suspend fun pinCommand(path: String, session: CanadaSession, v: CanadaVehicleSummary, pAuth: String) =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject { put("pin", session.pin.orEmpty()) }.toString().toRequestBody(jsonMedia)
            val req = Request.Builder().url(apiUrl + path).post(body).commandHeaders(session, v.id, pAuth).build()
            call(req)
            Unit
        }

    // --- Plumbing ----------------------------------------------------------

    /** Runs [request] and returns the parsed JSON body. Throws on non-2xx and
     *  on an expired session (surfaced as 401 so the repository re-authenticates). */
    private fun call(request: Request): JsonElement = raw(request).use { resp ->
        val text = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
            val msg = friendly(resp.code, text)
            AppLog.log("ERROR ${resp.code} ${request.method} ${request.url.encodedPath}: $msg")
            throw BlueLinkException(msg, code = resp.code)
        }
        val root = if (text.isBlank()) JsonObject(emptyMap()) else parseJson(text, resp.code)
        // Successful HTTP with an in-band error code still needs to surface as
        // a session expiry so CanadaRepository's retry-once logic can trigger.
        val statusCode = root.path("responseCode")?.str() ?: root.path("status", "statusCode")?.str()
        if (statusCode != null && statusCode != "0000" && statusCode != "0") {
            val msg = root.path("responseDesc").str() ?: root.path("status", "errorMessage").str()
                ?: "Canada request failed ($statusCode)"
            val expired = statusCode.contains("401") || msg.contains("expired", ignoreCase = true) ||
                msg.contains("session", ignoreCase = true)
            AppLog.log("ERROR ${request.method} ${request.url.encodedPath}: $msg")
            throw BlueLinkException(msg, code = if (expired) 401 else resp.code)
        }
        root
    }

    private fun raw(request: Request): Response = client.newCall(request).execute()

    private fun friendly(code: Int, body: String): String {
        val msg = runCatching {
            json.parseToJsonElement(body).obj()?.let { it["responseDesc"] ?: it.path("status", "errorMessage") }?.str()
        }.getOrNull()
        return msg?.takeIf { it.isNotBlank() } ?: "Canada request failed (HTTP $code)"
    }

    private fun parseJson(text: String, code: Int): JsonElement =
        runCatching { json.parseToJsonElement(text) }
            .getOrElse { throw BlueLinkException(friendly(code, text), code = code) }

    // --- JSON helpers (see KiaUsaApi for the identical convention) ----------

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull
    private fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.doubleOrNull
    private fun JsonElement?.flag(): Boolean? =
        (this as? JsonPrimitive)?.let { it.booleanOrNull ?: it.intOrNull?.let { v -> v != 0 } }

    /** Canada's status payload reports distance in km; Bloo's shared models
     *  (Dte/RangeByFuel) store distance as miles everywhere else, converting
     *  to km only at display time per the user's own unit setting (see
     *  FormatUtils.formatDistance) -- so every raw distance value coming out
     *  of this API needs to be normalized to miles right here. */
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
