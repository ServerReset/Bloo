package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.PinLockout
import com.bloo.bluelink.data.aiAutoSummarize
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
    probeAi()
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
    coldStartAutoLogin()
}

/**
 * Cold-start auto-login: if any brand has saved credentials (from a previous session), silently
 * restore all of them and start loading the garage — the one-time launch path, separate from the
 * interactive login(). After a phone restart (see BootGate) it stops at the sign-in form instead and
 * runs again once the whole re-auth flow has passed.
 */
internal fun AppViewModel.coldStartAutoLogin() {
    if (com.bloo.bluelink.data.BootGate.isLocked(getApplication())) {
        logStartup("Cold start: phone restarted -> re-auth required before anything runs")
        _state.update { it.copy(screen = Screen.Login, bootReauth = true) }
        reportInfo("Your phone restarted. Sign in again to continue.")
        return
    }
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

/** When the AI probe last ran, so coming back to the app does not hammer it. */
private var lastAiProbeMs = 0L

/** The one probe loop in flight: a new request while it runs would race it and could overwrite its answer. */
private var aiProbeJob: kotlinx.coroutines.Job? = null

/**
 * Asks the system whether Gemini Nano is usable and records the ANSWER, including the reason when the
 * check fails, in [UiState.aiAvailability] and the log. A result that is not usable is retried a few
 * times with growing waits (the service can still be starting, or the first call can race the
 * foreground), and again whenever the app returns to the foreground. Nothing AI is shown unless the
 * result is usable: [UiState.aiSupported] follows it. One loop at a time; [restart] cancels a running
 * one (the Debug card's "Check again"). A transient exception never downgrades a model that already
 * answered "usable" -- only a definite "Unavailable" does.
 */
internal fun AppViewModel.probeAi(restart: Boolean = false) {
    if (aiProbeJob?.isActive == true) {
        if (!restart) return
        aiProbeJob?.cancel()
    }
    lastAiProbeMs = System.currentTimeMillis()
    aiProbeJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        var attempt = 0
        while (true) {
            val startedAt = System.currentTimeMillis()
            val result = ai.availability()
            val tookMs = System.currentTimeMillis() - startedAt
            logStartup("AI probe: ${result.describe()} (${tookMs}ms, attempt ${attempt + 1})")
            val enabled = if (result.usable) settingsStore.aiEnabled() else false
            val auto = if (result.usable) settingsStore.aiAutoSummarize() else false
            _state.update {
                val keep = !result.usable && it.aiSupported && result is com.bloo.bluelink.data.AiAvailability.Error
                it.copy(
                    aiAvailability = result,
                    aiSupported = if (keep) true else result.usable,
                    aiEnabled = if (result.usable) enabled else it.aiEnabled,
                    aiAutoSummarize = if (result.usable) auto else it.aiAutoSummarize,
                )
            }
            val wait = com.bloo.bluelink.data.AiProbePolicy.nextDelayMs(result, attempt) ?: break
            kotlinx.coroutines.delay(wait)
            attempt++
        }
    }
}

/** Probes again on returning to the app if Gemini Nano was not found, at most once a minute. */
internal fun AppViewModel.recheckAiOnForeground() {
    val now = System.currentTimeMillis()
    if (com.bloo.bluelink.data.AiProbePolicy.shouldRecheckOnForeground(_state.value.aiAvailability, lastAiProbeMs, now)) probeAi()
}

/** The Debug card's "Check again": probe now, whatever the last result was. */
fun AppViewModel.reprobeAi() = probeAi(restart = true)
