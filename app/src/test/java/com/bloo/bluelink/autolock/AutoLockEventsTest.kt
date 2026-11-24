package com.bloo.bluelink.autolock

import android.bluetooth.BluetoothAdapter
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which Bluetooth events start an evaluation. */
class AutoLockEventsTest {
    @Test fun turningThePhonesBluetoothOffIsNotLeavingTheCar() {
        assertFalse(AutoLockEvents.disconnectMeansLeaving(BluetoothAdapter.STATE_TURNING_OFF))
        assertFalse(AutoLockEvents.disconnectMeansLeaving(BluetoothAdapter.STATE_OFF))
        assertTrue(AutoLockEvents.disconnectMeansLeaving(BluetoothAdapter.STATE_ON))
        assertTrue(AutoLockEvents.disconnectMeansLeaving(null), "an unreadable state still counts")
    }

    @Test fun theSameDepartureSeenTwiceStartsOneEvaluation() {
        assertTrue(AutoLockEvents.startsNewEvaluation(null, running = false))
        assertFalse(AutoLockEvents.startsNewEvaluation(DetectionState.CONFIRMING, running = true))
        assertFalse(AutoLockEvents.startsNewEvaluation(DetectionState.GRACE, running = true))
        assertFalse(AutoLockEvents.startsNewEvaluation(DetectionState.LOCKING, running = true))
    }

    @Test fun aNewDepartureAfterTheLastOneFinishedStartsAgain() {
        for (done in DetectionState.entries.filter { it.isTerminal }) {
            assertTrue(AutoLockEvents.startsNewEvaluation(done, running = false), "after $done")
            assertTrue(AutoLockEvents.startsNewEvaluation(done, running = true), "after $done, job winding down")
        }
        assertTrue(AutoLockEvents.startsNewEvaluation(DetectionState.CONFIRMING, running = false), "a job that died mid-way")
    }
}

/** Which saved departures a walk in a restarted process may still resume. */
class AutoLockPendingTest {
    @Test fun aRecentDepartureIsStillOpen() {
        assertTrue(AutoLockPending.stillOpen(atMs = 1_000, nowMs = 61_000, windowMs = 120_000))
        assertTrue(AutoLockPending.stillOpen(atMs = 1_000, nowMs = 121_000, windowMs = 120_000), "at the edge")
    }

    @Test fun anOldOrFutureDepartureIsNot() {
        assertFalse(AutoLockPending.stillOpen(atMs = 1_000, nowMs = 121_001, windowMs = 120_000), "too old to be this walk")
        assertFalse(AutoLockPending.stillOpen(atMs = 5_000, nowMs = 1_000, windowMs = 120_000), "a clock change put it in the future")
    }
}
