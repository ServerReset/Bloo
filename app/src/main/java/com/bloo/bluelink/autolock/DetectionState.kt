package com.bloo.bluelink.autolock

/**
 * High-level lifecycle of one "did I leave the car?" evaluation. Ported from the
 * i5-AutoLock reference app (github.com/Vel-San/i5-AutoLock) and adapted onto Bloo's own
 * multi-brand [com.bloo.bluelink.data.Vehicle]/[com.bloo.bluelink.data.VehicleStatus] and
 * [com.bloo.bluelink.data.runCarCommand] plumbing instead of a dedicated BlueLink client.
 */
enum class DetectionState {
    IDLE,
    ARMED,        // Watching for a disconnect trigger.
    CONFIRMING,   // Trigger fired; waiting for corroborating signals (activity/geofence).
    GRACE,        // Counting down before acting.
    VERIFYING,    // Querying the vehicle status via the API.
    LOCKING,      // Sending the lock command.
    LOCKED,       // Success.
    SKIPPED,      // Nothing to do (already locked / engine on / door open / etc.).
    ABORTED,      // User returned / reconnected / cancelled.
    ERROR,        // Something failed.
    ;

    val isTerminal: Boolean
        get() = this == LOCKED || this == SKIPPED || this == ABORTED || this == ERROR
}

/** External signals feeding [LockStateMachine]. */
sealed interface DetectionEvent {
    data object CarBluetoothDisconnected : DetectionEvent
    data object CarBluetoothReconnected : DetectionEvent
    data object WalkingConfirmed : DetectionEvent
    data object MovedBeyondGeofence : DetectionEvent
    data object GraceElapsed : DetectionEvent
    data object UserCancelled : DetectionEvent
}
