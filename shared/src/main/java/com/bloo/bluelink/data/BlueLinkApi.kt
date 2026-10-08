package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import kotlinx.coroutines.delay

/**
 * Thin client over the real Hyundai Blue Link US telematics API. Base URL, credentials, paths and
 * headers come from the reverse-engineered community projects referenced in [Models].
 */
class BlueLinkApi(private val brand: Brand = Brand.HYUNDAI) {
    // These four all delegate straight to the [brand] passed at construction time, so one
    // BlueLinkApi instance can be pointed at either Hyundai or Genesis (both share this same API
    // shape, just different base URLs and OAuth credentials) just by constructing it with a
    // different Brand.
    internal val baseUrl get() = brand.baseUrl

    internal val host get() = brand.host

    internal val clientId get() = brand.clientId

    internal val clientSecret get() = brand.clientSecret

    companion object {
        // Two different User-Agent strings the real endpoints expect depending on which call is
        // being made: plain okhttp's default-looking UA for most authenticated calls, and Postman's
        // UA specifically for the OAuth token endpoints (mirrors what the reverse-engineered
        // reference clients observed the real app sending to each).
        const val UA_OKHTTP = "okhttp/3.12.0"
        const val UA_POSTMAN = "PostmanRuntime/7.26.10"

        // PROCESS-WIDE, not per-instance.
    }

    internal val json get() = ApiHttp.json

    internal val client: OkHttpClient get() = ApiHttp.client

    // The two request-body content types this API's endpoints expect: plain JSON for most commands,
    // and form-urlencoded specifically for lock/unlock (see [formCommand] below) — sending the
    // wrong one for a given endpoint results in the server rejecting the body.
    internal val jsonMedia = "application/json".toMediaType()

    internal val formMedia = "application/x-www-form-urlencoded".toMediaType()

    // --- Auth ------------------------------------------------------------

    /** Exchange a username/password for a fresh access+refresh token pair. */
    suspend fun login(username: String, password: String): TokenResponse = execute {
        val body = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                put("username", kotlinx.serialization.json.JsonPrimitive(username))
                put("password", kotlinx.serialization.json.JsonPrimitive(password))
            }
        ).toRequestBody(jsonMedia)
        json.decodeFromString(TokenResponse.serializer(), call(tokenRequest("/v2/ac/oauth/token", body)))
    }

    /**
     * Exchange a still-valid refresh token for a new access token, without requiring the user's
     * password again. Same request shape/endpoint family as [login], just a different path and body
     * field.
     */
    suspend fun refresh(refreshToken: String): TokenResponse = execute {
        val body = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                put("refresh_token", kotlinx.serialization.json.JsonPrimitive(refreshToken))
            }
        ).toRequestBody(jsonMedia)
        json.decodeFromString(TokenResponse.serializer(), call(tokenRequest("/v2/ac/oauth/token/refresh", body)))
    }

    /** The OAuth token endpoints' own request shape: POST [path] with the JSON [body] and the
     *  client_id/secret + Postman UA headers they expect. */
    private fun tokenRequest(path: String, body: okhttp3.RequestBody): Request = Request.Builder()
        .url("$baseUrl$path")
        .post(body)
        .header("Content-Type", "application/json")
        .header("client_id", clientId)
        .header("client_secret", clientSecret)
        .header("User-Agent", UA_POSTMAN)
        .build()

    // --- Vehicles --------------------------------------------------------

    /** Fetch every car enrolled on this account. */
    suspend fun vehicles(accessToken: String, username: String): List<Vehicle> = execute {
        val request = Request.Builder()
            .url("$baseUrl/ac/v2/enrollment/details/$username")
            .get()
            .header("access_token", accessToken)
            .header("client_id", clientId)
            .header("Host", host)
            .header("User-Agent", UA_OKHTTP)
            .header("payloadGenerated", "20200226171938")
            .header("includeNonConnectedVehicles", "Y")
            .build()
        val body = call(request)
        // Cold-start diagnostic: a real device log showed this whole suspend function (dispatch
        // onto IO + call() + this) taking ~2.1s total while call()'s own body- read logged only
        // 644ms of that -- ~1.5s unaccounted for. call() already times connect+headers+body-read as
        // one number, so it can't say which SIDE of that gap the rest is on.
        val decodeStartedAt = System.currentTimeMillis()
        val parsed = json.decodeFromString(EnrollmentResponse.serializer(), body)
        val vehicles = parsed.enrolledVehicleDetails.map { it.vehicleDetails.toVehicle(brand) }
        val decodeMs = System.currentTimeMillis() - decodeStartedAt
        if (decodeMs > 200) {
            AppLog.log("BlueLinkApi.vehicles(): decode + map took ${decodeMs}ms")
        }
        vehicles
    }

    // --- Commands --------------------------------------------------------

    /**
     * Fetch the car's current status. [refresh] controls whether the car is asked to report *fresh*
     * telemetry (a live poll, slower and rate limited) via the REFRESH header, versus just
     * returning whatever the server last cached from the car — the caller decides which trade-off
     * it wants.
     */
    suspend fun status(token: String, username: String, pin: String, v: Vehicle, refresh: Boolean): VehicleStatus? =
        execute {
            val request = baseRequest("/ac/v2/rcs/rvs/vehicleStatus", token, username, pin, v)
                .get()
                .header("REFRESH", refresh.toString())
                .build()
            json.decodeFromString(VehicleStatusResponse.serializer(), call(request)).vehicleStatus
        }

    /** Fetch the car's last-known GPS fix via the dedicated (rate-limited) findMyCar endpoint. */
    suspend fun location(token: String, username: String, pin: String, v: Vehicle): GeoLocation? =
        execute {
            val request = baseRequest("/ac/v2/rcs/rfc/findMyCar", token, username, pin, v)
                .get()
                .build()
            val parsed = json.decodeFromString(VehicleLocationResponse.serializer(), call(request))
            val coord = parsed.coord
            val lat = coord?.lat
            val lon = coord?.lon
            if (lat != null && lon != null) GeoLocation(lat, lon, parsed.speed?.value) else null
        }

    /**
     * Recent drives with (for EVs) energy breakdowns. Mirrors the community client's
     * _get_ev_trip_details; cars whose head unit doesn't report trips return an empty list (the
     * caller treats a failure here as "no trips").
     */
    suspend fun tripDetails(token: String, username: String, pin: String, v: Vehicle): List<EvTrip> =
        execute {
            val request = baseRequest("/ac/v2/ts/alerts/maintenance/evTripDetails", token, username, pin, v)
                .header("userId", username)
                .get()
                .build()
            json.decodeFromString(EvTripDetailsResponse.serializer(), call(request)).tripdetails
        }

    /**
     * Lock the doors. Confusingly named endpoint: "rdo/off" locks (remote door operation, off =
     * secured), not the other way around.
     */
    suspend fun lock(token: String, username: String, pin: String, v: Vehicle) =
        formCommand("/ac/v2/rcs/rdo/off", token, username, pin, v)

    /** Unlock the doors ("rdo/on" — see [lock] for the naming logic). */
    suspend fun unlock(token: String, username: String, pin: String, v: Vehicle) =
        formCommand("/ac/v2/rcs/rdo/on", token, username, pin, v)

    /**
     * Flash the hazard lights only. Reference client: rcs/rhl/light, same userName+vin JSON body
     * and header set as lock/unlock. Hyundai/Genesis only -- Kia's US API has no equivalent
     * endpoint.
     */
    suspend fun flashLights(token: String, username: String, pin: String, v: Vehicle) =
        jsonCommand("/ac/v2/rcs/rhl/light", token, username, pin, v)

    /** Flash the hazard lights and sound the horn. Reference client: rcs/rhl/hnl. */
    suspend fun hornAndLights(token: String, username: String, pin: String, v: Vehicle) =
        jsonCommand("/ac/v2/rcs/rhl/hnl", token, username, pin, v)

    suspend fun stopClimate(token: String, username: String, pin: String, v: Vehicle): String = execute {
        // Pure EVs use evc/fatc/stop (no engine). ICE and PHEVs use rcs/rsc/stop (remote engine
        // start can be cancelled). The v.isEv flag comes from the enrollment API and is true only
        // for pure EVs — PHEVs are false.
        val path = if (v.isEv) "/ac/v2/evc/fatc/stop" else "/ac/v2/rcs/rsc/stop"
        val request = baseRequest(path, token, username, pin, v)
            .post(ByteArray(0).toRequestBody(null))
            .build()
        call(request)
    }

    /** Start charging (EV). Real US endpoint: /ac/v2/evc/charge/start */
    suspend fun startCharge(token: String, username: String, pin: String, v: Vehicle): String = execute {
        val request = baseRequest("/ac/v2/evc/charge/start", token, username, pin, v)
            .post(ByteArray(0).toRequestBody(null))
            .build()
        call(request)
    }

    /** Stop charging (EV). Real US endpoint: /ac/v2/evc/charge/stop */
    suspend fun stopCharge(token: String, username: String, pin: String, v: Vehicle): String = execute {
        val request = baseRequest("/ac/v2/evc/charge/stop", token, username, pin, v)
            .post(ByteArray(0).toRequestBody(null))
            .build()
        call(request)
    }

    /**
     * Shared body for the form-urlencoded commands (lock/unlock): a minimal `userName=...&vin=...`
     * body posted with [formMedia], on top of the usual [baseRequest] auth/vehicle headers.
     */
    internal suspend fun formCommand(
        path: String, token: String, username: String, pin: String, v: Vehicle,
    ): String = execute {
        val form = "userName=$username&vin=${v.vin}".toRequestBody(formMedia)
        val request = baseRequest(path, token, username, pin, v)
            .post(form)
            .build()
        call(request)
    }

    /**
     * Same userName+vin payload as [formCommand], but JSON -- the horn/lights endpoints reject the
     * form-urlencoded body lock/unlock use.
     */
    internal suspend fun jsonCommand(
        path: String, token: String, username: String, pin: String, v: Vehicle,
    ): String = execute {
        val body = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                put("userName", kotlinx.serialization.json.JsonPrimitive(username))
                put("vin", kotlinx.serialization.json.JsonPrimitive(v.vin))
            },
        ).toRequestBody(jsonMedia)
        val request = baseRequest(path, token, username, pin, v)
            .post(body)
            .build()
        call(request)
    }

    /**
     * Builds the common header set every authenticated command request needs (auth token in two
     * different header names, vehicle identifiers, locale/routing headers the API expects even
     * though most of their values never vary) — [path] is joined onto [baseUrl] and the caller
     * still needs to attach its own HTTP method + body before calling build().
     */
    internal fun baseRequest(
        path: String, token: String, username: String, pin: String, v: Vehicle,
    ): Request.Builder = Request.Builder()
        .url("$baseUrl$path")
        .header("access_token", token)
        // The reference client also passes the access token as `accessToken` and the secret as
        // `clientSecret` on every command; some endpoints (findMyCar, fatc) appear to validate
        // these even though rdo does not.
        .header("accessToken", token)
        .header("client_id", clientId)
        .header("clientSecret", clientSecret)
        .header("accept", "application/json, text/plain, */*")
        .header("accept-language", "en-US,en;q=0.9")
        .header("Host", host)
        .header("User-Agent", UA_OKHTTP)
        .header("registrationId", v.regId)
        .header("gen", v.generation)
        .header("vin", v.vin)
        .header("APPCLOUD-VIN", v.vin)
        .header("username", username)
        .header("blueLinkServicePin", pin)
        .header("offset", gmtOffsetHours())
        .header("Language", "0")
        .header("language", "0")
        .header("to", "ISS")
        .header("encryptFlag", "false")
        .header("from", "SPA")
        .header("brandIndicator", v.brandIndicator.ifBlank { brand.code })

    // --- Plumbing --------------------------------------------------------

    /**
     * Like [call], but retries once after a short pause when the server returns a transient 5xx.
     * Used for HVAC, where Hyundai occasionally 502s a valid call.
     */
    internal suspend fun callWithRetry(request: Request): String {
        return try {
            call(request)
        } catch (e: BlueLinkException) {
            val transient = e.code != null && e.code in 500..599
            if (!transient) throw e
            AppLog.log("Retrying ${request.method} ${request.url.encodedPath} after ${e.code}…")
            delay(1500)
            call(request)
        } catch (t: Throwable) {
            // A response whose body cannot be framed (see ResponseFraming) fails while READING the
            // body, so the server already answered and the connection is the suspect. GETs only:
            // re-sending a command POST could fire it twice.
            if (ResponseFraming.isFramingFailure(t)) {
                ResponseFraming.retryOnceOnFreshConnection(request) { call(it) }
            } else {
                throw t
            }
        }
    }

    /**
     * Executes [request] synchronously (must run off the main thread — all callers go through
     * [execute]'s Dispatchers.IO) and returns the raw response body text.
     */
    internal fun call(request: Request): String {
        // The HttpLoggingInterceptor's own logged "Nms" (BASIC level) times only chain.proceed() --
        // which OkHttp returns as soon as the response STATUS LINE and HEADERS are parsed, with the
        // body left as a lazy, unread stream tied to the live socket.
        client.newCall(request).execute().use { resp ->
            val text = resp.bodyWithSlowReadLog()
            if (!resp.isSuccessful) {
                val message = friendlyError(resp.code, text)
                AppLog.log("ERROR ${resp.code} ${request.method} ${request.url.encodedPath}: $message")
                throw BlueLinkException(message, code = resp.code)
            }
            return text
        }
    }

    /** Pull the human-readable message out of Blue Link's JSON error envelope. */
    internal fun friendlyError(code: Int, body: String): String {
        val message = runCatching {
            json.parseToJsonElement(body).let { el ->
                (el as? kotlinx.serialization.json.JsonObject)?.let { obj ->
                    // contentOrNull + the "null" sentinel guard, per key, BEFORE the `?:`.
                    // Normalising each key to a real String? first fixes both: the "null" literal
                    // is dropped and the fallback actually reaches errorSubMessage.
                    fun field(key: String): String? =
                        (obj[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }
                    field("errorMessage") ?: field("errorSubMessage")
                }
            }
        }.getOrNull()
        return message?.takeIf { it.isNotBlank() } ?: "Request failed (HTTP $code)"
    }

    /**
     * Runs [block] on the IO dispatcher (network calls shouldn't block the caller's thread) and
     * normalises any thrown exception into a [BlueLinkException] — a [BlueLinkException] thrown
     * deeper (e.g. by [call]) passes through unchanged so its HTTP code/message survive, while any
     * other exception (IOException, SerializationException, etc.) is wrapped so every public method
     * on this class has one exception type callers need to handle.
     */
    internal suspend fun <T> execute(block: suspend () -> T): T {
        // Logged only past a threshold, unconditionally (not just for the cold-start path -- see
        // BlueLinkRepository/AppViewModel's own logStartupTiming for that): the mutex was proven
        // instant and the actual HTTP request/response were both proven fast in a real report, yet
        // several real seconds still elapsed somewhere between the two -- this
        // withContext(Dispatchers.IO) hop is the next-most-likely place for that time to be hiding
        // (a busy/starved IO dispatcher, or a slow one-time class-load the very first time this
        // call shape runs), so it needs its own number rather than being assumed instant the way
        // coroutine dispatch onto Main already was ruled out to be.
        val dispatchStartedAt = System.currentTimeMillis()
        return withContext(Dispatchers.IO) {
            val dispatchMs = System.currentTimeMillis() - dispatchStartedAt
            if (dispatchMs > 500) {
                AppLog.log("BlueLinkApi: dispatch onto Dispatchers.IO took ${dispatchMs}ms")
            }
            try {
                block()
            } catch (e: BlueLinkException) {
                throw e
            } catch (e: Exception) {
                // A cancelled coroutine is not a network error: let it unwind as itself.
                if (e is kotlinx.coroutines.CancellationException) throw e
                throw BlueLinkException(e.message ?: "Network error", e)
            }
        }
    }
}

/**
 * Flattens the API's nested [VehicleDetails] onto the UI-facing [Vehicle] shape, filling in
 * sensible fallbacks for anything the API left blank: an unnamed car falls back to its model name,
 * then to the last 6 VIN characters; a missing generation defaults to "2" (the most common case
 * among the reference clients' sample data); isEv is derived from the single-character evStatus
 * code ("E" specifically, case-insensitively).
 */
private fun VehicleDetails.toVehicle(brand: Brand): Vehicle = Vehicle(
    vin = vin,
    regId = regid,
    name = vehicleDisplayName(nickName, modelName, vin),
    model = listOfNotNull(modelYear, modelName).joinToString(" ").ifBlank { brand.label },
    generation = vehicleGeneration ?: "2",
    brandIndicator = brandIndicator ?: "",
    isEv = evStatus.equals("E", ignoreCase = true),
    odometer = odometer,
)

/**
 * The one exception type every [BlueLinkApi] public method can throw (see [BlueLinkApi.execute]).
 * [code] carries the HTTP status when the failure came from a server response (null for a pure
 * network/parse failure), which callers use to distinguish e.g. an expired-session 401 from a
 * generic error.
 */
class BlueLinkException(
    message: String,
    cause: Throwable? = null,
    val code: Int? = null,
) : Exception(message, cause)
