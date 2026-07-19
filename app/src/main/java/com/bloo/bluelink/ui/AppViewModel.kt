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
import com.bloo.bluelink.data.CarAlerts
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.Notifications
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.Credentials
import com.bloo.bluelink.data.KiaAuth
import com.bloo.bluelink.data.KiaRepository
import com.bloo.bluelink.data.VehicleRepository
import com.bloo.bluelink.data.links
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.maskEmail
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
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.VehicleStatus
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
    val message: String? = null,
    /** "error" (default), "success", or "info" — controls snackbar colour. */
    val messageType: String = "error",
    /** A newer CI build than what's installed, if the update checker found one
     *  and it hasn't been dismissed this session or snoozed. */
    val updateInfo: com.bloo.bluelink.update.UpdateInfo? = null,
    /** Same "a newer build exists" fact as [updateInfo], but NOT cleared by
     *  dismissing/snoozing the popup -- only by a later check finding the app
     *  is current, or the user turning update checks off. Drives the hero
     *  pebble's persistent "update available" banner, so dismissing the
     *  one-time popup doesn't also lose every other way back to it. */
    val updateAvailable: com.bloo.bluelink.update.UpdateInfo? = null,
    /** True while a manual "Check now" request is in flight. */
    val updateChecking: Boolean = false,
    /** Non-null when the last update check failed (network/API error). */
    val updateCheckFailed: String? = null,
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

    /** VINs with a status request currently queued or running (de-dupes). */
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

    private val updateStore = com.bloo.bluelink.data.UpdateStore(app)

    val updateChecksEnabled: StateFlow<Boolean> =
        updateStore.checksEnabled.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private suspend fun checkAlerts(v: Vehicle, status: VehicleStatus) {
        val alerts = CarAlerts.evaluate(settingsStore, v, status)
        alerts.forEach { Notifications.post(getApplication(), it.id, it.title, it.text, it.actions) }
        alerts.firstOrNull()?.let { a -> _state.update { it.copy(message = a.text) } }
    }

    fun setNotifyService(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyService(v) }
    fun setNotifyDoor(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyDoor(v) }
    fun setDoorOpenMinutes(m: Int) = viewModelScope.launch { settingsStore.setDoorOpenMinutes(m) }
    fun setNotifyRunning(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyRunning(v) }
    fun setRunningMinutes(m: Int) = viewModelScope.launch { settingsStore.setRunningMinutes(m) }

    /** Write the current live status/location maps to disk (survives restart). */
    private fun persistCache() {
        val s = _state.value
        viewModelScope.launch {
            statusCache.save(s.statuses, s.locations, s.placeNames, s.lastFetched)
        }
    }

    init {
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
        // Bloo isn't on the Play Store, so check its own GitHub Actions build
        // channel once per cold start (debounced internally — see UpdateChecker).
        viewModelScope.launch {
            when (val result = com.bloo.bluelink.update.UpdateChecker.checkPhone(getApplication())) {
                is com.bloo.bluelink.update.UpdateCheckResult.Available ->
                    _state.update { it.copy(updateInfo = result.info, updateAvailable = result.info) }
                is com.bloo.bluelink.update.UpdateCheckResult.Failed ->
                    _state.update { it.copy(updateCheckFailed = result.error) }
                is com.bloo.bluelink.update.UpdateCheckResult.UpToDate ->
                    _state.update { it.copy(updateAvailable = null) }
            }
        }
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
        viewModelScope.launch {
            val brands = store.loggedInBrands()
            if (brands.isEmpty()) return@launch
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

    /** Sign in (or add another account). Multiple brands can be active at once. */
    fun login(username: String, password: String, pin: String, brand: Brand) {
        if (username.isBlank() || password.isBlank() || (pin.isBlank() && !brand.usesOtpLogin)) {
            _state.update { it.copy(message = "Email, password and PIN are all required") }
            return
        }
        if (brand == Brand.KIA) {
            loginKia(username.trim(), password, pin.trim())
            return
        }
        launchBusy {
            (repoFor(brand) as BlueLinkRepository).login(username.trim(), password, pin.trim())
            credentialStore.save(Credentials(username.trim(), password, pin.trim(), brand))
            AppLog.log("Signed in as ${maskEmail(username.trim())} (${brand.label})")
            _state.update { it.copy(accounts = credentialStore.loadAll(), addingAccount = false) }
            loadGarageInternal()
        }
    }

    // Kia sign-in is a two-step dance: password first, then (usually) a
    // one-time code sent to the account's email or phone. The credentials are
    // held here between the steps and only persisted once fully signed in.
    private var kiaPending: Credentials? = null

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
            _state.update { it.copy(message = "Enter the code you received") }
            return
        }
        launchBusy {
            kiaRepo().verifyOtp(creds.email, creds.password, creds.pin, code.trim(), otp.challenge)
            kiaPending = null
            _state.update { it.copy(kiaOtp = null) }
            finishKiaLogin(creds)
        }
    }

    fun kiaCancelOtp() {
        kiaPending = null
        _state.update { it.copy(kiaOtp = null) }
    }

    private suspend fun finishKiaLogin(creds: Credentials) {
        credentialStore.save(creds)
        AppLog.log("Signed in as ${maskEmail(creds.email)} (Kia)")
        _state.update { it.copy(accounts = credentialStore.loadAll(), addingAccount = false) }
        loadGarageInternal()
    }

    fun beginAddAccount() = _state.update { it.copy(addingAccount = true) }
    fun cancelAddAccount() = _state.update { s ->
        // If the user arrived here by backing out of the biometric prompt, "Cancel"
        // must re-lock the app — not silently return them to the already-loaded garage.
        if (s.lockedToLogin) s.copy(addingAccount = false, locked = true, lockedToLogin = false)
        else s.copy(addingAccount = false)
    }

    fun logout(brand: Brand) {
        viewModelScope.launch {
            runCatching { repoFor(brand).logout() }
            credentialStore.clear(brand)
            repos.remove(brand)
            AppLog.log("Signed out of ${brand.label}")
            val remaining = credentialStore.loadAll()
            if (remaining.isEmpty()) {
                _state.value = UiState(screen = Screen.Login)
            } else {
                _state.update { it.copy(accounts = remaining) }
                loadGarage()
            }
        }
    }

    /** Fix a wrong/locked service PIN without re-entering the whole account. */
    fun updatePin(brand: Brand, pin: String) {
        if (pin.isBlank()) return
        viewModelScope.launch {
            store.updatePin(brand, pin.trim())
            credentialStore.updatePin(brand, pin.trim())
            _state.update { it.copy(accounts = credentialStore.loadAll(), message = "PIN updated for ${brand.label}") }
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
        if (backgroundedAtMs > 0 && System.currentTimeMillis() - backgroundedAtMs > 10 * 60 * 1000L) {
            val anyStale = _state.value.lastFetched.values.any { System.currentTimeMillis() - it > 10 * 60 * 1000L }
            if (anyStale) reportInfo("Data may be stale — pull down to refresh")
        }
    }

    fun canUseBiometrics(): Boolean =
        BiometricManager.from(getApplication())
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setBiometricLock(enabled) }
    }

    // --- Garage / vehicles ----------------------------------------------

    fun loadGarage() = launchBusy { loadGarageInternal() }

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
                    lastError = "Lost access to the Drive file — set up sync again"
                    settingsStore.setLastSyncError(lastError)
                }
            }
            val wifiOnly = settingsStore.syncWifiOnly()
            val settingsMode = settingsStore.settingsMode()
            val vehicles = _state.value.vehicles
            val defaultPresets = vehicles.associate { v ->
                v.vin to (settingsStore.defaultClimatePreset(v.vin) ?: "smart")
            }
            _state.update { it.copy(syncUri = uri, lastSyncMs = lastSync, syncError = lastError, syncWifiOnly = wifiOnly, settingsMode = settingsMode, defaultClimatePresets = defaultPresets) }
        }
        // Bidirectional auto-sync on refresh: download newer settings from Drive,
        // then upload our current settings (merge loop for cross-device sync).
        // The actual download/compare/import/upload sequence lives in
        // SettingsStore.performDriveSync() — shared with the watch's on-demand
        // "Sync now" request (WearBridge.driveSync) so there's exactly one
        // implementation of that logic.
        viewModelScope.launch {
            _state.map { it.refreshing }.distinctUntilChanged().collect { wasRefreshing ->
                if (!wasRefreshing) {
                    val outcome = withContext(Dispatchers.IO) { settingsStore.performDriveSync() }
                    if (outcome.ran) {
                        _state.update { it.copy(lastSyncMs = outcome.syncedAtMs, syncError = outcome.error) }
                    }
                }
            }
        }
    }

    /**
     * Switch the visible car (swipe). Updates the index, and lazily loads this
     * car's status only if we don't already have it — so already-loaded cars are
     * never re-fetched on a swipe, but a car that failed to load at startup gets
     * another chance when you view it.
     */
    fun selectIndex(index: Int) {
        val v = _state.value.vehicles.getOrNull(index) ?: return
        _state.update { it.copy(currentIndex = index) }
        viewModelScope.launch { settingsStore.setLastVehicleVin(v.vin) }
        ensureStatus(v)
    }

    fun expand(index: Int) = _state.update { it.copy(expandedIndex = index, currentIndex = index) }
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
        val status = _state.value.statusFor(v)
        when (cmd) {
            // Toggles: do the opposite of the last-known state.
            "doors" -> if (status?.doorLock == true) unlock(v) else lock(v)
            "climate" -> if (status?.airCtrlOn == true) stopClimate(v) else {
                startClimate(v, ClimateRequest(tempF = 72, defrost = false, durationMinutes = 10))
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

    fun refreshStatus(v: Vehicle) =
        loadStatus(
            v, refresh = true, errorMessage = "Refresh failed",
            logSuccess = "Status refreshed for ${v.name}", surfaceErrors = true,
        )

    /**
     * Fetches one car's status. All fetches funnel through [statusMutex] so they
     * run strictly sequentially (Blue Link 502s on overlapping requests), and a
     * VIN already queued/running is skipped so we never pile up duplicates.
     */
    private fun loadStatus(
        v: Vehicle,
        refresh: Boolean,
        errorMessage: String,
        logSuccess: String? = null,
        surfaceErrors: Boolean = true,
    ) {
        synchronized(statusInFlight) {
            if (!statusInFlight.add(v.vin)) return
            if (surfaceErrors) surfaceInFlight.add(v.vin)
        }
        // Only show the spinner/settle-haptic for user-triggered refreshes; silent
        // background fetches (ensureStatus) run without touching refreshing so the
        // UI stays still and no settle haptic fires when they complete.
        if (surfaceErrors) _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                statusMutex.withLock {
                    repoFor(v).status(v, refresh = refresh)?.let { s ->
                        // The status payload carries last-known GPS for free — use
                        // it so the map/location works without the rate-limited
                        // findMyCar call (this is what the official app does).
                        val statusLoc = s.vehicleLocation?.coord?.let { c ->
                            val lat = c.lat
                            val lon = c.lon
                            if (lat != null && lon != null) {
                                GeoLocation(lat, lon, s.vehicleLocation?.speed?.value)
                            } else null
                        }
                        _state.update { st ->
                            st.copy(
                                statuses = st.statuses + (v.vin to s),
                                lastFetched = st.lastFetched + (v.vin to System.currentTimeMillis()),
                                locations = if (statusLoc != null) {
                                    st.locations + (v.vin to statusLoc)
                                } else st.locations,
                            )
                        }
                        persistSnapshots()
                        persistCache()
                        checkAlerts(v, s)
                        // Auto-AI: refresh the summary off the new data if enabled.
                        autoSummarize(v)
                        statusLoc?.let { loc ->
                            reverseGeocode(loc)?.let { place ->
                                _state.update { it.copy(placeNames = it.placeNames + (v.vin to place)) }
                            }
                        }
                    }
                }
                sessionFetched.add(v.vin)
                logSuccess?.let { AppLog.log(it) }
            } catch (e: Exception) {
                val msg = e.message ?: errorMessage
                AppLog.log("⚠ ${v.name}: $msg")
                if (surfaceErrors) _state.update { it.copy(message = "${v.name}: $msg") }
            } finally {
                // Clear refreshing only when no more user-visible (surfaceErrors) fetches remain.
                // Background fetches finishing after a user refresh must not prematurely clear
                // the spinner or trigger the settle haptic.
                val noMoreSurface = synchronized(statusInFlight) {
                    statusInFlight.remove(v.vin)
                    surfaceInFlight.remove(v.vin)
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
        // Refresh any home-screen widgets so their status reflects the new data.
        runCatching { com.bloo.bluelink.widget.BlooWidget().updateAll(getApplication()) }
        // Refresh Quick Settings tiles too.
        com.bloo.bluelink.tiles.BlooTileService.requestUpdates(getApplication())
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
            updated = status?.dateTime,
            // A non-null status is freshly-fetched data; null means we're building a
            // placeholder snapshot with no live status yet (leave fetchedAt unknown).
            fetchedAt = if (status != null) System.currentTimeMillis() else 0L,
            odometer = v.odometer,
            licensePlate = _state.value.licensePlates[v.vin],
            lastServiceMiles = _state.value.lastServiceMiles[v.vin],
            serviceIntervalMiles = _state.value.serviceIntervalMiles[v.vin],
        )
    }

    private fun applyOrder(vehicles: List<Vehicle>, order: List<String>): List<Vehicle> {
        if (order.isEmpty()) return vehicles
        val byVin = vehicles.associateBy { it.vin }
        val ordered = order.mapNotNull { byVin[it] }
        val rest = vehicles.filter { it.vin !in order }
        return ordered + rest
    }

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

    fun dismissSettingsHint() = _state.update { it.copy(showSettingsHint = false) }

    fun dismissSettingsCoach() = _state.update { it.copy(showSettingsCoach = false) }

    fun setPowertrain(v: Vehicle, value: Powertrain) {
        _state.update { it.copy(powertrains = it.powertrains + (v.vin to value)) }
        // Republish immediately: the watch's Charge/Fuel tile derives hasBattery
        // from the synced snapshot, so a correction here needs to reach it right
        // away rather than waiting on the next status refresh.
        viewModelScope.launch { settingsStore.setPowertrain(v.vin, value); persistSnapshots() }
    }

    // --- App self-update (GitHub Actions builds; Bloo isn't on the Play Store) ---

    /** "Not now": the checker only ever runs once per cold start anyway (its
     *  own debounce), so just clearing the in-memory prompt is enough - no
     *  persisted state needed. updateAvailable is left alone so the hero
     *  pebble's banner still offers a way back to the update. */
    fun dismissUpdate() = _state.update { it.copy(updateInfo = null, updateCheckFailed = null) }

    /** "Remind me in a few days": persists a snooze that outlasts the checker's
     *  normal debounce window too. */
    fun snoozeUpdate() {
        _state.update { it.copy(updateInfo = null, updateCheckFailed = null) }
        viewModelScope.launch { com.bloo.bluelink.update.UpdateChecker.snooze(getApplication()) }
    }

    /** Re-opens the update popup from the hero pebble's persistent banner. */
    fun reopenUpdatePrompt() = _state.update { it.copy(updateInfo = it.updateAvailable) }

    fun setUpdateChecksEnabled(enabled: Boolean) {
        viewModelScope.launch { updateStore.setChecksEnabled(enabled) }
        if (!enabled) _state.update { it.copy(updateInfo = null, updateCheckFailed = null, updateAvailable = null) }
    }

    /** Manual "Check now": ignores the debounce/snooze/enabled gates (force), and
     *  reports the outcome - the prompt appears if there's a newer build, else a
     *  brief "you're up to date" message. */
    fun checkForUpdatesNow() {
        if (_state.value.updateChecking) return
        _state.update { it.copy(updateChecking = true, updateCheckFailed = null) }
        viewModelScope.launch {
            val result = com.bloo.bluelink.update.UpdateChecker.checkPhone(getApplication(), force = true)
            _state.update {
                when (result) {
                    is com.bloo.bluelink.update.UpdateCheckResult.Available ->
                        it.copy(updateChecking = false, updateInfo = result.info, updateAvailable = result.info, updateCheckFailed = null)
                    is com.bloo.bluelink.update.UpdateCheckResult.UpToDate ->
                        it.copy(updateChecking = false, updateAvailable = null, message = "You're on the latest build.", messageType = "success")
                    is com.bloo.bluelink.update.UpdateCheckResult.Failed ->
                        it.copy(updateChecking = false, updateCheckFailed = result.error)
                }
            }
        }
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

    /** Summarize a car's last-fetched status with on-device Gemini Nano. */
    fun summarizeCar(v: Vehicle) {
        if (v.vin in _state.value.aiBusy) return
        val status = _state.value.statusFor(v) ?: run {
            _state.update { it.copy(message = "Refresh ${v.name} first, then summarize.") }
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
                        AppLog.log("⚠ AI summary: ${e.message}")
                        st.copy(aiBusy = st.aiBusy - v.vin, message = "AI summary failed: ${e.message ?: "unknown error"}")
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
            }.getOrNull()
            _state.update { it.copy(aiBusy = it.aiBusy - "search", aiSearchReply = reply) }
        }
    }

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

    fun setTileBackground(value: Boolean) {
        _state.update { it.copy(tileBackground = value) }
        viewModelScope.launch {
            settingsStore.setTileBackground(value)
            com.bloo.bluelink.tiles.BlooTileService.requestUpdates(getApplication())
        }
    }

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
            val fetched = runCatching { repoFor(v).trips(v) }
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

    fun deleteClimatePreset(v: Vehicle, id: String) {
        _state.update {
            val updated = it.climatePresets[v.vin].orEmpty().filter { p -> p.id != id }
            it.copy(climatePresets = it.climatePresets + (v.vin to updated))
        }
        viewModelScope.launch { settingsStore.deleteClimatePreset(v.vin, id) }
    }

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
        val cs = com.bloo.bluelink.data.ClimateSync(
            activePresetId = presetId,
            tempF = req.tempF,
            durationMinutes = req.durationMinutes,
            defrost = req.defrost,
            steering = req.steeringWheelHeat,
            seatFrontLeft = req.seatFrontLeft.apiValue,
            seatFrontRight = req.seatFrontRight.apiValue,
            seatRearLeft = req.seatRearLeft.apiValue,
            seatRearRight = req.seatRearRight.apiValue,
        )
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
            _state.update { it.copy(statuses = it.statuses + (v.vin to st)) }
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
                it.copy(message = "Showing last-known location — a live locate is over today's limit. Try again later.")
            }
            else -> throw BlueLinkException(
                "Couldn't get the car's location — it may be asleep, out of coverage, or over " +
                    "the daily location-lookup limit. Try again later.",
            )
        }
    }

    private suspend fun reverseGeocode(loc: GeoLocation): String? = withContext(Dispatchers.IO) {
        runCatching {
            val results = Geocoder(getApplication(), Locale.getDefault())
                .getFromLocation(loc.latitude, loc.longitude, 1)
            results?.firstOrNull()?.let { a ->
                listOfNotNull(a.locality ?: a.subAdminArea, a.adminArea)
                    .distinct()
                    .joinToString(", ")
                    .ifBlank { a.getAddressLine(0) }
            }
        }.getOrNull()
    }

    // --- Commands (per-action pending + optimistic state flip) -----------

    /**
     * The endpoint family (EV vs ICE) is chosen from [v.isEv], but the user can
     * mark a car as a plug-in hybrid that the API reports as gas. Honour that so
     * PHEVs use the EV climate/charge endpoints.
     */
    private fun electric(v: Vehicle): Vehicle =
        if (_state.value.hasBattery(v)) v.copy(isEv = true) else v

    fun lock(v: Vehicle) = runCommand(v.vin, "doors", "Locked", { it.copy(doorLock = true) }) { repoFor(v).lock(v) }
    fun unlock(v: Vehicle) = runCommand(v.vin, "doors", "Unlocked", { it.copy(doorLock = false) }) { repoFor(v).unlock(v) }

    fun stopClimate(v: Vehicle) =
        runCommand(v.vin, "climate", "Climate off", { it.copy(airCtrlOn = false) }) { repoFor(v).stopClimate(v) }

    fun startClimate(v: Vehicle, req: ClimateRequest) =
        runCommand(v.vin, "climate", "Climate on (${req.tempF}°F)", { it.copy(airCtrlOn = true) }) {
            repoFor(v).startClimate(v, req)
        }

    fun startCharge(v: Vehicle) =
        runCommand(v.vin, "charge", "Charging", { it.copy(evStatus = it.evStatus?.copy(batteryCharge = true)) }) {
            repoFor(v).startCharge(electric(v))
        }

    fun stopCharge(v: Vehicle) =
        runCommand(v.vin, "charge", "Charging stopped", { it.copy(evStatus = it.evStatus?.copy(batteryCharge = false)) }) {
            repoFor(v).stopCharge(electric(v))
        }

    fun setChargeLimits(v: Vehicle, acPercent: Int, dcPercent: Int) =
        runCommand(v.vin, "chargeLimit", "Charge limits set (AC $acPercent% / DC $dcPercent%)", null) {
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
                _state.update { it.copy(message = msg) }
                // Revert the optimistic state on failure by scheduling a fresh refresh.
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

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settingsStore.setThemeMode(mode)
        runCatching { com.bloo.bluelink.widget.BlooWidget().updateAll(getApplication()) }
    }
    fun setFontChoice(choice: FontChoice) = viewModelScope.launch { settingsStore.setFontChoice(choice) }
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
        // Push the restored appearance/preferences down to the watch too --
        // otherwise a manual restore only takes effect on the phone until
        // some unrelated event (a pebble reorder, the next Drive sync) later
        // happens to trigger a watch push.
        if (error == null) {
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
            _state.update { it.copy(message = "Couldn't get lasting access to that file — try picking it again") }
            return@launch
        }
        AppLog.log("Drive auto-sync enabled")
        settingsStore.setSyncUri(uri.toString())
        _state.update { it.copy(syncUri = uri.toString()) }
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

    /** Import settings from a Drive file and set up auto-sync to that file. */
    fun importSettingsAndSync(context: android.content.Context, uri: android.net.Uri) = viewModelScope.launch {
        val json = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull()
        }
        // importSettingsJson returns null on success, or an error message on a
        // rejected/corrupt backup (wrong format, newer version) -- surface that
        // instead of reporting "imported" for a file that was actually rejected.
        val importError = json?.let { settingsStore.importSettingsJson(it) }
        if (json != null && importError == null) AppLog.log("Settings imported from Drive")
        else if (importError != null) AppLog.log("⚠ Settings import from Drive: $importError")
        val granted = runCatching {
            getApplication<android.app.Application>().contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.isSuccess
        if (!granted) {
            // Same reasoning as setSyncUri: without a persisted grant, sync is
            // guaranteed to start failing the moment this process dies, so
            // don't claim auto-sync is enabled -- but the import itself (if it
            // succeeded) already landed, so still report that part honestly.
            AppLog.log("⚠ Drive sync: couldn't get persistent access to that file")
            _state.update {
                it.copy(message = when {
                    json == null -> "Couldn't get lasting access to that file — try picking it again"
                    importError != null -> "Couldn't import ($importError), and couldn't get lasting access to that file"
                    else -> "Settings imported, but couldn't set up auto-sync — try picking the file again"
                })
            }
            return@launch
        }
        settingsStore.setSyncUri(uri.toString())
        val message = when {
            json == null -> "Auto-sync enabled"
            importError != null -> "Auto-sync enabled, but couldn't import: $importError"
            else -> "Settings imported and auto-sync enabled"
        }
        _state.update { it.copy(syncUri = uri.toString(), message = message) }
    }

    /** Set Wi-Fi only vs any network for auto-sync. */
    fun setSyncWifiOnly(wifiOnly: Boolean) = viewModelScope.launch {
        AppLog.log("Drive sync: ${if (wifiOnly) "Wi-Fi only" else "any network"}")
        settingsStore.setSyncWifiOnly(wifiOnly)
        _state.update { it.copy(syncWifiOnly = wifiOnly) }
    }

    // --- Weather ---------------------------------------------------------

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
            _state.update { it.copy(message = "No device location available — try setting a place instead") }
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

    fun setColumnsFlipped(flipped: Boolean) = viewModelScope.launch { settingsStore.setColumnsFlipped(flipped) }
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

    fun setAuroraBackground(value: Boolean) = viewModelScope.launch { settingsStore.setAuroraBackground(value) }

    fun setAuroraMotion(value: String) = viewModelScope.launch { settingsStore.setAuroraMotion(value) }

    fun setAuroraColorMode(value: String) = viewModelScope.launch { settingsStore.setAuroraColorMode(value) }

    fun setAuroraCustomColor(value: String?) = viewModelScope.launch { settingsStore.setAuroraCustomColor(value) }

    fun setUnitSystem(value: String) = viewModelScope.launch { settingsStore.setUnitSystem(value) }

    fun clearLogs() = AppLog.clear()
    fun clearMessage() = _state.update { it.copy(message = null) }

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

    fun setDefaultClimatePreset(vin: String, id: String?) = viewModelScope.launch {
        settingsStore.setDefaultClimatePreset(vin, id)
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            try {
                block()
            } catch (e: Exception) {
                val msg = e.message ?: "Something went wrong"
                AppLog.log("⚠ $msg")
                _state.update { it.copy(message = msg) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
