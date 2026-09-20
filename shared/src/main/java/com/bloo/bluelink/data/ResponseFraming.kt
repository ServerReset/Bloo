package com.bloo.bluelink.data

import okhttp3.Request

/**
 * Detection and recovery for a malformed HTTP response BODY FRAMING.
 *
 * What this looks like to a user: "Couldn't load your vehicles" with a detail line like
 * `Expected leading [0-9a-fA-F] character but was 0x7b`. That message comes from okio's
 * `readHexadecimalUnsignedLong`, which OkHttp uses to read a CHUNK SIZE -- 0x7b is `{`. In
 * other words the server declared `Transfer-Encoding: chunked` and then sent a raw JSON body
 * with no chunk framing at all. It is not our serialization, not our URL, and not our JSON:
 * the bytes on the wire did not match the protocol the response claimed.
 *
 * It is a real thing that happens through CDNs, WAFs, corporate proxies, VPNs and captive
 * portals, and it is usually TRANSIENT and connection-specific -- the same request on a fresh
 * connection normally succeeds. The connection that produced it is also suspect: OkHttp pools
 * and reuses connections, so a socket that delivered a truncated/misframed body must not be
 * reused for the retry, or the retry inherits the same corruption. Hence `Connection: close`
 * on the retry request, which forces a new socket.
 *
 * Deliberately GET-ONLY at the call sites: a framing failure happens while READING the
 * response, i.e. after the server has already processed the request. Re-sending a GET is
 * harmless; re-sending a POST could fire a car command twice.
 */
object ResponseFraming {

    /** True when [t] is (or wraps) a response-framing failure rather than an app bug. */
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
     *
     * GET-only, enforced here so every call site gets the safe rule without repeating it: a
     * framing failure happens while READING the body, i.e. after the server has already acted
     * on the request, so re-sending a GET is harmless while re-sending a POST could fire a car
     * command twice.
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
