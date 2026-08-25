package com.bloo.bluelink.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinLockTest {

    // --- Hash / verify -----------------------------------------------------

    @Test
    fun roundTrip_verifySucceeds() {
        val salt = PinCrypto.newSalt()
        val record = PinRecord(salt, PinCrypto.PIN_DEFAULT_ITERATIONS, PinCrypto.hash("482937", salt, PinCrypto.PIN_DEFAULT_ITERATIONS))
        assertTrue(record.verify("482937"))
    }

    @Test
    fun wrongPin_fails() {
        val salt = PinCrypto.newSalt()
        val record = PinRecord(salt, PinCrypto.PIN_DEFAULT_ITERATIONS, PinCrypto.hash("1234", salt, PinCrypto.PIN_DEFAULT_ITERATIONS))
        assertFalse(record.verify("4321"))
        assertFalse(record.verify("12345"))
        assertFalse(record.verify(""))
    }

    @Test
    fun salts_areUnique() {
        val a = PinCrypto.newSalt()
        val b = PinCrypto.newSalt()
        assertNotEquals(a.toList(), b.toList())
    }

    @Test
    fun samePin_differentSalt_hashesDifferently() {
        val pin = "777777"
        val h1 = PinCrypto.hash(pin, PinCrypto.newSalt(), 10_000)
        val h2 = PinCrypto.hash(pin, PinCrypto.newSalt(), 10_000)
        assertNotEquals(h1.toList(), h2.toList())
    }

    @Test
    fun constantTimeEquals_worksBothWays() {
        val a = byteArrayOf(1, 2, 3, 4)
        assertTrue(PinCrypto.constantTimeEquals(a, byteArrayOf(1, 2, 3, 4)))
        assertFalse(PinCrypto.constantTimeEquals(a, byteArrayOf(1, 2, 3, 5)))
        assertFalse(PinCrypto.constantTimeEquals(a, byteArrayOf(1, 2, 3)))
    }

    // --- Record codec ------------------------------------------------------

    @Test
    fun record_encodeDecode_roundTrips() {
        val salt = PinCrypto.newSalt()
        val record = PinRecord(salt, 200_000, PinCrypto.hash("80000000", salt, 200_000))
        val decoded = PinRecord.decode(record.encode())
        assertNotNull(decoded)
        assertEquals(record.encode(), decoded!!.encode())
        assertTrue(decoded.verify("80000000"))
    }

    @Test
    fun record_decode_rejectsGarbage() {
        assertNull(PinRecord.decode(null))
        assertNull(PinRecord.decode(""))
        assertNull(PinRecord.decode("just|two|parts"))
        assertNull(PinRecord.decode("ab|5|cd"))                 // bad base64
        assertNull(PinRecord.decode("YWJj|5|Y2Q=="))             // wrong salt size
        assertNull(PinRecord.decode(PinRecord(PinCrypto.newSalt(), 10, ByteArray(32)).encode())) // too few iterations
        assertNull(PinRecord.decode(PinRecord(PinCrypto.newSalt(), 10_000_000, ByteArray(32)).encode())) // too many
        assertNull(PinRecord.decode(PinRecord(PinCrypto.newSalt(), 10_000, ByteArray(7)).encode())) // wrong hash size
    }

    // --- Lockout policy ----------------------------------------------------

    private val t0 = 1_000_000_000_000L

    @Test
    fun lockout_noWindowBeforeFifthStrike() {
        var s = PinLockout()
        repeat(4) {
            s = s.onFailure(t0)
            assertFalse("failure ${s.failures} must not lock", s.isLocked(t0))
        }
    }

    @Test
    fun lockout_fifthStrike_opensThirtySecondWindow() {
        var s = PinLockout()
        repeat(4) { s = s.onFailure(t0) }
        s = s.onFailure(t0)
        assertTrue(s.isLocked(t0))
        assertEquals(PinLockout.BASE_WINDOW_MS, s.remainingMs(t0))
        assertNull(s.attemptsRemainingInBatch(t0))
        // Window is exactly 30s: at t0+29_999 still locked, t0+30_000 free.
        assertTrue(s.isLocked(t0 + 29_999))
        assertFalse(s.isLocked(t0 + 30_000))
    }

    @Test
    fun lockout_escalation_doublesPerBatch() {
        var s = PinLockout()
        for (batch in 1..4) {
            val now = t0 + batch * 100_000L
            repeat(5) { s = s.onFailure(now) }
            val expected = PinLockout.BASE_WINDOW_MS * (1L shl (batch - 1))
            assertEquals("batch $batch", expected, s.lockedUntilEpochMs - now)
            // windowMs helper agrees
            assertEquals(expected, PinLockout.windowMs(batch))
        }
    }

    @Test
    fun lockout_failuresDuringWindow_carryOverNotExtend() {
        var s = PinLockout()
        repeat(5) { s = s.onFailure(t0) }          // locked until t0+30s
        val end = s.lockedUntilEpochMs
        s = s.onFailure(t0 + 5_000L)               // failed twice more inside the window
        s = s.onFailure(t0 + 6_000L)
        assertEquals(7, s.failures)
        assertEquals(end, s.lockedUntilEpochMs)    // window unchanged
    }

    @Test
    fun lockout_success_resetsEverything() {
        var s = PinLockout()
        repeat(7) { s = s.onFailure(t0) }
        s = s.onSuccess()
        assertEquals(0, s.failures)
        assertEquals(0L, s.lockedUntilEpochMs)
        assertFalse(s.isLocked(t0))
        assertEquals(5, s.attemptsRemainingInBatch(t0))
    }

    @Test
    fun lockout_attemptsRemaining_countsDownWithinBatch() {
        var s = PinLockout()
        assertEquals(5, s.attemptsRemainingInBatch(t0))
        s = s.onFailure(t0)
        assertEquals(4, s.attemptsRemainingInBatch(t0))
        repeat(4) { s = s.onFailure(t0) }
        assertNull(s.attemptsRemainingInBatch(t0)) // rejected → no "attempts" shown
    }
}
class PinLockBoundaryTest {

    // --- formatLockoutSeconds: ceil-to-second rounding ----------------------

    @Test
    fun formatLockout_roundsUpEveryPartialSecond() {
        // (ms + 999) / 1000 -- any partial second renders as a full one so a
        // countdown never shows "0:00" while the user is actually still locked.
        assertEquals("0:00", formatLockoutSeconds(0L))
        assertEquals("0:01", formatLockoutSeconds(1L))
        assertEquals("0:01", formatLockoutSeconds(999L))
        assertEquals("0:01", formatLockoutSeconds(1000L))
        assertEquals("0:02", formatLockoutSeconds(1001L))
    }

    @Test
    fun formatLockout_minuteBorders() {
        assertEquals("1:00", formatLockoutSeconds(59_999L))
        assertEquals("1:00", formatLockoutSeconds(60_000L))
        assertEquals("1:01", formatLockoutSeconds(60_001L))
        // The 10th-strike window is 30s * 2^9 = 256 minutes -- the escalation
        // is exponential, and the renderer must not choke on it.
        assertEquals("256:00", formatLockoutSeconds(PinLockout.windowMs(10)))
    }

    // --- remainingMs / isLocked at exact boundaries --------------------------

    @Test
    fun remainingMs_exactlyAtBoundaryIsZeroAndUnlocked() {
        val lock = PinLockout(failures = 5, lockedUntilEpochMs = 1_000_000L)
        assertEquals(0L, lock.remainingMs(1_000_000L))
        assertEquals(false, lock.isLocked(1_000_000L))
        // One millisecond before the end: still locked, exactly 1ms left.
        assertEquals(1L, lock.remainingMs(999_999L))
        assertEquals(true, lock.isLocked(999_999L))
    }

    @Test
    fun remainingMs_neverLocked_isZero() {
        val lock = PinLockout()
        assertEquals(0L, lock.remainingMs(123456789L))
        assertEquals(false, lock.isLocked(123456789L))
    }

    // --- window arithmetic ----------------------------------------------------

    @Test
    fun windowMs_doublesPerBatch() {
        assertEquals(30_000L, PinLockout.windowMs(1))
        assertEquals(60_000L, PinLockout.windowMs(2))
        assertEquals(480_000L, PinLockout.windowMs(5))
    }

    @Test
    fun success_midWindow_resetsFailuresAndWindow() {
        val locked = PinLockout(failures = 5, lockedUntilEpochMs = 1_000_000L)
        val reset = locked.onSuccess()
        assertEquals(0, reset.failures)
        assertEquals(0L, reset.lockedUntilEpochMs)
        assertEquals(false, reset.isLocked(1_000_000L))
    }
}
