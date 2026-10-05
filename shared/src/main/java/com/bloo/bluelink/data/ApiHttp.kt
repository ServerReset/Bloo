package com.bloo.bluelink.data

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

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
}

/**
 * Parse a response body to JSON, converting a malformed/empty/non-JSON body (a WAF HTML block page,
 * a gateway 5xx, a truncated response) into a [BlueLinkException] — which the repository layer
 * already catches — instead of letting a raw SerializationException/IOException crash the app.
 */
fun parseJsonOrThrow(json: Json, text: String, code: Int, message: String): kotlinx.serialization.json.JsonElement =
    runCatching { json.parseToJsonElement(text) }
        .getOrElse { throw BlueLinkException(message, code = code) }
