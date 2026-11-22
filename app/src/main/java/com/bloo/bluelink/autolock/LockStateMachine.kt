package com.bloo.bluelink.autolock

/**
 * Pure, side-effect-free transition logic -- ported near-verbatim from i5-AutoLock's
 * `LockStateMachine`. [AutoLockController] owns timers and I/O; this class only computes
 * the next state, which is what makes it exhaustively unit-testable.
 *
 * Confirmation policy: after the Bluetooth disconnect trigger, Activity Recognition must confirm a walk before the grace countdown
 * starts. Physical detection is mandatory, so a disconnect alone never reaches GRACE.
 */
class LockStateMachine {

    private var walkingConfirmed = false

    fun next(current: DetectionState, event: DetectionEvent): DetectionState = when (event) {
        DetectionEvent.CarBluetoothReconnected,
        DetectionEvent.UserCancelled,
        -> DetectionState.ABORTED

        DetectionEvent.CarBluetoothDisconnected -> {
            if (current == DetectionState.IDLE) {
                if (walkingConfirmed) DetectionState.GRACE else DetectionState.CONFIRMING
            } else current
        }

        DetectionEvent.WalkingConfirmed -> {
            walkingConfirmed = true
            promoteIfConfirming(current)
        }

        DetectionEvent.GraceElapsed ->
            if (current == DetectionState.GRACE) DetectionState.VERIFYING else current
    }

    private fun promoteIfConfirming(current: DetectionState): DetectionState =
        if (current == DetectionState.CONFIRMING && walkingConfirmed) DetectionState.GRACE else current
}
