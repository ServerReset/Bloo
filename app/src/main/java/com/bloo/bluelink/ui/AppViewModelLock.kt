package com.bloo.bluelink.ui

import androidx.biometric.BiometricManager
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.STALE_STATUS_MS
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.setBiometricLock
import com.bloo.bluelink.data.setLockTiming
import com.bloo.bluelink.data.shouldRelockAfter
import com.bloo.bluelink.data.wireKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- Lock timing and the biometric lock (extracted from AppViewModel) --

/** Fix a wrong/locked service PIN without re-entering the whole account. */
fun AppViewModel.updatePin(brand: Brand, pin: String) {
    if (pin.isBlank()) return
    viewModelScope.launch {
        store.updatePin(brand, pin.trim())
        credentialStore.updatePin(brand, pin.trim())
        _state.update { it.copy(accounts = credentialStore.loadAll(), message = "PIN updated for ${brand.label}.", messageType = ToastKind.SUCCESS) }
        AppLog.log("Updated PIN for ${brand.label}")
    }
}

/** The phone's lock (or the lock screen's own credential) has been passed: the app opens. */
fun AppViewModel.unlocked() {
    // Passing the lock from the sign-in screen (lockToLogin set addingAccount) must also leave that screen: Screens
    // shows Login while addingAccount is set, so without clearing it the fingerprint unlocked and stayed on Login.
    _state.update { it.copy(locked = false, lockedToLogin = false, addingAccount = if (it.lockedToLogin) false else it.addingAccount) }
    // A launcher shortcut that arrived while the lock was up waits for this moment (see tryRunPendingShortcut).
    tryRunPendingShortcut()
    if (pendingBootUnlock) {
        // The last step of the post-restart flow: the app lock passed, so the boot is open and the
        // normal start (auto-login, garage, background work) can run.
        pendingBootUnlock = false
        openBootAndStart()
        return
    }
    if (_state.value.vehicles.isEmpty() && !loadingGarage) loadGarage()
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

/**
 * From the lock overlay, back out to the login screen. Sets lockedToLogin so Cancel on the login
 * form re-locks instead of bypassing auth.
 */
fun AppViewModel.lockToLogin() = _state.update { it.copy(locked = false, addingAccount = true, lockedToLogin = true) }

/**
 * Persist how long after backgrounding the app should re-lock (see [maybeRelock], which reads this
 * back out of [SettingsStore] on the next foreground).
 */
fun AppViewModel.setLockTiming(value: LockTiming) {
    persist { setLockTiming(value) }
}

/**
 * Locks the app again if it was in the background long enough (per the user's lock timing). Suspending, so a caller
 * that must act AFTER the decision (a launcher shortcut) can wait for it.
 */
internal suspend fun AppViewModel.relockIfDue(backgroundedAtMs: Long, screenTurnedOff: Boolean) {
    if (_state.value.locked) return
    val a = settingsStore.appearance.first()
    // The app lock re-arms when it is on and the device has a lock to unlock with: the device's own lock is the
    // only unlock now (there is no app PIN).
    val armed = withContext(Dispatchers.IO) { a.biometricLock && canUseBiometrics() }
    if (!armed) return
    val elapsed = System.currentTimeMillis() - backgroundedAtMs
    // See LockTiming.wireKey (exhaustive, so a new enum value must be mapped) and
    // shouldRelockAfter's own doc for the legacy wire keys it still honours.
    if (shouldRelockAfter(elapsed, a.lockTiming.wireKey, screenTurnedOff)) _state.update { it.copy(locked = true) }
}

fun AppViewModel.maybeRelock(backgroundedAtMs: Long, screenTurnedOff: Boolean = false) {
    if (_state.value.locked) return
    viewModelScope.launch { relockIfDue(backgroundedAtMs, screenTurnedOff) }
    // Prompt to refresh if data is stale after returning from background.
    if (backgroundedAtMs > 0 && System.currentTimeMillis() - backgroundedAtMs > STALE_STATUS_MS) {
        val anyStale = _state.value.lastFetched.values.any { System.currentTimeMillis() - it > STALE_STATUS_MS }
        if (anyStale) reportInfo("Data may be stale, pull down to refresh")
    }
}

/**
 * Whether the device currently has usable biometrics (biometric/face) enrolled -- gates whether
 * [UiState.locked] / [maybeRelock] can ever apply, since there's nothing to authenticate against
 * otherwise.
 */
fun AppViewModel.canUseBiometrics(): Boolean =
    BiometricManager.from(getApplication())
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL) ==
        BiometricManager.BIOMETRIC_SUCCESS

/**
 * Turn the biometric app-lock on/off in Settings. Persists only -- the actual locking/unlocking
 * flow is driven separately by [maybeRelock] and [unlocked] reading this flag back out on each
 * foreground.
 */
fun AppViewModel.setBiometricLock(enabled: Boolean) {
    persist { setBiometricLock(enabled) }
}

/**
 * Shows the phone's lock as the sign-in screen opens after a restart, while the sign-in secrets are still behind it.
 * Confirming it unseals them for this sign-in; cancelling leaves Sign in to ask again.
 */
fun AppViewModel.promptDeviceLockForBoot(activity: androidx.fragment.app.FragmentActivity?) {
    if (activity == null || !canUseBiometrics()) return
    viewModelScope.launch {
        val locked = withContext(Dispatchers.IO) { credentialStore.secretsLocked }
        if (!locked || _state.value.bootDeviceLockProven) return@launch
        showBiometricPrompt(
            activity = activity,
            title = "Unlock Bloo",
            subtitle = "Confirm it's you to sign in",
            onSuccess = { _state.update { it.copy(bootDeviceLockProven = true) } },
            onError = { },
        )
    }
}

/**
 * The sign-in step after a phone restart. The account details typed on the form must match a stored account (checked
 * locally, nothing is sent anywhere). A phone lock confirmed on the sign-in screen is this boot's lock; without one,
 * a locked secrets vault asks for it here, and the app lock is the step after the details.
 */
fun AppViewModel.reauthAfterRestart(
    username: String,
    password: String,
    pin: String,
    brand: Brand,
    activity: androidx.fragment.app.FragmentActivity?,
) {
    viewModelScope.launch {
        val proven = _state.value.bootDeviceLockProven
        val locked = withContext(Dispatchers.IO) { credentialStore.secretsLocked }
        if (!locked || proven) {
            finishBootReauth(username, password, pin, brand, deviceLockProven = proven)
            return@launch
        }
        if (!canUseBiometrics()) {
            reportError("Set a screen lock to sign in.")
            return@launch
        }
        if (activity == null) {
            reportError("Couldn't ask for your phone's lock. Try again.")
            return@launch
        }
        showBiometricPrompt(
            activity = activity,
            title = "Unlock Bloo",
            subtitle = "Confirm it's you to sign in",
            onSuccess = { finishBootReauth(username, password, pin, brand, deviceLockProven = true) },
            onError = { reportInfo("Sign in again once your phone's lock is confirmed.") },
        )
    }
}

/** Checks the typed details against the stored account, then opens the boot, or leaves the app lock to decide. */
private fun AppViewModel.finishBootReauth(username: String, password: String, pin: String, brand: Brand, deviceLockProven: Boolean) {
    viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { credentialStore.matchesStoredAccount(brand, username, password, pin) }
        if (!ok) {
            reportError("Those details don't match.")
            return@launch
        }
        // The phone's lock was just confirmed for this sign-in, so the app lock is not asked for again.
        val armed = !deviceLockProven && withContext(Dispatchers.IO) {
            val a = settingsStore.appearance.first()
            a.biometricLock && canUseBiometrics()
        }
        _state.update { it.copy(bootReauth = false, bootDeviceLockProven = false) }
        if (armed) {
            pendingBootUnlock = true
            _state.update { it.copy(screen = Screen.Loading, locked = true) }
        } else {
            openBootAndStart()
        }
    }
}

/** Opens this boot for good (see BootGate), clears the "sign in" notification and starts as normal. */
private fun AppViewModel.openBootAndStart() {
    val app = getApplication<android.app.Application>()
    com.bloo.bluelink.data.BootGate.markOpen(app)
    com.bloo.bluelink.data.BootReauthNotice.cancel(app)
    com.bloo.bluelink.widget.BlooWidgetUpdater.requestUpdate(app)
    com.bloo.bluelink.autolock.AutoLockBootReceiver.startWatchIfEnabled(app)
    coldStartAutoLogin()
}
