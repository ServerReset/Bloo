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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * A Kia US session. The [sid] is the session token (sent as the `sid` header);
 * [rmtoken] lets us re-authenticate silently (skipping the OTP step); [deviceId]
 * must stay stable across logins (the rmtoken is bound to it).
 */
data class KiaSession(
    val sid: String,
    val rmtoken: String?,
    val deviceId: String,
    val pin: String?,
)

/** A Kia US vehicle. [key] (the "vinkey") is session-specific and refreshed on login. */
data class KiaVehicleSummary(
    val id: String,
    val name: String,
    val model: String,
    val key: String,
    val isEv: Boolean,
)

/** Outcome of a Kia US login: a ready session, or an OTP challenge to solve. */
sealed interface KiaAuth {
    data class LoggedIn(val session: KiaSession) : KiaAuth
    data class OtpRequired(
        val otpKey: String,
        val xid: String,
        val email: String?,
        val sms: String?,
        val hasEmail: Boolean,
        val hasSms: Boolean,
    ) : KiaAuth
}

/**
 * Client for the Kia US "Kia Connect" telematics API (api.owners.kia.com), a
 * different backend from Hyundai/Genesis US. Faithfully follows the community
 * hyundai_kia_connect_api (KiaUvoApiUSA): an OTP-gated login that yields a session
 * id + a reusable rmtoken, with commands keyed by `sid` + `vinkey`.
 */
class KiaUsaApi {

    companion object {
        const val BASE = "api.owners.kia.com"
        const val API = "https://api.owners.kia.com/apigw/v1/"
        private const val CLIENT_ID = "SPACL716-APL"
        private const val SECRET_KEY = "sydnat-9kykci-Kuhtep-h5nK"
        private const val USER_AGENT = "KIAPrimo_iOS/37 CFNetwork/1335.0.3.4 Darwin/21.6.0"

        /** A fresh, stable device id (persist it; the rmtoken is bound to it). */
        fun newDeviceId(): String = UUID.randomUUID().toString().uppercase(Locale.US)
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    private val jsonMedia = "application/json;charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(
            HttpLoggingInterceptor { line -> AppLog.log(line) }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            },
        )
        .build()

    // --- Headers ---------------------------------------------------------

    private fun gmtOffsetHours(): String {
        val offsetMs = TimeZone.getDefault().getOffset(System.currentTimeMillis())
        return (offsetMs / 3_600_000).toString()
    }

    private fun rfc1123Date(): String =
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("GMT") }
            .format(System.currentTimeMillis())

    private fun Request.Builder.apiHeaders(deviceId: String): Request.Builder = this
        .header("content-type", "application/json;charset=utf-8")
        .header("accept", "application/json")
        .header("accept-language", "en-US,en;q=0.9")
        .header("accept-charset", "utf-8")
        .header("apptype", "L")
        .header("appversion", "7.22.0")
        .header("clientid", CLIENT_ID)
        .header("clientuuid", uuid5FromDns(deviceId))
        .header("from", "SPA")
        .header("Host", BASE)
        .header("language", "0")
        .header("offset", gmtOffsetHours())
        .header("ostype", "iOS")
        .header("osversion", "15.8.5")
        .header("phonebrand", "iPhone")
        .header("secretkey", SECRET_KEY)
        .header("to", "APIGW")
        .header("tokentype", "A")
        .header("User-Agent", USER_AGENT)
        .header("date", rfc1123Date())
        .header("deviceid", deviceId)

    private fun Request.Builder.authedHeaders(session: KiaSession, vehicle: KiaVehicleSummary): Request.Builder =
        apiHeaders(session.deviceId).header("sid", session.sid).header("vinkey", vehicle.key)

    // --- Auth ------------------------------------------------------------

    /** Step 1: username/password. Returns a session, or an OTP challenge. */
    suspend fun authUser(
        username: String, password: String, deviceId: String, rmtoken: String?, pin: String?,
    ): KiaAuth = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("deviceKey", deviceId)
            put("deviceType", 2)
            put("userCredential", buildJsonObject { put("userId", username); put("password", password) })
            put("tncFlag", 1)
        }.toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url(API + "prof/authUser").post(body).apiHeaders(deviceId)
            .apply { rmtoken?.let { header("rmtoken", it) } }
            .build()
        raw(req).use { resp ->
            val text = resp.body?.string().orEmpty()
            val sid = resp.header("sid")
            if (sid != null) {
                return@withContext KiaAuth.LoggedIn(KiaSession(sid, rmtoken, deviceId, pin))
            }
            val payload = json.parseToJsonElement(text).obj()?.get("payload")?.obj()
            val otpKey = payload?.get("otpKey")?.str()
            if (otpKey != null) {
                return@withContext KiaAuth.OtpRequired(
                    otpKey = otpKey,
                    xid = resp.header("xid").orEmpty(),
                    email = payload["email"]?.str(),
                    sms = payload["phone"]?.str(),
                    hasEmail = payload["hasEmail"]?.bool() == true,
                    hasSms = payload["hasPhone"]?.bool() == true,
                )
            }
            throw BlueLinkException(friendly(resp.code, text), code = resp.code)
        }
    }

    /** Step 2a: deliver the one-time code to "EMAIL" or "SMS". */
    suspend fun sendOtp(otpKey: String, notifyType: String, xid: String, deviceId: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(API + "cmm/sendOTP")
            .post("{}".toRequestBody(jsonMedia))
            .apiHeaders(deviceId)
            .header("otpkey", otpKey).header("notifytype", notifyType).header("xid", xid)
            .build()
        call(req)
        Unit
    }

    /** Step 2b: verify the code and finish login, producing a session. */
    suspend fun verifyOtpAndComplete(
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
        val finalSid = raw(finishReq).use { resp ->
            resp.header("sid") ?: throw BlueLinkException(friendly(resp.code, resp.body?.string().orEmpty()), code = resp.code)
        }
        KiaSession(finalSid, rmtoken, deviceId, pin)
    }

    // --- Vehicles --------------------------------------------------------

    suspend fun vehicles(session: KiaSession): List<KiaVehicleSummary> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(API + "ownr/gvl").get()
            .apiHeaders(session.deviceId).header("sid", session.sid).build()
        val root = call(req)
        val list = root.path("payload", "vehicleSummary") as? JsonArray ?: JsonArray(emptyList())
        list.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o["vehicleIdentifier"]?.str() ?: return@mapNotNull null
            KiaVehicleSummary(
                id = id,
                name = o["nickName"]?.str() ?: o["modelName"]?.str() ?: id.takeLast(6),
                model = o["modelName"]?.str() ?: "Kia",
                key = o["vehicleKey"]?.str().orEmpty(),
                isEv = o["fuelType"]?.int() == 4,
            )
        }
    }

    // --- Status / location ----------------------------------------------

    suspend fun status(session: KiaSession, vehicle: KiaVehicleSummary): VehicleStatus? = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("vehicleConfigReq", buildJsonObject {
                put("airTempRange", "0"); put("maintenance", "1"); put("seatHeatCoolOption", "0")
                put("vehicle", "1"); put("vehicleFeature", "0")
            })
            put("vehicleInfoReq", buildJsonObject {
                put("drivingActivty", "0"); put("dtc", "1"); put("enrollment", "1")
                put("functionalCards", "0"); put("location", "1"); put("vehicleStatus", "1"); put("weather", "0")
            })
            put("vinKey", buildJsonArray { add(JsonPrimitive(vehicle.key)) })
        }.toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url(API + "cmm/gvi").post(body)
            .authedHeaders(session, vehicle).build()
        val root = call(req)
        val info = (root.path("payload", "vehicleInfoList") as? JsonArray)?.firstOrNull()?.obj()
            ?: return@withContext null
        val parsed = parseStatus(info)
        // Charge limits live on a separate endpoint (cmm/gvi omits targetSOC).
        val ev = parsed.evStatus
        if (ev == null) parsed
        else {
            val targets = runCatching { chargeTargets(session, vehicle) }.getOrNull()
            if (targets == null) parsed else parsed.copy(evStatus = ev.copy(reservChargeInfos = targets))
        }
    }

    /**
     * Read AC/DC charge limits from evc/gts (payload.targetSOClist). Levels of 0
     * mean "not reported yet" per the community client, so they're skipped.
     */
    private fun chargeTargets(session: KiaSession, vehicle: KiaVehicleSummary): ReservChargeInfos? {
        val req = Request.Builder().url(API + "evc/gts").get()
            .authedHeaders(session, vehicle).build()
        val list = call(req).path("payload", "targetSOClist") as? JsonArray ?: return null
        val targets = list.mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val plug = o["plugType"]?.int() ?: return@mapNotNull null
            val level = o["targetSOClevel"]?.int() ?: return@mapNotNull null
            if (level == 0) null else TargetSOC(plug, level)
        }
        return if (targets.isEmpty()) null else ReservChargeInfos(targets)
    }

    /** Force the car to report fresh status (async; returns when accepted). */
    suspend fun forceRefresh(session: KiaSession, vehicle: KiaVehicleSummary) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(API + "rems/rvs")
            .post(buildJsonObject { put("requestType", 0) }.toString().toRequestBody(jsonMedia))
            .authedHeaders(session, vehicle).build()
        call(req)
        Unit
    }

    /**
     * Map Kia's cmm/gvi payload (vehicleInfoList[0]) onto our shared
     * [VehicleStatus]. Field paths follow the community hyundai_kia_connect_api
     * (KiaUvoApiUSA._update_vehicle_properties).
     */
    private fun parseStatus(info: JsonObject): VehicleStatus {
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
            tirePressure = vs.path("tirePressure", "all").int()?.let { TirePressure(all = it) },
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

    // --- Commands --------------------------------------------------------

    suspend fun lock(session: KiaSession, v: KiaVehicleSummary) = getCommand("rems/door/lock", session, v)
    suspend fun unlock(session: KiaSession, v: KiaVehicleSummary) = getCommand("rems/door/unlock", session, v)
    suspend fun stopClimate(session: KiaSession, v: KiaVehicleSummary) = getCommand("rems/stop", session, v)
    suspend fun stopCharge(session: KiaSession, v: KiaVehicleSummary) = getCommand("evc/cancel", session, v)

    suspend fun startCharge(session: KiaSession, v: KiaVehicleSummary) = withContext(Dispatchers.IO) {
        postCommand("evc/charge", session, v, buildJsonObject { put("chargeRatio", 100) })
    }

    suspend fun setChargeTargets(session: KiaSession, v: KiaVehicleSummary, ac: Int, dc: Int) = withContext(Dispatchers.IO) {
        postCommand(
            "evc/sts", session, v,
            buildJsonObject {
                put("targetSOClist", buildJsonArray {
                    add(buildJsonObject { put("plugType", 0); put("targetSOClevel", dc) })
                    add(buildJsonObject { put("plugType", 1); put("targetSOClevel", ac) })
                })
            },
        )
    }

    suspend fun startClimate(session: KiaSession, v: KiaVehicleSummary, req: ClimateRequest) = withContext(Dispatchers.IO) {
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
                    put("steeringWheel", if (req.steeringWheelHeat) 1 else 0)
                    put("steeringWheelStep", if (req.steeringWheelHeat) 1 else 0)
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

    /** Kia's heat/vent seat encoding (type 1 = heat, 2 = cool, 0 = off). */
    private fun seatSettings(level: Int): JsonObject = when (level) {
        8 -> buildJsonObject { put("heatVentType", 1); put("heatVentLevel", 4); put("heatVentStep", 1) }
        7 -> buildJsonObject { put("heatVentType", 1); put("heatVentLevel", 3); put("heatVentStep", 2) }
        6 -> buildJsonObject { put("heatVentType", 1); put("heatVentLevel", 2); put("heatVentStep", 3) }
        5 -> buildJsonObject { put("heatVentType", 2); put("heatVentLevel", 4); put("heatVentStep", 1) }
        4 -> buildJsonObject { put("heatVentType", 2); put("heatVentLevel", 3); put("heatVentStep", 2) }
        3 -> buildJsonObject { put("heatVentType", 2); put("heatVentLevel", 2); put("heatVentStep", 3) }
        1 -> buildJsonObject { put("heatVentType", 1); put("heatVentLevel", 4); put("heatVentStep", 1) }
        else -> buildJsonObject { put("heatVentType", 0); put("heatVentLevel", 1); put("heatVentStep", 0) }
    }

    private suspend fun getCommand(path: String, session: KiaSession, v: KiaVehicleSummary) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(API + path).get().authedHeaders(session, v).build()
        call(req)
        Unit
    }

    private fun postCommand(path: String, session: KiaSession, v: KiaVehicleSummary, body: JsonObject) {
        val req = Request.Builder().url(API + path)
            .post(body.toString().toRequestBody(jsonMedia)).authedHeaders(session, v).build()
        call(req)
    }

    // --- Plumbing --------------------------------------------------------

    /**
     * Run a request and return the parsed JSON body. Throws on non-2xx, and on
     * Kia's in-band errors: HTTP 200 with status.statusCode != 0. An expired
     * session (errorType 1, errorCode 1003/1005) is surfaced as a 401 so the
     * repository layer can re-authenticate with the rmtoken and retry.
     */
    private fun call(request: Request): JsonElement = raw(request).use { resp ->
        val text = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
            val msg = friendly(resp.code, text)
            AppLog.log("ERROR ${resp.code} ${request.method} ${request.url.encodedPath}: $msg")
            throw BlueLinkException(msg, code = resp.code)
        }
        val root = if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text)
        val status = root.path("status")
        val statusCode = status.path("statusCode").int()
        if (statusCode != null && statusCode != 0) {
            val errorType = status.path("errorType").int()
            val errorCode = status.path("errorCode").int()
            if (statusCode == 1 && errorType == 1 && (errorCode == 1003 || errorCode == 1005)) {
                throw BlueLinkException("Kia session expired", code = 401)
            }
            val msg = status.path("errorMessage").str() ?: "Kia request failed (error $errorCode)"
            AppLog.log("ERROR ${request.method} ${request.url.encodedPath}: $msg")
            throw BlueLinkException(msg, code = resp.code)
        }
        root
    }

    private fun raw(request: Request): Response = client.newCall(request).execute()

    private fun friendly(code: Int, body: String): String {
        val msg = runCatching {
            json.parseToJsonElement(body).obj()?.path("status", "errorMessage")?.str()
                ?: json.parseToJsonElement(body).obj()?.get("errorMessage")?.str()
        }.getOrNull()
        return msg?.takeIf { it.isNotBlank() } ?: "Kia request failed (HTTP $code)"
    }

    // --- JSON helpers ----------------------------------------------------

    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
    private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull
    private fun JsonElement?.dbl(): Double? = (this as? JsonPrimitive)?.doubleOrNull
    private fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull

    /** Boolean that tolerates Kia's mixed encodings: true/false or 0/1. */
    private fun JsonElement?.flag(): Boolean? =
        (this as? JsonPrimitive)?.let { it.booleanOrNull ?: it.intOrNull?.let { v -> v != 0 } }

    /** Descend through nested objects/arrays by key (numeric keys index arrays). */
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

    /** RFC 4122 v5 (name-based, SHA-1) UUID in the DNS namespace — matches the iOS app. */
    private fun uuid5FromDns(name: String): String {
        val namespace = byteArrayOf(
            0x6b, 0xa7.toByte(), 0xb8.toByte(), 0x10, 0x9d.toByte(), 0xad.toByte(), 0x11, 0xd1.toByte(),
            0x80.toByte(), 0xb4.toByte(), 0x00, 0xc0.toByte(), 0x4f, 0xd4.toByte(), 0x30, 0xc8.toByte(),
        )
        val md = MessageDigest.getInstance("SHA-1")
        md.update(namespace)
        md.update(name.toByteArray(Charsets.UTF_8))
        val h = md.digest()
        h[6] = ((h[6].toInt() and 0x0f) or 0x50).toByte() // version 5
        h[8] = ((h[8].toInt() and 0x3f) or 0x80).toByte() // variant
        val hex = h.take(16).joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
            "${hex.substring(16, 20)}-${hex.substring(20, 32)}"
    }
}
