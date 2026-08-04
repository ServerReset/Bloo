package com.bloo.bluelink.data

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pure-JVM tests for [EuStamp]. These pin the stamp ALGORITHM independently of
 * the real (FILL-FROM-SOURCE) CFB seed: a zero-byte CFB makes the XOR a no-op,
 * so the stamp reduces to base64("$appId:$timestamp") — a deterministic vector
 * that stays valid no matter what the shipped seed turns out to be.
 */
class EuStampTest {

    @Test
    fun zeroCfbMakesStampPlainBase64OfAppIdColonTimestamp() {
        val appId = "app"
        val zeroCfb = Base64.getEncoder().encodeToString(ByteArray(9)) // all-zero bytes
        val stamp = EuStamp.generate(appId = appId, cfbBase64 = zeroCfb, timestampMillis = 5L)
        assertEquals(
            Base64.getEncoder().encodeToString("app:5".toByteArray()),
            stamp,
        )
    }

    @Test
    fun cfbShorterThanRawRepeatsSeed() {
        // 1-byte CFB (0x01) XORs every byte of the raw with 0x01; decoding the
        // stamp and XORing back with 0x01 must reproduce "$appId:$timestamp".
        val oneByteCfb = Base64.getEncoder().encodeToString(byteArrayOf(1))
        val stamp = EuStamp.generate(appId = "ab", cfbBase64 = oneByteCfb, timestampMillis = 42L)
        val decoded = Base64.getDecoder().decode(stamp)
        val recovered = ByteArray(decoded.size) { (decoded[it].toInt() xor 1).toByte() }
        assertEquals("ab:42", String(recovered))
    }
}
