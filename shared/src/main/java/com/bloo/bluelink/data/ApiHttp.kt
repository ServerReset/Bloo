package com.bloo.bluelink.data

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

/**
 * The one HTTP stack and JSON parser every brand API client shares.
 *
 * Blue Link, Kia US, Europe and Canada each built their own — the same
 * `OkHttpClient` (30s connect, 60s read, a BASIC-level logging interceptor that
 * routes to [AppLog]) and, for three of them, the same lenient [Json] — as
 * process-wide `sharedClient`/`sharedJson` companions. Four copies of an OkHttp
 * builder is four places a timeout or an interceptor can drift, and the JSON
 * config has to agree across brands anyway (they all parse the same evolving
 * OEM payloads). Both live here now, so each client is `ApiHttp.client` /
 * `ApiHttp.json`.
 *
 * PROCESS-WIDE, not per-client: a real account's cold start constructs several
 * clients, and a per-client OkHttpClient carries its own dispatcher, thread
 * pool, connection pool and route database — no TLS or connection reuse across
 * them. OkHttpClient and Json are both thread-safe and designed to be shared.
 */
object ApiHttp {
    val json: Json = Json {
        // The OEM payloads evolve (new fields, generation-specific quirks) faster
        // than the models are updated, so unknown keys are ignored instead of throwing.
        ignoreUnknownKeys = true
        // Some fields arrive as a type the model doesn't expect (numbers where a
        // string is modelled, etc.); coerce to a best-effort value rather than
        // failing the whole decode. isLenient eases the OEMs' occasionally loose JSON.
        isLenient = true
        coerceInputValues = true
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(
            // BASIC logs the request/response LINE only (no bodies), so a password,
            // PIN or token in an auth body is never written to the log.
            HttpLoggingInterceptor { line -> AppLog.log(line) }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            },
        )
        .build()
}
