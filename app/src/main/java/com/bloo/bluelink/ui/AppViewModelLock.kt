package com.bloo.bluelink.ui

import androidx.biometric.BiometricManager
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.PinCrypto
import com.bloo.bluelink.data.PinLockout
import com.bloo.bluelink.data.PinRecord
import com.bloo.bluelink.data.STALE_STATUS_MS
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.shouldRelockAfter
import com.bloo.bluelink.data.wireKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bloo.bluelink.data.setBiometricLock
import com.bloo.bluelink.data.setLockTiming

// --- App PIN, lock timing, and biometric lock (extracted from AppViewModel) --

/** Fix a wrong/locked service PIN without re-entering the whole account. */
fun AppViewModel.updatePin(brand: Brand, pin: String) {
    if (pin.isBlank()) return
    viewModelScope.launch {
        store.updatePin(brand, pin.trim())
        credentialStore.updatePin(brand, pin.trim())
        _state.update { it.copy(accounts = credentialStore.loadAll(), message = "PIN updated for ${brand.label}", messageType = "success") }
        AppLog.log("Updated PIN for ${brand.label}")
    }
}

/** Dismiss the lock overlay (the garage was already loaded behind it).
 *  A successful PIN verify routes through here too -- and resets the
 *  failure counter (see [verifyAppPin]). */
fun AppViewModel.unlocked() {
    _state.update { it.copy(locked = false, lockedToLogin = false, pinAttemptRejected = false) }
    if (_state.value.vehicles.isEmpty() && !loadingGarage) loadGarage()
    // If status fetching was deferred waiting for the lock screen to unlock,
    // perform the deferred fetch now — wait for the lock-away blur animation
    // (450ms) to complete first so pebble recompositions from incoming status
    // don't overlap the expensive unlock animation.
    if (deferredStatusLoad) {
        deferredStatusLoad = false
        val vehicles = _state.value.vehicles
        val index = _currentIndex.value
        logStartup("unlocked(): deferred status fetch starting")
        viewModelScope.launch {
            delay(500)  // Wait for unlock blur animation (450ms) to complete
            vehicles.getOrNull(index)?.let {
                logStartup("unlocked(): fetching status for ${it.name} (current car)")
                ensureStatus(it, logStartupTiming = true)
            }
            launch {
                vehicles.forEachIndexed { i, v -> if (i != index) ensureStatus(v) }
            }
        }
    }
}

/** From the lock overlay, back out to the login screen.
 *  Sets lockedToLogin so Cancel on the login form re-locks instead of bypassing auth. */
fun AppViewModel.lockToLogin() = _state.update { it.copy(locked = false, addingAccount = true, lockedToLogin = true) }

/** Persist how long after backgrounding the app should re-lock (see
 *  [maybeRelock], which reads this back out of [SettingsStore] on the next
 *  foreground). Fire-and-forget: the write is async, nothing in [_state]
 *  reflects the new value directly since the lock-timing setting itself
 *  isn't rendered anywhere that needs it synchronously. */
fun AppViewModel.setLockTiming(value: LockTiming) {
    viewModelScope.launch { settingsStore.setLockTiming(value) }
}

/**
 * Re-engage the lock when returning to the foreground, honouring the user's
 * [LockTiming] setting. [backgroundedAtMs] is when the app was last stopped;
 * [screenTurnedOff] is whether the screen turned off while the app was away, which
 * [LockTiming.SCREEN_OFF] keys off.
 */
fun AppViewModel.maybeRelock(backgroundedAtMs: Long, screenTurnedOff: Boolean = false) {
    if (_state.value.locked) return
    viewModelScope.launch {
        val a = settingsStore.appearance.first()
        // Either mechanism re-arms the lock: the biometric lock when the
        // device has usable biometrics, or the app PIN when one is set. The PIN
        // half reads the encrypted prefs, so the whole check is taken on IO.
        val armed = withContext(Dispatchers.IO) { (a.biometricLock && canUseBiometrics()) || pinInstalled() }
        if (!armed) return@launch
        val elapsed = System.currentTimeMillis() - backgroundedAtMs
        // See LockTiming.wireKey (exhaustive, so a new enum value must be mapped) and
        // shouldRelockAfter's own doc for the legacy wire keys it still honours.
        if (shouldRelockAfter(elapsed, a.lockTiming.wireKey, screenTurnedOff)) _state.update { it.copy(locked = true) }
    }
    // Prompt to refresh if data is stale after returning from background.
    if (backgroundedAtMs > 0 && System.currentTimeMillis() - backgroundedAtMs > STALE_STATUS_MS) {
        val anyStale = _state.value.lastFetched.values.any { System.currentTimeMillis() - it > STALE_STATUS_MS }
        if (anyStale) reportInfo("Data may be stale, pull down to refresh")
    }
}

/** Whether the device currently has usable biometrics (biometric/face)
 *  enrolled -- gates whether [UiState.locked] / [maybeRelock] can ever
 *  apply, since there's nothing to authenticate against otherwise.
 *  BIOMETRIC_WEAK is used (rather than STRONG) so a wider range of
 *  device authenticators (including some face-only ones) still qualify. */
fun AppViewModel.canUseBiometrics(): Boolean =
    BiometricManager.from(getApplication())
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS

/** Turn the biometric app-lock on/off in Settings. Persists only -- the
 *  actual locking/unlocking flow is driven separately by [maybeRelock]
 *  and [unlocked] reading this flag back out on each foreground. */
fun AppViewModel.setBiometricLock(enabled: Boolean) {
    viewModelScope.launch { settingsStore.setBiometricLock(enabled) }
}

}

/** Whether a PIN record currently exists in the credential store. */
private fun AppViewModel.pinInstalled(): Boolean = credentialStore.getPinRecord() != null

/** Re-mirrors PIN presence + lockout state from the credential store
 *  into [UiState] (called on cold start and after every mutation). */
fun AppViewModel.refreshPinState() {
    // Reads CredentialStore's lazy encrypted prefs (Tink/AndroidKeystore), so this
    // runs on IO: every caller is a state refresh, none of them can observe the
    // result synchronously, and doing it on Main.immediate put the first
    // EncryptedSharedPreferences construction on the cold-start critical path.
    viewModelScope.launch(Dispatchers.IO) {
        val pinSet = credentialStore.getPinRecord() != null
        val lockout = PinLockout(
            credentialStore.getPinFailures(),
            credentialStore.getPinLockedUntil(),
            credentialStore.getPinLockedUntilElapsed(),
        )
        _state.update { it.copy(appPinSet = pinSet, pinLockout = lockout) }
    }
}

/**
 * Sets (or replaces) the app PIN. The PIN is never persisted: only its
 * PBKDF2-stretched hash + salt live in CredentialStore's encrypted
 * storage (see [PinRecord]). The stretch runs off the main thread.
 */
fun AppViewModel.setAppPin(pin: String) {
    viewModelScope.launch {
        val record = withContext(Dispatchers.Default) {
            val salt = PinCrypto.newSalt()
            PinRecord(salt, PinCrypto.PIN_DEFAULT_ITERATIONS, PinCrypto.hash(pin, salt, PinCrypto.PIN_DEFAULT_ITERATIONS))
        }
        credentialStore.setPinRecord(record.encode())
        refreshPinState()
    }
}

/** Removes the app PIN entirely (the caller must have already verified
 *  the current PIN -- see [verifyAppPin] for the gate). */
fun AppViewModel.removeAppPin() {
    viewModelScope.launch {
        credentialStore.setPinRecord(null)
        refreshPinState()
    }
}

/**
 * Verifies a PIN attempt against the stored record, enforcing the
 * [PinLockout] policy: while the rejection window is open the attempt is
 * rejected outright (no work spent on it), otherwise a wrong PIN records
 * a failure and every fifth failure opens a window that doubles per
 * batch (30s, 1m, 2m, ...). A correct PIN resets the counter and unlocks
 * like a biometric would.
 *
 * Result surfaces through [UiState.pinAttemptRejected] /
 * [UiState.pinLockout]; the overlay acknowledges via
 * [acknowledgePinRejection].
 */
fun AppViewModel.verifyAppPin(pin: String) {
    viewModelScope.launch {
        val record = PinRecord.decode(credentialStore.getPinRecord()) ?: return@launch
        val now = System.currentTimeMillis()
        // The monotonic reading alongside the wall clock: the wall clock is what someone
        // holding the device can move, and moving it used to retire the rejection window.
        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        var lockout = PinLockout(
            credentialStore.getPinFailures(),
            credentialStore.getPinLockedUntil(),
            credentialStore.getPinLockedUntilElapsed(),
        )
        if (lockout.isLocked(now, nowElapsed)) {
            _state.update { it.copy(pinAttemptRejected = true) }
            return@launch
        }
        val ok = withContext(Dispatchers.Default) { record.verify(pin) }
        if (ok) {
            credentialStore.setPinLockout(lockout.onSuccess())
            refreshPinState()
            _state.update { it.copy(pinAcceptedTick = it.pinAcceptedTick + 1) }
            unlocked()
        } else {
            lockout = lockout.onFailure(now, nowElapsed)
            credentialStore.setPinLockout(lockout)
            _state.update { it.copy(pinLockout = lockout, pinAttemptRejected = true) }
        }
    }
}

/** Clears the transient "last attempt was rejected" flag the lock
 *  overlay shows; called when the overlay re-shows its input state. */
fun AppViewModel.acknowledgePinRejection() {
    _state.update { it.copy(pinAttemptRejected = false) }
}
