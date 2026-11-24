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
