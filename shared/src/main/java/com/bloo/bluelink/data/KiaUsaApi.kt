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
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

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
        // Endpoint + client credentials come from the central Brand definition.
        val BASE = Brand.KIA.host
        val API = "${Brand.KIA.baseUrl}/apigw/v1/"
        private val CLIENT_ID = Brand.KIA.clientId
        private val SECRET_KEY = Brand.KIA.clientSecret
        // Mimics an actual iOS Kia Connect client build, since the API appears
        // to key some behavior off a recognized User-Agent string.
        private const val USER_AGENT = "KIAPrimo_iOS/37 CFNetwork/1335.0.3.4 Darwin/21.6.0"

        /** A fresh, stable device id (persist it; the rmtoken is bound to it). */
        fun newDeviceId(): String = UUID.randomUUID().toString().uppercase(Locale.US)

        // The one shared OkHttp/Json stack -- see [ApiHttp]. This class is
        // constructed per call on the hot command paths (notification actions,
        // AutoLock, climate auto-extend), and a per-call OkHttpClient would mean
        // a fresh TCP + TLS handshake every time.

        /** Shared because SimpleDateFormat construction parses the pattern and
         *  loads locale DateFormatSymbols, and this runs on EVERY Kia request.
         *  ThreadLocal because SimpleDateFormat is not thread-safe. GMT is pinned
         *  explicitly, so caching it can't pick up a stale default time zone. */
        private val rfc1123Format = ThreadLocal.withInitial {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("GMT") }
        }
    }

    internal val json get() = ApiHttp.json

    internal val jsonMedia = "application/json;charset=utf-8".toMediaType()

    internal val client: OkHttpClient get() = ApiHttp.client

    // --- Headers ---------------------------------------------------------

    /** Current time formatted as an RFC 1123 date string in GMT — the exact
     *  format HTTP's own Date header uses, which the Kia API expects as its
     *  own `date` header on every request (see [apiHeaders]). */
    internal fun rfc1123Date(): String =
        rfc1123Format.get()!!.format(System.currentTimeMillis())

    /** The full set of headers every Kia API call needs regardless of
     *  endpoint (client/device identification, locale, a fresh timestamp) —
     *  [authedHeaders] below layers session-specific headers (sid/vinkey) on
     *  top of this for calls that need an active session. */
    internal fun Request.Builder.apiHeaders(deviceId: String): Request.Builder = this
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

    /** [apiHeaders] plus the two headers that identify *which* logged-in
     *  session and *which* car a command applies to — every authenticated
     *  call (status, lock/unlock, climate, etc.) goes through this. */
    internal fun Request.Builder.authedHeaders(session: KiaSession, vehicle: KiaVehicleSummary): Request.Builder =
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
                // Prefer a freshly-issued rmtoken if the server sent one on this
                // silent re-auth -- verifyOtpAndComplete's own response already
                // does this (reads resp.header("rmtoken")); this branch used to
                // just echo back the caller-supplied token unconditionally, so a
                // server-side rotation would never get persisted, eventually
                // failing with an already-invalidated rmtoken and forcing a full
                // OTP re-login that a valid replacement would have avoided.
                val freshRmtoken = resp.header("rmtoken") ?: rmtoken
                return@withContext KiaAuth.LoggedIn(KiaSession(sid, freshRmtoken, deviceId, pin))
            }
            val payload = parseJson(text, resp.code).obj()?.get("payload")?.obj()
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

    // --- Vehicles --------------------------------------------------------

    /** Fetch every vehicle registered on this Kia account. Mechanism: calls
     *  ownr/gvl (get vehicle list), then walks payload.vehicleSummary — an
     *  entry with no vehicleIdentifier is dropped entirely (mapNotNull) since
     *  there's nothing to key the car by; fuelType == 4 is the API's encoding
     *  for a pure EV. */
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
                name = vehicleDisplayName(o["nickName"]?.str(), o["modelName"]?.str(), id),
                model = o["modelName"]?.str()?.takeIf { it.isNotBlank() } ?: "Kia",
                key = o["vehicleKey"]?.str().orEmpty(),
                isEv = o["fuelType"]?.int() == 4,
            )
        }
    }

    // --- Status / location ----------------------------------------------

    /** Fetch the car's current status. Mechanism: posts a request body to
     *  cmm/gvi (get vehicle info) that explicitly opts into the sub-sections
     *  this app cares about (location, vehicleStatus) while opting out of
     *  ones it doesn't (weather, functionalCards) to keep the response
     *  smaller; the response wraps the actual status in a one-element
     *  vehicleInfoList array (returns null if that's empty/missing). Because
     *  cmm/gvi's own response doesn't include EV charge-limit targets, EVs
     *  get a second request ([chargeTargets]) merged in afterward — done as
     *  a best-effort (runCatching) so a failure fetching just the charge
     *  targets doesn't blank out the rest of an otherwise-successful status
     *  fetch. */
    suspend fun status(session: KiaSession, vehicle: KiaVehicleSummary): VehicleStatus? = withContext(Dispatchers.IO) {
        val info = fetchInfo(session, vehicle) ?: return@withContext null
        val parsed = parseStatus(info)
        // Charge limits live on a separate endpoint (cmm/gvi omits targetSOC).
        val ev = parsed.evStatus
        if (ev == null) parsed
        else {
            val targets = runCatching { chargeTargets(session, vehicle) }.getOrNull()
            if (targets == null) parsed else parsed.copy(evStatus = ev.copy(reservChargeInfos = targets))
        }
    }

    /** Read just the car's GPS position. Same cmm/gvi fetch as [status] via
     *  [fetchInfo], but skips the EV charge-targets ([chargeTargets]) round-trip
     *  since location doesn't need them — returns null if the fetch is
     *  empty/missing or the parsed status carries no location. */
    suspend fun location(session: KiaSession, vehicle: KiaVehicleSummary): VehicleLocation? =
        fetchInfo(session, vehicle)?.let { parseStatus(it).vehicleLocation }

    /** Shared cmm/gvi fetch+unwrap used by [status] and [location]: posts the
     *  request body opting into location + vehicleStatus (and out of the
     *  sections this app ignores) and returns the one-element vehicleInfoList's
     *  object, or null when that array is empty/missing. */
    internal suspend fun fetchInfo(session: KiaSession, vehicle: KiaVehicleSummary): JsonObject? = withContext(Dispatchers.IO) {
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
        (root.path("payload", "vehicleInfoList") as? JsonArray)?.firstOrNull()?.obj()
    }

    /**
     * Read AC/DC charge limits from evc/gts (payload.targetSOClist). Levels of 0
     * mean "not reported yet" per the community client, so they're skipped.
     */
    internal fun chargeTargets(session: KiaSession, vehicle: KiaVehicleSummary): ReservChargeInfos? {
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

    // --- Commands --------------------------------------------------------

    // These four are simple no-body GET commands — see [getCommand] for the
    // shared mechanism (fire the request, discard the response body, only
    // care whether it succeeded).
    suspend fun lock(session: KiaSession, v: KiaVehicleSummary) = getCommand("rems/door/lock", session, v)

    suspend fun unlock(session: KiaSession, v: KiaVehicleSummary) = getCommand("rems/door/unlock", session, v)

    suspend fun stopClimate(session: KiaSession, v: KiaVehicleSummary) = getCommand("rems/stop", session, v)

    suspend fun stopCharge(session: KiaSession, v: KiaVehicleSummary) = getCommand("evc/cancel", session, v)

    /** Start charging. chargeRatio is fixed at 100 -- the actual AC/DC charge
     *  *limit* percentages are configured separately via [setChargeTargets];
     *  this call is just the on/off trigger. */
    suspend fun startCharge(session: KiaSession, v: KiaVehicleSummary) = withContext(Dispatchers.IO) {
        postCommand("evc/charge", session, v, buildJsonObject { put("chargeRatio", 100) })
    }

    /** Set EV charge target SOC for AC and DC in percent. Mechanism: like the
     *  Hyundai/Genesis equivalent, both targets are sent together in one
     *  targetSOClist body (plugType 0 = DC, 1 = AC — same encoding used
     *  throughout this codebase), since the endpoint has no way to update
     *  just one without resending the other's current value too. */
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

    /** Kia's heat/vent seat encoding (type 1 = heat, 2 = cool, 0 = off). */
    internal fun seatSettings(level: Int): JsonObject = when (level) {
        8 -> buildJsonObject { put("heatVentType", 1); put("heatVentLevel", 4); put("heatVentStep", 1) }
        7 -> buildJsonObject { put("heatVentType", 1); put("heatVentLevel", 3); put("heatVentStep", 2) }
        6 -> buildJsonObject { put("heatVentType", 1); put("heatVentLevel", 2); put("heatVentStep", 3) }
        5 -> buildJsonObject { put("heatVentType", 2); put("heatVentLevel", 4); put("heatVentStep", 1) }
        4 -> buildJsonObject { put("heatVentType", 2); put("heatVentLevel", 3); put("heatVentStep", 2) }
        3 -> buildJsonObject { put("heatVentType", 2); put("heatVentLevel", 2); put("heatVentStep", 3) }
        else -> buildJsonObject { put("heatVentType", 0); put("heatVentLevel", 1); put("heatVentStep", 0) }
    }

    /** Shared shape for the simple no-body GET commands (lock/unlock/stop):
     *  build the URL, attach session+vehicle headers, run it via [call]
     *  (which already throws on any failure) and discard the parsed
     *  response — these commands only need a success/failure signal. */
    internal suspend fun getCommand(path: String, session: KiaSession, v: KiaVehicleSummary) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(API + path).get().authedHeaders(session, v).build()
        call(req)
        Unit
    }

    /** Shared shape for commands that need a JSON request body (charge,
     *  climate start, charge targets) — same header/error handling as
     *  [getCommand], just POSTing [body] instead of a bodyless GET. Runs
     *  synchronously; callers wrap it in withContext(Dispatchers.IO)
     *  themselves. */
    internal fun postCommand(path: String, session: KiaSession, v: KiaVehicleSummary, body: JsonObject) {
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
    /** The retrying entry point: a GET whose body can't be framed is retried once on a fresh
     *  connection (see [ResponseFraming]); POSTs are never retried here. */
    internal fun call(request: Request): JsonElement =
        ResponseFraming.retryOnceOnFreshConnection(request) { rawCall(it) }

    internal fun rawCall(request: Request): JsonElement = raw(request).use { resp ->
        // on a slow/cellular connection with a real payload.
        val text = resp.bodyWithSlowReadLog()
        if (!resp.isSuccessful) {
            val msg = friendly(resp.code, text)
            AppLog.log("ERROR ${resp.code} ${request.method} ${request.url.encodedPath}: $msg")
            throw BlueLinkException(msg, code = resp.code)
        }
        val root = if (text.isBlank()) JsonObject(emptyMap()) else parseJson(text, resp.code)
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

    internal fun raw(request: Request): Response = client.newCall(request).execute()

    internal fun friendly(code: Int, body: String): String {
        val msg = runCatching {
            json.parseToJsonElement(body).obj()?.path("status", "errorMessage")?.str()
                ?: json.parseToJsonElement(body).obj()?.get("errorMessage")?.str()
        }.getOrNull()
        return msg?.takeIf { it.isNotBlank() } ?: "Kia request failed (HTTP $code)"
    }

    /**
     * Parse a response body to JSON, converting a malformed/empty/non-JSON body
     * (WAF HTML block page, gateway 5xx, truncated response) into a
     * [BlueLinkException] — which the repository layer already catches — instead of
     * letting a raw SerializationException/IOException crash the app.
     */
    internal fun parseJson(text: String, code: Int): JsonElement =
        parseJsonOrThrow(json, text, code, friendly(code, text))

    /** RFC 4122 v5 (name-based, SHA-1) UUID in the DNS namespace — matches the iOS app. */
    internal fun uuid5FromDns(name: String): String {
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
