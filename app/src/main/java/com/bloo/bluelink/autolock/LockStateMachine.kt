package com.bloo.bluelink.autolock

/**
 * Pure, side-effect-free transition logic -- ported near-verbatim from i5-AutoLock's
 * `LockStateMachine`. [AutoLockController] owns timers and I/O; this class only computes
 * the next state, which is what makes it exhaustively unit-testable.
 *
 * Confirmation policy: after the Bluetooth disconnect trigger, walking confirmation (via
 * Activity Recognition) is always required before the grace countdown starts, plus geofence
 * confirmation when that's *enabled* -- a disabled geofence counts as already-confirmed, so a
 * car without it goes straight from walking confirmation to GRACE.
 */
class LockStateMachine(useGeofence: Boolean) {

    private var walkingConfirmed = false
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
    }

    private fun promoteIfConfirming(current: DetectionState): DetectionState =
        if (current == DetectionState.CONFIRMING && allConfirmed()) DetectionState.GRACE else current

    private fun allConfirmed(): Boolean = walkingConfirmed && geofenceConfirmed
}
