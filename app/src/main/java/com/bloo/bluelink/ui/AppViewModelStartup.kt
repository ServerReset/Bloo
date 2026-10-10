package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.PinLockout
import com.bloo.bluelink.data.aiEnabled
import com.bloo.bluelink.rethrowIfCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The cold-start path, run once from [AppViewModel]'s init block: probe on-device AI and Shizuku
 * (both gated features), kick off the self-update check, restore the last-known status from disk,
 * then silently auto-login every saved brand and load the garage (pausing on the lock screen when
 * one is required). Kept out of the constructor so the init block reads as a short summary of the
 * launch sequence rather than a hundred lines of it.
 */
internal fun AppViewModel.bootstrapColdStart() {
    // Probe on-device Gemini Nano once; the AI toggle only appears if present. On
    // Dispatchers.IO, like the probe below: ai.isSupported() builds an ML Kit client
    // synchronously on first call, which on viewModelScope's Main.immediate landed on the main
    // thread during cold start.
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val supported = ai.isSupported()
        logStartup("AI probe done in ${System.currentTimeMillis() - startedAt}ms, supported=$supported")
        if (supported) {
            _state.update {
                it.copy(
                    aiSupported = true,
                    aiEnabled = settingsStore.aiEnabled(),
                )
            }
        }
    }
    // Probe Shizuku; the "seamless install" toggle only appears if it's installed + running.
    // pingBinder() is a cross-process binder call, so keep it off the main thread (like the AI
    // probe above).
    refreshShizukuAvailable()
    // Bloo isn't on the Play Store, so check its own build channel once per cold start
    // (debounced internally — see UpdateChecker). Also re-run on every user-triggered refresh,
    // see refreshStatus below.
    checkForUpdate()
    com.bloo.bluelink.data.StartupTrace.markIfStarting("AppViewModel init block: synchronous tail done")
    // Restore the last-known status/location from disk so the UI shows stale-but-useful data
    // immediately, before any network call returns.
    viewModelScope.launch {
        val startedAt = System.currentTimeMillis()
        val cached = statusCache.load()
        logStartup(
            "Status cache restored in ${System.currentTimeMillis() - startedAt}ms: " +
                "${cached.statuses.size} status(es), ${cached.locations.size} location(s)",
        )
        if (cached.statuses.isNotEmpty() || cached.locations.isNotEmpty()) {
            _state.update {
                it.copy(
                    statuses = cached.statuses + it.statuses,
                    locations = cached.locations + it.locations,
                    placeNames = cached.placeNames + it.placeNames,
                    lastFetched = cached.fetched + it.lastFetched,
                )
            }
        }
    }
    // Cold-start auto-login: if any brand has saved credentials (from a previous session),
    // silently restore all of them and start loading the garage — this is the one-time launch
    // path, separate from the interactive login() below.
    viewModelScope.launch {
        val brands = store.loggedInBrands()
        if (brands.isEmpty()) {
            logStartup("Cold start: no saved logins -> Screen.Login")
            _state.update { it.copy(screen = Screen.Login) }
            return@launch
        }
        logStartup("Cold start: ${brands.size} saved login(s) (${brands.joinToString { it.label }})")
        // repoFor(it) lazily creates+caches one VehicleRepository per brand in the `repos` map
        // (see repoFor above) so later calls just reuse it.
        try {
            // The LOCK decision first: it puts the lock screen up, and needs only the
            // appearance and the PIN record, not repo construction or credentials (those run in
            // parallel below).
            val appearance = settingsStore.appearance.first()
            val (lockMechanisms, appPinSet, lockout) = withContext(Dispatchers.IO) {
                val pinSet = credentialStore.getPinRecord() != null
                Triple(
                    (appearance.biometricLock && canUseBiometrics()) || pinSet,
                    pinSet,
                    PinLockout(
                        credentialStore.getPinFailures(),
                        credentialStore.getPinLockedUntil(),
                        credentialStore.getPinLockedUntilElapsed(),
                    ),
                )
            }
            _state.update { it.copy(appPinSet = appPinSet, pinLockout = lockout) }
            if (lockMechanisms) {
                logStartup("Cold start: lock screen required, deferring garage load until unlock")
                _state.update { it.copy(locked = true) }
            }
            // The garage load's two independent blocking pieces, started together: building
            // each brand's shared OkHttp client, and the encrypted credential load (MasterKey +
            // EncryptedSharedPreferences).
            val parallelIoStartedAt = System.currentTimeMillis()
            val accounts = withContext(Dispatchers.IO) {
                coroutineScope {
                    val repos = async { brands.forEach { repoFor(it) } }
                    val creds = async { credentialStore.loadAll() }
                    repos.await()
                    creds.await()
                }
            }
            logStartup(
                "Cold start: repos + credentials loaded in " +
                    "${System.currentTimeMillis() - parallelIoStartedAt}ms: ${accounts.size} account(s)",
            )
            _state.update { it.copy(accounts = accounts) }
            logStartup("Cold start: calling loadGarage()")
            loadGarage()
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            AppLog.log("⚠ cold-start auto-login failed: ${e.message}")
            _state.update { if (it.screen == Screen.Loading) it.copy(screen = Screen.Login) else it }
        }
    }
}
