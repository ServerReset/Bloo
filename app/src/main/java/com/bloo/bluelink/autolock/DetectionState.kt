package com.bloo.bluelink.autolock

/**
 * High-level lifecycle of one "did I leave the car?" evaluation. Ported from the
 * i5-AutoLock reference app (github.com/Vel-San/i5-AutoLock) and adapted onto Bloo's own
 * multi-brand [com.bloo.bluelink.data.Vehicle]/[com.bloo.bluelink.data.VehicleStatus] and
 * [com.bloo.bluelink.data.runCarCommand] plumbing instead of a dedicated BlueLink client.
 */
enum class DetectionState {
    IDLE,
    CONFIRMING,   // Trigger fired; waiting for walking confirmation.
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

/** External signals feeding [LockStateMachine]. GRACE -> VERIFYING has no event of its own --
 *  [AutoLockController]'s countdown loop advances that transition directly once it finishes,
 *  since it already owns the timer driving it. */
sealed interface DetectionEvent {
    data object CarBluetoothDisconnected : DetectionEvent
    data object CarBluetoothReconnected : DetectionEvent
    data object WalkingConfirmed : DetectionEvent
    data object UserCancelled : DetectionEvent
}

/**
 * True while the in-process [AutoLockController] is the live owner of an evaluation for a car:
 * it has an active job and will reach a terminal state on its own. Used by the fallback deadline
 * receiver to stand down rather than racing that job to the same lock command.
 */
val DetectionState.isControllerOwned: Boolean
    get() = this == DetectionState.CONFIRMING ||
        this == DetectionState.GRACE ||
        this == DetectionState.VERIFYING ||
        this == DetectionState.LOCKING
