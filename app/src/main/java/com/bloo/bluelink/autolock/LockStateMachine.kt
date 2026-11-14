package com.bloo.bluelink.autolock

/**
 * Pure, side-effect-free transition logic -- ported near-verbatim from i5-AutoLock's
 * `LockStateMachine`. [AutoLockController] owns timers and I/O; this class only computes
 * the next state, which is what makes it exhaustively unit-testable.
 *
 * Confirmation policy: after the Bluetooth disconnect trigger, every *enabled* corroborating
 * signal (activity recognition + geofence) must confirm before the grace countdown starts.
 * Disabled signals count as already-confirmed, so a car with neither enabled goes straight
 * from the disconnect to GRACE -- matching this app's existing behaviour for a car with no
 * extra confirmation configured.
 */
class LockStateMachine(useActivityRecognition: Boolean, useGeofence: Boolean) {

    private var walkingConfirmed = !useActivityRecognition
    private var geofenceConfirmed = !useGeofence

    fun next(current: DetectionState, event: DetectionEvent): DetectionState = when (event) {
        DetectionEvent.CarBluetoothReconnected,
        DetectionEvent.UserCancelled,
        -> DetectionState.ABORTED

        DetectionEvent.CarBluetoothDisconnected -> {
            if (current == DetectionState.IDLE) {
                if (allConfirmed()) DetectionState.GRACE else DetectionState.CONFIRMING
            } else current
        }

        DetectionEvent.WalkingConfirmed -> {
            walkingConfirmed = true
            promoteIfConfirming(current)
        }

        DetectionEvent.MovedBeyondGeofence -> {
            geofenceConfirmed = true
            promoteIfConfirming(current)
        }

        DetectionEvent.GraceElapsed ->
            if (current == DetectionState.GRACE) DetectionState.VERIFYING else current
    }

    private fun promoteIfConfirming(current: DetectionState): DetectionState =
        if (current == DetectionState.CONFIRMING && allConfirmed()) DetectionState.GRACE else current

    private fun allConfirmed(): Boolean = walkingConfirmed && geofenceConfirmed
}
