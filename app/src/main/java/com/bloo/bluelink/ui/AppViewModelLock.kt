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
import com.bloo.bluelink.autolock.AutoLockBootReceiver
import com.bloo.bluelink.data.BootReauthNotice
import com.bloo.bluelink.widget.BlooWidgetUpdater
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
 * The sign-in step after a phone restart. Biometrics are not offered before it: the account details are typed on the form and must
 * match a stored account (checked on the phone, nothing is sent anywhere). Then ONE prompt, and only when there is something to unlock:
 * it unseals the sign-in secrets (it takes only what their key accepts, a strong biometric or the phone's own lock) and it is also this
 * sign-in's app lock, so nothing asks again after it. It used to take two: one for the secrets and another for the app lock.
 */
fun AppViewModel.reauthAfterRestart(
    username: String,
    password: String,
    pin: String,
    brand: Brand,
    activity: androidx.fragment.app.FragmentActivity?,
) {
    viewModelScope.launch {
        suspend fun matches() = withContext(Dispatchers.IO) { credentialStore.matchesStoredAccount(brand, username, password, pin) }
        // The vault reports itself locked only once a read has failed, so the details are checked first and the lock looked at after.
        var ok = matches()
        val vaultLocked = withContext(Dispatchers.IO) { credentialStore.secretsLocked }
        val appLockArmed = withContext(Dispatchers.IO) {
            val a = settingsStore.appearance.first()
            a.biometricLock && canUseBiometrics()
        }
        when (BootReauthPolicy.afterDetails(ok, vaultLocked, appLockArmed, canPrompt = canUseBiometrics() && activity != null)) {
            BootReauthPolicy.Step.Reject -> {
                reportError("Those details don't match.")
                return@launch
            }
            BootReauthPolicy.Step.Open -> {
                openAfterBootReauth()
                return@launch
            }
            BootReauthPolicy.Step.CannotPrompt -> {
                reportError(if (activity == null) "Couldn't ask for your phone's lock. Try again." else "Set a screen lock to sign in.")
                return@launch
            }
            BootReauthPolicy.Step.Prompt -> Unit
        }
        activity ?: return@launch
        val passed = awaitBiometricPrompt(activity, title = "Unlock Bloo", subtitle = "Confirm it's you to finish signing in", strong = true)
        if (!passed) {
            reportInfo("Sign in again once your phone's lock is confirmed.")
            return@launch
        }
        // The secrets were sealed: now that the prompt has opened them, the details can be checked.
        if (vaultLocked) ok = matches()
        if (!BootReauthPolicy.opensAfterPrompt(passed, ok)) {
            reportError("Those details don't match.")
            return@launch
        }
        openAfterBootReauth()
    }
}

/** The restart sign-in is complete (details matched, and the one prompt passed when there was one): the app opens without asking again. */
private fun AppViewModel.openAfterBootReauth() {
    _state.update { it.copy(bootReauth = false) }
    openBootAndStart(justConfirmed = true)
}

/** Opens this boot for good (see BootGate), clears the "sign in" notification and starts as normal. */
private fun AppViewModel.openBootAndStart(justConfirmed: Boolean = false) {
    val app = getApplication<android.app.Application>()
    com.bloo.bluelink.data.BootGate.markOpen(app)
    BootReauthNotice.cancel(app)
    BlooWidgetUpdater.requestUpdate(app)
    AutoLockBootReceiver.startWatchIfEnabled(app)
    // The cold start would otherwise put the app lock up again, a second prompt straight after the sign-in's one.
    coldStartAutoLogin(justConfirmed = justConfirmed)
}
