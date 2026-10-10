package com.bloo.bluelink.data

import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/** The one HTTP stack and JSON parser every brand API client shares. */
object ApiHttp {
    val json: Json = Json {
        // The OEM payloads evolve (new fields, generation-specific quirks) faster than the models
        // are updated, so unknown keys are ignored instead of throwing.
        ignoreUnknownKeys = true
        // Some fields arrive as a type the model doesn't expect (numbers where a string is
        // modelled, etc.); coerce to a best-effort value rather than failing the whole decode.
        // isLenient eases the OEMs' occasionally loose JSON.
        isLenient = true
        coerceInputValues = true
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(
            // BASIC logs the request/response LINE only (no bodies), so a password, PIN or token in
            // an auth body is never written to the log.
            HttpLoggingInterceptor { line -> AppLog.log(line) }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            },
        )
        .build()

    /**
     * A fresh client for the side HTTP calls that want shorter, bounded timeouts than the brand APIs
     * (weather, update checks): a slow or absent server should fail fast rather than hold a screen.
     * A new instance per caller (its own pool), not the shared [client].
     */
    fun shortTimeoutClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
}

/**
 * Parse a response body to JSON, converting a malformed/empty/non-JSON body (a WAF HTML block page,
 * a gateway 5xx, a truncated response) into a [BlueLinkException] — which the repository layer
 * already catches — instead of letting a raw SerializationException/IOException crash the app.
 */
fun parseJsonOrThrow(json: Json, text: String, code: Int, message: String): kotlinx.serialization.json.JsonElement =
    runCatching { json.parseToJsonElement(text) }
        .getOrElse { throw BlueLinkException(message, code = code) }

/**
 * The shared request skeleton of the brand clients that report failures in-band (Canada, Kia US): runs [request],
 * retrying once on a fresh connection when a GET's body cannot be framed (see [ResponseFraming]), turns any non-2xx
 * into a [BlueLinkException] carrying [friendly]'s message, parses the body (blank = empty object), and hands the
 * parsed root to [checkInBand], which throws for the brand's own in-band error codes and otherwise returns.
 */
internal fun executeJson(
    client: OkHttpClient,
    json: Json,
    request: okhttp3.Request,
    friendly: (code: Int, body: String) -> String,
    checkInBand: (root: kotlinx.serialization.json.JsonElement, request: okhttp3.Request, httpCode: Int) -> Unit,
): kotlinx.serialization.json.JsonElement =
    ResponseFraming.retryOnceOnFreshConnection(request) { req ->
        client.newCall(req).execute().use { resp ->
            val text = resp.bodyWithSlowReadLog()
            if (!resp.isSuccessful) {
                val msg = friendly(resp.code, text)
                AppLog.log("ERROR ${resp.code} ${req.method} ${req.url.encodedPath}: $msg")
                throw BlueLinkException(msg, code = resp.code)
            }
            val root = if (text.isBlank()) kotlinx.serialization.json.JsonObject(emptyMap())
            else parseJsonOrThrow(json, text, resp.code, friendly(resp.code, text))
            checkInBand(root, req, resp.code)
            root
        }
    }
