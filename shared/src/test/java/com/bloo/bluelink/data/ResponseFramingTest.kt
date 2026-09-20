package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [ResponseFraming]'s detection, which is what decides whether a broken HTTP response
 * gets retried on a fresh connection or surfaced to the user as-is.
 *
 * The message it must recognise is not ours: it comes from okio's
 * `readHexadecimalUnsignedLong`, which OkHttp calls to read an HTTP chunk size, and it looks
 * like `Expected leading [0-9a-fA-F] character but was 0x7b` when a server declares
 * `Transfer-Encoding: chunked` and then sends an unchunked JSON body. Getting this match
 * wrong in either direction is expensive: a miss shows a user a hex-parser message instead of
 * "the server sent a malformed response" AND skips the retry that usually fixes it, while a
 * false positive would retry (and, for a POST, potentially double-fire) requests that failed
 * for real reasons.
 */
class ResponseFramingTest {

    @Test
    fun recognisesTheOkioChunkSizeFailure() {
        val e = IllegalArgumentException("Expected leading [0-9a-fA-F] character but was 0x7b")
        assertTrue(ResponseFraming.isFramingFailure(e))
    }

    @Test
    fun recognisesItThroughWrapping() {
        // The API layer wraps non-BlueLinkException throwables, so the real failure is a cause.
        val wrapped = BlueLinkException(
            "Expected leading [0-9a-fA-F] character but was 0x7b",
            IllegalArgumentException("Expected leading [0-9a-fA-F] character but was 0x7b"),
        )
        assertTrue(ResponseFraming.isFramingFailure(wrapped))
    }

    @Test
    fun recognisesOtherFramingBreakages() {
        assertTrue(ResponseFraming.isFramingFailure(java.io.EOFException("unexpected end of stream")))
        assertTrue(ResponseFraming.isFramingFailure(IllegalStateException("Expected chunk size")))
    }

    @Test
    fun leavesOrdinaryFailuresAlone() {
        // These must NOT be retried as framing problems: they are real failures (auth, HTTP
        // errors, timeouts) where a blind retry wastes a round trip or repeats a command.
        assertFalse(ResponseFraming.isFramingFailure(BlueLinkException("Request failed (HTTP 500)", code = 500)))
        assertFalse(ResponseFraming.isFramingFailure(java.io.IOException("timeout")))
        assertFalse(ResponseFraming.isFramingFailure(IllegalArgumentException("Bad charge limit")))
        assertFalse(ResponseFraming.isFramingFailure(RuntimeException()))
    }

    @Test
    fun userMessageReplacesTheParserTextOnlyForFramingFailures() {
        val framing = IllegalArgumentException("Expected leading [0-9a-fA-F] character but was 0x7b")
        val friendly = ResponseFraming.userMessage(framing)
        assertNotNull(friendly)
        assertFalse(friendly.contains("0x7b"))
        assertTrue(friendly.contains("malformed", ignoreCase = true))

        assertNull(ResponseFraming.userMessage(java.io.IOException("timeout")))
        assertEquals(null, ResponseFraming.userMessage(BlueLinkException("HTTP 500", code = 500)))
    }
}
