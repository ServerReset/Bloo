package com.bloo.bluelink.autolock

import com.bloo.bluelink.data.CarCommandResult
import com.bloo.bluelink.data.VehicleStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What AutoLock does with the car's status, and how a failed lock is retried. */
class LockPolicyTest {
    @Test fun anUnlockedCarIsLocked() {
        assertEquals(LockDecision.Lock, LockPolicy.decide(VehicleStatus(doorLock = false, engine = false)))
        assertEquals(LockDecision.Lock, LockPolicy.decide(VehicleStatus(doorLock = false)), "engine not reported")
    }

    @Test fun aLockStateTheCarHasNotReportedIsLockedToo() {
        assertEquals(LockDecision.Lock, LockPolicy.decide(VehicleStatus(doorLock = null, engine = false)))
    }

    @Test fun aLockedCarIsLeftAlone() {
        assertTrue(LockPolicy.decide(VehicleStatus(doorLock = true)) is LockDecision.Skip)
    }

    @Test fun aRunningCarIsLeftAlone() {
        assertTrue(LockPolicy.decide(VehicleStatus(doorLock = false, engine = true)) is LockDecision.Skip)
        assertTrue(LockPolicy.decide(VehicleStatus(doorLock = null, engine = true)) is LockDecision.Skip)
    }

    private fun failed(message: String? = null, refused: Boolean = false) = CarCommandResult("V", "lock", ok = false, message = message, refused = refused)

    @Test fun aFailedLockIsTriedThreeTimesInAll() {
        assertEquals(2_500L, LockRetry.nextDelayMs(failed("timeout"), attempt = 1))
        assertEquals(5_000L, LockRetry.nextDelayMs(failed("timeout"), attempt = 2))
        assertNull(LockRetry.nextDelayMs(failed("timeout"), attempt = 3))
        assertEquals(2_500L, LockRetry.nextDelayMs(null, attempt = 1), "no response at all is retried")
    }

    @Test fun aLockThatWorkedIsNotRetried() {
        assertNull(LockRetry.nextDelayMs(CarCommandResult("V", "lock", ok = true), attempt = 1))
    }

    @Test fun retryingIsSkippedWhenItCannotHelp() {
        assertNull(LockRetry.nextDelayMs(failed(refused = true), attempt = 1), "already being sent by another path")
        assertNull(LockRetry.nextDelayMs(failed("You have exceeded the daily remote service request limit"), attempt = 1))
        assertTrue(LockRetry.isLimit("Daily LIMIT reached"))
    }
}
