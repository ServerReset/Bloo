package com.bloo.bluelink.data

// --- Appearance and display preference setters (extracted from SettingsStore) --

suspend fun SettingsStore.setHapticsEnabled(value: Boolean) {
    putFlag(SettingsStore.Keys.HAPTICS, value)
}

suspend fun SettingsStore.setSeamlessInstallShizuku(value: Boolean) {
    putFlag(SettingsStore.Keys.SEAMLESS_INSTALL_SHIZUKU, value)
}

suspend fun SettingsStore.setClassicHeadings(value: Boolean) {
    putFlag(SettingsStore.Keys.CLASSIC_HEADINGS, value)
}

suspend fun SettingsStore.setMotionBlur(value: Boolean) {
    putFlag(SettingsStore.Keys.MOTION_BLUR, value)
}

suspend fun SettingsStore.setReduceMotion(value: Boolean) {
    putFlag(SettingsStore.Keys.REDUCE_MOTION, value)
}

suspend fun SettingsStore.setKeyboardShortcuts(value: Boolean) {
    putFlag(SettingsStore.Keys.KEYBOARD_SHORTCUTS, value)
}

suspend fun SettingsStore.setBiometricLock(enabled: Boolean) {
    putFlag(SettingsStore.Keys.BIOMETRIC, enabled)
}

/** Stores the enum's name() as a string; read back with LockTiming.valueOf(),
 *  falling back to LockTiming.IMMEDIATE if the stored name no longer matches
 *  an enum constant (e.g. after a rename). */
suspend fun SettingsStore.setLockTiming(value: LockTiming) {
    editTracked { it[SettingsStore.Keys.LOCK_TIMING] = value.name }
}

suspend fun SettingsStore.setColumnsFlipped(flipped: Boolean) {
    putFlag(SettingsStore.Keys.FLIPPED, flipped)
}
