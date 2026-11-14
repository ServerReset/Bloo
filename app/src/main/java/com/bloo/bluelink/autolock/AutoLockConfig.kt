package com.bloo.bluelink.autolock

/**
 * Per-car AutoLock configuration, persisted in [com.bloo.bluelink.data.SettingsStore]
 * (`autoLockConfig`/`setAutoLockConfig`). Defaults are the safe/inert ones: disabled, and
 * [dryRun] true so turning AutoLock on for the first time never sends a real lock command
 * until the user has watched it "would have locked" a few times and trusts it -- the same
 * safety-rail the i5-AutoLock reference app ships with.
 */
data class AutoLockConfig(
    val enabled: Boolean = false,
    /** The car's paired Bluetooth device (its head unit), by MAC address. Null until the
     *  user picks one in Settings -- AutoLock can't trigger without it. */
    val deviceAddress: String? = null,
    val deviceName: String? = null,
    val graceSeconds: Int = 30,
    /** Runs the full detect -> verify flow but never sends the real lock command -- logs
     *  "would have locked" instead. Off by default: picking a car is the person turning auto-lock on, and a
     *  dry run left on looks exactly like a lock that never happens. Settings can still turn it on to test. */
    val dryRun: Boolean = false,
    /** Physical detection: the car is locked only after Activity Recognition sees the person walking away from it. When on, a walk that
     *  is never confirmed means no lock. Saved per car, and on by default. */
    val useActivityRecognition: Boolean = true,
) {
    /** Whether this car has enough configured to ever actually trigger. */
    val isUsable: Boolean get() = deviceAddress != null
}
