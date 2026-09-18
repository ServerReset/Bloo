package com.bloo.bluelink.autolock

/**
 * Pure, side-effect-free transition logic -- ported near-verbatim from i5-AutoLock's
 * `LockStateMachine`. [AutoLockController] owns timers and I/O; this class only computes
 * the next state, which is what makes it exhaustively unit-testable.
 *
 * Confirmation policy: after the Bluetooth disconnect trigger, walking confirmation (via
 * Activity Recognition) is always required before the grace countdown starts.
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
            if (current == DetectionState.CONFIRMING) DetectionState.GRACE else current
        }
    }
}
