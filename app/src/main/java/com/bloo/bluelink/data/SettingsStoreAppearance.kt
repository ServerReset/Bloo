package com.bloo.bluelink.data



// --- Appearance and display preference setters (extracted from SettingsStore) --

suspend fun SettingsStore.setHapticsEnabled(value: Boolean) {
    editTracked { it[SettingsStore.Keys.HAPTICS] = value.toString() }
}

suspend fun SettingsStore.setPebbleOutline(value: Boolean) {
    editTracked { it[SettingsStore.Keys.PEBBLE_OUTLINE] = value.toString() }
}

suspend fun SettingsStore.setShowSearch(value: Boolean) {
    editTracked { it[SettingsStore.Keys.SHOW_SEARCH] = value.toString() }
}

suspend fun SettingsStore.setSeamlessInstallShizuku(value: Boolean) {
    editTracked { it[SettingsStore.Keys.SEAMLESS_INSTALL_SHIZUKU] = value.toString() }
}

suspend fun SettingsStore.setBiometricLock(enabled: Boolean) {
    editTracked { it[SettingsStore.Keys.BIOMETRIC] = enabled.toString() }
}

/** Stores the enum's name() as a string; read back with LockTiming.valueOf(),
 *  falling back to LockTiming.IMMEDIATE if the stored name no longer matches
 *  an enum constant (e.g. after a rename). */
suspend fun SettingsStore.setLockTiming(value: LockTiming) {
    editTracked { it[SettingsStore.Keys.LOCK_TIMING] = value.name }
}

suspend fun SettingsStore.setColumnsFlipped(flipped: Boolean) {
    editTracked { it[SettingsStore.Keys.FLIPPED] = flipped.toString() }
}
