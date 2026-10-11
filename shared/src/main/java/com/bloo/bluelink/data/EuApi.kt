package com.bloo.bluelink.data

import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * A signed-in Hyundai Bluelink Europe (CCAPI / "CCS2") session. [deviceId] is the `ccsp-device-id`
 * from device registration and must stay stable across refreshes; [pin] is the account service PIN,
 * required to mint the short-lived control token every command needs (the EU analogue of Canada's
 * `pAuth`). [accessToken] is the bare token — headers prepend "Bearer ".
 */
data class EuSession(
    val accessToken: String,
    val refreshToken: String?,
    val deviceId: String,
    val pin: String?,
    // --- OneApp/CCI token set (Hyundai EU) ---
    // Hyundai's WAF now blocks the legacy IDPConnect authorize/signin by client_id, so
    // sign-in goes through the OneApp client_id + cci-api-eu.hyundai.com, which mint a
    // CCS-usable access token AND a set of CCI tokens. The CCI set is what a token
    // REFRESH needs (the CCS token can't be refreshed on its own), so it has to be
    // carried here and persisted. Null for any non-CCI path.
    val cciAccessToken: String? = null,
    val exchangeableToken: String? = null,
    val exchangeableRefreshToken: String? = null,
    val nonCcsToken: String? = null,
    val nonCcsRefreshToken: String? = null,
    val idToken: String? = null,
)

data class EuVehicleSummary(
    val id: String,
    val name: String,
    val model: String,
    val vin: String,
    val isEv: Boolean,
    val ccs2: Int,
)

/**
 * Client for Hyundai Bluelink Europe on the CCAPI platform ("CCS2" — E-GMP / 2023+ cars). One
 * shared API shape that Kia Connect EU and Genesis EU also ride (different host/client/login-form
 * host only), like the three Canada brands share [CanadaApi]; only Hyundai EU is wired today via
 * [Brand.isEurope].
 */
class EuApi(private val brand: Brand) {
    init {
        require(brand.isEurope) { "EuApi requires a Europe brand, got $brand" }
    }

    internal val host get() = brand.host

    internal val userApi get() = "${brand.baseUrl}/api/v1/user/"
    internal val spa get() = "${brand.baseUrl}/api/v1/spa/"
    internal val spaV2 get() = "${brand.baseUrl}/api/v2/spa/"
    internal val serviceId get() = brand.clientId

    internal val clientSecret get() = brand.clientSecret

    // Hyundai EU sign-in form / identity host and OAuth redirect target. When Kia/Genesis EU are
    // added these become brand-keyed (idpconnect-eu.kia.com, redirect_uri .../oauth2/redirect for
    // Kia).
    internal val loginFormHost get() = "https://idpconnect-eu.hyundai.com"

    // --- OneApp/CCI login (Hyundai EU) --------------------------------------
    // Hyundai's WAF blocks the LEGACY IDPConnect authorize by client_id, so the old
    // sign-in returns 403. The OneApp client_id is NOT on the block list; its authorize
    // succeeds, and the resulting code is exchanged at cci-api-eu.hyundai.com for a CCI
    // token set, then token-exchanged for a CCS token the legacy ccapi:8080 endpoints
    // still accept. Ported from hyundai_kia_connect_api KiaUvoApiEU._login_with_password_cci.
    internal val oneAppClientId get() = "4f4953b5-02e1-4dbc-8599-87e983ee1be5"
    internal val oneAppRedirectUri get() = "https://oneapp.hyundai.com/redirect"
    internal val cciApiUrl get() = "https://cci-api-eu.hyundai.com"
    internal val cciPackageId get() = "com.hyundai.oneapp.eu"
    internal val cciClientName get() = "hyundai"
    internal val cciClientVersion get() = "1.3.3"
    internal val cciClientOsVersion get() = "18.7"
    internal val cciNotificationProvider get() = "APNS"

    companion object {
        private const val USER_AGENT_OKHTTP = "okhttp/3.12.0"
        // The IDPConnect authorize endpoint 400s without the "_CCS_APP_AOS" suffix.
        internal const val USER_AGENT_IDP =
            "Mozilla/5.0 (Linux; Android 4.1.1; Galaxy Nexus Build/JRO03C) AppleWebKit/535.19 " +
                "(KHTML, like Gecko) Chrome/18.0.1025.166 Mobile Safari/535.19_CCS_APP_AOS"

        /** A fresh device id (persisted; the ccsp-device-id is derived per login). */
        fun newDeviceId(): String = UUID.randomUUID().toString()

        // Force-refresh polling: the car reports asynchronously (~20s live), so poll /latest this
        // many times at this interval (≈20s cap) waiting for the snapshot timestamp to advance.
        // Kept small for EU's strict rate limits.
        private const val REFRESH_POLLS = 5
        private const val REFRESH_POLL_INTERVAL_MS = 4000L

        // Same config as the shared parser -- see [ApiHttp.json].
        private val sharedJson = ApiHttp.json

        // The one shared OkHttp stack -- see [ApiHttp].
        internal val sharedClient: OkHttpClient get() = ApiHttp.client
    }

    internal val json get() = sharedJson

    internal val jsonMedia = "application/json;charset=UTF-8".toMediaType()

    internal val client: OkHttpClient get() = sharedClient

    // The CCAPI stamp binds to the request time in Unix SECONDS (see EuStamp).
    internal fun nowStamp(): String = EuStamp.generate(unixSeconds = System.currentTimeMillis() / 1000)

    // --- Headers -------------------------------------------------------------

    /** CCAPI service headers every prd.eu-ccapi call needs (pre- or post-auth). */
    internal fun Request.Builder.apiHeaders(): Request.Builder = this
        .header("Content-Type", "application/json;charset=UTF-8")
        .header("ccsp-service-id", serviceId)
        .header("ccsp-application-id", EuStamp.APP_ID)
        .header("Stamp", nowStamp())
        .header("Host", host)
        .header("Connection", "Keep-Alive")
        .header("Accept-Encoding", "gzip")
        .header("User-Agent", USER_AGENT_OKHTTP)

    /** [apiHeaders] plus the bearer access token, device id and CCS2-support flag. */
    internal fun Request.Builder.authHeaders(session: EuSession, ccs2: Int): Request.Builder =
        apiHeaders()
            // Strip any prefix a stored session may still carry from the old CCI bug, so an existing
            // login is corrected on its first call instead of needing a re-login ("Bearer Bearer ..."
            // is rejected with a 403).
            .header("Authorization", "Bearer " + session.accessToken.removePrefix("Bearer ").trim())
            .header("ccsp-device-id", session.deviceId)
            .header("Ccuccs2protocolsupport", ccs2.toString())

    /**
     * [authHeaders] with the PIN-derived control token in both Authorization and AuthorizationCCSP
     * — CCS2 control endpoints authenticate on the control token, not the plain access token.
     * [controlToken] already carries "Bearer ".
     */
    internal fun Request.Builder.commandHeaders(session: EuSession, ccs2: Int, controlToken: String): Request.Builder =
        authHeaders(session, ccs2)
            .header("Authorization", controlToken)
            .header("AuthorizationCCSP", controlToken)

    // --- Auth ----------------------------------------------------------------

    /**
     * Registers this device with the CCAPI push channel and returns the `ccsp-device-id` all
     * authenticated calls carry. Bloo doesn't use CCAPI push — it only needs the device id the
     * register call mints from a generated push registration id. Ported from
     * KiaUvoApiEU._get_device_id (no auth token).
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

    /** Exchange the refresh token for a fresh access token (no re-login). */
    suspend fun refresh(session: EuSession): EuSession = withContext(Dispatchers.IO) {
        // A OneApp/CCI session refreshes through the CCI token set, not the legacy OAuth
        // refresh_token grant (the CCS token can't be refreshed on its own). See [refreshCci].
        if (session.cciAccessToken != null || session.nonCcsToken != null) {
            return@withContext refreshCci(session)
        }
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
     * Mint the short-lived control token every command needs by verifying the PIN (PUT user/pin).
     * Not cached here — [EuRepository] caches it and refetches on a 401, like [CanadaRepository]
     * does with `pAuth`.
     */
    suspend fun controlToken(session: EuSession, pin: String): String = withContext(Dispatchers.IO) {
        // Hyundai answers a missing or malformed PIN with a bare HTTP 400, which read as an outage.
        val cleanPin = pin.trim()
        if (cleanPin.length != 4 || !cleanPin.all { it.isDigit() }) {
            throw BlueLinkException("Enter your 4-digit Bluelink service PIN to send commands")
        }
        val body = buildJsonObject { put("deviceId", session.deviceId); put("pin", cleanPin) }
            .toString().toRequestBody(jsonMedia)
        val req = Request.Builder().url(userApi + "pin")
            .header("Content-Type", "application/json")
            // Strip any prefix a stored session may still carry from the old CCI bug, so an existing
            // login is corrected on its first call instead of needing a re-login ("Bearer Bearer ..."
            // is rejected with a 403).
            .header("Authorization", "Bearer " + session.accessToken.removePrefix("Bearer ").trim())
            .header("Host", host)
            .header("Accept-Encoding", "gzip")
            .header("User-Agent", USER_AGENT_OKHTTP)
            .put(body).build()
        val token = try {
            call(req).path("controlToken").str()
        } catch (e: BlueLinkException) {
            if (e.code == 400) throw BlueLinkException("Incorrect service PIN", code = 400) else throw e
        } ?: throw BlueLinkException("Incorrect service PIN")
        if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    // --- Status / location ---------------------------------------------------

    /**
     * Latest CCS2 vehicle state (carstatus is on the v1 SPA API; only the ccs2 control commands are
     * on v2).
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

    /**
     * Last-known parked GPS via the dedicated `/location/park` endpoint (returns
     * `resMsg.coord.lat/lon`) — the CCS2 carstatus snapshot doesn't reliably carry a position.
     * Non-rate-limited (parked position), access-token auth.
     */
    suspend fun location(session: EuSession, v: EuVehicleSummary): GeoLocation? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(spa + "vehicles/${v.id}/location/park")
            .get().authHeaders(session, v.ccs2).build()
        val loc = call(req).path("resMsg") ?: return@withContext null
        val lat = loc.path("coord", "lat").dbl()
        val lon = loc.path("coord", "lon").dbl()
        if (lat != null && lon != null) GeoLocation(lat, lon, loc.path("speed", "value").dbl()) else null
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

    internal suspend fun control(
        session: EuSession, v: EuVehicleSummary, controlToken: String, path: String, cmd: JsonObject,
    ) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(spaV2 + "vehicles/${v.id}/ccs2/control/$path")
            .post(cmd.toString().toRequestBody(jsonMedia))
            .commandHeaders(session, v.ccs2, controlToken).build()
        call(req)
        Unit
    }

    // --- Plumbing ------------------------------------------------------------

    /**
     * RSA-PKCS1v1.5-encrypt [password] with the JWK public key ([nB64Url]/[eB64Url] are base64url
     * modulus/exponent), returning lowercase hex — matching the reference's
     * `cipher.encrypt(pw).hex()`.
     */
    internal fun rsaEncryptHex(password: String, nB64Url: String, eB64Url: String): String {
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

    /**
     * Runs [request] on [httpClient] and returns the parsed JSON body. Throws on non-2xx (401 ->
     * [EuRepository] refreshes + retries) and on an in-band `retCode == "F"` error. The failing
     * method+path is included in the message. See [ResponseFraming]: GET-only retry on a fresh
     * connection for an unframable body.
     */
    internal fun call(request: Request, httpClient: OkHttpClient = this.client): JsonElement =
        ResponseFraming.retryOnceOnFreshConnection(request) { rawCall(it, httpClient) }

    internal fun rawCall(request: Request, httpClient: OkHttpClient): JsonElement =
        httpClient.newCall(request).execute().use { resp ->
            val text = resp.bodyWithSlowReadLog()
            val root = if (text.isBlank()) JsonObject(emptyMap())
            else runCatching { json.parseToJsonElement(text) }.getOrNull() ?: JsonObject(emptyMap())
            val where = "${request.method} ${request.url.encodedPath}"
            // In-band CCAPI status (resCode/resMsg) can accompany EITHER a 2xx or a 4xx HTTP
            // status. Codes from the reference's _check_response_for_errors.
            val resCode = root.path("resCode").str()
            val resMsg = root.path("resMsg").str()

            // 4004 "Duplicate request": an identical command is already being processed server-side
            // (e.g. a command fired right after a refresh, or a double-tap) — the request DID land,
            // so treat it as an accepted no-op whatever the HTTP status, and never retry (that just
            // duplicates again).
            if (resCode == "4004") {
                AppLog.log("$where: duplicate request — already accepted, ignoring")
                return@use root
            }

            // Only genuine token/device expiry retries (mapped to 401). 7501 = auth, 4002 = bad
            // deviceId, or an explicit "token expired" message. A plain HTTP 401 counts too.
            // Everything else is a terminal error (no retry) — notably we do NOT treat a bare 403
            // as retryable.
            val expired = resp.code == 401 || resCode == "7501" || resCode == "4002" ||
                (resMsg?.contains("token", true) == true && resMsg.contains("expired", true))

            if (!resp.isSuccessful || root.path("retCode").str() == "F") {
                val msg = resMsg ?: friendly(resp.code, text)
                AppLog.log("ERROR ${resp.code} $where: $msg (resCode $resCode)")
                throw BlueLinkException("$msg [$where]", code = if (expired) 401 else resp.code)
            }
            root
        }

    internal fun friendly(code: Int, body: String): String {
        val msg = runCatching {
            json.parseToJsonElement(body).obj()?.let { it["resMsg"] ?: it.path("error", "message") }?.str()
        }.getOrNull()
        return msg?.takeIf { it.isNotBlank() } ?: "Europe request failed (HTTP $code)"
    }

    /**
     * Filters the 12V auxiliary battery reading the way the reference project's own
     * `normalize_battery_soc` does, which this file's raw `.intLoose()` read skipped entirely:
     * `Electronics.Battery.SensorReliability == 1` means the CCS2 stack is flagging the reading
     * itself as unreliable (an expected state after a 12V reset or during an ICCU fault on IONIQ 5
     * / Kia EV, not an error to surface), and a raw value outside 0..100 is a sentinel (255/0xFF,
     * -1) rather than a real percentage.
     */
    internal fun normalizeBattery12V(level: Int?, sensorReliability: Int?): Int? {
        if (sensorReliability == 1) return null
        if (level == null || level !in 0..100) return null
        return level
    }
}
