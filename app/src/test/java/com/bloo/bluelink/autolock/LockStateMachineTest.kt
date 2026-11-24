package com.bloo.bluelink.autolock

import com.bloo.bluelink.autolock.DetectionEvent.CarBluetoothDisconnected
import com.bloo.bluelink.autolock.DetectionEvent.CarBluetoothReconnected
import com.bloo.bluelink.autolock.DetectionEvent.GraceElapsed
import com.bloo.bluelink.autolock.DetectionEvent.UserCancelled
import com.bloo.bluelink.autolock.DetectionEvent.WalkingConfirmed
import com.bloo.bluelink.autolock.DetectionState.ABORTED
import com.bloo.bluelink.autolock.DetectionState.CONFIRMING
import com.bloo.bluelink.autolock.DetectionState.GRACE
import com.bloo.bluelink.autolock.DetectionState.IDLE
import com.bloo.bluelink.autolock.DetectionState.VERIFYING
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A disconnect alone never reaches the countdown; a walk does; a reconnect aborts; a finished evaluation stays finished. */
class LockStateMachineTest {
    @Test fun aDisconnectWaitsForAWalk() {
        val m = LockStateMachine()
        assertEquals(CONFIRMING, m.next(IDLE, CarBluetoothDisconnected))
        assertEquals(CONFIRMING, m.next(CONFIRMING, GraceElapsed), "no countdown without a walk")
    }

    @Test fun aWalkStartsTheCountdownAndItsEndVerifies() {
        val m = LockStateMachine()
        val confirming = m.next(IDLE, CarBluetoothDisconnected)
        val grace = m.next(confirming, WalkingConfirmed)
        assertEquals(GRACE, grace)
        assertEquals(VERIFYING, m.next(grace, GraceElapsed))
    }

    @Test fun aWalkSeenBeforeTheDisconnectStillCounts() {
        val m = LockStateMachine()
        assertEquals(IDLE, m.next(IDLE, WalkingConfirmed))
        assertEquals(GRACE, m.next(IDLE, CarBluetoothDisconnected))
    }

    @Test fun aWalkOnItsOwnNeverStartsTheCountdown() {
        assertEquals(IDLE, LockStateMachine().next(IDLE, WalkingConfirmed))
    }

    @Test fun aSecondDisconnectChangesNothing() {
        val m = LockStateMachine()
        assertEquals(GRACE, m.next(GRACE, CarBluetoothDisconnected))
        assertEquals(CONFIRMING, m.next(CONFIRMING, CarBluetoothDisconnected))
    }

    @Test fun aReconnectOrACancelAbortsAnythingInFlight() {
        for (state in listOf(IDLE, CONFIRMING, GRACE, VERIFYING, DetectionState.LOCKING)) {
            assertEquals(ABORTED, LockStateMachine().next(state, CarBluetoothReconnected), "reconnect from $state")
            assertEquals(ABORTED, LockStateMachine().next(state, UserCancelled), "cancel from $state")
        }
    }

    @Test fun aFinishedEvaluationKeepsItsOutcome() {
        val terminal = DetectionState.entries.filter { it.isTerminal }
        for (state in terminal) {
            for (event in listOf(CarBluetoothReconnected, UserCancelled, CarBluetoothDisconnected, WalkingConfirmed, GraceElapsed)) {
                assertEquals(state, LockStateMachine().next(state, event), "$event after $state")
            }
        }
    }

    @Test fun onlyTheFourOutcomesAreTerminal() {
        assertEquals(
            setOf(DetectionState.LOCKED, DetectionState.SKIPPED, ABORTED, DetectionState.ERROR),
            DetectionState.entries.filter { it.isTerminal }.toSet(),
        )
        assertFalse(GRACE.isTerminal)
        assertTrue(DetectionState.LOCKED.isTerminal)
    }
}
