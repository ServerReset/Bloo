package com.bloo.bluelink.autolock

import android.bluetooth.BluetoothAdapter

/**
 * Pure rules for which Bluetooth events AutoLock acts on, apart from Android so they can be tested.
 */
internal object AutoLockEvents {
    /**
     * Whether a car's Bluetooth DISCONNECT means the phone left the car. Not when the phone's own Bluetooth is turning off or off
     * (airplane mode, the quick-settings toggle): every link drops then, the car included, while the person may still be sitting in it.
     * [adapterState] is [BluetoothAdapter.getState], or null when it could not be read (then the disconnect counts).
     */
    fun disconnectMeansLeaving(adapterState: Int?): Boolean =
        adapterState != BluetoothAdapter.STATE_TURNING_OFF && adapterState != BluetoothAdapter.STATE_OFF

    /**
     * Whether a new disconnect for a car should start an evaluation, given the one already in flight ([current], or null). The manifest
     * receiver and the watcher's own receiver both see the same disconnect; the second used to cancel and restart the first, losing a
     * walk it had already seen. A disconnect while one is still being judged is the same departure.
     */
    fun startsNewEvaluation(current: DetectionState?, running: Boolean): Boolean =
        !running || current == null || current.isTerminal || current == DetectionState.IDLE
}
