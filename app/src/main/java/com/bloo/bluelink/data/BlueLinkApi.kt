package com.bloo.bluelink.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Thin client over the real Hyundai Blue Link US telematics API.
 *
 * Base URL, credentials, paths and headers come from the reverse-engineered
 * community projects referenced in [Models]. There is no mock/simulated path:
 * every call goes to https://api.telematics.hyundaiusa.com.
 */
class BlueLinkApi(private val brand: Brand = Brand.HYUNDAI) {

    private val baseUrl get() = brand.baseUrl
    private val host get() = brand.host
    private val clientId get() = brand.clientId
    private val clientSecret get() = brand.clientSecret

    companion object {
        const val UA_OKHTTP = "okhttp/3.12.0"
        const val UA_POSTMAN = "PostmanRuntime/7.26.10"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(
            // BASIC level logs the request line + response line only (no bodies),
            // so the password in the auth body is never written to the log.
            HttpLoggingInterceptor { line -> AppLog.log(line) }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        )
        .build()

    private val jsonMedia = "application/json".toMediaType()
    private val formMedia = "application/x-www-form-urlencoded".toMediaType()

    /** Current GMT offset in whole hours (e.g. -5 EST, -4 EDT). */
    private fun gmtOffsetHours(): String {
        val offsetMs = TimeZone.getDefault().getOffset(System.currentTimeMillis())
        return (offsetMs / 3_600_000).toString()
    }

    // --- Auth ------------------------------------------------------------

    suspend fun login(username: String, password: String): TokenResponse = execute {
        val body = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                put("username", kotlinx.serialization.json.JsonPrimitive(username))
                put("password", kotlinx.serialization.json.JsonPrimitive(password))
            }
        ).toRequestBody(jsonMedia)

        val request = Request.Builder()
            .url("$baseUrl/v2/ac/oauth/token")
            .post(body)
            .header("Content-Type", "application/json")
            .header("client_id", clientId)
            .header("client_secret", clientSecret)
            .header("User-Agent", UA_POSTMAN)
            .build()
        json.decodeFromString(TokenResponse.serializer(), call(request))
    }

    suspend fun refresh(refreshToken: String): TokenResponse = execute {
        val body = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                put("refresh_token", kotlinx.serialization.json.JsonPrimitive(refreshToken))
            }
        ).toRequestBody(jsonMedia)

        val request = Request.Builder()
            .url("$baseUrl/v2/ac/oauth/token/refresh")
            .post(body)
            .header("Content-Type", "application/json")
            .header("client_id", clientId)
            .header("client_secret", clientSecret)
            .header("User-Agent", UA_POSTMAN)
            .build()
        json.decodeFromString(TokenResponse.serializer(), call(request))
    }

    // --- Vehicles --------------------------------------------------------

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
        val parsed = json.decodeFromString(EnrollmentResponse.serializer(), call(request))
        parsed.enrolledVehicleDetails.map { it.vehicleDetails.toVehicle() }
    }

    // --- Commands --------------------------------------------------------

    suspend fun status(token: String, username: String, pin: String, v: Vehicle, refresh: Boolean): VehicleStatus? =
        execute {
            val request = baseRequest("/ac/v2/rcs/rvs/vehicleStatus", token, username, pin, v)
                .get()
                .header("REFRESH", refresh.toString())
                .build()
            json.decodeFromString(VehicleStatusResponse.serializer(), call(request)).vehicleStatus?.also { st ->
                // Diagnostic: does the status payload carry GPS? (presence only)
                val hasLoc = st.vehicleLocation?.coord?.lat != null
                AppLog.log("status ${v.name}: embedded location ${if (hasLoc) "present" else "absent"}")
            }
        }

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
     * Recent drives with (for EVs) energy breakdowns. Mirrors the community
     * client's _get_ev_trip_details; cars whose head unit doesn't report trips
     * return an empty list (the caller treats a failure here as "no trips").
     */
    suspend fun tripDetails(token: String, username: String, pin: String, v: Vehicle): List<EvTrip> =
        execute {
            val request = baseRequest("/ac/v2/ts/alerts/maintenance/evTripDetails", token, username, pin, v)
                .header("userId", username)
                .get()
                .build()
            json.decodeFromString(EvTripDetailsResponse.serializer(), call(request)).tripdetails
        }

    suspend fun lock(token: String, username: String, pin: String, v: Vehicle) =
        formCommand("/ac/v2/rcs/rdo/off", token, username, pin, v)

    suspend fun unlock(token: String, username: String, pin: String, v: Vehicle) =
        formCommand("/ac/v2/rcs/rdo/on", token, username, pin, v)

    suspend fun stopClimate(token: String, username: String, pin: String, v: Vehicle): String = execute {
        // Pure EVs use evc/fatc/stop (no engine). ICE and PHEVs use rcs/rsc/stop
        // (remote engine start can be cancelled). The v.isEv flag comes from the
        // enrollment API and is true only for pure EVs — PHEVs are false.
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

    /** Start climate / remote start. Temperature is Fahrenheit for US vehicles. */
    suspend fun startClimate(
        token: String, username: String, pin: String, v: Vehicle, req: ClimateRequest,
    ): String = execute {
        // Body shapes mirror the community hyundai_kia_connect_api exactly. Newer
        // head units (Gen5W) reject the bloated body the old code sent (it included
        // Ims/username/vin/seat info on every EV) with a 502 "could not complete
        // your request". For EVs the accepted body is minimal; seat-heat + duration
        // are only honoured on generation-3 cars, and Ims/username/vin are
        // ICE-only fields.
        fun seatInfo() = kotlinx.serialization.json.buildJsonObject {
            put("drvSeatHeatState", kotlinx.serialization.json.JsonPrimitive(req.seatFrontLeft.apiValue))
            put("astSeatHeatState", kotlinx.serialization.json.JsonPrimitive(req.seatFrontRight.apiValue))
            put("rlSeatHeatState", kotlinx.serialization.json.JsonPrimitive(req.seatRearLeft.apiValue))
            put("rrSeatHeatState", kotlinx.serialization.json.JsonPrimitive(req.seatRearRight.apiValue))
        }
        val isEv = v.isEv
        val gen3 = v.generation.trim() == "3"
        val path = if (isEv) "/ac/v2/evc/fatc/start" else "/ac/v2/rcs/rsc/start"
        val payload = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                if (isEv) {
                    put("airCtrl", kotlinx.serialization.json.JsonPrimitive(1))
                    put("airTemp", kotlinx.serialization.json.buildJsonObject {
                        put("value", kotlinx.serialization.json.JsonPrimitive(req.tempF.toString()))
                        put("unit", kotlinx.serialization.json.JsonPrimitive(1))
                    })
                    put("defrost", kotlinx.serialization.json.JsonPrimitive(req.defrost))
                    put("heating1", kotlinx.serialization.json.JsonPrimitive(if (req.steeringWheelHeat) 1 else 0))
                    // Older (gen-3) EVs additionally accept duration + seat heat.
                    if (gen3) {
                        put("igniOnDuration", kotlinx.serialization.json.JsonPrimitive(req.durationMinutes))
                        put("seatHeaterVentInfo", seatInfo())
                    }
                } else {
                    put("Ims", kotlinx.serialization.json.JsonPrimitive(0))
                    put("airCtrl", kotlinx.serialization.json.JsonPrimitive(1))
                    put("airTemp", kotlinx.serialization.json.buildJsonObject {
                        put("unit", kotlinx.serialization.json.JsonPrimitive(1))
                        put("value", kotlinx.serialization.json.JsonPrimitive(req.tempF.toString()))
                    })
                    put("defrost", kotlinx.serialization.json.JsonPrimitive(req.defrost))
                    put("heating1", kotlinx.serialization.json.JsonPrimitive(if (req.steeringWheelHeat) 1 else 0))
                    put("igniOnDuration", kotlinx.serialization.json.JsonPrimitive(req.durationMinutes))
                    put("seatHeaterVentInfo", seatInfo())
                    put("username", kotlinx.serialization.json.JsonPrimitive(username))
                    put("vin", kotlinx.serialization.json.JsonPrimitive(v.vin))
                }
            }
        ).toRequestBody(jsonMedia)

        val request = baseRequest(path, token, username, pin, v)
            .post(payload)
            .build()
        // One short retry clears the occasional transient 502 without bothering
        // the user.
        callWithRetry(request)
    }

    /** Set EV charge target SOC for AC (plugType 1) and DC (plugType 0) in percent. */
    suspend fun setChargeTargets(
        token: String, username: String, pin: String, v: Vehicle, acPercent: Int, dcPercent: Int,
    ): String = execute {
        val payload = json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.buildJsonObject {
                put("targetSOClist", kotlinx.serialization.json.buildJsonArray {
                    add(kotlinx.serialization.json.buildJsonObject {
                        put("plugType", kotlinx.serialization.json.JsonPrimitive(0))
                        put("targetSOClevel", kotlinx.serialization.json.JsonPrimitive(dcPercent))
                    })
                    add(kotlinx.serialization.json.buildJsonObject {
                        put("plugType", kotlinx.serialization.json.JsonPrimitive(1))
                        put("targetSOClevel", kotlinx.serialization.json.JsonPrimitive(acPercent))
                    })
                })
            }
        ).toRequestBody(jsonMedia)

        val request = baseRequest("/ac/v2/evc/charge/targetsoc/set", token, username, pin, v)
            .post(payload)
            .build()
        call(request)
    }

    private suspend fun formCommand(
        path: String, token: String, username: String, pin: String, v: Vehicle,
    ): String = execute {
        val form = "userName=$username&vin=${v.vin}".toRequestBody(formMedia)
        val request = baseRequest(path, token, username, pin, v)
            .post(form)
            .build()
        call(request)
    }

    private fun baseRequest(
        path: String, token: String, username: String, pin: String, v: Vehicle,
    ): Request.Builder = Request.Builder()
        .url("$baseUrl$path")
        .header("access_token", token)
        // The reference client also passes the access token as `accessToken` and
        // the secret as `clientSecret` on every command; some endpoints
        // (findMyCar, fatc) appear to validate these even though rdo does not.
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
     * Like [call], but retries once after a short pause when the server returns a
     * transient 5xx. Used for HVAC, where Hyundai occasionally 502s a valid call.
     */
    private fun callWithRetry(request: Request): String {
        return try {
            call(request)
        } catch (e: BlueLinkException) {
            val transient = e.code != null && e.code in 500..599
            if (!transient) throw e
            AppLog.log("Retrying ${request.method} ${request.url.encodedPath} after ${e.code}…")
            Thread.sleep(1500)
            call(request)
        }
    }

    private fun call(request: Request): String {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val message = friendlyError(resp.code, text)
                AppLog.log("ERROR ${resp.code} ${request.method} ${request.url.encodedPath}: $message")
                throw BlueLinkException(message, code = resp.code)
            }
            return text
        }
    }

    /** Pull the human-readable message out of Blue Link's JSON error envelope. */
    private fun friendlyError(code: Int, body: String): String {
        val message = runCatching {
            json.parseToJsonElement(body).let { el ->
                (el as? kotlinx.serialization.json.JsonObject)?.let { obj ->
                    (obj["errorMessage"] ?: obj["errorSubMessage"])
                        ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                }
            }
        }.getOrNull()
        return message?.takeIf { it.isNotBlank() } ?: "Request failed (HTTP $code)"
    }

    private suspend fun <T> execute(block: () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: BlueLinkException) {
            throw e
        } catch (e: Exception) {
            throw BlueLinkException(e.message ?: "Network error", e)
        }
    }
}

private fun VehicleDetails.toVehicle(): Vehicle = Vehicle(
    vin = vin,
    regId = regid,
    name = nickName?.takeIf { it.isNotBlank() } ?: modelName ?: vin.takeLast(6),
    model = listOfNotNull(modelYear, modelName).joinToString(" ").ifBlank { "Hyundai" },
    generation = vehicleGeneration ?: "2",
    brandIndicator = brandIndicator ?: "",
    isEv = evStatus.equals("E", ignoreCase = true),
    odometer = odometer,
)

class BlueLinkException(
    message: String,
    cause: Throwable? = null,
    val code: Int? = null,
) : Exception(message, cause)
