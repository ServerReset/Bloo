package com.bloo.bluelink.data

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure-JVM tests for [EuStamp]. These pin the stamp ALGORITHM independently of
 * the real CFB seed, matching the reference's `zip(CFB, raw)` semantics: the XOR
 * is truncated to the shorter operand, the CFB is never repeated, and the
 * timestamp is a plain integer (seconds).
 */
class EuStampTest {

    @Test
    fun zeroCfbMakesStampPlainBase64OfAppIdColonTimestamp() {
        // A CFB longer than the raw, all zero bytes, makes XOR a no-op, so the
        // stamp reduces to base64("$appId:$seconds") — a vector independent of
        // the real seed.
        val appId = "app"
        val zeroCfb = Base64.getEncoder().encodeToString(ByteArray(16)) // 16 zero bytes > "app:5"
        val stamp = EuStamp.generate(appId = appId, cfbBase64 = zeroCfb, unixSeconds = 5L)
        assertEquals(
            Base64.getEncoder().encodeToString("app:5".toByteArray()),
            stamp,
        )
    }

    @Test
    fun xorIsReversibleAndTruncatedToRawLength() {
        // CFB (all 0x01) longer than the raw: output length == raw length, and
        // XORing the decoded stamp back with 0x01 recovers "$appId:$seconds".
        val cfb = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
        val stamp = EuStamp.generate(appId = "ab", cfbBase64 = cfb, unixSeconds = 42L)
        val decoded = Base64.getDecoder().decode(stamp)
        assertEquals("ab:42".length, decoded.size) // truncated to raw, not CFB, length
        val recovered = ByteArray(decoded.size) { (decoded[it].toInt() xor 1).toByte() }
        assertEquals("ab:42", String(recovered))
    }
}
