package com.bloo.bluelink.ui

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.PinLockout
import com.bloo.bluelink.data.Credentials
import com.bloo.bluelink.data.CanadaAuth
import com.bloo.bluelink.data.CanadaRepository
import com.bloo.bluelink.data.KiaAuth
import com.bloo.bluelink.data.KiaRepository
import com.bloo.bluelink.data.VehicleRepository
import com.bloo.bluelink.data.StatusCache
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.Weather
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.bloo.bluelink.data.aiEnabled
import com.bloo.bluelink.data.setSettingsMode
import com.bloo.bluelink.data.snapshot

/**
 * A pending Kia one-time-code challenge shown over the login form. [sentTo] is
 * the destination the code went to ("EMAIL"/"SMS"), null while still choosing.
 */
data class KiaOtpUi(
    val challenge: KiaAuth.OtpRequired,
    val sentTo: String? = null,
)

/** A pending Canada one-time-code challenge shown over the login form. Unlike
 *  [KiaOtpUi] there's no destination to choose (Canada is email-only), so the
 *  code is already sent by the time this appears — see [AppViewModel.loginCanada]. */
data class CanadaOtpUi(
    val challenge: CanadaAuth.OtpRequired,
    val brand: Brand,
)

/**
 * @Stable, and this is the parameter that would otherwise undo the work done
 * on [UiState].
 *
 * Skippability is ALL-or-nothing per call site: one unstable parameter makes
 * the whole composable non-skippable, no matter how stable the rest are. `vm`
 * is passed alongside `state` into VehicleDetailContent and down into every
 * pebble, so marking UiState immutable while leaving this inferred-unstable
 * would have changed nothing at all at exactly the call sites it was meant to
 * fix.
 *
 * The promise holds the way it holds for any ViewModel: this is a single
 * instance that lives longer than the composition reading it, its identity
 * never changes under a composable, equality is referential, and everything
 * observable about it is read through a StateFlow collected into snapshot
 * state -- which is what notifies composition of a change.
 */

/** How long [AppViewModel.bootstrapDriveSync]'s own launch-time sync pass waits before
 *  starting, so it doesn't compete with the cold-start critical path (the currently-
 *  viewed car's own status fetch) for I/O -- see that call site's own doc for the real,
 *  timed report this came from. Long enough to cover that fetch's own network round trip
 *  under normal conditions; short enough that a user who opens Settings within the first
 *  few seconds still sees a sync that's already well underway rather than one that looks
 *  like it never started. */
internal const val DRIVE_SYNC_COLD_START_DELAY_MS = 3_000L

@Stable
class AppViewModel(app: Application) : AndroidViewModel(app) {
    // Set before any other property, so every timing log measures from the first instant this
    // constructor runs (MainActivity's `by viewModels()` forces it ahead of setContent). Each
    // `logStartup` call below narrows a slow cold start down to one stage.
    internal val coldStartAt = System.currentTimeMillis()

    /** Logs [message] to [AppLog] with elapsed time since this ViewModel was constructed
     *  ([coldStartAt]) -- see that property's own doc. Startup-only: nothing outside the
     *  cold-start path below calls this, since "+1234ms since app start" stops being a
     *  meaningful number once the app has been open and used for a while. */
    internal fun logStartup(message: String) {
        AppLog.log("$message (+${System.currentTimeMillis() - coldStartAt}ms)")
        // Same breadcrumb on the greppable startup trace, so one logcat filter shows the
        // ViewModel's phases interleaved with Application/Activity/frame marks.
        com.bloo.bluelink.data.StartupTrace.markIfStarting(message)
    }

    // Each store construction is timed individually: the ViewModel is constructed
    // synchronously on the main thread by MainActivity's `viewModels()` dereference, so
    // anything a constructor does (opening SharedPreferences, resolving DataStore files,
    // building an ML Kit client) is on the critical path to the first frame.
    internal val store = com.bloo.bluelink.data.StartupTrace.trace("SessionStore()") { SessionStore(app) }

    internal val settingsStore = com.bloo.bluelink.data.StartupTrace.trace("SettingsStore()") { SettingsStore(app) }

    internal val credentialStore = com.bloo.bluelink.data.StartupTrace.trace("CredentialStore()") { CredentialStore(app) }

    internal val snapshotStore = com.bloo.bluelink.data.StartupTrace.trace("SnapshotStore()") { SnapshotStore(app) }

    internal val statusCache = com.bloo.bluelink.data.StartupTrace.trace("StatusCache()") { StatusCache(app) }

    internal val ai = com.bloo.bluelink.data.StartupTrace.trace("Ai()") { com.bloo.bluelink.data.Ai(app) }

    // One repository per signed-in brand (any mix of brands can be active).
    internal val repos = mutableMapOf<Brand, VehicleRepository>()

    internal fun repoFor(brand: Brand): VehicleRepository =
        repos.getOrPut(brand) { com.bloo.bluelink.data.repositoryFor(brand, store, credentialStore) }

    internal fun kiaRepo(): KiaRepository = repoFor(Brand.KIA) as KiaRepository

    internal fun canadaRepo(brand: Brand): CanadaRepository = repoFor(brand) as CanadaRepository

    internal fun brandOf(v: Vehicle): Brand =
        Brand.fromIndicator(v.brandIndicator)

    internal fun repoFor(v: Vehicle): VehicleRepository = repoFor(brandOf(v))

    @Volatile
    internal var loadingGarage = false

    /** A pending app-icon shortcut (vin to command) awaiting the garage to load. */
    @Volatile
    internal var pendingShortcut: Pair<String, String>? = null

    /** One-shot: has [refreshLiveChargeBar] forgotten this process's live-charge
     *  dismissals yet? See that function's own comment for why. */
    @Volatile
    internal var liveChargeDismissalsResetThisSession = false

    /** Set to true during garage load if the app will show a lock screen, so that
     *  status fetching is deferred until after unlock (avoiding recomposition jank
     *  that overlaps the lock-away blur animation). */
    @Volatile
    internal var deferredStatusLoad = false

    internal val _state = MutableStateFlow(UiState())

    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Serializes ALL vehicleStatus calls account-wide (shared with the background
     * worker via [com.bloo.bluelink.data.BlueLinkGate]). Blue Link rejects
     * overlapping requests with `502 ... a previous request is pending`.
     */
    internal val statusMutex = com.bloo.bluelink.data.BlueLinkGate.statusMutex

    /** Status requests currently queued or running, keyed "vin:refresh"
     *  (de-dupes; a live refresh=true isn't dropped behind a background
     *  refresh=false fetch for the same car). */
    /**
     * Which car the pager is on -- its OWN flow, deliberately not a field of
     * [UiState], and this is the swipe fix.
     *
     * UiState is a data class, so its equals covers every field; change one
     * and no reader can skip. It is threaded into VehicleDetailContent and
     * from there into every pebble, so putting the pager's position inside it
     * meant that finishing a swipe -- the one moment the whole object had to
     * change for a reason no pebble cares about -- rebuilt all three live car
     * pages. selectIndex already guarded the case where the index did not
     * actually move, and described the cost as "the worst kind of hitch:
     * invisible work at exactly the moment the user is watching the gesture
     * finish." That guard only ever covered the no-op; a real page change paid
     * it in full, every time.
     *
     * Split out, the settle touches nothing the pages read, so they skip
     * entirely. The handful of screen-level composables that genuinely need
     * the index collect this instead, and only they recompose.
     */
    internal val _currentIndex = MutableStateFlow(0)

    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    /**
     * APK download progress (0-1 while downloading, null before/after), split out of
     * [UiState] for the same reason as [currentIndex]: it updates on every 64KB chunk of a
     * multi-MB download, and while it lived in the monolithic UiState each of those hundreds
     * of ticks forced an equals-diff and recomposition of every pebble on the live pager
     * pages -- exactly while the user is watching the tile. Collected only by
     * UpdateAvailableTile, so a chunk now invalidates just the progress bar and percent text.
     */
    internal val _updateDownloadProgress = MutableStateFlow<Float?>(null)

    val updateDownloadProgress: StateFlow<Float?> = _updateDownloadProgress.asStateFlow()

    internal val statusInFlight = mutableSetOf<String>()

    /** Subset of [statusInFlight] whose call used surfaceErrors=true (drives the spinner + settle haptic). */
    internal val surfaceInFlight = mutableSetOf<String>()

    /** VINs fetched from the network this session (cache restore doesn't count). */
    internal val sessionFetched = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Guards [bootstrapDriveSync] so it starts its collector exactly once per
     *  ViewModel, no matter how many times the garage (re)loads. */
    internal val driveSyncBootstrapped = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Copy-pasteable activity log shown in Settings. */
    /**
     * Ticks whenever the activity log changes. The Settings logs card collects THIS and
     * snapshots the log when it ticks, instead of every log line allocating a fresh
     * 500-element list for a StateFlow nobody may be watching (see [AppLog]).
     */
    val logsVersion: StateFlow<Int> = AppLog.version

    /** Point-in-time copy of the activity log, for the Settings logs card. */
    fun logSnapshot(): List<String> = AppLog.snapshot()

    val appearance: StateFlow<SettingsStore.Appearance> =
        settingsStore.appearance.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SettingsStore.Appearance(),
        )

    val notifications: StateFlow<SettingsStore.NotificationPrefs> =
        settingsStore.notifications.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SettingsStore.NotificationPrefs(),
        )

    /** Write the current live status/location maps to disk (survives restart). */
    internal fun persistCache() {
        val s = _state.value
        viewModelScope.launch {
            statusCache.save(s.statuses, s.locations, s.placeNames, s.lastFetched)
        }
    }

    init {
        logStartup("AppViewModel constructed")
        // Probe on-device Gemini Nano once; the AI toggle only appears if present.
        // On Dispatchers.IO, like the probe below: ai.isSupported() builds an ML Kit client
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
        // Probe Shizuku; the "seamless install" toggle only appears if it's installed
        // + running. pingBinder() is a cross-process binder call, so keep it off the
        // main thread (like the AI probe above). Re-probed on resume via
        // refreshShizukuAvailable() so starting Shizuku after launch reveals the toggle.
        refreshShizukuAvailable()
        // Bloo isn't on the Play Store, so check its own build channel once
        // per cold start (debounced internally — see UpdateChecker). Also
        // re-run on every user-triggered refresh, see refreshStatus below.
        checkForUpdate()
        com.bloo.bluelink.data.StartupTrace.markIfStarting("AppViewModel init block: synchronous tail done")
        // Restore the last-known status/location from disk so the UI shows
        // stale-but-useful data immediately, before any network call returns.
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
        // Cold-start auto-login: if any brand has saved credentials (from a
        // previous session), silently restore all of them and start loading the
        // garage — this is the one-time launch path, separate from the
        // interactive login() below.
        viewModelScope.launch {
            val brands = store.loggedInBrands()
            if (brands.isEmpty()) {
                logStartup("Cold start: no saved logins -> Screen.Login")
                // Genuinely logged out -- the real destination IS Login, so
                // move off Screen.Loading (screen's own default) to it now
                // rather than waiting on anything else to do it.
                _state.update { it.copy(screen = Screen.Login) }
                return@launch
            }
            logStartup("Cold start: ${brands.size} saved login(s) (${brands.joinToString { it.label }})")
            // repoFor(it) lazily creates+caches one VehicleRepository per brand
            // in the `repos` map (see repoFor above) so later calls just reuse it.
            //
            // In try/catch because the default screen is a static Loading screen with no interactive
            // escape: an exception anywhere before loadGarage() takes over (it has its own per-brand
            // handling, see loadGarageInner) must not leave the user stuck on it.
            try {
                // The LOCK decision first: it puts the lock screen up, and needs only the appearance and
                // the PIN record, not repo construction or credentials (those run in parallel below).
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
                // The garage load's two independent blocking pieces, started together: building each
                // brand's shared OkHttp client, and the encrypted credential load (MasterKey +
                // EncryptedSharedPreferences). Both belong off Main (this runs on Main.immediate).
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppLog.log("⚠ cold-start auto-login failed: ${e.message}")
                _state.update { if (it.screen == Screen.Loading) it.copy(screen = Screen.Login) else it }
            }
        }
    }

    // --- Auth ------------------------------------------------------------

    // Kia sign-in is a two-step dance: password first, then (usually) a
    // one-time code sent to the account's email or phone. The credentials are
    // held here between the steps and only persisted once fully signed in.
    internal var kiaPending: Credentials? = null

    // Canada sign-in (Hyundai/Genesis/Kia) is also a two-step dance, but unlike
    // Kia US there's no destination choice (email only) and the account's PIN
    // IS required (every command needs it, see CanadaApi.pinAuth) -- so the PIN
    // typed into the login form travels straight through the OTP challenge.
    internal var canadaPending: Credentials? = null

    /** Large-screen only: expand one car to full screen (also selects it, so
     *  the two indices never disagree about which car is "current"). */
    fun expand(index: Int) {
        _currentIndex.value = index
        _state.update { it.copy(expandedIndex = index) }
    }

    /** Back out of the expanded single-car view to the grid. */
    fun collapse() = _state.update { it.copy(expandedIndex = null) }

    /**
     * How long a text field must be quiet before its change is published to the other
     * surfaces. Long enough to collapse a whole typed word, short enough that letting go
     * of the keyboard feels immediate.
     */
    internal val textFieldPublishDebounceMs = 400L

    /** In-flight debounced publishes, keyed so two fields -- or the same field on two
     *  cars -- never cancel each other's pending work. */
    internal val pendingPublishes = mutableMapOf<String, Job>()

    internal suspend fun persistSnapshots(vehicles: List<Vehicle> = _state.value.vehicles) {
        snapshotStore.saveVehicles(vehicles.map { snapshotOf(it, _state.value.statuses[it.vin], _state.value) })
        refreshLiveChargeBar(vehicles)
    }

    /** Re-sort a freshly-fetched vehicle list to match the user's saved
     *  drag-and-drop [order] (a list of VINs). Any VIN in [order] that no
     *  longer matches a fetched vehicle is simply skipped (mapNotNull), and
     *  any newly-appeared vehicle not yet in [order] (a car added to the
     *  account since the order was last saved) is appended at the end rather
     *  than dropped, so new cars still show up somewhere. */

    // --- AutoLock (app/.../autolock/) -------------------------------------
    //
    // Thin passthroughs: Settings reads/writes SettingsStore directly (no UiState copy), since these
    // values are only consumed in the background by the auto-lock receiver/service.

    /** Live per-car evaluation state (detection phase + grace countdown), for the Settings
     *  section to show "watching…" / "locking in 12s" / "locked" while a test or a real
     *  trigger is in flight. */
    val autoLockState: StateFlow<Map<String, com.bloo.bluelink.autolock.AutoLockEvalState>>
        get() = com.bloo.bluelink.autolock.AutoLockController.state

    /** Toggle a pebble (detail section) open/closed for a car (persisted). */
    /**
     * Collapse keys ("<vin>:<section>") on their own small flow.
     *
     * Settings cards are pebbles too (SettingsCard renders through PebbleShell), so they get
     * their open/closed state from the same place car pebbles do -- but a card observing the
     * whole UiState to learn one boolean would recompose all ~19 of them on every status poll.
     * This is the one field they actually need, so a change to anything else cannot touch them.
     */
    val collapsedSections: StateFlow<Set<String>> = state
        .map { it.collapsedPebbles }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // --- App self-update (GitHub Actions builds; Bloo isn't on the Play Store) ---

    /** The GitHub Actions build number this app was compiled from (0 = local build). */
    val currentBuildNumber: Int get() = com.bloo.bluelink.BuildConfig.BUILD_RUN_NUMBER

    /** Per-VIN pending debounced climate save (see saveClimateDebounced). */
    internal val climateSaveJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    internal var liveLocationJob: kotlinx.coroutines.Job? = null



    // Appearance/preference setters each just write one field to SettingsStore; `appearance` is a
    // StateFlow mirroring it, so the UI follows once the DataStore write re-emits (none touch _state).
    // setThemeMode / setFontChoice / setDynamicColor / setColorPalette / saveCustomPalette /
    // deleteCustomPalette / setActiveCustomPaletteId moved to AppViewModelAppearance.kt.

    // exportSettings / importSettings / setSyncUri / clearSyncUri / importSettingsAndSync /
    // importSettingsAndSyncSuspend / setSyncWifiOnly moved to AppViewModelSync.kt.

    // --- Weather ---------------------------------------------------------
    internal val weather = WeatherController(getApplication(), settingsStore, _state, viewModelScope)

    fun clearWeatherLocation() = weather.clearWeatherLocation()

    fun loadHomeWeather(force: Boolean = false) = weather.loadHomeWeather(force)

    fun loadCarWeather(v: Vehicle, force: Boolean = false) = weather.loadCarWeather(v, force)

    // setColumnsFlipped / setUiScaleSoon / setVibrancySoon / setHapticsEnabled /
    // setPebbleOutline / setShowSearch / searchBubblePosition / setSearchBubblePosition /
    // setSeamlessInstallShizuku / refreshShizukuAvailable / setAuroraBackground /
    // setAuroraMotion / setUnitSystem moved to AppViewModelAppearance.kt.

    /** Wipe the in-memory activity log shown in Settings (not persisted, so
     *  nothing to clear on disk). */
    fun clearLogs() = AppLog.clear()

    /** Dismiss the current snackbar. Also resets [UiState.messageType] back to
     *  the "error" default so a prior success/info message can't leave the type
     *  sticky -- the next raw `message = ...` set (e.g. a command/status/login
     *  failure that doesn't go through reportError) then renders in the error
     *  colour rather than inheriting the previous benign colour. */
    fun clearMessage() = _state.update { it.copy(message = null, messageType = "error") }

    /** Surface (and log) an error raised by the UI layer. */
    fun reportError(msg: String) {
        AppLog.log("⚠ $msg")
        _state.update { it.copy(message = msg, messageType = "error") }
    }

    /** A neutral, non-error snackbar message (e.g. a setup nudge). */
    fun reportInfo(msg: String) {
        _state.update { it.copy(message = msg, messageType = "info") }
    }

    /** Switch between simple and advanced settings view. */
    fun setSettingsMode(mode: String) {
        _state.update { it.copy(settingsMode = mode) }
        viewModelScope.launch { settingsStore.setSettingsMode(mode) }
    }
}
