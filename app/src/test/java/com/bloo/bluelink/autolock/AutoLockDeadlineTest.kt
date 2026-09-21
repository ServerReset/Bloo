package com.bloo.bluelink.autolock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins AutoLock's fallback deadline arithmetic.
 *
 * This is a pure-function test on purpose. The bug it exists for was arming the fallback
 * alarm at 0ms: the deadline fired the instant the Bluetooth receiver scheduled it, found no
 * walk-away confirmation yet (the activity transition takes seconds to arrive), took the "may
 * still be sitting in the car" skip branch and cleared the persisted record -- so the
 * confirmation, when it did arrive, found nothing pending. The alarm path is the only one
 * that works with the phone in a pocket on Android 12+, so AutoLock silently never locked.
 * Nothing in the flow surfaced that as an error; only the arithmetic is assertable offline.
 */
class AutoLockDeadlineTest {

    @Test
    fun `alarm path deadline covers the confirmation window plus the grace`() {
        assertEquals(
            AutoLockController.CONFIRM_TIMEOUT_MS + 30_000L,
            AutoLockAlarm.alarmPathDeadlineMs(30),
        )
    }

    @Test
    fun `alarm path deadline is never zero, even with no grace`() {
        assertTrue(AutoLockAlarm.alarmPathDeadlineMs(0) > 0)
        // At minimum it must leave room for the walk-away confirmation the user's own
        // activity recognition sends, which is the window the service path gives.
        assertTrue(AutoLockAlarm.alarmPathDeadlineMs(0) >= AutoLockController.CONFIRM_TIMEOUT_MS)
    }

    @Test
    fun `service path deadline leaves the safety-net margin on top of the alarm deadline`() {
        assertEquals(
            AutoLockAlarm.alarmPathDeadlineMs(45) + 20_000L,
            AutoLockAlarm.servicePathDeadlineMs(45),
        )
    }
}
