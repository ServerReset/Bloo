package com.bloo.bluelink.ui

import android.app.Application
import android.location.Geocoder
import androidx.biometric.BiometricManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.BlueLinkException
import com.bloo.bluelink.data.BlueLinkRepository
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.ChargingLive
import com.bloo.bluelink.data.CarAlerts
import com.bloo.bluelink.data.formatPlaceName
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.DEFAULT_CLIMATE_DURATION_MIN
import com.bloo.bluelink.data.DEFAULT_CLIMATE_TEMP_F
import com.bloo.bluelink.data.Notifications
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.Credentials
import com.bloo.bluelink.data.CanadaAuth
import com.bloo.bluelink.data.CanadaRepository
import com.bloo.bluelink.data.KiaAuth
import com.bloo.bluelink.data.KiaRepository
import com.bloo.bluelink.data.VehicleRepository
import com.bloo.bluelink.data.links
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.maskEmail
import com.bloo.bluelink.data.ReservChargeInfos
import com.bloo.bluelink.data.TargetSOC
import com.bloo.bluelink.data.STALE_STATUS_MS
import com.bloo.bluelink.data.StatusCache
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.DEFAULT_SECTIONS
import com.bloo.bluelink.data.EvTrip
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.toClimateSync
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.targetForCurrentPlug
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.WeatherApi
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

/** How long a cached weather reading is considered fresh (15 minutes). */
private const val WEATHER_TTL_MS = 15 * 60 * 1000L

// Debounce window for the auto-push-on-change collector: a burst of edits (e.g.
// dragging pebbles, sliding a value) coalesces into one Drive write this long
// after the LAST change. Short enough to feel instant, long enough not to write
// per keystroke.
private const val AUTO_PUSH_DEBOUNCE_MS = 2000L

// How long the update tile lingers with an "Undo" strip after "Not now" before
// the dismiss commits — the call-back window.
private const val UPDATE_DISMISS_UNDO_MS = 4500L

// "Remind me": both the reminder-notification worker delay and the matching
// snooze window. Kept as one value so the two stay aligned (see snoozeUpdate).
private const val UPDATE_REMINDER_DELAY_MS = 24L * 60 * 60 * 1000L
/** Request code for the Shizuku runtime-permission prompt (seamless install). */
private const val SHIZUKU_INSTALL_REQUEST_CODE = 4711

sealed interface Screen {
    data object Login : Screen
    /** No vehicles enrolled (or still loading the first time). */
    data object Empty : Screen
    /** First-run welcome wizard that sets up car features before reaching the app. */
    data object Onboarding : Screen
    /** Feature-setup wizard for one or more newly-detected cars (post-first-run). */
    data class CarSetup(val vins: List<String>) : Screen
    /** Main screen: the car carousel/grid. */
    data object Garage : Screen
    data object Settings : Screen
}

/**
 * PERF, and it matters more than it looks: every pebble on every live car-pager
 * page takes this whole object as a parameter. Kotlin's `List`/`Map`/`Set` are
 * interfaces, so without this annotation the Compose compiler infers UiState as
 * UNSTABLE, which makes every one of those composables NON-SKIPPABLE — they
 * re-execute whenever their parent recomposes, even when the state instance is
 * bit-for-bit the same one.
 *
 * That was the car-swipe lag. GarageScreen (the pager's parent) reads several
 * per-frame animation values in composition scope (the dots fade, the pull
 * shift), so a single 200ms fade recomposed GarageScreen ~12 times, and each of
 * those recomposed all ~30 pebbles across the three pages
 * beyondViewportPageCount=1 keeps live — for a state object that never changed.
 *
 * The promise this annotation makes is real here: every property is a `val`, and
 * every collection is rebuilt by `copy()` (`_state.update { it.copy(...) }`)
 * rather than mutated in place, so `equals` is a sound recomposition signal.
 * Keep it that way — never put a MutableList/mutableStateListOf field here.
 */
@androidx.compose.runtime.Immutable
data class UiState(
    val screen: Screen = Screen.Login,
    /** Biometric app-lock overlay: the real app renders (blurred) behind it. */
    val locked: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val vehicles: List<Vehicle> = emptyList(),
    val currentIndex: Int = 0,
    /** On large screens, the index expanded to full screen (null = grid view). */
    val expandedIndex: Int? = null,
    val statuses: Map<String, VehicleStatus> = emptyMap(),
    /** Wall-clock millis the app last pulled status from the server, keyed by VIN. */
    val lastFetched: Map<String, Long> = emptyMap(),
    val locations: Map<String, GeoLocation> = emptyMap(),
    /** Recent EV trips by VIN (loaded lazily when the Trips pebble is shown). */
    val trips: Map<String, List<EvTrip>> = emptyMap(),
    /** User-named climate presets by VIN. */
    val climatePresets: Map<String, List<ClimatePreset>> = emptyMap(),
    /** Live climate draft mirrored from the watch, by VIN (two-way climate sync). */
    val climateSync: Map<String, com.bloo.bluelink.data.ClimateSync> = emptyMap(),
    val seatConfigs: Map<String, SeatConfig> = emptyMap(),
    val powertrains: Map<String, Powertrain> = emptyMap(),
    val sectionOrders: Map<String, List<String>> = emptyMap(),
    val imageUrls: Map<String, String> = emptyMap(),
    val placeNames: Map<String, String> = emptyMap(),
    /** Current weather at the user's configured "home" location, if loaded. */
    val homeWeather: Weather? = null,
    /** Current weather at each car's last-known location, keyed by VIN. */
    val carWeather: Map<String, Weather> = emptyMap(),
    val licensePlates: Map<String, String> = emptyMap(),
    val lastServiceMiles: Map<String, Int> = emptyMap(),
    val serviceIntervalMiles: Map<String, Int> = emptyMap(),
    /** In-flight commands, keyed "vin:action", so each control can show its own spinner. */
    val pending: Set<String> = emptySet(),
    /** Collapsed pebbles, keyed "vin:section". Absent = expanded. */
    val collapsedPebbles: Set<String> = emptySet(),
    /** Hidden pebbles, keyed "vin:section". */
    val hiddenPebbles: Set<String> = emptySet(),
    /** Per-VIN pebble pinned to the dual-column "hot spot" (under car info). */
    val hotspotSections: Map<String, String> = emptyMap(),
    /** Quick-tile assignments: index -> (vin, command), or null if unassigned. */
    val tileConfigs: List<Pair<String, String>?> = List(12) { null },
    /** Optional per-tile custom names (index -> label or null). */
    val tileLabels: List<String?> = List(12) { null },
    /** Per-tile climate target: "default", "smart", or a preset id. */
    val tileClimateTargets: List<String> = List(12) { "default" },
    /** Quick tiles run the command in the background (vs opening the app). */
    val tileBackground: Boolean = false,
    /** Quick tiles kick a throttled status refresh when shown. */
    val tileLiveRefresh: Boolean = false,
    /** Enabled app-icon shortcut ids ("cmd_vin"); null = show all. */
    val shortcutSet: Set<String>? = null,
    /** Whether Shizuku is installed + running, so the "seamless install" toggle is
     *  worth showing. The toggle itself lives on Appearance (seamlessInstallShizuku). */
    val shizukuAvailable: Boolean = false,
    /** On-device Gemini Nano availability + opt-in, and produced summaries. */
    val aiSupported: Boolean = false,
    val aiEnabled: Boolean = false,
    /** Run AI summaries automatically on open/refresh/command (manual still works). */
    val aiAuto: Boolean = false,
    val aiSummaries: Map<String, String> = emptyMap(),
    /** In-flight AI work: VINs being summarized, plus "search" for the query box. */
    val aiBusy: Set<String> = emptySet(),
    val aiSearchReply: String? = null,
    /** First-run coach mark on the Settings screen (points at the back arrow). */
    val showSettingsCoach: Boolean = false,
    /** Gentle hint shown on the garage right after onboarding, nudging the user
     *  toward Settings to fine-tune each car. */
    val showSettingsHint: Boolean = false,
    /** All signed-in accounts (one per brand). */
    val accounts: List<Credentials> = emptyList(),
    /** Showing the login form to add another account while already signed in. */
    val addingAccount: Boolean = false,
    /** True when the user backed out of the biometric prompt to the login screen.
     *  Cancelling in this state must re-lock rather than navigate to the garage. */
    val lockedToLogin: Boolean = false,
    /** Kia sign-in only: a pending one-time-code challenge. */
    val kiaOtp: KiaOtpUi? = null,
    /** Canada sign-in only (Hyundai/Genesis/Kia): a pending one-time-code challenge. */
    val canadaOtp: CanadaOtpUi? = null,
    val message: String? = null,
    /** "error" (default), "success", or "info" — controls snackbar colour. */
    val messageType: String = "error",
    /** A newer CI build than what's installed, if the update checker found
     *  one. Drives the standalone update tile that's pinned right below the
     *  hero tile -- null means no tile, regardless of [updateTileDismissed]. */
    val updateAvailable: com.bloo.bluelink.update.UpdateInfo? = null,
    /** "Not now" on the update tile: hides it until the NEXT update check (any
     *  Available result clears it — see checkForUpdate). "Remind me" also sets
     *  this but pairs it with a snooze + a 1-day reminder worker. */
    val updateTileDismissed: Boolean = false,
    /** True during the brief undo/call-back window after the user dismisses the
     *  update tile: the tile stays visible with an "Undo" strip; a short timer
     *  then commits [updateTileDismissed] unless the user calls it back. */
    val updatePendingDismiss: Boolean = false,
    /** True while the update APK is being downloaded in-app (see
     *  AppViewModel.downloadUpdateInBackground). */
    val updateDownloading: Boolean = false,
    /** 0-1 while [updateDownloading], or null before/after (indeterminate). */
    val updateDownloadProgress: Float? = null,
    /** True once the update APK has finished downloading and is sitting in
     *  the cache ready to hand to the installer -- lets the update tile's
     *  button do "tap to download, tap again to install". Reset by a fresh
     *  update check finding a different/no build available. */
    val updateApkReady: Boolean = false,
    /** True while a Shizuku seamless install is running, so a second Install tap (or a
     *  permission-grant retry) can't spawn a concurrent PackageInstaller session. */
    val updateInstalling: Boolean = false,
    /** True while a manual "Check for updates" is in flight (drives the Settings
     *  button's spinner + disables it). Auto/background checks don't set this. */
    val updateChecking: Boolean = false,
    /** Settings mode: "simple" (essential settings) or "advanced" (all settings). */
    val settingsMode: String = "simple",
    /** Per-VIN default preset ID for the one-tap climate Start button. */
    val defaultClimatePresets: Map<String, String> = emptyMap(),
    /** Drive URI (content://...) for auto-backup; null when not configured. */
    val syncUri: String? = null,
    /** Last time settings were synced with Drive (ms), for merge decisions. */
    val lastSyncMs: Long = 0L,
    /** Wi-Fi only sync (true) or any network (false). */
    val syncWifiOnly: Boolean = true,
    /** Reason the last Drive sync attempt didn't fully succeed, or null if it did
     *  (or hasn't run yet). Cleared by the next attempt that succeeds. */
    val syncError: String? = null,
    /** Devices sharing this sync file (name/model/last-seen), for the Settings
     *  "your devices" list. From the last sync's merged registry / cache. */
    val syncDevices: List<com.bloo.bluelink.data.SyncMerge.SyncDevice> = emptyList(),
    /** The device id designated primary (source of truth), or null if none. */
    val syncPrimaryId: String? = null,
    /** This device's own sync id, so the UI can mark "This device" and hide
     *  "Make primary" on self. */
    val thisDeviceId: String? = null,
    /** This device's friendly sync name (editable in Settings). */
    val syncDeviceName: String = "",
    /** Short fingerprint of the actual Drive file this device syncs to. Two
     *  devices showing DIFFERENT fingerprints are on different files (the main
     *  reason sync doesn't converge). Null when sync isn't set up. */
    val syncFileFingerprint: String? = null,
    /** Set when the garage fetch came back empty because a request actually
     *  failed (network/API error), not because the account genuinely has zero
     *  vehicles. Distinguishes a real failure from "not signed in" / "no
     *  vehicles" in [Screen.Empty]. Cleared by the next successful load. */
    val garageLoadError: String? = null,
) {
    fun statusFor(v: Vehicle): VehicleStatus? = statuses[v.vin]

    fun fetchedAt(v: Vehicle): Long? = lastFetched[v.vin]

    fun isPending(vin: String, action: String): Boolean = "$vin:$action" in pending

    fun isPebbleExpanded(vin: String, section: String): Boolean = "$vin:$section" !in collapsedPebbles

    fun isPebbleHidden(vin: String, section: String): Boolean = "$vin:$section" in hiddenPebbles

    fun hotspotFor(vin: String): String? = hotspotSections[vin]

    fun isShortcutEnabled(vin: String, cmd: String): Boolean =
        shortcutSet?.contains("${cmd}_$vin") ?: true

    fun seatConfigFor(v: Vehicle): SeatConfig = seatConfigs[v.vin] ?: SeatConfig()

    fun sectionsFor(v: Vehicle): List<String> = sectionOrders[v.vin] ?: DEFAULT_SECTIONS

    /** Effective powertrain: user override, else EV/gas inferred from the API. */
    fun powertrainOf(v: Vehicle): Powertrain =
        powertrains[v.vin] ?: if (v.isEv) Powertrain.EV else Powertrain.GAS

    /** Has a high-voltage battery you can charge (EV or plug-in hybrid). */
    fun hasBattery(v: Vehicle): Boolean = powertrainOf(v) == Powertrain.EV || powertrainOf(v) == Powertrain.PHEV

    /** Burns fuel (everything except a pure EV). */
    fun hasFuel(v: Vehicle): Boolean = powertrainOf(v) != Powertrain.EV

    /**
     * Whether the car is moving/on, for the header. Speed (from a location fix)
     * is the strongest signal; otherwise fall back to the ignition state. Null
     * when we genuinely can't tell (e.g. an EV that's never been located).
     */
    fun drivingLabel(v: Vehicle): String? {
        val status = statusFor(v)
        // If it's charging it's definitely parked — don't show a driving badge.
        if (status?.evStatus?.batteryCharge == true) return null
        val speed = locations[v.vin]?.speed
        val engine = status?.engine
        return when {
            speed != null && speed > 0 -> "Driving"
            engine == true -> "Running"
            engine == false -> "Parked"
            else -> null
        }
    }

    /** True when the car is moving — used to make climate read-only (the car
     *  rejects remote climate commands while driving). */
    fun isDriving(v: Vehicle): Boolean {
        val speed = locations[v.vin]?.speed ?: statusFor(v)?.vehicleLocation?.speed?.value
        return speed != null && speed > 0
    }

    /** Powertrain label for the header. */
    fun powertrainLabel(v: Vehicle): String = when (powertrainOf(v)) {
        Powertrain.GAS -> "Gas"
        Powertrain.HYBRID -> "Hybrid"
        Powertrain.PHEV -> "PHEV"
        Powertrain.EV -> "EV"
    }
}

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

/** Minimum time a command control stays locked after firing, to block double-taps. */
private const val MIN_COMMAND_LOCK_MS = 3000L

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SessionStore(app)
    private val settingsStore = SettingsStore(app)
    private val credentialStore = CredentialStore(app)
    private val snapshotStore = SnapshotStore(app)
    private val statusCache = StatusCache(app)
    private val ai = com.bloo.bluelink.data.Ai(app)
    // One repository per signed-in brand (any mix of brands can be active).
    private val repos = mutableMapOf<Brand, VehicleRepository>()

    private fun repoFor(brand: Brand): VehicleRepository =
        repos.getOrPut(brand) { com.bloo.bluelink.data.repositoryFor(brand, store, credentialStore) }

    private fun kiaRepo(): KiaRepository = repoFor(Brand.KIA) as KiaRepository

    private fun canadaRepo(brand: Brand): CanadaRepository = repoFor(brand) as CanadaRepository

    private fun brandOf(v: Vehicle): Brand =
        Brand.fromIndicator(v.brandIndicator)

    private fun repoFor(v: Vehicle): VehicleRepository = repoFor(brandOf(v))

    @Volatile
    private var loadingGarage = false

    /** A pending app-icon shortcut (vin to command) awaiting the garage to load. */
    @Volatile
    private var pendingShortcut: Pair<String, String>? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Serializes ALL vehicleStatus calls account-wide (shared with the background
     * worker via [com.bloo.bluelink.data.BlueLinkGate]). Blue Link rejects
     * overlapping requests with `502 ... a previous request is pending`.
     */
    private val statusMutex = com.bloo.bluelink.data.BlueLinkGate.statusMutex

    /** Status requests currently queued or running, keyed "vin:refresh"
     *  (de-dupes; a live refresh=true isn't dropped behind a background
     *  refresh=false fetch for the same car). */
    private val statusInFlight = mutableSetOf<String>()

    /** Subset of [statusInFlight] whose call used surfaceErrors=true (drives the spinner + settle haptic). */
    private val surfaceInFlight = mutableSetOf<String>()

    /** VINs fetched from the network this session (cache restore doesn't count). */
    private val sessionFetched = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    /** Guards [bootstrapDriveSync] so it starts its collector exactly once per
     *  ViewModel, no matter how many times the garage (re)loads. */
    private val driveSyncBootstrapped = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Copy-pasteable activity log shown in Settings. */
    val logs: StateFlow<List<String>> = AppLog.lines

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
    private suspend fun checkAlerts(v: Vehicle, status: VehicleStatus) {
        val alerts = CarAlerts.evaluate(settingsStore, v, status)
        alerts.forEach { Notifications.post(getApplication(), it.id, it.title, it.text, it.actions) }
        alerts.firstOrNull()?.let { a -> _state.update { it.copy(message = a.text, messageType = "error") } }
    }

    // Simple notification-preference setters: each just fires a coroutine that
    // writes one field to SettingsStore's DataStore. They don't touch _state
    // directly because `notifications` above is already a StateFlow mirroring
    // settingsStore.notifications, so the UI picks up the change automatically
    // once the write completes and the underlying Flow re-emits.
    fun setNotifyService(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyService(v) }
    fun setNotifyDoor(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyDoor(v) }
    fun setDoorOpenMinutes(m: Int) = viewModelScope.launch { settingsStore.setDoorOpenMinutes(m) }
    fun setNotifyRunning(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyRunning(v) }
    fun setRunningMinutes(m: Int) = viewModelScope.launch { settingsStore.setRunningMinutes(m) }
    fun setNotifyUnlocked(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyUnlocked(v) }

    /** Turning the live charging bar off clears any already-posted one at
     *  once, rather than leaving it pinned in the shade until the next poll
     *  happens to notice the setting changed. */
    fun setNotifyCharging(v: Boolean) = viewModelScope.launch {
        settingsStore.setNotifyCharging(v)
        if (!v) {
            ChargingLive.cancelAll(getApplication(), _state.value.vehicles.map { it.vin })
            // Kill the 5-minute poll chain too, or it keeps waking up just to
            // discover the feature is off and do nothing.
            com.bloo.bluelink.work.ChargingPollWorker.cancel(getApplication())
        }
    }
    fun setUnlockedMinutes(m: Int) = viewModelScope.launch { settingsStore.setUnlockedMinutes(m) }

    /** Write the current live status/location maps to disk (survives restart). */
    private fun persistCache() {
        val s = _state.value
        viewModelScope.launch {
            statusCache.save(s.statuses, s.locations, s.placeNames, s.lastFetched)
        }
    }

    init {
        // Everything below launches independent coroutines in viewModelScope
        // (so they're all cancelled together when the ViewModel is cleared) and
        // most of them are long-lived `collect` loops on a Flow — they never
        // complete, they just re-run their body every time the upstream Flow
        // emits a new value, for the lifetime of the ViewModel. Each one is
        // one-directional (phone state -> watch), independent of the others,
        // and safe to fire in any order since they only ever read from
        // settingsStore/_state and write out to the watch bridge.
        // Mirror appearance/preferences to a paired watch whenever they change,
        // so the watch theme + settings always match the phone live.
        viewModelScope.launch {
            settingsStore.appearance.collect { a ->
                com.bloo.bluelink.wear.WearBridge.publishSettings(getApplication(), a)
            }
        }
        // Re-publish settings (which carry each car's pebble order) whenever the
        // user reorders pebbles, so the watch's tile layout follows the phone.
        viewModelScope.launch {
            _state.map { it.sectionOrders }.distinctUntilChanged().collect {
                com.bloo.bluelink.wear.WearBridge.publishSettings(
                    getApplication(), settingsStore.appearance.first(),
                )
            }
        }
        // Mirror saved climate presets to the watch whenever they change.
        viewModelScope.launch {
            _state.map { it.climatePresets }.distinctUntilChanged().collect { presets ->
                com.bloo.bluelink.wear.WearBridge.publishPresets(getApplication(), presets)
            }
        }
        // Mirror the watch's live climate draft (sliders, active preset) into state.
        viewModelScope.launch {
            com.bloo.bluelink.data.ClimateSyncStore(getApplication()).flow
                .collect { s -> _state.update { it.copy(climateSync = s.byVin) } }
        }
        // Mirror weather / car photos / AI summaries to the watch.
        viewModelScope.launch {
            _state.map { s ->
                com.bloo.bluelink.data.WearExtras(
                    homeWeather = s.homeWeather?.toWear(),
                    carWeather = s.carWeather.mapValues { it.value.toWear() },
                    images = s.imageUrls,
                    ai = s.aiSummaries,
                )
            }.distinctUntilChanged().collect { extras ->
                com.bloo.bluelink.wear.WearBridge.publishExtras(getApplication(), extras)
            }
        }
        // Probe on-device Gemini Nano once; the AI toggle only appears if present.
        viewModelScope.launch {
            val supported = ai.isSupported()
            if (supported) {
                _state.update {
                    it.copy(
                        aiSupported = true,
                        aiEnabled = settingsStore.aiEnabled(),
                        aiAuto = settingsStore.aiAuto(),
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
        // Restore the last-known status/location from disk so the UI shows
        // stale-but-useful data immediately, before any network call returns.
        viewModelScope.launch {
            val cached = statusCache.load()
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
            if (brands.isEmpty()) return@launch
            // repoFor(it) lazily creates+caches one VehicleRepository per brand
            // in the `repos` map (see repoFor above) so later calls just reuse it.
            brands.forEach { repoFor(it) }
            _state.update { it.copy(accounts = credentialStore.loadAll()) }
            val locked = settingsStore.appearance.first().biometricLock && canUseBiometrics()
            // Load the garage either way so it's ready (and visible, blurred)
            // behind the lock overlay; the overlay just gates interaction.
            if (locked) _state.update { it.copy(locked = true) }
            loadGarage()
        }
    }

    // --- Auth ------------------------------------------------------------

    /**
     * Sign in (or add another account). Multiple brands can be active at once.
     * Validates the fields locally first (PIN is skipped for brands that use a
     * one-time-code login instead of a PIN), then branches: Kia always goes
     * through [loginKia]'s two-step OTP dance, everything else does a normal
     * synchronous BlueLinkRepository login wrapped in [launchBusy] (so the
     * "loading" spinner shows and any thrown exception becomes a snackbar).
     * On success the credentials are persisted (so next cold start auto-logs-in)
     * and the garage is (re)loaded to pull in the newly-added account's cars.
     */
    fun login(username: String, password: String, pin: String, brand: Brand) {
        if (username.isBlank() || password.isBlank() || (pin.isBlank() && brand.requiresPin)) {
            _state.update { it.copy(message = "Email, password and PIN are all required", messageType = "error") }
            return
        }
        if (brand == Brand.KIA) {
            loginKia(username.trim(), password, pin.trim())
            return
        }
        if (brand.isCanada) {
            loginCanada(username.trim(), password, pin.trim(), brand)
            return
        }
        launchBusy {
            (repoFor(brand) as BlueLinkRepository).login(username.trim(), password, pin.trim())
            credentialStore.save(Credentials(username.trim(), password, pin.trim(), brand))
            AppLog.log("Signed in as ${maskEmail(username.trim())} (${brand.label})")
            _state.update { it.copy(accounts = credentialStore.loadAll(), addingAccount = false) }
            // Push the session to the watch IMMEDIATELY on login success (not only
            // via the eventual post-garage-fetch publish) so a watch waiting on a
            // "Set up on phone" handoff advances past its login screen right away.
            runCatching { com.bloo.bluelink.wear.WearBridge.publishAuth(getApplication()) }
            loadGarageInternal()
        }
    }

    // Kia sign-in is a two-step dance: password first, then (usually) a
    // one-time code sent to the account's email or phone. The credentials are
    // held here between the steps and only persisted once fully signed in.
    private var kiaPending: Credentials? = null

    /**
     * Step 1 of Kia login: attempt sign-in with just username/password/PIN. Kia's
     * API either logs straight in ([KiaAuth.LoggedIn]) or demands a one-time code
     * ([KiaAuth.OtpRequired]) — which branch happens depends on the account and
     * isn't knowable ahead of time. On the OTP branch the credentials are stashed
     * in [kiaPending] (NOT yet persisted to [credentialStore]) and the UI is told
     * to show the OTP challenge; [kiaSendOtp]/[kiaVerifyOtp] complete the flow.
     */
    private fun loginKia(username: String, password: String, pin: String) {
        launchBusy {
            when (val auth = kiaRepo().startLogin(username, password, pin)) {
                is KiaAuth.LoggedIn -> {
                    kiaPending = null
                    finishKiaLogin(Credentials(username, password, pin, Brand.KIA))
                }
                is KiaAuth.OtpRequired -> {
                    kiaPending = Credentials(username, password, pin, Brand.KIA)
                    AppLog.log("Kia requires a one-time code (email: ${auth.hasEmail}, sms: ${auth.hasSms})")
                    _state.update { it.copy(kiaOtp = KiaOtpUi(auth)) }
                }
            }
        }
    }

    /** Send the Kia one-time code to the chosen destination ("EMAIL"/"SMS"). */
    fun kiaSendOtp(notifyType: String) {
        val otp = _state.value.kiaOtp ?: return
        launchBusy {
            kiaRepo().sendOtp(otp.challenge, notifyType)
            AppLog.log("Kia one-time code sent via $notifyType")
            _state.update { it.copy(kiaOtp = otp.copy(sentTo = notifyType)) }
        }
    }

    /** Verify the Kia one-time code and finish signing in. */
    fun kiaVerifyOtp(code: String) {
        val otp = _state.value.kiaOtp ?: return
        val creds = kiaPending ?: return
        if (code.isBlank()) {
            _state.update { it.copy(message = "Enter the code you received", messageType = "error") }
            return
        }
        launchBusy {
            kiaRepo().verifyOtp(creds.email, creds.password, creds.pin, code.trim(), otp.challenge)
            kiaPending = null
            _state.update { it.copy(kiaOtp = null) }
            finishKiaLogin(creds)
        }
    }

    /** Back out of the Kia OTP challenge screen: drops the stashed
     *  credentials (they were never persisted) and clears the challenge UI. */
    fun kiaCancelOtp() {
        kiaPending = null
        _state.update { it.copy(kiaOtp = null) }
    }

    /** Finish a fully-authenticated Kia login (reached from either the direct
     *  [KiaAuth.LoggedIn] branch or after [kiaVerifyOtp] succeeds): persist the
     *  credentials now that they're verified, refresh the account list, close
     *  the login form, and load the garage. */
    private suspend fun finishKiaLogin(creds: Credentials) {
        credentialStore.save(creds)
        AppLog.log("Signed in as ${maskEmail(creds.email)} (Kia)")
        _state.update { it.copy(accounts = credentialStore.loadAll(), addingAccount = false) }
        // Push the Kia session to the watch immediately (Kia can't sign in on-watch,
        // so the watch relies entirely on this push) — same reasoning as login().
        runCatching { com.bloo.bluelink.wear.WearBridge.publishAuth(getApplication()) }
        loadGarageInternal()
    }

    // Canada sign-in (Hyundai/Genesis/Kia) is also a two-step dance, but unlike
    // Kia US there's no destination choice (email only) and the account's PIN
    // IS required (every command needs it, see CanadaApi.pinAuth) -- so the PIN
    // typed into the login form travels straight through the OTP challenge.
    private var canadaPending: Credentials? = null

    /**
     * Step 1 of Canada login: attempt sign-in with username/password. Returns
     * straight in ([CanadaAuth.LoggedIn]) if this device is still within a
     * prior login's 90-day remembered-device grant, otherwise an email code
     * challenge ([CanadaAuth.OtpRequired]) -- which is sent immediately (no
     * destination to pick, unlike Kia), so the UI goes straight to a
     * code-entry dialog; [canadaVerifyOtp] completes the flow.
     */
    private fun loginCanada(username: String, password: String, pin: String, brand: Brand) {
        launchBusy {
            when (val auth = canadaRepo(brand).startLogin(username, password, pin)) {
                is CanadaAuth.LoggedIn -> {
                    canadaPending = null
                    finishCanadaLogin(Credentials(username, password, pin, brand))
                }
                is CanadaAuth.OtpRequired -> {
                    canadaPending = Credentials(username, password, pin, brand)
                    canadaRepo(brand).sendOtp(auth)
                    AppLog.log("${brand.label} requires a one-time code (email)")
                    _state.update { it.copy(canadaOtp = CanadaOtpUi(auth, brand)) }
                }
            }
        }
    }

    /** Verify the Canada one-time code and finish signing in. */
    fun canadaVerifyOtp(code: String) {
        val otp = _state.value.canadaOtp ?: return
        val creds = canadaPending ?: return
        if (code.isBlank()) {
            _state.update { it.copy(message = "Enter the code you received", messageType = "error") }
            return
        }
        launchBusy {
            canadaRepo(otp.brand).verifyOtp(creds.email, creds.pin, code.trim(), otp.challenge)
            canadaPending = null
            _state.update { it.copy(canadaOtp = null) }
            finishCanadaLogin(creds)
        }
    }

    /** Back out of the Canada OTP challenge screen: drops the stashed
     *  credentials (they were never persisted) and clears the challenge UI. */
    fun canadaCancelOtp() {
        canadaPending = null
        _state.update { it.copy(canadaOtp = null) }
    }

    /** Finish a fully-authenticated Canada login (reached from either the
     *  direct [CanadaAuth.LoggedIn] branch or after [canadaVerifyOtp]
     *  succeeds) -- same shape as [finishKiaLogin]. */
    private suspend fun finishCanadaLogin(creds: Credentials) {
        credentialStore.save(creds)
        AppLog.log("Signed in as ${maskEmail(creds.email)} (${creds.brand.label})")
        _state.update { it.copy(accounts = credentialStore.loadAll(), addingAccount = false) }
        runCatching { com.bloo.bluelink.wear.WearBridge.publishAuth(getApplication()) }
        loadGarageInternal()
    }

    /** Show the login form again on top of an already-loaded garage, so the
     *  user can sign into a second (or third) brand without losing the first. */
    fun beginAddAccount() = _state.update { it.copy(addingAccount = true) }
    fun cancelAddAccount() = _state.update { s ->
        // If the user arrived here by backing out of the biometric prompt, "Cancel"
        // must re-lock the app — not silently return them to the already-loaded garage.
        if (s.lockedToLogin) s.copy(addingAccount = false, locked = true, lockedToLogin = false)
        else s.copy(addingAccount = false)
    }

    /**
     * Sign out of one brand. The server-side logout call is best-effort
     * (`runCatching` — a failed logout call shouldn't block clearing local
     * state), but clearing the cached credentials and dropping the brand's
     * cached [VehicleRepository] from [repos] always happens so the app
     * forgets that brand for good. If that was the LAST signed-in account, the
     * whole [UiState] is replaced with a fresh one pointed at the login screen
     * (wiping any stale per-VIN data for the signed-out cars); otherwise the
     * garage is reloaded so it no longer shows that brand's vehicles.
     */
    fun logout(brand: Brand) {
        viewModelScope.launch {
            runCatching { repoFor(brand).logout() }
            credentialStore.clear(brand)
            repos.remove(brand)
            AppLog.log("Signed out of ${brand.label}")
            val remaining = credentialStore.loadAll()
            if (remaining.isEmpty()) {
                // Wipe account-derived telemetry from disk on full sign-out: the
                // last-known car GPS/lock/charge state and reverse-geocoded place
                // names persist as plaintext JSON and would otherwise re-load into
                // the UI (and keep the widget/tiles rendering the last location) on
                // the next cold start. Session tokens + credentials are already
                // cleared above; this closes the derived-location leak. Mirrors the
                // watch's own signOutAll (snapshotStore.saveVehicles(emptyList())).
                runCatching { statusCache.clear() }
                runCatching { snapshotStore.saveVehicles(emptyList()) }
                runCatching { com.bloo.bluelink.tiles.BlooTileService.requestUpdates(getApplication()) }
                // Preserve device-capability probe results across the full state
                // reset -- they're device capabilities, not account state, and were
                // only probed once in init. Dropping them hid the AI/Shizuku UI on a
                // capable device after signing back in within the same session.
                _state.value = UiState(
                    screen = Screen.Login,
                    aiSupported = _state.value.aiSupported,
                    aiEnabled = _state.value.aiEnabled,
                    aiAuto = _state.value.aiAuto,
                    shizukuAvailable = _state.value.shizukuAvailable,
                )
            } else {
                _state.update { it.copy(accounts = remaining) }
                loadGarage()
            }
            // Push the (now reduced or empty) auth bundle to the watch on BOTH
            // branches so a sign-out actually reaches it and wipes the revoked
            // session -- WearStateWriter.persistAuth is authoritative to the
            // bundle, clearing any brand not present in it.
            com.bloo.bluelink.wear.WearBridge.publishAuth(getApplication())
        }
    }

    /** Fix a wrong/locked service PIN without re-entering the whole account. */
    fun updatePin(brand: Brand, pin: String) {
        if (pin.isBlank()) return
        viewModelScope.launch {
            store.updatePin(brand, pin.trim())
            credentialStore.updatePin(brand, pin.trim())
            _state.update { it.copy(accounts = credentialStore.loadAll(), message = "PIN updated for ${brand.label}", messageType = "success") }
            AppLog.log("Updated PIN for ${brand.label}")
        }
    }

    /** Dismiss the lock overlay (the garage was already loaded behind it). */
    fun unlocked() {
        _state.update { it.copy(locked = false, lockedToLogin = false) }
        if (_state.value.vehicles.isEmpty() && !loadingGarage) loadGarage()
    }

    /** From the lock overlay, back out to the login screen.
     *  Sets lockedToLogin so Cancel on the login form re-locks instead of bypassing auth. */
    fun lockToLogin() = _state.update { it.copy(locked = false, addingAccount = true, lockedToLogin = true) }

    /** Persist how long after backgrounding the app should re-lock (see
     *  [maybeRelock], which reads this back out of [SettingsStore] on the next
     *  foreground). Fire-and-forget: the write is async, nothing in [_state]
     *  reflects the new value directly since the lock-timing setting itself
     *  isn't rendered anywhere that needs it synchronously. */
    fun setLockTiming(value: LockTiming) {
        viewModelScope.launch { settingsStore.setLockTiming(value) }
    }

    /**
     * Re-engage the lock when returning to the foreground, honouring the user's
     * [LockTiming] setting. [backgroundedAtMs] is when the app was last stopped and
     * [screenOff] whether the screen turned off while it was away.
     */
    fun maybeRelock(backgroundedAtMs: Long, screenOff: Boolean) {
        if (_state.value.locked) return
        viewModelScope.launch {
            val a = settingsStore.appearance.first()
            if (!a.biometricLock || !canUseBiometrics()) return@launch
            val elapsed = System.currentTimeMillis() - backgroundedAtMs
            val shouldLock = when (a.lockTiming) {
                LockTiming.OFF -> false
                LockTiming.IMMEDIATE -> true
                LockTiming.AFTER_1_MIN -> elapsed >= 60_000
                LockTiming.AFTER_5_MIN -> elapsed >= 300_000
                LockTiming.AFTER_10_MIN -> elapsed >= 600_000
            }
            if (shouldLock) _state.update { it.copy(locked = true) }
        }
        // Prompt to refresh if data is stale after returning from background.
        if (backgroundedAtMs > 0 && System.currentTimeMillis() - backgroundedAtMs > STALE_STATUS_MS) {
            val anyStale = _state.value.lastFetched.values.any { System.currentTimeMillis() - it > STALE_STATUS_MS }
            if (anyStale) reportInfo("Data may be stale, pull down to refresh")
        }
    }

    /** Whether the device currently has usable biometrics (fingerprint/face)
     *  enrolled -- gates whether [UiState.locked] / [maybeRelock] can ever
     *  apply, since there's nothing to authenticate against otherwise.
     *  BIOMETRIC_WEAK is used (rather than STRONG) so a wider range of
     *  device authenticators (including some face-only ones) still qualify. */
    fun canUseBiometrics(): Boolean =
        BiometricManager.from(getApplication())
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    /** Turn the biometric app-lock on/off in Settings. Persists only -- the
     *  actual locking/unlocking flow is driven separately by [maybeRelock]
     *  and [unlocked] reading this flag back out on each foreground. */
    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setBiometricLock(enabled) }
    }

    // --- Garage / vehicles ----------------------------------------------

    /** Public entry point for a full garage (re)load, wrapped in [launchBusy]
     *  so [UiState.loading] shows and any thrown exception becomes a snackbar. */
    fun loadGarage() = launchBusy { loadGarageInternal() }

    /** Re-entrancy guard around [loadGarageInner]: the [loadingGarage] flag
     *  (checked/set here, not inside [loadGarageInner] itself so every caller
     *  goes through this one gate) makes sure only one garage load runs at a
     *  time -- e.g. a login finishing and a manual pull-to-refresh landing at
     *  the same moment shouldn't run two overlapping fetches of every brand's
     *  vehicle list. `@Volatile` because this can be read/written from
     *  different coroutines dispatched onto different threads. */
    private suspend fun loadGarageInternal() {
        if (loadingGarage) return
        loadingGarage = true
        try {
            loadGarageInner()
        } finally {
            loadingGarage = false
        }
    }

    private suspend fun loadGarageInner() {
        // Merge vehicles from every signed-in brand; one brand failing shouldn't
        // hide the others. Track failures separately from "this account
        // genuinely has zero vehicles" -- collapsing both into the same empty
        // list used to make a network/API failure display as "Not signed in"
        // or "No vehicles found", which looked like the app had silently
        // signed the user out rather than telling them what actually happened.
        var lastError: String? = null
        val fetched = repos.values.flatMap { r ->
            runCatching { statusMutex.withLock { r.vehicles() } }.getOrElse { e ->
                val msg = e.message ?: "Couldn't load vehicles"
                AppLog.log("⚠ $msg")
                lastError = msg
                emptyList()
            }
        }
        if (fetched.isEmpty()) {
            // Still bootstrap Drive sync on an empty/failed cold start so the
            // restore + persisted-grant check + auto-sync collector run once this
            // session -- bootstrapDriveSync is idempotent (AtomicBoolean guard),
            // so the non-empty path below calling it again is a no-op.
            bootstrapDriveSync()
            _state.update { it.copy(vehicles = emptyList(), screen = Screen.Empty, garageLoadError = lastError) }
            return
        }
        val vehicles = applyOrder(fetched, settingsStore.vehicleOrder())
        snapshotStore.saveVehicles(vehicles.map { snapshotOf(it, null) })
        val seatConfigs = vehicles.associate { it.vin to settingsStore.seatConfig(it.vin) }
        val powertrains = vehicles.mapNotNull { v -> settingsStore.powertrain(v.vin)?.let { v.vin to it } }.toMap()
        val sectionOrders = vehicles.associate { it.vin to settingsStore.sectionOrder(it.vin) }
        val images = vehicles.mapNotNull { v -> settingsStore.imageUrl(v.vin)?.let { v.vin to it } }.toMap()
        val plates = vehicles.associate { it.vin to settingsStore.licensePlate(it.vin) }.filterValues { it.isNotBlank() }
        val lastSvc = vehicles.mapNotNull { v -> settingsStore.lastServiceMiles(v.vin)?.let { v.vin to it } }.toMap()
        val svcInterval = vehicles.mapNotNull { v -> settingsStore.serviceIntervalMiles(v.vin)?.let { v.vin to it } }.toMap()
        val climatePresets = vehicles.associate { it.vin to settingsStore.climatePresets(it.vin) }
        val firstRun = !settingsStore.onboardingSeen()
        val unconfiguredVins = vehicles.filter { !settingsStore.isCarConfigured(it.vin) }.map { it.vin }
        // On first open all pebbles start expanded regardless of any stored state.
        val collapsed = if (firstRun) emptySet()
        else vehicles.flatMap { v -> settingsStore.collapsedSections(v.vin).map { "${v.vin}:$it" } }.toSet()
        val hidden = vehicles.flatMap { v -> settingsStore.hiddenSections(v.vin).map { "${v.vin}:$it" } }.toSet()
        val hotspots = vehicles.mapNotNull { v -> settingsStore.hotspot(v.vin)?.let { v.vin to it } }.toMap()
        val tileConfigs = (0 until com.bloo.bluelink.data.TILE_COUNT).map { settingsStore.tileConfig(it) }
        val tileLabels = (0 until com.bloo.bluelink.data.TILE_COUNT).map { settingsStore.tileLabel(it) }
        val tileClimateTargets = (0 until com.bloo.bluelink.data.TILE_COUNT).map { settingsStore.tileClimateTarget(it) }
        val tileBackground = settingsStore.tileBackground()
        val tileLiveRefresh = settingsStore.tileLiveRefresh()
        val shortcutSet = settingsStore.enabledShortcuts()
        val lastVin = settingsStore.lastVehicleVin()
        val index = vehicles.indexOfFirst { it.vin == lastVin }.let { if (it < 0) 0 else it }
        val screen = when {
            firstRun -> Screen.Onboarding
            unconfiguredVins.isNotEmpty() -> Screen.CarSetup(unconfiguredVins)
            else -> Screen.Garage
        }
        _state.update {
            it.copy(
                vehicles = vehicles,
                seatConfigs = seatConfigs,
                powertrains = powertrains,
                sectionOrders = sectionOrders,
                imageUrls = images,
                licensePlates = plates,
                lastServiceMiles = lastSvc,
                serviceIntervalMiles = svcInterval,
                climatePresets = climatePresets,
                collapsedPebbles = collapsed,
                hiddenPebbles = hidden,
                hotspotSections = hotspots,
                tileConfigs = tileConfigs,
                tileLabels = tileLabels,
                tileClimateTargets = tileClimateTargets,
                tileBackground = tileBackground,
                tileLiveRefresh = tileLiveRefresh,
                shortcutSet = shortcutSet,
                currentIndex = index,
                screen = screen,
                garageLoadError = null,
            )
        }
        // One-time: now that vehicles (and their default climate presets) are
        // known, start the Drive auto-sync bootstrap + collector.
        bootstrapDriveSync()
        // Keep the app-icon long-press shortcuts in sync with the current cars.
        com.bloo.bluelink.Shortcuts.refresh(getApplication(), vehicles, shortcutSet)
        // Run any shortcut that was tapped before the garage finished loading.
        tryRunPendingShortcut()
        // Fetch current car first for an immediate view, then prefetch the rest
        // (REFRESH=false -> cached last-known status, doesn't burn remote quota).
        ensureStatus(vehicles[index])
        viewModelScope.launch {
            vehicles.forEachIndexed { i, v -> if (i != index) ensureStatus(v) }
        }
    }

    /**
     * Re-reads this device's local per-car config (seat capability, powertrain,
     * photo, license plate, service intervals, pebble order) for the currently
     * loaded vehicles and folds it straight into state -- the same local reads
     * [loadGarageInner] already does once at startup, no network call. A
     * restored/imported settings backup only ever writes to [settingsStore]
     * directly; without this, the already-composed UI (onboarding mid-flow, a
     * Settings screen already open) kept showing whatever it loaded before the
     * import, until some unrelated event happened to trigger a full reload.
     */
    private suspend fun refreshLocalCarConfig() {
        val vehicles = _state.value.vehicles
        if (vehicles.isEmpty()) return
        val seatConfigs = vehicles.associate { it.vin to settingsStore.seatConfig(it.vin) }
        val powertrains = vehicles.mapNotNull { v -> settingsStore.powertrain(v.vin)?.let { v.vin to it } }.toMap()
        val sectionOrders = vehicles.associate { it.vin to settingsStore.sectionOrder(it.vin) }
        val images = vehicles.mapNotNull { v -> settingsStore.imageUrl(v.vin)?.let { v.vin to it } }.toMap()
        val plates = vehicles.associate { it.vin to settingsStore.licensePlate(it.vin) }.filterValues { it.isNotBlank() }
        val lastSvc = vehicles.mapNotNull { v -> settingsStore.lastServiceMiles(v.vin)?.let { v.vin to it } }.toMap()
        val svcInterval = vehicles.mapNotNull { v -> settingsStore.serviceIntervalMiles(v.vin)?.let { v.vin to it } }.toMap()
        val climatePresets = vehicles.associate { it.vin to settingsStore.climatePresets(it.vin) }
        // These fields used to be omitted here (only loadGarageInner read them),
        // so an imported change to pebble visibility, collapse state, hotspots,
        // Quick-tile config, or the shortcut set wrote DataStore but never reached
        // the running UiState -- the garage kept rendering the pre-sync layout
        // until a full cold-start reload. That was the "hid a pebble / moved a
        // Quick-tile, synced, nothing changed" bug. Read them here too, mirroring
        // loadGarageInner exactly (incl. its firstRun empty-collapsed rule).
        val firstRun = !settingsStore.onboardingSeen()
        val collapsed = if (firstRun) emptySet()
        else vehicles.flatMap { v -> settingsStore.collapsedSections(v.vin).map { "${v.vin}:$it" } }.toSet()
        val hidden = vehicles.flatMap { v -> settingsStore.hiddenSections(v.vin).map { "${v.vin}:$it" } }.toSet()
        val hotspots = vehicles.mapNotNull { v -> settingsStore.hotspot(v.vin)?.let { v.vin to it } }.toMap()
        val tileConfigs = (0 until com.bloo.bluelink.data.TILE_COUNT).map { settingsStore.tileConfig(it) }
        val tileLabels = (0 until com.bloo.bluelink.data.TILE_COUNT).map { settingsStore.tileLabel(it) }
        val tileClimateTargets = (0 until com.bloo.bluelink.data.TILE_COUNT).map { settingsStore.tileClimateTarget(it) }
        val tileBackground = settingsStore.tileBackground()
        val tileLiveRefresh = settingsStore.tileLiveRefresh()
        val shortcutSet = settingsStore.enabledShortcuts()
        _state.update {
            it.copy(
                seatConfigs = seatConfigs,
                powertrains = powertrains,
                sectionOrders = sectionOrders,
                imageUrls = images,
                licensePlates = plates,
                lastServiceMiles = lastSvc,
                serviceIntervalMiles = svcInterval,
                climatePresets = climatePresets,
                collapsedPebbles = collapsed,
                hiddenPebbles = hidden,
                hotspotSections = hotspots,
                tileConfigs = tileConfigs,
                tileLabels = tileLabels,
                tileClimateTargets = tileClimateTargets,
                tileBackground = tileBackground,
                tileLiveRefresh = tileLiveRefresh,
                shortcutSet = shortcutSet,
            )
        }
        // Quick-tile / shortcut changes must also re-push the launcher shortcuts,
        // exactly as loadGarageInner does, so an imported shortcut-set change is
        // reflected in the app-icon long-press menu and not just in-app.
        com.bloo.bluelink.Shortcuts.refresh(getApplication(), vehicles, shortcutSet)
    }

    /**
     * One-time Drive-sync bootstrap: restore the saved sync URI / settings-mode /
     * last-sync-time / per-car default climate presets, then start the
     * bidirectional auto-sync collector (download-then-upload whenever a refresh
     * settles). Guarded by [driveSyncBootstrapped] so calling this more than once
     * (the garage can reload after a re-login) never starts a second collector.
     *
     * This used to be spliced into the middle of [loadStatus] — which runs on
     * every single vehicle status fetch — so every manual refresh started a
     * brand-new, permanent `_state.refreshing` collector that itself did a full
     * Drive download + merge + upload. None of those collectors ever completed,
     * so a long session accumulated an unbounded pile of them, and each later
     * refresh fired ALL of them at once: redundant network calls and concurrent
     * writes to the same Drive file racing each other.
     */
    private fun bootstrapDriveSync() {
        if (!driveSyncBootstrapped.compareAndSet(false, true)) return
        // Restore auto-sync Drive URI and last sync timestamp from preferences.
        viewModelScope.launch {
            val uri = settingsStore.syncUri()
            val lastSync = settingsStore.lastSyncMs()
            // Restores a failure the background periodic worker hit while the
            // app was closed, so it's visible in Settings on next launch instead
            // of only ever surfacing if a foreground sync happens to fail too.
            var lastError = settingsStore.lastSyncError()
            // Proactive check: don't wait for the next sync attempt to discover
            // the persisted grant is gone (revoked in system Settings, or the
            // picked Drive file/folder was deleted) -- surface it the moment
            // the app opens instead.
            if (uri != null) {
                val stillGranted = runCatching {
                    getApplication<android.app.Application>().contentResolver.persistedUriPermissions.any {
                        it.uri.toString() == uri && it.isReadPermission && it.isWritePermission
                    }
                }.getOrDefault(true) // Assume fine if the check itself fails; performDriveSync will report the real error.
                if (!stillGranted) {
                    lastError = "Lost access to the Drive file. Set up sync again"
                    settingsStore.setLastSyncError(lastError)
                }
            }
            val wifiOnly = settingsStore.syncWifiOnly()
            val settingsMode = settingsStore.settingsMode()
            val vehicles = _state.value.vehicles
            val defaultPresets = vehicles.associate { v ->
                v.vin to (settingsStore.defaultClimatePreset(v.vin) ?: "smart")
            }
            // Restore the cached device registry + primary + this-device identity so
            // Settings shows "your devices" immediately on launch, before (and even
            // without) the first live sync of the session.
            val cachedDevices = settingsStore.syncedDevices()
            val cachedPrimary = settingsStore.syncPrimaryDeviceId()
            val myDeviceId = settingsStore.syncDeviceId()
            val myDeviceName = settingsStore.syncDeviceName()
            val fileFingerprint = settingsStore.syncFileFingerprint()
            _state.update {
                it.copy(
                    syncUri = uri, lastSyncMs = lastSync, syncError = lastError, syncWifiOnly = wifiOnly,
                    settingsMode = settingsMode, defaultClimatePresets = defaultPresets,
                    syncDevices = cachedDevices, syncPrimaryId = cachedPrimary,
                    thisDeviceId = myDeviceId, syncDeviceName = myDeviceName,
                    syncFileFingerprint = fileFingerprint,
                )
            }
        }
        // Bidirectional auto-sync on refresh: download newer settings from Drive,
        // then upload our current settings (merge loop for cross-device sync).
        // The actual download/compare/import/upload sequence lives in
        // SettingsStore.performDriveSync() — shared with the watch's on-demand
        // "Sync now" request (WearBridge.driveSync) so there's exactly one
        // implementation of that logic.
        viewModelScope.launch {
            _state.map { it.refreshing }.distinctUntilChanged().collect { wasRefreshing ->
                // Route through the single sync path so an imported remote is
                // actually folded into UiState (refreshLocalCarConfig), not just
                // lastSyncMs/syncError -- same handling as setSyncUri / retryDriveSync.
                if (!wasRefreshing) runDriveSyncNow()
            }
        }
        // Auto-push on ANY tracked change: every editTracked() that touches a
        // portable pref (a settings toggle, a pebble/section reorder, per-car
        // config…) appends to the dirty set, so observing it here lets sync feel
        // automatic and seamless instead of only firing on a data refresh or the
        // 2h worker. Debounced with a cancel-and-restart job so a burst of edits
        // (dragging pebbles, nudging a slider) coalesces into ONE Drive write
        // ~2s after the last change rather than hammering Drive per keystroke.
        // Only runs when sync is configured; the download-then-upload merge in
        // performDriveSync stays the single source of truth.
        viewModelScope.launch {
            var pushJob: kotlinx.coroutines.Job? = null
            settingsStore.dirtyKeysFlow
                .distinctUntilChanged()
                .collect { dirty ->
                    // Empty = nothing pending (or a sync just cleared it) — cancel any
                    // scheduled push and wait for the next real change.
                    if (dirty.isEmpty() || _state.value.syncUri == null) {
                        pushJob?.cancel()
                        return@collect
                    }
                    pushJob?.cancel()
                    // viewModelScope.launch (not a bare `launch`): the collect{}
                    // lambda's receiver is FlowCollector, not a CoroutineScope, so
                    // the debounce job is launched on the ViewModel's own scope.
                    pushJob = viewModelScope.launch {
                        kotlinx.coroutines.delay(AUTO_PUSH_DEBOUNCE_MS)
                        runDriveSyncNow()
                    }
                }
        }
        // NOTE: no explicit launch-time sync is needed here. The refreshing-transition
        // collector above collects a StateFlow-derived flow, which emits its CURRENT
        // value to a new collector immediately; `refreshing` is false at bootstrap, so
        // that collector already fires runDriveSyncNow() once on launch (distinctUntil-
        // Changed passes the first emission through). An earlier explicit initial-pull
        // block here was removed as a redundant second pass on the same driveSyncMutex.
    }

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
        if (_state.value.currentIndex == index) {
            ensureStatus(v)
            return
        }
        _state.update { it.copy(currentIndex = index) }
        viewModelScope.launch { settingsStore.setLastVehicleVin(v.vin) }
        ensureStatus(v)
    }

    /** Large-screen only: expand one car to full screen (also selects it, so
     *  the two indices never disagree about which car is "current"). */
    fun expand(index: Int) = _state.update { it.copy(expandedIndex = index, currentIndex = index) }
    /** Back out of the expanded single-car view to the grid. */
    fun collapse() = _state.update { it.copy(expandedIndex = null) }

    /**
     * Handle an app-icon shortcut (or, later, a "open app + run" quick tile). If
     * the garage isn't loaded yet the request is queued and run once it is.
     */
    fun handleShortcut(vin: String, cmd: String) {
        pendingShortcut = vin to cmd
        tryRunPendingShortcut()
    }

    private fun tryRunPendingShortcut() {
        val (vin, cmd) = pendingShortcut ?: return
        val v = _state.value.vehicles.firstOrNull { it.vin == vin } ?: return
        pendingShortcut = null
        val idx = _state.value.vehicles.indexOf(v)
        if (idx >= 0) selectIndex(idx)
        // selectIndex only updates which car is current, not which screen is
        // showing -- tapping a car-specific widget while the app was sitting
        // on Settings (or any other screen) previously selected the right car
        // underneath without ever bringing it into view. A widget/shortcut
        // tap always means "look at this car," so force back to the garage.
        _state.update { it.copy(screen = Screen.Garage, expandedIndex = null) }
        val status = _state.value.statusFor(v)
        when (cmd) {
            // Toggles: do the opposite of the last-known state.
            "doors" -> if (status?.doorLock == true) unlock(v) else lock(v)
            "climate" -> if (status?.airCtrlOn == true) stopClimate(v) else {
                // Gate the start on !isDriving, matching the in-app control (which
                // goes read-only while driving) -- the car rejects remote climate
                // while moving, so firing it would only waste a serialized request
                // slot and surface a spurious "command failed".
                if (!_state.value.isDriving(v)) {
                    startClimate(v, ClimateRequest(tempF = DEFAULT_CLIMATE_TEMP_F, defrost = false, durationMinutes = DEFAULT_CLIMATE_DURATION_MIN))
                }
            }
            "lock" -> lock(v)
            "unlock" -> unlock(v)
            "locate" -> locate(v)
            "bluelink" -> openOemApp(v)
            // "open" just selects the car (done above).
        }
    }

    /** Launch the OEM Bluelink/Genesis/Kia app for this car's brand. */
    private fun openOemApp(v: Vehicle) {
        val ctx = getApplication<Application>()
        val links = brandOf(v).links
        val launch = ctx.packageManager.getLaunchIntentForPackage(links.appPackage)
            ?: android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(links.playStoreUrl),
            )
        runCatching {
            ctx.startActivity(launch.apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    }

    /**
     * Fetches fresh status once per session. Disk-cached data is shown instantly
     * (so no UI flash), but we still pull a live update — otherwise a warm cache
     * would leave the garage permanently stale until a manual refresh.
     */
    private fun ensureStatus(v: Vehicle) {
        if (v.vin in sessionFetched) return
        // Background load: log failures but don't interrupt with a toast (one
        // flaky car shouldn't spam errors over the others).
        loadStatus(v, refresh = false, errorMessage = "Couldn't load status", surfaceErrors = false)
    }

    fun refreshStatus(v: Vehicle) {
        loadStatus(
            v, refresh = true, errorMessage = "Refresh failed",
            logSuccess = "Status refreshed for ${v.name}", surfaceErrors = true,
        )
        // A manual pull-to-refresh is exactly the moment someone's actively
        // looking at the app and wants everything current -- piggyback the
        // (internally debounced, so this doesn't hammer the network on rapid
        // refreshes) update check here instead of only ever firing once at
        // cold start, which could go a whole session without re-checking.
        checkForUpdate()
    }

    /** Shared by the cold-start check and every refreshStatus() call. Debounced/
     *  snoozed internally (see UpdateChecker) -- safe to call as often as this is.
     *  [force] bypasses the debounce/snooze (for a user-initiated "Check now");
     *  [surfaceResult] reports UpToDate/Failed to the snackbar (auto checks stay silent). */
    private fun checkForUpdate(force: Boolean = false, surfaceResult: Boolean = false) {
        viewModelScope.launch {
            if (surfaceResult) _state.update { it.copy(updateChecking = true) }
            try {
                val result = com.bloo.bluelink.update.UpdateChecker.checkPhone(getApplication(), force = force)
                when (result) {
                    is com.bloo.bluelink.update.UpdateCheckResult.Available -> {
                        _state.update {
                            // A previously-downloaded APK is only still good if it's
                            // for this same build -- a newer one showing up means the
                            // cached file is stale.
                            val sameBuild = it.updateAvailable?.run?.runNumber == result.info.run.runNumber
                            it.copy(
                                updateAvailable = result.info,
                                updateApkReady = it.updateApkReady && sameBuild,
                                // "Not now" only hides the tile until the NEXT check: any
                                // Available result (even the same build re-found on a
                                // refresh) clears the dismissed flag so the tile comes
                                // back. ("Remind me" is the one that stays hidden longer —
                                // it sets a snooze so checkPhone short-circuits to UpToDate
                                // until the reminder worker clears it, so we never reach
                                // this branch while snoozed.) A pending (undo-window)
                                // dismiss is also cleared so a refresh mid-countdown just
                                // keeps the tile.
                                updateTileDismissed = false,
                                updatePendingDismiss = false,
                            )
                        }
                        // A manual check found a newer build — the update tile appears on
                        // the garage screen, which isn't visible from Settings, so also
                        // confirm via the snackbar (else the button looks like a no-op).
                        if (surfaceResult) _state.update {
                            it.copy(message = "Update available: ${com.bloo.bluelink.data.buildLabel(result.info.run.runNumber)}", messageType = "info")
                        }
                    }
                    is com.bloo.bluelink.update.UpdateCheckResult.Failed ->
                        if (surfaceResult) _state.update { it.copy(message = "Couldn't reach GitHub to check for updates.", messageType = "error") }
                    // else: silent -- next refresh tries again
                    is com.bloo.bluelink.update.UpdateCheckResult.UpToDate -> {
                        _state.update {
                            // Never yank the tile out from under work already in
                            // flight. UpToDate is returned for a genuine "nothing
                            // newer" AND for a snooze short-circuit, so a refresh
                            // during a download or install used to clear
                            // updateAvailable and take the whole tile with it --
                            // the progress UI vanished mid-install with no way
                            // back to it, which is what a refresh looked like it
                            // was doing. A ready-to-install APK is kept for the
                            // same reason: the user still has an Install button
                            // to press.
                            if (it.updateDownloading || it.updateInstalling || it.updateApkReady) it
                            else it.copy(updateAvailable = null, updateApkReady = false, updateTileDismissed = false)
                        }
                        if (surfaceResult) _state.update { it.copy(message = "You're on the latest build.", messageType = "info") }
                    }
                }
            } finally {
                if (surfaceResult) _state.update { it.copy(updateChecking = false) }
            }
        }
    }

    /** User-initiated "Check for updates" from Settings: forces past the debounce/
     *  snooze and surfaces the result (up-to-date / can't-reach) to the snackbar. If a
     *  newer build is found it just appears as the usual update tile. */
    fun checkForUpdateManually() = checkForUpdate(force = true, surfaceResult = true)

    /**
     * Fetches one car's status. The network call funnels through [statusMutex]
     * so they run strictly sequentially (Blue Link 502s on overlapping
     * requests), and a (vin, refresh) already queued/running is skipped so we
     * never pile up duplicates -- keyed on refresh too so a live pull-to-refresh
     * isn't dropped behind an in-flight background (refresh=false) fetch.
     */
    private fun loadStatus(
        v: Vehicle,
        refresh: Boolean,
        errorMessage: String,
        logSuccess: String? = null,
        surfaceErrors: Boolean = true,
    ) {
        // Key the in-flight set on (vin, refresh) so a user pull-to-refresh
        // (refresh=true) is never deduped behind an already-queued background
        // fetch (refresh=false) for the same car -- otherwise the manual call
        // returned instantly with no spinner and no live poll.
        val inFlightKey = "${v.vin}:$refresh"
        synchronized(statusInFlight) {
            if (!statusInFlight.add(inFlightKey)) return
            if (surfaceErrors) surfaceInFlight.add(v.vin)
        }
        // Only show the spinner/settle-haptic for user-triggered refreshes; silent
        // background fetches (ensureStatus) run without touching refreshing so the
        // UI stays still and no settle haptic fires when they complete.
        if (surfaceErrors) _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                // Only the network status() call needs the account-wide mutex
                // (Blue Link 502s on overlapping requests). Capture the result
                // and EXIT the lock before running the slow, purely-local
                // follow-up work (checkAlerts' DataStore read, the blocking
                // Geocoder) so it doesn't stall every other car's fetch and the
                // background poller behind it.
                val s = statusMutex.withLock { repoFor(v).status(v, refresh = refresh) }
                s?.let { status ->
                    // The status payload carries last-known GPS for free — use
                    // it so the map/location works without the rate-limited
                    // findMyCar call (this is what the official app does).
                    val statusLoc = status.vehicleLocation?.coord?.let { c ->
                        val lat = c.lat
                        val lon = c.lon
                        if (lat != null && lon != null) {
                            GeoLocation(lat, lon, status.vehicleLocation?.speed?.value)
                        } else null
                    }
                    _state.update { st ->
                        st.copy(
                            statuses = st.statuses + (v.vin to status),
                            lastFetched = st.lastFetched + (v.vin to System.currentTimeMillis()),
                            locations = if (statusLoc != null) {
                                st.locations + (v.vin to statusLoc)
                            } else st.locations,
                        )
                    }
                    persistSnapshots()
                    persistCache()
                    checkAlerts(v, status)
                    // Auto-AI: refresh the summary off the new data if enabled.
                    autoSummarize(v)
                    statusLoc?.let { loc ->
                        reverseGeocode(loc)?.let { place ->
                            _state.update { it.copy(placeNames = it.placeNames + (v.vin to place)) }
                        }
                    }
                    // Only mark fetched once a non-null status actually arrived, so
                    // a car that returned null (e.g. asleep) is retried when viewed.
                    sessionFetched.add(v.vin)
                }
                logSuccess?.let { AppLog.log(it) }
            } catch (e: Exception) {
                val msg = e.message ?: errorMessage
                AppLog.log("⚠ ${v.name}: $msg")
                if (surfaceErrors) _state.update { it.copy(message = "${v.name}: $msg", messageType = "error") }
            } finally {
                // Clear refreshing only when no more user-visible (surfaceErrors) fetches remain.
                // Background fetches finishing after a user refresh must not prematurely clear
                // the spinner or trigger the settle haptic.
                val noMoreSurface = synchronized(statusInFlight) {
                    statusInFlight.remove(inFlightKey)
                    // Only clear this VIN's surface entry if THIS call added it
                    // (surfaceErrors=true). Otherwise a concurrent background
                    // (refresh=false) fetch for the same car -- now possible since
                    // in-flight is keyed on (vin, refresh) -- would clobber a live
                    // refresh's entry and clear the spinner prematurely.
                    if (surfaceErrors) surfaceInFlight.remove(v.vin)
                    surfaceInFlight.isEmpty()
                }
                if (noMoreSurface) _state.update { it.copy(refreshing = false) }
            }
        }
    }

    /** Persist a new car display order (drag-and-drop in Settings). */
    fun reorderVehicles(order: List<Vehicle>) {
        _state.update { s ->
            // Keep the same CAR selected across a reorder, not the same
            // position -- selectIndex/expand always update currentIndex
            // together with vehicles, but this was the one place that moved
            // vehicles without it, so dragging a car above the currently
            // selected one silently swapped which car the detail view showed.
            val selectedVin = s.vehicles.getOrNull(s.currentIndex)?.vin
            val newIndex = order.indexOfFirst { it.vin == selectedVin }
            s.copy(vehicles = order, currentIndex = if (newIndex >= 0) newIndex else s.currentIndex)
        }
        viewModelScope.launch {
            settingsStore.setVehicleOrder(order.map { it.vin })
            persistSnapshots(order)
        }
    }

    private suspend fun persistSnapshots(vehicles: List<Vehicle> = _state.value.vehicles) {
        snapshotStore.saveVehicles(vehicles.map { snapshotOf(it, _state.value.statuses[it.vin]) })
        // Mirror the fresh snapshots to a paired watch (no-op when none is connected).
        com.bloo.bluelink.wear.WearBridge.publish(getApplication())
        // Refresh Quick Settings tiles too.
        com.bloo.bluelink.tiles.BlooTileService.requestUpdates(getApplication())
        refreshChargingBar(vehicles)
    }

    /**
     * Brings the live charging notification in step with what the app itself
     * just fetched.
     *
     * Until now only the background workers ever wrote that bar, so the one
     * moment the user has the freshest possible data -- standing in the app,
     * having just pulled to refresh -- was the one moment the bar in the
     * shade didn't move. It reads the same fields the workers do, and
     * update() cancels on its own when a car isn't charging, so this also
     * clears the bar the instant a refresh shows charging has finished.
     *
     * It also starts the 5-minute poll chain when the app is first to see
     * charging begin, rather than waiting up to 30 minutes for AlertWorker
     * to notice and hand off.
     */
    private suspend fun refreshChargingBar(vehicles: List<Vehicle>) {
        runCatching {
            val enabled = settingsStore.notificationPrefs().charging
            val statuses = _state.value.statuses
            var anyCharging = false
            vehicles.forEach { v ->
                val ev = statuses[v.vin]?.evStatus
                if (ev?.batteryCharge == true) anyCharging = true
                ChargingLive.update(
                    context = getApplication(),
                    vin = v.vin,
                    carName = v.name,
                    charging = ev?.batteryCharge == true,
                    percent = ev?.batteryStatus,
                    minutesToFull = ev?.remainTime2?.atc?.value?.toInt(),
                    pluggedInLabel = ev?.pluggedInLabel,
                    enabled = enabled,
                    chargeLimit = ev?.targetForCurrentPlug(),
                )
            }
            if (enabled && anyCharging) {
                com.bloo.bluelink.work.ChargingPollWorker.kick(getApplication())
            }
        }
    }


    private fun snapshotOf(v: Vehicle, status: VehicleStatus?): VehicleSnapshot {
        // Use the effective powertrain (a PHEV reads battery %, not fuel %).
        val hasBattery = _state.value.hasBattery(v)
        val percent = status?.percentFor(hasBattery)
        val range = status?.rangeMiFor(hasBattery)
        return VehicleSnapshot(
            vin = v.vin,
            name = v.name,
            model = v.model,
            isEv = v.isEv,
            hasBattery = hasBattery,
            regId = v.regId,
            generation = v.generation,
            brandIndicator = v.brandIndicator,
            percent = percent,
            rangeMi = range,
            locked = status?.doorLock,
            charging = status?.evStatus?.batteryCharge,
            climateOn = status?.airCtrlOn,
            engineOn = status?.engine,
            lat = status?.vehicleLocation?.coord?.lat,
            lon = status?.vehicleLocation?.coord?.lon,
            speedMph = status?.vehicleLocation?.speed?.value,
            updated = status?.dateTime,
            // A non-null status is freshly-fetched data; null means we're building a
            // placeholder snapshot with no live status yet (leave fetchedAt unknown).
            fetchedAt = if (status != null) System.currentTimeMillis() else 0L,
            odometer = v.odometer,
            licensePlate = _state.value.licensePlates[v.vin],
            lastServiceMiles = _state.value.lastServiceMiles[v.vin],
            serviceIntervalMiles = _state.value.serviceIntervalMiles[v.vin],
            chargeLimitPct = status?.evStatus?.targetForCurrentPlug(),
        )
    }

    /** Re-sort a freshly-fetched vehicle list to match the user's saved
     *  drag-and-drop [order] (a list of VINs). Any VIN in [order] that no
     *  longer matches a fetched vehicle is simply skipped (mapNotNull), and
     *  any newly-appeared vehicle not yet in [order] (a car added to the
     *  account since the order was last saved) is appended at the end rather
     *  than dropped, so new cars still show up somewhere. */
    private fun applyOrder(vehicles: List<Vehicle>, order: List<String>): List<Vehicle> {
        if (order.isEmpty()) return vehicles
        val byVin = vehicles.associateBy { it.vin }
        val ordered = order.mapNotNull { byVin[it] }
        val rest = vehicles.filter { it.vin !in order }
        return ordered + rest
    }

    /** Set (or, with a blank string, clear) a custom car photo URL. */
    fun setVehicleImage(vin: String, url: String) {
        _state.update {
            it.copy(
                imageUrls = if (url.isBlank()) it.imageUrls - vin else it.imageUrls + (vin to url.trim()),
            )
        }
        viewModelScope.launch { settingsStore.setImageUrl(vin, url) }
    }

    fun setLicensePlate(vin: String, plate: String) {
        _state.update {
            it.copy(
                licensePlates = if (plate.isBlank()) it.licensePlates - vin
                else it.licensePlates + (vin to plate.trim()),
            )
        }
        // Also republish the snapshot immediately so the watch's Info tile picks
        // up the new plate right away instead of waiting on the next status
        // refresh to happen to rebuild it.
        viewModelScope.launch { settingsStore.setLicensePlate(vin, plate); persistSnapshots() }
    }

    fun setLastServiceMiles(vin: String, miles: Int?) {
        _state.update {
            it.copy(
                lastServiceMiles = if (miles == null) it.lastServiceMiles - vin
                else it.lastServiceMiles + (vin to miles),
            )
        }
        // Republish immediately so the watch's Info tile reflects it right away.
        viewModelScope.launch { settingsStore.setLastServiceMiles(vin, miles); persistSnapshots() }
    }

    fun setServiceIntervalMiles(vin: String, miles: Int?) {
        _state.update {
            it.copy(
                serviceIntervalMiles = if (miles == null) it.serviceIntervalMiles - vin
                else it.serviceIntervalMiles + (vin to miles),
            )
        }
        // Republish immediately so the watch's Info tile reflects it right away.
        viewModelScope.launch { settingsStore.setServiceIntervalMiles(vin, miles); persistSnapshots() }
    }

    /** Toggle one seat-heater/cooler (or steering-wheel-heat) capability flag
     *  for a car. [field] is a short code ("dh" = driver heat, "dc" = driver
     *  cool, "ph"/"pc" = passenger, "rlh"/"rlc"/"rrh"/"rrc" = rear left/right,
     *  "sw" = steering wheel) mapped to the matching [SeatConfig] property;
     *  an unrecognized code is a no-op (`else -> current`). These flags don't
     *  come from the vehicle API -- they record which seat features the user
     *  says this specific trim actually has, so the climate UI only offers
     *  controls that will work. */
    fun setSeatFlag(v: Vehicle, field: String, value: Boolean) {
        val current = _state.value.seatConfigs[v.vin] ?: SeatConfig()
        val updated = when (field) {
            "dh" -> current.copy(driverHeat = value)
            "dc" -> current.copy(driverCool = value)
            "ph" -> current.copy(passHeat = value)
            "pc" -> current.copy(passCool = value)
            "rlh" -> current.copy(rearLeftHeat = value)
            "rlc" -> current.copy(rearLeftCool = value)
            "rrh" -> current.copy(rearRightHeat = value)
            "rrc" -> current.copy(rearRightCool = value)
            "sw" -> current.copy(steeringWheel = value)
            else -> current
        }
        _state.update { it.copy(seatConfigs = it.seatConfigs + (v.vin to updated)) }
        viewModelScope.launch { settingsStore.setSeatFlag(v.vin, field, value) }
    }

    /** Toggle a pebble (detail section) open/closed for a car (persisted). */
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

    /** Show/hide a non-essential pebble for a car (persisted). */
    fun setSectionHidden(v: Vehicle, section: String, hidden: Boolean) {
        val key = "${v.vin}:$section"
        _state.update {
            it.copy(hiddenPebbles = if (hidden) it.hiddenPebbles + key else it.hiddenPebbles - key)
        }
        viewModelScope.launch {
            settingsStore.setSectionHidden(v.vin, section, hidden)
            // hiddenSections isn't part of Appearance, so it isn't covered by the
            // appearance.collect mirror below -- republish explicitly so the
            // watch's matching tile hides/shows immediately.
            com.bloo.bluelink.wear.WearBridge.publishSettings(getApplication(), appearance.value)
        }
    }

    /** Finish first-run onboarding (wizard complete) and land in the app. */
    fun finishOnboarding() {
        val vins = _state.value.vehicles.map { it.vin }
        viewModelScope.launch {
            settingsStore.setOnboardingSeen()
            vins.forEach { settingsStore.setCarConfigured(it) }
        }
        _state.update {
            it.copy(screen = if (it.vehicles.isEmpty()) Screen.Empty else Screen.Garage, garageLoadError = null)
        }
    }

    /** Mark newly-detected cars as configured and return to the garage. */
    fun finishCarSetup(vins: List<String>) {
        viewModelScope.launch { vins.forEach { settingsStore.setCarConfigured(it) } }
        _state.update {
            it.copy(screen = if (it.vehicles.isEmpty()) Screen.Empty else Screen.Garage, garageLoadError = null)
        }
    }

    /** Dismiss the post-onboarding "check out Settings" hint on the garage
     *  (in-memory only -- it's a one-time nudge, not worth persisting). */
    fun dismissSettingsHint() = _state.update { it.copy(showSettingsHint = false) }

    /** Dismiss the coach mark pointing at Settings' back arrow. */
    fun dismissSettingsCoach() = _state.update { it.copy(showSettingsCoach = false) }

    fun setPowertrain(v: Vehicle, value: Powertrain) {
        _state.update { it.copy(powertrains = it.powertrains + (v.vin to value)) }
        // Republish immediately: the watch's Charge/Fuel tile derives hasBattery
        // from the synced snapshot, so a correction here needs to reach it right
        // away rather than waiting on the next status refresh.
        viewModelScope.launch { settingsStore.setPowertrain(v.vin, value); persistSnapshots() }
    }

    // --- App self-update (GitHub Actions builds; Bloo isn't on the Play Store) ---

    /** "Not now" on the update tile — but with a brief call-back window: instead
     *  of hiding the tile instantly, this starts an undo countdown ([updatePending-
     *  Dismiss]) during which the tile stays visible with an "Undo" strip. After
     *  [UPDATE_DISMISS_UNDO_MS] the dismiss commits (tile hides until the next
     *  update check re-surfaces it — see checkForUpdate, which clears the flag on
     *  any Available result). The job is cancel-and-restart so tapping dismiss
     *  again just restarts the window; [undoDismissUpdate] cancels it. */
    private var updateDismissJob: kotlinx.coroutines.Job? = null
    fun dismissUpdate() {
        updateDismissJob?.cancel()
        _state.update { it.copy(updatePendingDismiss = true) }
        updateDismissJob = viewModelScope.launch {
            kotlinx.coroutines.delay(UPDATE_DISMISS_UNDO_MS)
            _state.update { it.copy(updateTileDismissed = true, updatePendingDismiss = false) }
        }
    }

    /** "Undo" during the call-back window: cancel the pending dismiss so the tile
     *  stays. No-op if the window already elapsed (the tile is gone by then, but
     *  the next refresh brings it back anyway). */
    fun undoDismissUpdate() {
        updateDismissJob?.cancel()
        _state.update { it.copy(updatePendingDismiss = false, updateTileDismissed = false) }
    }

    /** "Remind me": hide the tile now, snooze checks so it doesn't re-surface on
     *  every refresh in the meantime, and schedule a one-time worker that in ~1 day
     *  posts a reminder notification AND clears the snooze so the tile comes back.
     *  Skips the undo window — "Remind me" is already an explicit deferral. */
    fun snoozeUpdate() {
        updateDismissJob?.cancel()
        _state.update { it.copy(updateTileDismissed = true, updatePendingDismiss = false) }
        viewModelScope.launch {
            // Snooze for 1 day to match the reminder worker's 1-day delay: if the
            // worker is delayed by Doze, a normal refresh still revives the tile at
            // ~1 day rather than it staying hidden for the longer default window.
            com.bloo.bluelink.update.UpdateChecker.snooze(getApplication(), UPDATE_REMINDER_DELAY_MS)
            com.bloo.bluelink.work.UpdateReminderWorker.schedule(getApplication())
        }
    }

    /** Fixed on-disk location for the downloaded update APK, inside the app's
     *  cache dir (so the system can reclaim it under storage pressure, and
     *  it's automatically cleaned up on uninstall). Always the same filename,
     *  so a later download simply overwrites a stale one. */
    private fun apkCacheFile(): java.io.File {
        val ctx = getApplication<Application>()
        return java.io.File(java.io.File(ctx.cacheDir, "apk"), "Bloo.apk")
    }

    /** Hand an already-downloaded APK to the system package installer via a
     *  FileProvider content:// URI (a file:// URI would be rejected under
     *  the FileUriExposedException policy on modern Android). Wrapped in
     *  runCatching since there's no reliable way to know in advance whether
     *  an installer activity will actually be available to resolve the
     *  intent; returns whether the install UI was successfully launched. */
    private fun launchApkInstaller(dest: java.io.File): Boolean {
        val ctx = getApplication<Application>()
        return runCatching {
            val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", dest)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        }.isSuccess
    }

    /** The update tile's primary button, first tap: downloads the APK in the
     *  background with no other UI change yet -- lets someone start the
     *  update mid-something-else and come back to it. The button then swaps
     *  to "Install" (see [installDownloadedUpdate]) once this finishes. */
    fun downloadUpdateInBackground() {
        val url = _state.value.updateAvailable?.run?.phoneApkUrl
        if (url == null) {
            _state.update { it.copy(message = "No direct download for this build. Use the browser link instead.") }
            return
        }
        if (_state.value.updateDownloading || _state.value.updateApkReady) return
        // Starting a download is an explicit "keep this update" signal: abort any
        // in-flight dismiss (undo-window) timer and un-hide the tile, so the pending
        // dismiss can't fire mid-download and strand the finished APK behind a hidden tile.
        updateDismissJob?.cancel()
        updateDismissJob = null
        _state.update { it.copy(updateDownloading = true, updateDownloadProgress = 0f, updatePendingDismiss = false, updateTileDismissed = false) }
        viewModelScope.launch {
            val dest = apkCacheFile()
            val ok = com.bloo.bluelink.data.UpdateApi.downloadApk(url, dest) { progress ->
                _state.update { it.copy(updateDownloadProgress = progress) }
            }
            _state.update { it.copy(updateDownloading = false, updateDownloadProgress = null, updateApkReady = ok) }
            if (!ok) {
                _state.update { it.copy(message = "Download failed. Check your connection and try again.") }
            }
        }
    }

    /** The update tile's second tap, once [downloadUpdateInBackground] has
     *  finished: the APK is already sitting in cache. If the user opted into
     *  seamless install AND Shizuku is running, install silently via ADB; otherwise
     *  (or on any Shizuku failure) hand it to the system installer as before. */
    fun installDownloadedUpdate() {
        if (!_state.value.updateApkReady) return
        // Guard against a second tap (or a permission-grant retry) re-entering while a
        // seamless install is already running — otherwise two concurrent installer
        // sessions write/commit the same APK.
        if (_state.value.updateInstalling) return
        val dest = apkCacheFile()
        if (!dest.exists()) {
            _state.update { it.copy(updateApkReady = false, message = "The downloaded update is gone. Tap Update to fetch it again.") }
            return
        }
        val installer = com.bloo.bluelink.update.ShizukuInstaller
        val wantSeamless = appearance.value.seamlessInstallShizuku && installer.isAvailable()
        if (!wantSeamless) {
            fallbackInstall(dest)
            return
        }
        if (!installer.hasPermission()) {
            // Ask; the grant arrives on onShizukuPermissionResult, which retries. Until
            // then leave the APK ready so a second tap (or the grant) completes it.
            _state.update { it.copy(message = "Grant Shizuku access to install updates silently.", messageType = "info") }
            installer.requestPermission(SHIZUKU_INSTALL_REQUEST_CODE)
            return
        }
        seamlessInstall(dest)
    }

    /** Runs the Shizuku silent install off the main thread, falling back to the
     *  system installer on any failure. */
    private fun seamlessInstall(dest: java.io.File) {
        if (_state.value.updateInstalling) return
        val ctx = getApplication<Application>()
        // messageType is REQUIRED here: it defaults to "error" (and clearMessage()
        // resets it to "error"), and the snackbar's colour `when` falls through to
        // the red errorContainer branch for anything it doesn't recognise. Without
        // it this progress message rendered as a red error toast mid-install.
        _state.update { it.copy(updateInstalling = true, message = "Installing update…", messageType = "info") }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = com.bloo.bluelink.update.ShizukuInstaller.installApk(dest, ctx.packageName)
            if (result.isFailure) {
                // Silent path failed (Shizuku died, OEM restriction, timed out, etc.) —
                // clear the in-flight flag and fall back to the system installer.
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    _state.update { it.copy(updateInstalling = false) }
                    fallbackInstall(dest)
                }
            } else {
                // Success. Usually the OS force-stops us as it swaps the APK, so this
                // never renders — BUT a replace-install commit reports STATUS_SUCCESS as
                // soon as it's staged and some OEMs defer the process kill. Give a
                // terminal state so the tile can't stay locked on "Installing…" forever:
                // clear the flags + prompt to reopen. (The running process still reports
                // the old build number, so a later auto-check may re-surface the same
                // update — acceptable, and far better than a permanent lock.)
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    _state.update {
                        it.copy(
                            updateInstalling = false,
                            updateApkReady = false,
                            message = "Update installed. Reopen Bloo to finish.",
                            messageType = "info",
                        )
                    }
                }
            }
        }
    }

    /** The classic tap-through system installer (also the fallback for the seamless
     *  path). Reports only if even this can't be launched. */
    private fun fallbackInstall(dest: java.io.File) {
        if (!launchApkInstaller(dest)) {
            _state.update { it.copy(message = "Couldn't open the installer. Find Bloo.apk in your downloads.") }
        }
    }

    /** Shizuku permission result forwarded from MainActivity. If the user just
     *  granted it and an update is still staged, complete the seamless install. */
    fun onShizukuPermissionResult(requestCode: Int, grantResult: Int) {
        if (requestCode != SHIZUKU_INSTALL_REQUEST_CODE) return
        if (grantResult != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            // Info, not error: the user made a choice and the normal installer still
            // works, so nothing is actually broken to report in red.
            _state.update { it.copy(message = "Shizuku access denied. Updates will use the normal installer.", messageType = "info") }
            return
        }
        val dest = apkCacheFile()
        if (_state.value.updateApkReady && dest.exists()) seamlessInstall(dest)
    }

    /** The GitHub Actions build number this app was compiled from (0 = local build). */
    val currentBuildNumber: Int get() = com.bloo.bluelink.BuildConfig.BUILD_RUN_NUMBER

    // --- On-device AI (Gemini Nano) --------------------------------------

    fun setAiEnabled(value: Boolean) {
        _state.update { it.copy(aiEnabled = value) }
        viewModelScope.launch {
            settingsStore.setAiEnabled(value)
            // aiEnabled isn't part of Appearance, so it isn't covered by the
            // appearance.collect mirror below -- republish explicitly so the
            // watch's own AI toggle/tile visibility updates immediately.
            com.bloo.bluelink.wear.WearBridge.publishSettings(getApplication(), appearance.value)
        }
    }

    /** Toggle "run AI summaries automatically" -- when on, [autoSummarize] is
     *  invoked after every status load/command instead of requiring a manual
     *  tap on Summarize. Updates [_state] immediately (so the switch reflects
     *  the change without waiting on the DataStore write) and persists async. */
    fun setAiAuto(value: Boolean) {
        _state.update { it.copy(aiAuto = value) }
        viewModelScope.launch { settingsStore.setAiAuto(value) }
    }

    /**
     * Auto-summarize a car when auto-AI is on (called after open/refresh/command).
     * Silent: no "refresh first" nudge and no error toast, since the user didn't
     * explicitly ask — they can always tap Summarize for the surfaced version.
     */
    private fun autoSummarize(v: Vehicle) {
        val s = _state.value
        if (!s.aiSupported || !s.aiEnabled || !s.aiAuto) return
        if (v.vin in s.aiBusy) return
        val status = s.statusFor(v) ?: return
        _state.update { it.copy(aiBusy = it.aiBusy + v.vin) }
        viewModelScope.launch {
            val result = runCatching { ai.summarize(summaryPrompt(v, status)) }
            _state.update { st ->
                result.fold(
                    onSuccess = { sum ->
                        st.copy(aiBusy = st.aiBusy - v.vin, aiSummaries = st.aiSummaries + (v.vin to sum))
                    },
                    onFailure = { e ->
                        AppLog.log("⚠ Auto AI summary: ${e.message}")
                        st.copy(aiBusy = st.aiBusy - v.vin)
                    },
                )
            }
        }
    }

    /**
     * Manual "Summarize" tap. Requires a status already in [UiState.statuses]
     * (if there isn't one, it asks the user to refresh first rather than
     * triggering a fetch itself); marks the VIN busy so the button can show a
     * spinner and a second tap is ignored via the guard above, runs the model
     * off the main thread inside [viewModelScope], and always clears the busy
     * flag on either branch of [Result.fold] -- success writes the summary
     * into [UiState.aiSummaries], failure logs and surfaces a snackbar.
     */
    fun summarizeCar(v: Vehicle) {
        if (v.vin in _state.value.aiBusy) return
        val status = _state.value.statusFor(v) ?: run {
            _state.update { it.copy(message = "Refresh ${v.name} first, then summarize.", messageType = "info") }
            return
        }
        _state.update { it.copy(aiBusy = it.aiBusy + v.vin) }
        viewModelScope.launch {
            // Build the prompt for THIS car only, so the result reflects just it.
            val prompt = summaryPrompt(v, status)
            val result = runCatching { ai.summarize(prompt) }
            _state.update { st ->
                result.fold(
                    onSuccess = { s ->
                        st.copy(aiBusy = st.aiBusy - v.vin, aiSummaries = st.aiSummaries + (v.vin to s))
                    },
                    onFailure = { e ->
                        // The exception text is AICore/Gemini-Nano implementation
                        // detail ("Feature not available: ...", binder/ExecutionException
                        // strings). Log it for diagnostics; show the user a sentence.
                        AppLog.log("⚠ AI summary: ${e.message}")
                        st.copy(aiBusy = st.aiBusy - v.vin, message = "Couldn't summarize ${v.name} right now.")
                    },
                )
            }
        }
    }

    /** Ask Gemini Nano a free-form question answered from the cars' live data. */
    fun askAi(query: String) {
        if (!_state.value.aiEnabled || query.isBlank()) return
        _state.update { it.copy(aiBusy = it.aiBusy + "search") }
        viewModelScope.launch {
            val data = _state.value.vehicles.joinToString("\n\n") { v ->
                carText(v, _state.value.statusFor(v))
            }
            val reply = runCatching {
                ai.summarize("Answer this question using only the data below.\nQuestion: $query\n\nData:\n$data")
            }.onFailure { AppLog.log("⚠ AI search: ${it.message}") }.getOrNull()
            // On failure both `thinking` and `reply` go false/null, and the answer
            // card renders on `thinking || reply != null` — so without a message the
            // whole card just silently vanished after "Thinking…", with no log line
            // either. Say something, like the per-car summary path already does.
            _state.update {
                it.copy(
                    aiBusy = it.aiBusy - "search",
                    aiSearchReply = reply,
                    message = if (reply == null) "Couldn't answer that. Try again." else it.message,
                )
            }
        }
    }

    /** Dismiss the AI search-answer card. */
    fun clearAiReply() = _state.update { it.copy(aiSearchReply = null) }

    /**
     * The instruction + data prompt sent to Gemini Nano for a SINGLE car. The
     * instruction tells the model to lead with the highest-priority facts (locked,
     * charging + time-to-full, driving) and the data block describes only [v], so
     * the summary reflects exactly the car the user tapped Summarize on.
     */
    private fun summaryPrompt(v: Vehicle, status: VehicleStatus?): String =
        "${v.name} vehicle status:\n" + carText(v, status)

    /**
     * A compact, readable description of a single car's current state for the AI,
     * ordered by importance (doors, charging, driving, then the rest).
     */
    private fun carText(v: Vehicle, status: VehicleStatus?): String {
        val s = _state.value
        val parts = mutableListOf<String>()
        parts += "Vehicle: ${v.name} (${v.model})."
        if (status == null) {
            parts += "No live status has been fetched yet for this car."
            return parts.joinToString(" ")
        }
        // Priority 1 — doors.
        parts += "The doors are ${if (status.doorLock == true) "locked" else "unlocked"}."
        // Priority 2 — charging + time to full (EV/PHEV only).
        if (s.hasBattery(v)) {
            if (status.evStatus?.batteryCharge == true) {
                val mins = status.evStatus?.remainTime2?.atc?.value?.toInt()?.takeIf { it > 0 }
                parts += if (mins != null) {
                    "It is charging, with about ${fmtTimeToFull(mins)} until fully charged."
                } else {
                    "It is currently charging."
                }
            } else {
                parts += "It is not charging."
            }
        }
        // Priority 3 — driving / parked.
        s.drivingLabel(v)?.let { parts += "The car is currently ${it.lowercase(Locale.US)}." }
        status.engine?.let { parts += "The engine is ${if (it) "on" else "off"}." }
        // Remaining status, most useful first.
        if (s.hasBattery(v)) status.evStatus?.batteryStatus?.let { parts += "The drive battery is at $it%." }
        if (s.hasFuel(v)) status.fuelLevel?.let { parts += "Fuel is at $it%." }
        status.rangeMiFor(s.hasBattery(v))?.let { parts += "Estimated driving range is $it miles." }
        parts += "Climate is ${if (status.airCtrlOn == true) "on" else "off"}."
        status.battery?.batSoc?.let { parts += "The 12V starter battery is at $it%." }
        v.odometer?.trim()?.takeIf { it.isNotBlank() }?.let { parts += "The odometer reads $it miles." }
        s.placeNames[v.vin]?.let { parts += "Last known location: $it." }
        // Warnings.
        if (status.tirePressureLamp?.hasWarning == true) parts += "There is a tire pressure warning."
        if (status.washerFluidStatus == true) parts += "The washer fluid is low."
        if (status.breakOilStatus == true) parts += "The brake fluid needs attention."
        if (status.smartKeyBatteryWarning == true) parts += "The key fob battery is low."
        return parts.joinToString(" ")
    }

    /** "45 minutes" or "1 hour 5 minutes" for a charge-time-to-full readout. */
    private fun fmtTimeToFull(minutes: Int): String {
        if (minutes < 60) return "$minutes minutes"
        val h = minutes / 60
        val m = minutes % 60
        val hStr = if (h == 1) "1 hour" else "$h hours"
        return if (m == 0) hStr else "$hStr $m minutes"
    }

    /** Toggle whether a given car+action app-icon shortcut is shown. */
    fun setShortcutEnabled(vin: String, cmd: String, enabled: Boolean) {
        val universe = _state.value.vehicles.flatMap { v ->
            com.bloo.bluelink.Shortcuts.ACTIONS.map { "${it}_${v.vin}" }
        }.toSet()
        val current = _state.value.shortcutSet ?: universe
        val updated = if (enabled) current + "${cmd}_$vin" else current - "${cmd}_$vin"
        _state.update { it.copy(shortcutSet = updated) }
        viewModelScope.launch {
            settingsStore.setEnabledShortcuts(updated)
            com.bloo.bluelink.Shortcuts.refresh(getApplication(), _state.value.vehicles, updated)
        }
    }

    /** Assign (or clear) a Quick Settings tile to a car + command. */
    fun setTileAssignment(index: Int, vin: String?, cmd: String?) {
        _state.update {
            val list = it.tileConfigs.toMutableList()
            if (index in list.indices) {
                list[index] = if (vin != null && cmd != null) vin to cmd else null
            }
            it.copy(tileConfigs = list)
        }
        viewModelScope.launch {
            settingsStore.setTileConfig(index, vin, cmd)
            com.bloo.bluelink.tiles.BlooTileService.requestUpdates(getApplication())
        }
    }

    /** Set (or clear with null/blank) a tile's custom display name. */
    fun setTileLabel(index: Int, label: String?) {
        _state.update {
            val list = it.tileLabels.toMutableList()
            if (index in list.indices) list[index] = label?.trim()?.takeIf { s -> s.isNotEmpty() }
            it.copy(tileLabels = list)
        }
        viewModelScope.launch {
            settingsStore.setTileLabel(index, label)
            com.bloo.bluelink.tiles.BlooTileService.requestUpdates(getApplication())
        }
    }

    /** Set what the climate tile runs: "default", "smart", or a preset id. */
    fun setTileClimateTarget(index: Int, target: String) {
        _state.update {
            val list = it.tileClimateTargets.toMutableList()
            if (index in list.indices) list[index] = target
            it.copy(tileClimateTargets = list)
        }
        viewModelScope.launch {
            settingsStore.setTileClimateTarget(index, target)
            com.bloo.bluelink.tiles.BlooTileService.requestUpdates(getApplication())
        }
    }

    // These two are global (not per-tile) toggles for how Quick Settings tiles
    // behave, following the same shape as setTileAssignment/setTileLabel/
    // setTileClimateTarget above: update _state for immediate UI feedback,
    // persist to SettingsStore, then poke BlooTileService so the system tiles
    // (which read their state independently, not via this StateFlow) refresh
    // right away instead of waiting for their next natural update tick.

    /** Whether tiles run their command in the background vs. opening the app. */
    fun setTileBackground(value: Boolean) {
        _state.update { it.copy(tileBackground = value) }
        viewModelScope.launch {
            settingsStore.setTileBackground(value)
            com.bloo.bluelink.tiles.BlooTileService.requestUpdates(getApplication())
        }
    }

    /** Whether tapping a tile also kicks a throttled status refresh. */
    fun setTileLiveRefresh(value: Boolean) {
        _state.update { it.copy(tileLiveRefresh = value) }
        viewModelScope.launch {
            settingsStore.setTileLiveRefresh(value)
            com.bloo.bluelink.tiles.BlooTileService.requestUpdates(getApplication())
        }
    }

    /** Pin (or clear, with null) a pebble to the dual-column hot spot. */
    fun setHotspot(v: Vehicle, section: String?) {
        _state.update {
            val m = it.hotspotSections.toMutableMap()
            if (section == null) m.remove(v.vin) else m[v.vin] = section
            it.copy(hotspotSections = m)
        }
        viewModelScope.launch { settingsStore.setHotspot(v.vin, section) }
    }

    /** Persist a new pebble order for a car (drag-and-drop on the card). */
    fun setSectionOrder(v: Vehicle, order: List<String>) {
        _state.update { it.copy(sectionOrders = it.sectionOrders + (v.vin to order)) }
        viewModelScope.launch { settingsStore.setSectionOrder(v.vin, order) }
    }

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
            // lock (loadStatus/runCommand/loadGarage + the watch's own loadTrips); this
            // was the lone gap. Only the network call is inside the lock — the filter
            // and result handling stay outside, matching loadStatus's minimal scope.
            val fetched = runCatching { statusMutex.withLock { repoFor(v).trips(v) } }
                .onFailure { e -> AppLog.log("⚠ Trips for ${v.name}: ${e.message ?: "failed"}") }
                .getOrNull()
                ?.filter { it.distance != null && it.distance!! > 0 }
            _state.update {
                it.copy(
                    trips = if (fetched != null) it.trips + (v.vin to fetched) else it.trips,
                    pending = it.pending - "${v.vin}:trips",
                )
            }
        }
    }

    /** Restore the last-used climate settings for a car (null if never saved). */
    suspend fun loadSavedClimate(v: Vehicle): ClimateRequest? = settingsStore.savedClimate(v.vin)

    private val climateSaveJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    /**
     * Debounced persist + watch-mirror of the live climate draft. Lives in
     * viewModelScope on purpose: a LaunchedEffect-side debounce is cancelled when
     * the ClimatePebble leaves composition (cover-screen tile swipe, car switch,
     * collapse), silently dropping any change made in the final 400ms - the
     * sliders then reverted to the stale persisted value on the next open.
     */
    fun saveClimateDebounced(v: Vehicle, req: ClimateRequest, activePresetId: String?) {
        climateSaveJobs[v.vin]?.cancel()
        climateSaveJobs[v.vin] = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            settingsStore.saveClimate(v.vin, req)
            publishClimateState(v.vin, activePresetId, req)
        }
    }

    // Climate-preset CRUD: each of these three follows the same optimistic
    // pattern -- compute the new per-VIN preset list, write it into
    // UiState.climatePresets immediately so the UI updates without waiting on
    // disk I/O, then persist the same change to SettingsStore asynchronously.
    // The [_state.map { it.climatePresets }...] collector in init mirrors the
    // result to the watch, so none of these need to publish to the watch
    // themselves.

    /** Save the current climate draft as a new named preset (a fresh
     *  timestamp-based id, so presets never collide even if named the same). */
    fun saveClimatePreset(v: Vehicle, name: String, req: ClimateRequest) {
        val preset = ClimatePreset(
            id = System.currentTimeMillis().toString(),
            name = name.trim().ifBlank { "Preset" },
            request = req,
        )
        _state.update {
            it.copy(climatePresets = it.climatePresets + (v.vin to (it.climatePresets[v.vin].orEmpty() + preset)))
        }
        viewModelScope.launch { settingsStore.saveClimatePreset(v.vin, preset) }
    }

    /** Remove one saved preset by id. */
    fun deleteClimatePreset(v: Vehicle, id: String) {
        _state.update {
            val updated = it.climatePresets[v.vin].orEmpty().filter { p -> p.id != id }
            it.copy(climatePresets = it.climatePresets + (v.vin to updated))
        }
        viewModelScope.launch { settingsStore.deleteClimatePreset(v.vin, id) }
    }

    /** Persist a new drag-and-drop order for a car's saved presets. */
    fun reorderClimatePresets(v: Vehicle, ordered: List<ClimatePreset>) {
        _state.update { it.copy(climatePresets = it.climatePresets + (v.vin to ordered)) }
        viewModelScope.launch { settingsStore.setClimatePresets(v.vin, ordered) }
    }

    /**
     * Mirror this car's live climate draft + active preset to the watch. Skips the
     * write when nothing changed, so state received *from* the watch doesn't echo
     * straight back and loop.
     */
    fun publishClimateState(vin: String, presetId: String?, req: ClimateRequest) {
        val cs = req.toClimateSync(presetId)
        if (_state.value.climateSync[vin] == cs) return
        val merged = _state.value.climateSync + (vin to cs)
        _state.update { it.copy(climateSync = merged) }
        com.bloo.bluelink.wear.WearBridge.publishClimate(
            getApplication(), com.bloo.bluelink.data.WearClimateState(merged),
        )
    }

    fun locate(v: Vehicle) = runCommand(v.vin, "locate", "Location updated", optimistic = null) {
        // The GPS rides along with a status refresh (this is what the official app
        // uses); prefer it over the heavily rate-limited findMyCar, which is the
        // thing that throws "exceeded the daily remote service request limit".
        val s = repoFor(v).status(v, refresh = true)
        s?.let { st ->
            // Advance lastFetched too (like loadStatus does) -- otherwise the
            // card's "updated X ago" stays stuck at the old time and maybeRelock's
            // stale check can wrongly nudge "pull to refresh" right after a Locate.
            _state.update {
                it.copy(
                    statuses = it.statuses + (v.vin to st),
                    lastFetched = it.lastFetched + (v.vin to System.currentTimeMillis()),
                )
            }
        }
        val statusLoc = s?.vehicleLocation?.coord?.let { c ->
            val lat = c.lat
            val lon = c.lon
            if (lat != null && lon != null) GeoLocation(lat, lon, s.vehicleLocation?.speed?.value) else null
        }
        // Only hit the rate-limited findMyCar if the status carried no GPS. If it
        // then fails (e.g. the daily locate limit) but we already have a fix, keep
        // showing that rather than throwing a scary error.
        val hadCached = _state.value.locations[v.vin] != null
        val loc = statusLoc ?: try {
            repoFor(v).location(v)
        } catch (e: BlueLinkException) {
            if (hadCached) null else throw e
        }
        when {
            loc != null -> {
                _state.update { it.copy(locations = it.locations + (v.vin to loc)) }
                reverseGeocode(loc)?.let { place ->
                    _state.update { it.copy(placeNames = it.placeNames + (v.vin to place)) }
                }
                loadCarWeather(v, force = true)
                persistCache()
            }
            hadCached -> _state.update {
                it.copy(message = "Showing last-known location. A live locate is over today's limit. Try again later.", messageType = "info")
            }
            else -> throw BlueLinkException(
                "Couldn't get the car's location. It may be asleep, out of coverage, or over " +
                    "the daily location-lookup limit. Try again later.",
            )
        }
    }

    /** Turn a lat/lon into a short human-readable place name (e.g. a
     *  neighborhood or city) using the on-device/Play-services Geocoder. Runs
     *  on Dispatchers.IO since Geocoder does blocking network/disk lookups.
     *  Wrapped in runCatching -- geocoding can fail (no network, unsupported
     *  locale, no result for the coordinates) and callers treat a null result
     *  as "just don't show a place name," not an error worth surfacing.
     *
     *  withTimeoutOrNull, not just runCatching: this uses the LEGACY blocking
     *  Geocoder overload (the async listener API needs API 33+, and this needs
     *  to work below that), and that overload has no bound of its own -- a
     *  flaky network can hang it indefinitely. The watch's own reverseGeocode
     *  already guards this exact call the same way; this one didn't, so a hang
     *  here silently meant the address never resolved and every location
     *  display was stuck on its raw-coordinate fallback forever, not just
     *  until the lookup finished. */
    private suspend fun reverseGeocode(loc: GeoLocation): String? = withContext(Dispatchers.IO) {
        kotlinx.coroutines.withTimeoutOrNull(6000) {
            runCatching {
                val results = Geocoder(getApplication(), Locale.getDefault())
                    .getFromLocation(loc.latitude, loc.longitude, 1)
                results?.firstOrNull()?.let { a -> formatPlaceName(a) }
            }.getOrNull()
        }
    }

    // --- Commands (per-action pending + optimistic state flip) -----------
    //
    // Every remote command below (lock/unlock, lights, climate, charging) is a
    // thin one-liner that just calls runCommand with:
    //   - a "vin:action" key so its own pending-spinner / MIN_COMMAND_LOCK_MS
    //     double-tap guard is independent of every other action on the car
    //     (locking doesn't block a simultaneous climate command, etc.),
    //   - a success message logged to AppLog and surfaced as a toast,
    //   - an optional "optimistic" lambda that patches the cached
    //     VehicleStatus immediately (before the network call returns) so the
    //     UI flips state right away instead of waiting out a full round trip
    //     -- null for commands with no simple boolean to flip (lights,
    //     charge-limit), and
    //   - the suspend `block` that actually calls the repository.
    // See runCommand's own doc comment below for exactly how the pending set,
    // the optimistic patch, the statusMutex serialization, and the failure
    // rollback (a follow-up refreshStatus) all fit together.

    /**
     * The endpoint family (EV vs ICE) is chosen from [v.isEv], but the user can
     * mark a car as a plug-in hybrid that the API reports as gas. Honour that so
     * PHEVs use the EV climate/charge endpoints.
     */
    private fun electric(v: Vehicle): Vehicle =
        if (_state.value.hasBattery(v)) v.copy(isEv = true) else v

    // Lock/unlock share the "doors" action key, so a lock command in flight
    // blocks a rapid-fire unlock (and vice versa) rather than letting them
    // race each other through the API. Each optimistically flips
    // VehicleStatus.doorLock the instant the command is accepted.
    fun lock(v: Vehicle) = runCommand(v.vin, "doors", "Locked", { it.copy(doorLock = true) }) { repoFor(v).lock(v) }
    fun unlock(v: Vehicle) = runCommand(v.vin, "doors", "Unlocked", { it.copy(doorLock = false) }) { repoFor(v).unlock(v) }

    // Both share the "hornLights" action key (only one can run at a time) and
    // have no boolean toggle to optimistically flip -- these are momentary
    // actions (the car doesn't have a persistent "lights are flashing" state
    // worth reflecting), so `optimistic` is null and the UI only shows the
    // pending spinner until the command completes.
    fun flashLights(v: Vehicle) = runCommand(v.vin, "hornLights", "Lights flashing", null) { repoFor(v).flashLights(v) }
    fun hornAndLights(v: Vehicle) = runCommand(v.vin, "hornLights", "Horn & lights", null) { repoFor(v).hornAndLights(v) }

    /** Turn climate off; optimistically flips [VehicleStatus.airCtrlOn] to
     *  false so the climate toggle in the UI responds immediately. Also cancels
     *  any pending [ClimateExtendWorker] chain for this car -- otherwise a
     *  scheduled follow-up command from an earlier, longer request could fire
     *  minutes later and silently turn climate back on after the user just
     *  turned it off. */
    fun stopClimate(v: Vehicle) =
        runCommand(v.vin, "climate", "Climate off", { it.copy(airCtrlOn = false) }) {
            com.bloo.bluelink.work.ClimateExtendWorker.cancel(getApplication(), v.vin)
            repoFor(v).stopClimate(v)
        }

    /**
     * Start climate with the given [req] (temp/duration/defrost/seat
     * heating/etc). Shares the "climate" action key with [stopClimate] so
     * starting and stopping can't race each other on the same car.
     *
     * The vendor API caps a single remote-start command's duration at
     * [com.bloo.bluelink.data.CLIMATE_DURATION_RANGE]'s upper bound (10
     * minutes) -- there's no such thing as a car-side "run for 25 minutes"
     * command. A longer [req.durationMinutes] (the "Run time" slider now goes
     * up to [com.bloo.bluelink.data.CLIMATE_EXTENDED_DURATION_RANGE]'s 30) is
     * chained instead: [com.bloo.bluelink.data.climateChunks] splits it into
     * chunks the car CAN run one at a time, this sends the first chunk right
     * now same as ever, and schedules [ClimateExtendWorker] to send each
     * following chunk the moment the one before it elapses -- so from the
     * car's perspective climate just keeps running past what any single
     * command could hold it at.
     */
    fun startClimate(v: Vehicle, req: ClimateRequest) =
        runCommand(v.vin, "climate", "Climate on (${req.tempF}°F)", { it.copy(airCtrlOn = true) }) {
            val chunks = com.bloo.bluelink.data.climateChunks(req.durationMinutes)
            // Unchanged behavior for every request already within the single-
            // command cap: chunks is just [req.durationMinutes] and this is
            // the same call it always was.
            repoFor(v).startClimate(v, req.copy(durationMinutes = chunks.first()))
            val remaining = chunks.drop(1).sum()
            val ctx = getApplication<android.app.Application>()
            if (remaining > 0) {
                com.bloo.bluelink.work.ClimateExtendWorker.schedule(
                    context = ctx,
                    vin = v.vin,
                    remainingMinutes = remaining,
                    tempF = req.tempF,
                    defrost = req.defrost,
                    steeringWheelHeat = req.steeringWheelHeat,
                    seatFrontLeft = req.seatFrontLeft.apiValue,
                    seatFrontRight = req.seatFrontRight.apiValue,
                    seatRearLeft = req.seatRearLeft.apiValue,
                    seatRearRight = req.seatRearRight.apiValue,
                    delayMinutes = chunks.first(),
                )
            } else {
                // This request alone covers everything -- clear any chain a
                // PRIOR, longer-running request might still have pending, so
                // it can't extend climate past what this shorter run intends.
                com.bloo.bluelink.work.ClimateExtendWorker.cancel(ctx, v.vin)
            }
        }

    // startCharge/stopCharge/setChargeLimits all route the vehicle through
    // electric(v) before calling the repository -- unlike the commands above,
    // these hit EV-only endpoints, so a car the user has manually marked as a
    // PHEV (which the API itself may still report as gas/isEv=false) needs
    // isEv forced true here or the call would go to the wrong (ICE) endpoint.

    /**
     * One-tap climate, for surfaces with no room for the full Climate pebble
     * (the flip cover's action bar).
     *
     * Starting climate needs a whole [ClimateRequest]; every other one-tap
     * surface -- widget, Quick Settings tile, watch -- resolves that the same
     * way, from the car's last-saved settings (see
     * [com.bloo.bluelink.data.WearAction.TOGGLE_CLIMATE]). This does the same
     * rather than inventing a second answer, falling back to a plain 72F /
     * 10-minute run only when the car has never had climate configured at all.
     */
    fun toggleClimate(v: Vehicle) {
        if (_state.value.statusFor(v)?.airCtrlOn == true) {
            stopClimate(v)
            return
        }
        viewModelScope.launch {
            val saved = runCatching { loadSavedClimate(v) }.getOrNull()
            startClimate(v, saved ?: ClimateRequest(tempF = 72, defrost = false, durationMinutes = 10))
        }
    }

    /** Begin charging; optimistically sets [VehicleStatus.evStatus]'s
     *  batteryCharge to true (a no-op patch if evStatus is itself null, since
     *  the nested `?.copy` on a null receiver stays null). */
    fun startCharge(v: Vehicle) =
        runCommand(v.vin, "charge", "Charging", { it.copy(evStatus = it.evStatus?.copy(batteryCharge = true)) }) {
            repoFor(v).startCharge(electric(v))
        }

    /** Stop charging; mirrors [startCharge]'s optimistic-patch shape but with
     *  the flag flipped false. Shares the "charge" action key with it. */
    fun stopCharge(v: Vehicle) =
        runCommand(v.vin, "charge", "Charging stopped", { it.copy(evStatus = it.evStatus?.copy(batteryCharge = false)) }) {
            repoFor(v).stopCharge(electric(v))
        }

    /** Set the AC (slow/L2) and DC (fast) charge-target percentages. Its own
     *  "chargeLimit" action key (distinct from "charge") so setting limits
     *  doesn't block a concurrent start/stop-charge tap, and vice versa; no
     *  optimistic patch since VehicleStatus doesn't carry a single field that
     *  maps cleanly onto "the limits are now X/Y" the way charging on/off does. */
    fun setChargeLimits(v: Vehicle, acPercent: Int, dcPercent: Int) =
        runCommand(
            v.vin, "chargeLimit", "Charge limits set (AC $acPercent% / DC $dcPercent%)",
            // Optimistic, like every other command here. The limit isn't just a
            // number in a settings row any more -- it's the seam in the hero's
            // charge bar, the notch in the widget's ring and the watch's, and
            // the Point on the live notification. Waiting for a round-trip and
            // a poll before any of those move makes tapping Set look like it
            // did nothing. Reverted locally by runCommand if the car refuses.
            { st ->
                val ev = st.evStatus
                if (ev == null) {
                    st
                } else {
                    st.copy(
                        evStatus = ev.copy(
                            // Replaced wholesale rather than merged: these two
                            // plug types are the entire list the API reports,
                            // and setChargeTargets always sends both.
                            reservChargeInfos = ReservChargeInfos(
                                listOf(
                                    TargetSOC(plugType = 0, targetSOClevel = dcPercent),
                                    TargetSOC(plugType = 1, targetSOClevel = acPercent),
                                ),
                            ),
                        ),
                    )
                }
            },
        ) {
            repoFor(v).setChargeTargets(electric(v), acPercent, dcPercent)
        }

    /**
     * Runs a command tracking a per-action spinner. On success it logs, shows a
     * message, and optimistically flips the cached status so the toggle updates.
     */
    private fun runCommand(
        vin: String,
        action: String,
        success: String,
        optimistic: ((VehicleStatus) -> VehicleStatus)?,
        block: suspend () -> Unit,
    ) {
        val key = "$vin:$action"
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            _state.update { it.copy(pending = it.pending + key, message = null) }
            // Snapshot the pre-command status so a failed command can be reverted
            // LOCALLY (no network) — see the catch block. Without this, a command
            // that fails offline left the optimistic value ("Locked") persisted to
            // the widget/QS-tile/watch with no way back except a successful poll.
            val prior = _state.value.statuses[vin]
            // Apply the optimistic state and persist it immediately so the widget
            // reflects the expected outcome before the network round-trip completes.
            if (optimistic != null) {
                _state.update { st ->
                    if (st.statuses[vin] != null) {
                        st.copy(statuses = st.statuses + (vin to optimistic(st.statuses.getValue(vin))))
                    } else st
                }
                persistSnapshots()
            }
            try {
                // Serialize with status fetches: Hyundai rejects overlapping
                // requests with "a previous request is pending".
                statusMutex.withLock { block() }
                AppLog.log(success)
                // Confirm the optimistic state (or reapply if it wasn't set above).
                _state.update { st ->
                    val statuses = if (optimistic != null && st.statuses[vin] != null) {
                        st.statuses + (vin to optimistic(st.statuses.getValue(vin)))
                    } else {
                        st.statuses
                    }
                    st.copy(statuses = statuses)
                }
                persistSnapshots()
                // Keep the Quick Settings tiles' state/icon in sync with the car.
                runCatching { com.bloo.bluelink.tiles.BlooTileService.requestUpdates(getApplication()) }
                // Auto-AI: a command changed the car's state, refresh the summary.
                _state.value.vehicles.firstOrNull { it.vin == vin }?.let { autoSummarize(it) }
            } catch (e: Exception) {
                val msg = e.message ?: "Command failed"
                AppLog.log("⚠ $msg")
                _state.update { it.copy(message = msg, messageType = "error") }
                // Revert the optimistic flip LOCALLY first, then re-persist, so every
                // surface (app + widget + QS tile + watch) returns to last-known-good
                // immediately — without depending on a network refresh that will
                // usually fail for the same reason the command did. Guarded on
                // `prior != null` (a null prior means nothing was flipped, since the
                // optimistic patch only applies when statuses[vin] != null; leave state
                // untouched rather than dropping a status a concurrent poll just added).
                if (optimistic != null && prior != null) {
                    _state.update { st -> st.copy(statuses = st.statuses + (vin to prior)) }
                    persistSnapshots()
                }
                // Still schedule a refresh as follow-up reconciliation: `prior` may be
                // slightly stale vs live data, but if the refresh also fails/returns
                // null every surface now sits at last-known-good, not the wrong value.
                viewModelScope.launch {
                    _state.value.vehicles.firstOrNull { it.vin == vin }?.let { refreshStatus(it) }
                }
            } finally {
                // Keep the control locked for at least MIN_COMMAND_LOCK_MS after a
                // command so a quick double-tap can't fire an overlapping request
                // (which Hyundai rejects as "a previous request is pending").
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed < MIN_COMMAND_LOCK_MS) {
                    kotlinx.coroutines.delay(MIN_COMMAND_LOCK_MS - elapsed)
                }
                _state.update { it.copy(pending = it.pending - key) }
            }
        }
    }

    // --- Settings / nav --------------------------------------------------

    /** Navigate to the Settings screen (the car carousel keeps its state
     *  behind it, restored as-is by [closeSettings]). */
    fun openSettings() = _state.update { it.copy(screen = Screen.Settings) }
    fun closeSettings() {
        // Always return to the card/grid view (collapse any expanded car).
        _state.update {
            it.copy(
                screen = if (it.vehicles.isEmpty()) Screen.Empty else Screen.Garage,
                expandedIndex = null,
                showSettingsCoach = false,
            )
        }
    }

    // Appearance/preference setters (setThemeMode through setColorPalette,
    // and again setPebbleOutline/setAuroraBackground/.../setUnitSystem further
    // below): each just writes one field to SettingsStore's DataStore and
    // returns. None of them touch _state directly because `appearance` above
    // is already a StateFlow mirroring settingsStore.appearance -- the UI
    // picks up the change automatically once the DataStore write completes
    // and that Flow re-emits, and the init-block collector separately mirrors
    // the same Flow out to a paired watch. setDynamicColor is the exception
    // that does extra work (see its own comment).
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settingsStore.setThemeMode(mode)
    }
    fun setFontChoice(choice: FontChoice) = viewModelScope.launch { settingsStore.setFontChoice(choice) }
    // Deviates from the group pattern above: turning dynamic (Material You)
    // color on means every car's custom fixed palette id would otherwise sit
    // around unused but still selected -- clear them all so switching back to
    // a fixed palette later doesn't silently resurrect a stale per-car choice
    // that no longer matches what's shown while dynamic color was active.
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setDynamicColor(enabled)
        if (enabled) settingsStore.clearAllCarPaletteIds()
    }
    fun setColorPalette(palette: ColorPalette) = viewModelScope.launch { settingsStore.setColorPalette(palette) }
    fun saveCustomPalette(palette: CustomPaletteData) = viewModelScope.launch { settingsStore.saveCustomPalette(palette) }
    fun deleteCustomPalette(id: String) = viewModelScope.launch { settingsStore.deleteCustomPalette(id) }
    fun setActiveCustomPaletteId(id: String?) = viewModelScope.launch { settingsStore.setActiveCustomPaletteId(id) }
    fun setCarPaletteId(vin: String, paletteId: String?) = viewModelScope.launch { settingsStore.setCarPaletteId(vin, paletteId) }

    /**
     * Share a full settings backup (includes colours and palettes) via the share
     * sheet, as a real file — not raw EXTRA_TEXT, which most file-saving targets
     * (Drive, Files, email attachments) don't accept as a share destination at
     * all, silently limiting "Export" to text-only apps and defeating the whole
     * point of producing something "Restore" can later read back in.
     */
    fun exportSettings(context: android.content.Context) = viewModelScope.launch {
        val json = settingsStore.exportSettingsJson()
        val uri = withContext(Dispatchers.IO) {
            runCatching {
                val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
                val file = java.io.File(dir, "bloo_settings_backup.json")
                file.writeText(json)
                androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrNull()
        }
        if (uri == null) {
            _state.update { it.copy(message = "Couldn't prepare the backup file") }
            return@launch
        }
        runCatching {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Bloo settings backup")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(
                android.content.Intent.createChooser(intent, "Export settings")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            AppLog.log("Settings exported")
        }.onFailure { _state.update { s -> s.copy(message = "Couldn't open the share sheet") } }
    }

    /** Restore a full settings backup from a user-picked JSON file. */
    fun importSettings(context: android.content.Context, uri: android.net.Uri) = viewModelScope.launch {
        val json = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull()
        }
        if (json == null) {
            _state.update { it.copy(message = "Couldn't read that file") }
            AppLog.log("⚠ Settings import: could not read file")
            return@launch
        }
        val error = settingsStore.importSettingsJson(json)
        AppLog.log(if (error == null) "Settings imported from backup" else "⚠ Settings import: $error")
        _state.update { it.copy(message = error ?: "Settings restored", messageType = if (error == null) "success" else "error") }
        if (error == null) {
            // Refresh the already-loaded vehicles' local config (seats, powertrain,
            // photo, ...) so the UI reflects the restore immediately instead of
            // waiting for some unrelated event to trigger a full garage reload.
            refreshLocalCarConfig()
            // Push the restored appearance/preferences down to the watch too --
            // otherwise a manual restore only takes effect on the phone until
            // some unrelated event (a pebble reorder, the next Drive sync) later
            // happens to trigger a watch push.
            com.bloo.bluelink.wear.WearBridge.publishSettings(getApplication(), settingsStore.appearance.first())
        }
    }

    /** Set up auto-sync: store a Drive URI for automatic backup on each refresh. */
    fun setSyncUri(uri: android.net.Uri) = viewModelScope.launch {
        val granted = runCatching {
            getApplication<android.app.Application>().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.isSuccess
        if (!granted) {
            // Without a PERSISTED grant, this session's temporary read/write
            // access from the picker intent works until the process dies, then
            // every sync attempt fails with a SecurityException forever with no
            // obvious fix in sight -- refuse to enable sync at all instead of
            // silently setting up something that's guaranteed to break later.
            AppLog.log("⚠ Drive sync: couldn't get persistent access to that file")
            _state.update { it.copy(message = "Couldn't get lasting access to that file. Try picking it again", messageType = "error") }
            return@launch
        }
        AppLog.log("Drive auto-sync enabled")
        // Reset per-file sync gate state BEFORE pointing at the (possibly new) file:
        // stale hash/synced-ever/lastSync/dirty from a previous file would block
        // adoption of and convergence with this one. This also re-arms join-adopt
        // (synced_ever=false), so if the picked file already has content (e.g. the
        // user pointed "Save to Drive" at an existing Bloo file) this device adopts
        // it; a brand-new empty file has nothing to adopt and just receives our
        // upload — either way correct.
        settingsStore.resetSyncStateForNewFile()
        settingsStore.setSyncUri(uri.toString())
        _state.update { it.copy(syncUri = uri.toString()) }
        // Push this device's settings to the file right away instead of
        // waiting for the next unrelated refresh cycle to complete -- see
        // runDriveSyncNow's doc comment for why that matters.
        runDriveSyncNow()
    }

    /** Disable auto-sync. */
    fun clearSyncUri() = viewModelScope.launch {
        settingsStore.setSyncUri(null)
        // Also drop any stale error (in-memory AND persisted) so re-enabling
        // sync later doesn't briefly show an error from the previous, now-
        // disabled setup before the first new sync attempt completes.
        settingsStore.setLastSyncError(null)
        _state.update { it.copy(syncUri = null, syncError = null) }
        AppLog.log("Drive auto-sync disabled")
    }

    /** Join an existing Drive sync file and set up auto-sync to it.
     *
     * Adoption now happens through [SettingsStore.performDriveSync]'s **join-adopt**
     * path (a device that has never synced THIS file fully adopts it as the source
     * of truth), NOT a separate up-front `importSettingsJson`. That's the actual bug
     * fix: the old explicit import routed through `editTracked`, which marked every
     * imported key dirty, so the very first sync pass then "protected" all of them
     * and the device never converged with the primary. We only need to (1) confirm
     * the file is readable, (2) take a persisted grant, (3) reset per-file gate state
     * so join-adopt arms, then (4) run one pass. */
    fun importSettingsAndSync(context: android.content.Context, uri: android.net.Uri) = viewModelScope.launch {
        // Read once purely to confirm the file is reachable; do NOT import it here.
        val readable = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.isSuccess
        }
        if (!readable) AppLog.log("⚠ Drive sync: couldn't read the picked file (will still try to enable sync)")
        val granted = runCatching {
            getApplication<android.app.Application>().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.isSuccess
        if (!granted) {
            // Without a persisted grant, sync is guaranteed to start failing the
            // moment this process dies, so don't claim auto-sync is enabled.
            AppLog.log("⚠ Drive sync: couldn't get persistent access to that file")
            _state.update {
                it.copy(message = "Couldn't get lasting access to that file. Try picking it again", messageType = "error")
            }
            return@launch
        }
        // Reset per-file gate state so join-adopt arms for this file (synced_ever
        // cleared), then point sync at it.
        settingsStore.resetSyncStateForNewFile()
        settingsStore.setSyncUri(uri.toString())
        _state.update { it.copy(syncUri = uri.toString(), message = "Auto-sync enabled", messageType = "success") }
        // One real pass now: performDriveSync join-adopts the file's settings (if it
        // has any) and uploads. refreshLocalCarConfig() below reflects an adopted
        // import into the already-loaded vehicles (seats/powertrain/photo) right away.
        runDriveSyncNow()
        if (_state.value.syncError == null) refreshLocalCarConfig()
    }

    /** Set Wi-Fi only vs any network for auto-sync. */
    fun setSyncWifiOnly(wifiOnly: Boolean) = viewModelScope.launch {
        AppLog.log("Drive sync: ${if (wifiOnly) "Wi-Fi only" else "any network"}")
        settingsStore.setSyncWifiOnly(wifiOnly)
        _state.update { it.copy(syncWifiOnly = wifiOnly) }
    }

    // --- Weather ---------------------------------------------------------

    /** Un-set the "home" weather location: clears the saved lat/lon/label and
     *  drops any already-fetched reading so the weather pebble hides itself. */
    fun clearWeatherLocation() = viewModelScope.launch {
        settingsStore.setWeatherLocation(null, null, null)
        _state.update { it.copy(homeWeather = null) }
    }

    /** Forward-geocode a place name and save it as the weather location. */
    fun setWeatherPlace(query: String) = viewModelScope.launch {
        val q = query.trim()
        if (q.isBlank()) return@launch
        val hit = withContext(Dispatchers.IO) {
            runCatching {
                Geocoder(getApplication(), Locale.getDefault()).getFromLocationName(q, 1)?.firstOrNull()
            }.getOrNull()
        }
        if (hit == null) {
            _state.update { it.copy(message = "Couldn't find \"$q\"") }
            return@launch
        }
        val label = listOfNotNull(hit.locality ?: hit.subAdminArea, hit.adminArea)
            .distinct().joinToString(", ").ifBlank { q }
        settingsStore.setWeatherLocation(hit.latitude, hit.longitude, label)
        loadHomeWeather(force = true)
    }

    /** Use the device's last-known location as the weather location (needs permission). */
    fun useDeviceLocationForWeather() = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { settingsStore.setWeatherFromDeviceLocation() }
        if (!ok) {
            _state.update { it.copy(message = "No device location available. Try setting a place instead") }
            return@launch
        }
        loadHomeWeather(force = true)
    }

    /** Fetch weather for the configured home location. Skips if a recent reading exists. */
    fun loadHomeWeather(force: Boolean = false) = viewModelScope.launch {
        val appearance = settingsStore.appearance.first()
        val lat = appearance.weatherLat
        val lon = appearance.weatherLon
        if (lat == null || lon == null) {
            _state.update { it.copy(homeWeather = null) }
            return@launch
        }
        val cached = _state.value.homeWeather
        if (!force && cached != null && System.currentTimeMillis() - cached.fetchedAt < WEATHER_TTL_MS) return@launch
        WeatherApi.fetch(lat, lon)?.let { w -> _state.update { it.copy(homeWeather = w) } }
    }

    /** Fetch weather at a car's last-known location, if any. */
    fun loadCarWeather(v: Vehicle, force: Boolean = false) = viewModelScope.launch {
        val loc = _state.value.locations[v.vin] ?: return@launch
        val cached = _state.value.carWeather[v.vin]
        if (!force && cached != null && System.currentTimeMillis() - cached.fetchedAt < WEATHER_TTL_MS) return@launch
        WeatherApi.fetch(loc.latitude, loc.longitude)?.let { w ->
            _state.update { it.copy(carWeather = it.carWeather + (v.vin to w)) }
        }
    }

    /** Swap which dual-column side the "hot spot" pebble lives on. */
    fun setColumnsFlipped(flipped: Boolean) = viewModelScope.launch { settingsStore.setColumnsFlipped(flipped) }
    /** Open in-app links (maps, OEM app store page, etc.) inside a custom
     *  tab instead of handing off to an external browser. */
    fun setLinksInApp(value: Boolean) = viewModelScope.launch { settingsStore.setLinksInApp(value) }

    // Deferred variants for the settings sliders: these two values recompose
    // ~the whole app (colorScheme / LocalDensity), so the commit waits a beat
    // past slider release to let the settle-bounce animation get a clean run.
    // In viewModelScope, not a screen-tied scope, so closing Settings inside
    // that beat can't drop the change.
    fun setUiScaleSoon(value: Float) =
        viewModelScope.launch { settingsStore.setUiScale(value) }
    fun setVibrancySoon(value: Float) =
        viewModelScope.launch { settingsStore.setVibrancy(value) }
    fun setHapticsEnabled(value: Boolean) = viewModelScope.launch { settingsStore.setHapticsEnabled(value) }

    // More of the same appearance-setter pattern described above the
    // setThemeMode group: DataStore write only, UI updates via the
    // `appearance` StateFlow mirror. setAuroraMotion/setAuroraColorMode
    // configure the animated background's speed and how it picks colors
    // (theme-derived vs. a fixed custom one); setAuroraCustomColor supplies
    // that fixed color (or null to fall back to theme-derived).
    fun setPebbleOutline(value: Boolean) = viewModelScope.launch { settingsStore.setPebbleOutline(value) }

    /** Toggle the opt-in Shizuku silent-install path (device-local; see SettingsStore).
     *  Turning it ON prompts for the Shizuku permission immediately — that request is
     *  also what makes Bloo appear in the Shizuku manager's app list (declaring the
     *  provider alone isn't enough). If Shizuku isn't running, guide the user. */
    fun setSeamlessInstallShizuku(value: Boolean) {
        viewModelScope.launch { settingsStore.setSeamlessInstallShizuku(value) }
        if (value) {
            val installer = com.bloo.bluelink.update.ShizukuInstaller
            if (installer.hasPermission()) return
            val queued = installer.requestPermissionOnEnable(SHIZUKU_INSTALL_REQUEST_CODE)
            if (!queued && !installer.isAvailable()) {
                _state.update {
                    it.copy(message = "Start Shizuku, then Bloo can install updates silently.", messageType = "info")
                }
            }
        }
    }

    /** Re-probe Shizuku availability off the main thread (binder ping). Called from
     *  init and on warm resume, so starting Shizuku while the app is open reveals the
     *  "Updates" toggle without needing a cold restart. */
    fun refreshShizukuAvailable() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val avail = com.bloo.bluelink.update.ShizukuInstaller.isAvailable()
            _state.update { it.copy(shizukuAvailable = avail) }
        }
    }

    fun setAuroraBackground(value: Boolean) = viewModelScope.launch { settingsStore.setAuroraBackground(value) }

    fun setAuroraMotion(value: String) = viewModelScope.launch { settingsStore.setAuroraMotion(value) }

    fun setAuroraColorMode(value: String) = viewModelScope.launch { settingsStore.setAuroraColorMode(value) }

    fun setAuroraCustomColor(value: String?) = viewModelScope.launch { settingsStore.setAuroraCustomColor(value) }

    /** Imperial vs. metric display throughout the app. */
    fun setUnitSystem(value: String) = viewModelScope.launch { settingsStore.setUnitSystem(value) }

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
        viewModelScope.launch {
            settingsStore.setSettingsMode(mode)
            // settingsMode isn't part of Appearance, so it isn't covered by the
            // appearance.collect mirror below -- republish explicitly so the
            // watch hides/shows the same advanced-only rows immediately,
            // same as setAiEnabled above.
            com.bloo.bluelink.wear.WearBridge.publishSettings(getApplication(), appearance.value)
        }
    }

    /** Set (or clear, with null) which saved preset the one-tap climate Start
     *  button runs for this car -- read back out in [bootstrapDriveSync]'s
     *  restore step into [UiState.defaultClimatePresets]. */
    fun setDefaultClimatePreset(vin: String, id: String?) = viewModelScope.launch {
        settingsStore.setDefaultClimatePreset(vin, id)
    }

    /** Manual "Sync now": force a full Drive push/pull right now. Available
     *  whenever sync is configured (not just after a failure) so the user can
     *  deliberately trigger a sync without waiting for a refresh or the 2h
     *  worker tick. Surfaces the outcome as a snackbar. */
    fun syncNow() {
        if (_state.value.syncUri == null) return
        viewModelScope.launch {
            runDriveSyncNow()
            val err = _state.value.syncError
            if (err == null) reportInfo("Synced with Drive") else reportError("Sync failed: $err")
        }
    }

    /** Kept for the existing "Sync now" retry affordance shown next to a sync
     *  error; delegates to the same path as [syncNow] without the snackbar. */
    fun retryDriveSync() {
        viewModelScope.launch { runDriveSyncNow() }
    }

    /** Designate [id] as the primary device (source of truth + tiebreaker). Persists
     *  locally and writes it into the Drive file on the sync pass that follows, so
     *  the choice propagates to every other device. */
    fun setPrimaryDevice(id: String) {
        viewModelScope.launch {
            settingsStore.setPrimaryDevice(id)
            _state.update { it.copy(syncPrimaryId = id) }
            runDriveSyncNow()
            // Push settings to the watch so its read-only devices summary reflects
            // the new primary immediately — same as pebble/AI/aurora changes do.
            // (setPrimaryDevice writes via the raw DataStore, so it doesn't trip the
            // dirty-key auto-push, and neither performDriveSync nor runDriveSyncNow
            // republishes to the watch — without this the watch shows the old primary
            // until some unrelated publish.)
            runCatching { com.bloo.bluelink.wear.WearBridge.publishSettings(getApplication(), appearance.value) }
        }
    }

    /** "Pull from primary now": force this device to fully adopt the file's settings
     *  on the next pass (the primary is the source of truth), then run it. */
    fun pullFromPrimary() {
        if (_state.value.syncUri == null) return
        viewModelScope.launch {
            settingsStore.requestPullFromPrimary()
            runDriveSyncNow()
            val err = _state.value.syncError
            if (err == null) reportInfo("Pulled the latest settings") else reportError("Couldn't pull: $err")
        }
    }

    /** Rename THIS device in the sync registry. Persists locally and republishes on
     *  the next sync pass (name changes ride the registry heartbeat). */
    fun renameThisDevice(name: String) {
        viewModelScope.launch {
            settingsStore.setSyncDeviceName(name)
            _state.update { it.copy(syncDeviceName = name.trim()) }
            runDriveSyncNow()
            // Republish so the watch's devices summary shows the new name at once
            // (same gap/fix as setPrimaryDevice — device name isn't dirty-tracked).
            runCatching { com.bloo.bluelink.wear.WearBridge.publishSettings(getApplication(), appearance.value) }
        }
    }

    /** Settings "Test sync" diagnostic: runs a non-destructive end-to-end
     *  round-trip against the real Drive file (permission → read → write →
     *  verify) and reports pass/fail as a snackbar, so the user can confirm
     *  sync actually works on their device/provider in one tap. Writes the
     *  file's own bytes back verbatim, so no settings are changed. */
    fun testSync() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { settingsStore.testSyncRoundTrip() }
            if (result.ok) reportInfo(result.message) else reportError(result.message)
        }
    }

    /** Runs one [SettingsStore.performDriveSync] pass right now and folds the
     *  outcome into [UiState]. Both [setSyncUri] and [importSettingsAndSync]
     *  used to just flip the syncUri pref and wait for the passive
     *  refreshing-transition collector in [bootstrapDriveSync] to notice --
     *  which meant "enable sync" didn't actually upload or download anything
     *  until the next unrelated data refresh happened to complete, sometimes
     *  never in the session (e.g. backgrounding right after setup). That's
     *  exactly why a second device picking the same file moments later found
     *  nothing real there yet. Calling this immediately after either flow
     *  makes "enable sync" actually push/pull data right away. */
    private suspend fun runDriveSyncNow() {
        val outcome = withContext(Dispatchers.IO) { settingsStore.performDriveSync() }
        // Recompute the file fingerprint each pass so it appears the moment sync is
        // set up / the file is changed (it's derived purely from the persisted URI).
        val fingerprint = withContext(Dispatchers.IO) { settingsStore.syncFileFingerprint() }
        if (outcome.ran) {
            if (outcome.imported) refreshLocalCarConfig()
            _state.update {
                it.copy(
                    lastSyncMs = outcome.syncedAtMs,
                    syncError = outcome.error,
                    // On a transient download failure the outcome carries an empty
                    // device list (nothing could be read this pass) — don't blank the
                    // Settings "Synced devices" list; keep whatever we last showed.
                    syncDevices = outcome.devices.ifEmpty { it.syncDevices },
                    syncPrimaryId = outcome.primaryDeviceId ?: it.syncPrimaryId,
                    thisDeviceId = outcome.selfDeviceId ?: it.thisDeviceId,
                    syncFileFingerprint = fingerprint,
                )
            }
        } else {
            _state.update { it.copy(syncFileFingerprint = fingerprint) }
        }
    }

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
    private fun launchBusy(block: suspend () -> Unit) {
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
