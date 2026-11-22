package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Pins what the HTTP log keeps of a request line: the method, host and path, never the query (a location or token can ride there). */
class HttpLogRedactionTest {
    @Test
    fun requestLineLosesItsQuery() {
        val line = "--> GET https://api.example.com/v1/forecast?latitude=43.65&longitude=-79.38 http/1.1"
        assertEquals("--> GET https://api.example.com/v1/forecast?… http/1.1", ApiHttp.withoutQuery(line))
    }

    @Test
    fun responseLineLosesItsQuery() {
        val line = "<-- 200 OK https://api.example.com/auth?token=abc123 (120ms, 42-byte body)"
        val logged = ApiHttp.withoutQuery(line)
        assertFalse("abc123" in logged)
        assertEquals("<-- 200 OK https://api.example.com/auth?… (120ms, 42-byte body)", logged)
    }

    @Test
    fun lineWithoutQueryIsUnchanged() {
        val line = "--> POST https://api.example.com/v1/vehicles/lock http/1.1 (64-byte body)"
        assertEquals(line, ApiHttp.withoutQuery(line))
    }
}
