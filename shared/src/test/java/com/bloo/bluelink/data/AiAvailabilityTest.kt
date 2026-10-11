package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiAvailabilityTest {
    @Test
    fun onlyAModelThatIsOrWillBeUsableTurnsTheFeaturesOn() {
        assertTrue(AiAvailability.Available.usable)
        assertTrue(AiAvailability.Downloadable.usable)
        assertTrue(AiAvailability.Downloading.usable)
        assertFalse(AiAvailability.Unavailable.usable)
        assertFalse(AiAvailability.Error("boom").usable)
    }

    @Test
    fun aFailedCheckSaysWhyInsteadOfPassingAsUnsupported() {
        assertEquals("Check failed: NoClassDefFoundError: Foo", AiAvailability.Error("NoClassDefFoundError: Foo").describe())
        assertEquals("Not available on this device", AiAvailability.Unavailable.describe())
    }

    @Test
    fun aUsableResultStopsTheRetries() {
        assertNull(AiProbePolicy.nextDelayMs(AiAvailability.Available, 0))
        assertNull(AiProbePolicy.nextDelayMs(AiAvailability.Downloading, 1))
    }

    @Test
    fun aFailureIsRetriedWithGrowingWaitsThenGivesUp() {
        assertEquals(3_000L, AiProbePolicy.nextDelayMs(AiAvailability.Error("x"), 0))
        assertEquals(15_000L, AiProbePolicy.nextDelayMs(AiAvailability.Unavailable, 1))
        assertEquals(60_000L, AiProbePolicy.nextDelayMs(AiAvailability.Error("x"), 2))
        assertNull(AiProbePolicy.nextDelayMs(AiAvailability.Error("x"), 3))
    }

    @Test
    fun comingBackToTheAppRechecksOnlyIfItWasNotWorkingAndNotTooOften() {
        val now = 1_000_000L
        assertTrue(AiProbePolicy.shouldRecheckOnForeground(AiAvailability.Unavailable, now - 61_000L, now))
        assertFalse(AiProbePolicy.shouldRecheckOnForeground(AiAvailability.Unavailable, now - 10_000L, now))
        assertFalse(AiProbePolicy.shouldRecheckOnForeground(AiAvailability.Available, now - 999_000L, now))
        assertTrue(AiProbePolicy.shouldRecheckOnForeground(null, 0L, now))
    }
}
