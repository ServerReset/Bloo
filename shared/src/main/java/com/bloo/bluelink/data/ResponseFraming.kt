package com.bloo.bluelink.data

import okhttp3.Request

/**
 * Detection and recovery for a malformed HTTP response BODY FRAMING. That message comes from okio's
 * `readHexadecimalUnsignedLong`, which OkHttp uses to read a CHUNK SIZE -- 0x7b is `{`.
 */
object ResponseFraming {

    fun isFramingFailure(t: Throwable): Boolean {
        var current: Throwable? = t
        var depth = 0
        while (current != null && depth < 5) {
            val message = current.message.orEmpty()
            if (message.contains("Expected leading [0-9a-fA-F] character") ||
                message.contains("Expected chunk size") ||
                message.contains("Invalid chunk") ||
                message.contains("unexpected end of stream")
            ) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }

    /**
     * Runs [call] on [request]; if it fails with a framing failure, retries ONCE on a fresh
     * connection and returns that result. Anything else propagates untouched.
     */
    fun <T> retryOnceOnFreshConnection(
        request: Request,
        call: (Request) -> T,
    ): T {
        return try {
            call(request)
        } catch (t: Throwable) {
            if (request.method != "GET" || !isFramingFailure(t)) throw t
            AppLog.log(
                "Malformed response framing from ${request.url.host} " +
                    "(${request.method} ${request.url.encodedPath}) — retrying on a new connection.",
            )
            call(
                request.newBuilder()
                    .header("Connection", "close")
                    .build(),
            )
        }
    }

    /** A message worth showing a person, or null when [t] is not a framing failure. */
    fun userMessage(t: Throwable): String? =
        if (isFramingFailure(t)) {
            "The server sent a malformed response. Check your connection and try again."
        } else {
            null
        }
}

/**
 * Measured separately from HttpLoggingInterceptor's own (headers-only) timing: `.string()` is what
 * actually downloads the body, and that can run seconds behind a fast-looking interceptor log on a
 * slow/cellular connection with a real payload.
 */
fun okhttp3.Response.bodyWithSlowReadLog(): String {
    val startedAt = System.currentTimeMillis()
    val text = body?.string().orEmpty()
    val bodyReadMs = System.currentTimeMillis() - startedAt
    if (bodyReadMs > 500) {
        AppLog.log(
            "${request.method} ${request.url.encodedPath}: response body " +
                "(${text.length} chars) took ${bodyReadMs}ms to download/read",
        )
    }
    return text
}
