package com.bloo.bluelink.ui

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.CarAlerts
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.Notifications
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
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.Weather
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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

    // Set before any other property below, so every timing log this class writes measures
    // from the actual first instant this constructor started running -- the moment
    // MainActivity.onCreate's `by viewModels()` dereference (see its own comment) forces
    // this ViewModel into existence, ahead of setContent. Reported directly as still
    // stuttering hard on first load even with BlooApplication's "App starting" and this
    // class's own "Garage loaded" breadcrumbs in place -- those two points bracket the
    // WHOLE cold start with nothing in between, so a slow stretch anywhere inside it had no
    // way to show up in a report. Every `logStartup` call below narrows that down to one
    // specific stage instead.
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

    /** The pending "Not now" undo-window timer (see dismissUpdate). */
    internal var updateDismissJob: kotlinx.coroutines.Job? = null

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

    /**
     * Evaluates this car's freshly-fetched [status] against the user's alert
     * thresholds (door-open duration, engine-running duration, etc. — see
     * [CarAlerts]), posts a system notification for every alert that fires, and
     * additionally surfaces the FIRST one as an in-app snackbar message so it's
     * visible even if the app is already in the foreground (where a system
     * notification is easy to miss). Called after every successful status load.
     */
    internal suspend fun checkAlerts(v: Vehicle, status: VehicleStatus) {
        val alerts = CarAlerts.evaluate(settingsStore, v, status)
        alerts.forEach { Notifications.post(getApplication(), it.id, it.title, it.text, it.actions, it.channelId) }
        alerts.firstOrNull()?.let { a -> _state.update { it.copy(message = a.text, messageType = "error") } }
    }

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
        // Dispatchers.IO -- like the Shizuku probe right below, and for the same
        // reason its own comment states but this one didn't follow: ai.isSupported()
        // touches a `by lazy` Summarizer client on first call (Ai.kt), constructing
        // an ML Kit client synchronously before the real suspension point, and
        // viewModelScope defaults to Dispatchers.Main.immediate. That construction
        // cost was landing on the main thread at exactly the moment of the reported
        // cold-start lag, alongside every other init-block probe.
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
            // Wrapped in try/catch, unlike before Screen.Loading existed: this
            // block used to have nothing riding on it completing -- the
            // default screen was Login itself, so any exception here just
            // left the user looking at an already-correct (if not-yet-auto-
            // filled) login form. Now the default is a static Loading screen
            // with no interactive escape, so an exception ANYWHERE before
            // loadGarage() itself takes over (which has its own, separate
            // per-brand error handling -- see loadGarageInner) must not leave
            // the user stuck looking at it forever.
            try {
                // The LOCK decision first: it is what puts the lock screen on the glass, the
                // first thing a returning user sees, and it needs only the appearance (warmed
                // DataStore) and the PIN record (warmed crypto) -- NOT the repo construction or
                // the full credential list, which feed the garage, not the lock. Those run in
                // parallel just below, behind the lock screen.
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
                // The garage load's two independent blocking pieces, started together. Repo
                // construction builds a shared OkHttp client per brand (Dispatcher,
                // ExecutorService, ConnectionPool, route database, the whole OkHttp class
                // graph); the encrypted credential load is CredentialStore's lazy prefs
                // (MasterKey + EncryptedSharedPreferences + Tink keyset parse). Both belong off
                // Main (this block runs on Main.immediate) and neither depends on the other.
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

    // --- App PIN (device unlock PIN) -------------------------------------

    // --- Garage / vehicles ----------------------------------------------

    /**
     * Switch the visible car (swipe). Updates the index, and lazily loads this
     * car's status only if we don't already have it — so already-loaded cars are
     * never re-fetched on a swipe, but a car that failed to load at startup gets
     * another chance when you view it.
     */
    fun selectIndex(index: Int) {
        val v = _state.value.vehicles.getOrNull(index) ?: return
        // A no-op selection must not emit. UiState is threaded into every
        // pebble and is unstable, so one emission recomposes every car page
        // currently in composition -- and this is called from a snapshotFlow on
        // the pager's settledPage, which re-fires whenever the pager re-settles
        // on the car it was already showing (a wrap snap, an external select
        // that matched, a settle that never left the page). Paying three full
        // car-page rebuilds to set currentIndex to the value it already holds
        // is the worst kind of hitch: invisible work at exactly the moment the
        // user is watching the gesture finish.
        if (_currentIndex.value == index) {
            ensureStatus(v)
            return
        }
        _currentIndex.value = index
        viewModelScope.launch { settingsStore.setLastVehicleVin(v.vin) }
        ensureStatus(v)
    }

    /** Large-screen only: expand one car to full screen (also selects it, so
     *  the two indices never disagree about which car is "current"). */
    fun expand(index: Int) {
        _currentIndex.value = index
        _state.update { it.copy(expandedIndex = index) }
    }
    /** Back out of the expanded single-car view to the grid. */
    fun collapse() = _state.update { it.copy(expandedIndex = null) }

    /** Persist a new car display order (drag-and-drop in Settings). */
    fun reorderVehicles(order: List<Vehicle>) {
        _state.update { s ->
            // Keep the same CAR selected across a reorder, not the same
            // position -- selectIndex/expand always update currentIndex
            // together with vehicles, but this was the one place that moved
            // vehicles without it, so dragging a car above the currently
            // selected one silently swapped which car the detail view showed.
            val selectedVin = s.vehicles.getOrNull(_currentIndex.value)?.vin
            val newIndex = order.indexOfFirst { it.vin == selectedVin }
            if (newIndex >= 0) _currentIndex.value = newIndex
            s.copy(vehicles = order)
        }
        viewModelScope.launch {
            settingsStore.setVehicleOrder(order.map { it.vin })
            persistSnapshots(order)
        }
    }

    /**
     * How long a text field must be quiet before its change is published to the other
     * surfaces. Long enough to collapse a whole typed word, short enough that letting go
     * of the keyboard feels immediate.
     */
    private val textFieldPublishDebounceMs = 400L

    /** In-flight debounced publishes, keyed so two fields -- or the same field on two
     *  cars -- never cancel each other's pending work. */
    private val pendingPublishes = mutableMapOf<String, Job>()

    /**
     * Publish after [textFieldPublishDebounceMs] of quiet on [key], superseding any
     * publish still pending for that same key.
     *
     * This exists because three settings are edited through raw `onValueChange` text
     * fields -- licence plate, last-service miles, service interval -- and each one used
     * to run the full [persistSnapshots] fan-out on every single typed character. That
     * is a full snapshot re-encode and disk commit, then a re-read and re-decode of the
     * whole snapshot payload by every interested surface. Typing a seven-character plate
     * did all of that seven times.
     *
     * What is NOT debounced, deliberately: the `_state` update (so the field the user is
     * typing in stays responsive) and the SettingsStore write itself (so the value is
     * durable the instant it's typed, and closing the app mid-word cannot lose it). Only
     * the cross-surface publish waits, and only for as long as the user keeps typing.
     *
     * [persistSnapshots] reads `_state`, never the settings store, so a debounced publish
     * always carries the latest typed value rather than whatever was current when it was
     * scheduled.
     *
     * The map is only ever touched from the main dispatcher -- viewModelScope's default,
     * and there is no suspension point between the read and the write below -- so a plain
     * mutableMapOf is safe here. It is also never iterated, which is what made the other
     * plain map in this class a ConcurrentModificationException waiting to happen.
     */
    internal fun publishDebounced(key: String) {
        pendingPublishes[key]?.cancel()
        pendingPublishes[key] = viewModelScope.launch {
            delay(textFieldPublishDebounceMs)
            persistSnapshots()
            pendingPublishes.remove(key)
        }
    }

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
    // Thin passthroughs: the Settings UI reads/writes SettingsStore directly through these
    // (no UiState copy) because the values are otherwise only ever consumed in the
    // background, by AutoLockBluetoothReceiver/AutoLockService reading SettingsStore fresh
    // on each trigger -- there's no live-recomposition need the way seat flags have with the
    // climate pebble.

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

    /**
     * Settings cards call this exact function too, via the placeholder
     * [SettingsPseudoVehicle] (its `vin` is the only field [togglePebble] ever reads) --
     * not a separate `toggleSettingsCard` that used to duplicate this whole body under
     * [SETTINGS_CARD_VIN] by hand. Same state, same store, same persistence -- so a
     * Settings card remembers whether it was open across launches exactly the way a
     * car's pebble does, through the literal same code path rather than a lookalike.
     */
    fun togglePebble(v: Vehicle, section: String) {
        val key = "${v.vin}:$section"
        val collapsedNow = key !in _state.value.collapsedPebbles
        _state.update {
            it.copy(
                collapsedPebbles = if (collapsedNow) it.collapsedPebbles + key else it.collapsedPebbles - key,
            )
        }
        viewModelScope.launch { settingsStore.setSectionCollapsed(v.vin, section, collapsedNow) }
    }

    // --- App self-update (GitHub Actions builds; Bloo isn't on the Play Store) ---

    /** The GitHub Actions build number this app was compiled from (0 = local build). */
    val currentBuildNumber: Int get() = com.bloo.bluelink.BuildConfig.BUILD_RUN_NUMBER

    /** Fetch recent EV trips once per session (the Trips pebble calls this lazily). */
    fun loadTrips(v: Vehicle) {
        if (v.vin in _state.value.trips || _state.value.isPending(v.vin, "trips")) return
        viewModelScope.launch {
            _state.update { it.copy(pending = it.pending + "${v.vin}:trips") }
            // Only cache the result on a successful fetch -- caching emptyList()
            // on a transient failure looked identical to "genuinely no trips",
            // and since the vin's presence in the map is what gates a re-fetch
            // above, one bad network blip permanently stuck this car at "no
            // trips" for the rest of the session with no way to retry.
            // Serialize with every other repo call via the account-wide statusMutex:
            // Blue Link rejects overlapping requests ("a previous request is pending"),
            // and an unlocked trips() call could also race a concurrent 401 refresh
            // using the same stale refresh token. Every other repo.* path takes this
            // lock (loadStatus/runCommand/loadGarage/loadTrips); this
            // was the lone gap. Only the network call is inside the lock — the filter
            // and result handling stay outside, matching loadStatus's minimal scope.
            val fetched = runCatching { statusMutex.withLock { repoFor(v).trips(v) } }
                .onFailure { e -> AppLog.log("⚠ Trips for ${v.name}: ${e.message ?: "failed"}") }
                .getOrNull()
                ?.filter { (it.distance ?: 0.0) > 0 }
            _state.update {
                it.copy(
                    trips = if (fetched != null) it.trips + (v.vin to fetched) else it.trips,
                    pending = it.pending - "${v.vin}:trips",
                )
            }
        }
    }

    /** Per-VIN pending debounced climate save (see saveClimateDebounced). */
    internal val climateSaveJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    internal var liveLocationJob: kotlinx.coroutines.Job? = null

    /** The in-flight [loadNearbyChargers] fetch, if any -- see its own doc for why a
     *  superseded one is cancelled outright rather than just having its result ignored. */
    internal var chargerJob: kotlinx.coroutines.Job? = null

    // beginLiveDeviceLocation / locate moved to AppViewModelCommands.kt.

    // lock / unlock / flashLights / hornAndLights / stopClimate / startClimate /
    // toggleClimate / startCharge / stopCharge / setChargeLimits / runCommand /
    // recordRemoteAction moved to AppViewModelCommands.kt.

    // --- Settings / nav --------------------------------------------------

    /** Kept in sync by the garage pager's and the compact cover pager's own
     *  settle effects -- see
     *  [UiState.onSettingsPageSlot]'s own doc. Guarded the same way, so
     *  settling on the same kind of page repeatedly (two cars in a row, or
     *  two settles on the Settings slot) doesn't emit a redundant UiState
     *  update every time. */
    fun setOnSettingsPageSlot(value: Boolean) {
        if (_state.value.onSettingsPageSlot != value) _state.update { it.copy(onSettingsPageSlot = value) }
    }

    // toggleChargersVisible / loadNearbyChargers / setChargerApiKey / setChargerMinKw /
    // toggleChargerNetwork moved to AppViewModelChargers.kt (extension functions).

    // Appearance/preference setters (setThemeMode through setColorPalette,
    // and again setPebbleOutline/setAuroraBackground/.../setUnitSystem further
    // below): each just writes one field to SettingsStore's DataStore and
    // returns. None of them touch _state directly because `appearance` above
    // is already a StateFlow mirroring settingsStore.appearance -- the UI
    // picks up the change automatically once the DataStore write completes
    // and that Flow re-emits. setDynamicColor is the exception
    // that does extra work (see its own comment).
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

    /** Set (or clear, with null) which saved preset the one-tap climate Start
     *  button runs for this car -- read back out in [bootstrapDriveSync]'s
     *  restore step into [UiState.defaultClimatePresets]. */
    fun setDefaultClimatePreset(vin: String, id: String?) = viewModelScope.launch {
        settingsStore.setDefaultClimatePreset(vin, id)
        // The STATE write, which was missing. UiState.defaultClimatePresets is populated
        // exactly once per process, inside bootstrapDriveSync -- which is guarded by an
        // AtomicBoolean and so never runs again. So this wrote to disk and nothing on screen
        // changed: the one-tap climate Start button kept using the OLD default for the rest of
        // the session, and the setting only appeared to take effect after a restart.
        _state.update {
            it.copy(
                defaultClimatePresets = if (id == null) {
                    it.defaultClimatePresets - vin
                } else {
                    it.defaultClimatePresets + (vin to id)
                },
            )
        }
    }

    // syncNow / setPrimaryDevice / pullFromPrimary / renameThisDevice / removeSyncedDevice /
    // testSync / runDriveSyncNow moved to AppViewModelSync.kt.

    /**
     * Shared wrapper for the handful of operations that should show the
     * app-wide loading spinner ([UiState.loading]) rather than a per-action
     * one: sets loading=true and clears any stale message, runs [block] inside
     * viewModelScope, and in a finally-block always clears loading=false
     * regardless of success/failure -- so a thrown exception can never leave
     * the spinner stuck on. Any exception [block] throws is caught here,
     * logged, and turned into a snackbar message instead of crashing the
     * ViewModel's coroutine scope.
     */
    internal fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            try {
                block()
            } catch (e: Exception) {
                val msg = e.message ?: "Something went wrong"
                AppLog.log("⚠ $msg")
                _state.update { it.copy(message = msg, messageType = "error") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
