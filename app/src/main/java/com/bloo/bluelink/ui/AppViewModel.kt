package com.bloo.bluelink.ui

import android.app.Application
import android.location.Geocoder
import androidx.biometric.BiometricManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.BlueLinkApi
import com.bloo.bluelink.data.BlueLinkException
import com.bloo.bluelink.data.BlueLinkRepository
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.CarAlerts
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.Notifications
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.Credentials
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.StatusCache
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.DEFAULT_SECTIONS
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.VehicleStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

sealed interface Screen {
    data object Login : Screen
    data object Locked : Screen
    /** No vehicles enrolled (or still loading the first time). */
    data object Empty : Screen
    /** First-run welcome that funnels the user into Settings before the app. */
    data object Onboarding : Screen
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
    val seatConfigs: Map<String, SeatConfig> = emptyMap(),
    val powertrains: Map<String, Powertrain> = emptyMap(),
    val sectionOrders: Map<String, List<String>> = emptyMap(),
    val imageUrls: Map<String, String> = emptyMap(),
    val placeNames: Map<String, String> = emptyMap(),
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
    val tileConfigs: List<Pair<String, String>?> = List(4) { null },
    /** Quick tiles run the command in the background (vs opening the app). */
    val tileBackground: Boolean = false,
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
    /** All signed-in accounts (one per brand). */
    val accounts: List<Credentials> = emptyList(),
    /** Showing the login form to add another account while already signed in. */
    val addingAccount: Boolean = false,
    val message: String? = null,
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

    /** Powertrain label for the header. */
    fun powertrainLabel(v: Vehicle): String = when (powertrainOf(v)) {
        Powertrain.GAS -> "Gas"
        Powertrain.HYBRID -> "Hybrid"
        Powertrain.PHEV -> "PHEV"
        Powertrain.EV -> "EV"
    }
}

/** Minimum time a command control stays locked after firing, to block double-taps. */
private const val MIN_COMMAND_LOCK_MS = 3000L

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SessionStore(app)
    private val settingsStore = SettingsStore(app)
    private val credentialStore = CredentialStore(app)
    private val snapshotStore = SnapshotStore(app)
    private val statusCache = StatusCache(app)
    private val ai = com.bloo.bluelink.data.Ai(app)
    // One repository per signed-in brand (Hyundai/Genesis can both be active).
    private val repos = mutableMapOf<Brand, BlueLinkRepository>()

    private fun repoFor(brand: Brand): BlueLinkRepository =
        repos.getOrPut(brand) { BlueLinkRepository(BlueLinkApi(brand), store, brand) }

    private fun brandOf(v: Vehicle): Brand =
        Brand.fromIndicator(v.brandIndicator)

    private fun repoFor(v: Vehicle): BlueLinkRepository = repoFor(brandOf(v))

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

    /** VINs fetched from the network this session (cache restore doesn't count). */
    private val sessionFetched = java.util.Collections.synchronizedSet(mutableSetOf<String>())

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

    private suspend fun checkAlerts(v: Vehicle, status: VehicleStatus) {
        val alerts = CarAlerts.evaluate(settingsStore, v, status)
        alerts.forEach { Notifications.post(getApplication(), it.id, it.title, it.text) }
        alerts.firstOrNull()?.let { a -> _state.update { it.copy(message = a.text) } }
    }

    fun setNotifyService(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyService(v) }
    fun setNotifyDoor(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyDoor(v) }
    fun setDoorOpenMinutes(m: Int) = viewModelScope.launch { settingsStore.setDoorOpenMinutes(m) }

    /** Write the current live status/location maps to disk (survives restart). */
    private fun persistCache() {
        val s = _state.value
        viewModelScope.launch {
            statusCache.save(s.statuses, s.locations, s.placeNames, s.lastFetched)
        }
    }

    init {
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

    /** Sign in (or add another account). Both brands can be active at once. */
    fun login(username: String, password: String, pin: String, brand: Brand) {
        if (username.isBlank() || password.isBlank() || pin.isBlank()) {
            _state.update { it.copy(message = "Email, password and PIN are all required") }
            return
        }
        launchBusy {
            repoFor(brand).login(username.trim(), password, pin.trim())
            credentialStore.save(Credentials(username.trim(), password, pin.trim(), brand))
            AppLog.log("Signed in as ${username.trim()} (${brand.label})")
            _state.update { it.copy(accounts = credentialStore.loadAll(), addingAccount = false) }
            loadGarageInternal()
        }
    }

    fun beginAddAccount() = _state.update { it.copy(addingAccount = true) }
    fun cancelAddAccount() = _state.update { it.copy(addingAccount = false) }

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
        _state.update { it.copy(locked = false) }
        if (_state.value.vehicles.isEmpty() && !loadingGarage) loadGarage()
    }

    /** From the lock overlay, back out to the login screen. */
    fun lockToLogin() = _state.update { it.copy(locked = false, addingAccount = true) }

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
                LockTiming.IMMEDIATE -> true
                LockTiming.AFTER_1_MIN -> elapsed >= 60_000
                LockTiming.AFTER_5_MIN -> elapsed >= 300_000
                LockTiming.SCREEN_OFF -> screenOff
            }
            if (shouldLock) _state.update { it.copy(locked = true) }
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
        // hide the others.
        val fetched = repos.values.flatMap { r ->
            runCatching { statusMutex.withLock { r.vehicles() } }.getOrElse { e ->
                AppLog.log("⚠ ${e.message ?: "Couldn't load vehicles"}")
                emptyList()
            }
        }
        if (fetched.isEmpty()) {
            _state.update { it.copy(vehicles = emptyList(), screen = Screen.Empty) }
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
        val firstRun = !settingsStore.onboardingSeen()
        // On first open all pebbles start expanded regardless of any stored state.
        val collapsed = if (firstRun) emptySet()
        else vehicles.flatMap { v -> settingsStore.collapsedSections(v.vin).map { "${v.vin}:$it" } }.toSet()
        val hidden = vehicles.flatMap { v -> settingsStore.hiddenSections(v.vin).map { "${v.vin}:$it" } }.toSet()
        val hotspots = vehicles.mapNotNull { v -> settingsStore.hotspot(v.vin)?.let { v.vin to it } }.toMap()
        val tileConfigs = (0 until com.bloo.bluelink.data.TILE_COUNT).map { settingsStore.tileConfig(it) }
        val tileBackground = settingsStore.tileBackground()
        val shortcutSet = settingsStore.enabledShortcuts()
        val lastVin = settingsStore.lastVehicleVin()
        val index = vehicles.indexOfFirst { it.vin == lastVin }.let { if (it < 0) 0 else it }
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
                collapsedPebbles = collapsed,
                hiddenPebbles = hidden,
                hotspotSections = hotspots,
                tileConfigs = tileConfigs,
                tileBackground = tileBackground,
                shortcutSet = shortcutSet,
                currentIndex = index,
                // First run funnels through the onboarding screen → Settings.
                screen = if (firstRun) Screen.Onboarding else Screen.Garage,
            )
        }
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

    /** Launch the OEM Bluelink/Genesis app for this car's brand. */
    private fun openOemApp(v: Vehicle) {
        val ctx = getApplication<Application>()
        val pkg = if (Brand.fromIndicator(v.brandIndicator) == Brand.GENESIS) {
            "com.stationdm.genesis"
        } else {
            "com.stationdm.bluelink"
        }
        val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)
            ?: android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkg"),
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
        }
        _state.update { it.copy(refreshing = true) }
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
                val stillBusy = synchronized(statusInFlight) {
                    statusInFlight.remove(v.vin)
                    statusInFlight.isNotEmpty()
                }
                if (!stillBusy) _state.update { it.copy(refreshing = false) }
            }
        }
    }

    /** Persist a new car display order (drag-and-drop in Settings). */
    fun reorderVehicles(order: List<Vehicle>) {
        _state.update { it.copy(vehicles = order) }
        viewModelScope.launch {
            settingsStore.setVehicleOrder(order.map { it.vin })
            persistSnapshots(order)
        }
    }

    private suspend fun persistSnapshots(vehicles: List<Vehicle> = _state.value.vehicles) {
        snapshotStore.saveVehicles(vehicles.map { snapshotOf(it, _state.value.statuses[it.vin]) })
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
            regId = v.regId,
            generation = v.generation,
            brandIndicator = v.brandIndicator,
            percent = percent,
            rangeMi = range,
            locked = status?.doorLock,
            charging = status?.evStatus?.batteryCharge,
            climateOn = status?.airCtrlOn,
            updated = status?.dateTime,
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

    /**
     * Copies a picked/cropped image into the app's own storage so it survives
     * (cropper output lives in a temp cache and otherwise shows blank later).
     */
    fun importCarImage(vin: String, source: android.net.Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val app = getApplication<Application>()
                    val dir = java.io.File(app.filesDir, "cars").apply { mkdirs() }
                    val out = java.io.File(dir, "car_${vin}_${System.currentTimeMillis()}.jpg")
                    app.contentResolver.openInputStream(source)!!.use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                    out.absolutePath
                }.getOrNull()
            }
            if (path != null) {
                setVehicleImage(vin, path)
            } else {
                _state.update { it.copy(message = "Couldn't save that photo") }
            }
        }
    }

    fun setLicensePlate(vin: String, plate: String) {
        _state.update {
            it.copy(
                licensePlates = if (plate.isBlank()) it.licensePlates - vin
                else it.licensePlates + (vin to plate.trim()),
            )
        }
        viewModelScope.launch { settingsStore.setLicensePlate(vin, plate) }
    }

    fun setLastServiceMiles(vin: String, miles: Int?) {
        _state.update {
            it.copy(
                lastServiceMiles = if (miles == null) it.lastServiceMiles - vin
                else it.lastServiceMiles + (vin to miles),
            )
        }
        viewModelScope.launch { settingsStore.setLastServiceMiles(vin, miles) }
    }

    fun setServiceIntervalMiles(vin: String, miles: Int?) {
        _state.update {
            it.copy(
                serviceIntervalMiles = if (miles == null) it.serviceIntervalMiles - vin
                else it.serviceIntervalMiles + (vin to miles),
            )
        }
        viewModelScope.launch { settingsStore.setServiceIntervalMiles(vin, miles) }
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
        viewModelScope.launch { settingsStore.setSectionHidden(v.vin, section, hidden) }
    }

    /** Finish first-run onboarding by going (required) into Settings. */
    fun startSetup() {
        viewModelScope.launch { settingsStore.setOnboardingSeen() }
        _state.update { it.copy(screen = Screen.Settings, showSettingsCoach = true) }
    }

    fun dismissSettingsCoach() = _state.update { it.copy(showSettingsCoach = false) }

    fun setPowertrain(v: Vehicle, value: Powertrain) {
        _state.update { it.copy(powertrains = it.powertrains + (v.vin to value)) }
        viewModelScope.launch { settingsStore.setPowertrain(v.vin, value) }
    }

    // --- On-device AI (Gemini Nano) --------------------------------------

    fun setAiEnabled(value: Boolean) {
        _state.update { it.copy(aiEnabled = value) }
        viewModelScope.launch { settingsStore.setAiEnabled(value) }
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

    fun setTileBackground(value: Boolean) {
        _state.update { it.copy(tileBackground = value) }
        viewModelScope.launch {
            settingsStore.setTileBackground(value)
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

    /** Remote engine/climate start with defaults (the primary Climate action). */
    fun engineStart(v: Vehicle) =
        runCommand(v.vin, "climate", "Climate on", { it.copy(airCtrlOn = true) }) {
            repoFor(v).startClimate(v, ClimateRequest(tempF = 72, defrost = false, durationMinutes = 10))
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
            try {
                // Serialize with status fetches: Hyundai rejects overlapping
                // requests with "a previous request is pending".
                statusMutex.withLock { block() }
                AppLog.log(success)
                // Success is shown through the control's state (no toast); only
                // optimistically flip the cached status so the toggle updates.
                _state.update { st ->
                    val statuses = if (optimistic != null && st.statuses[vin] != null) {
                        st.statuses + (vin to optimistic(st.statuses.getValue(vin)))
                    } else {
                        st.statuses
                    }
                    st.copy(statuses = statuses)
                }
                persistSnapshots()
                // Auto-AI: a command changed the car's state, refresh the summary.
                _state.value.vehicles.firstOrNull { it.vin == vin }?.let { autoSummarize(it) }
            } catch (e: Exception) {
                val msg = e.message ?: "Command failed"
                AppLog.log("⚠ $msg")
                _state.update { it.copy(message = msg) }
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

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(mode) }
    fun setFontChoice(choice: FontChoice) = viewModelScope.launch { settingsStore.setFontChoice(choice) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsStore.setDynamicColor(enabled) }
    fun setColumnsFlipped(flipped: Boolean) = viewModelScope.launch { settingsStore.setColumnsFlipped(flipped) }
    fun setLinksInApp(value: Boolean) = viewModelScope.launch { settingsStore.setLinksInApp(value) }
    fun setUiScale(value: Float) = viewModelScope.launch { settingsStore.setUiScale(value) }
    fun setVibrancy(value: Float) = viewModelScope.launch { settingsStore.setVibrancy(value) }
    fun setHapticsEnabled(value: Boolean) = viewModelScope.launch { settingsStore.setHapticsEnabled(value) }

    fun clearLogs() = AppLog.clear()
    fun clearMessage() = _state.update { it.copy(message = null) }

    /** Surface (and log) an error raised by the UI layer. */
    fun reportError(msg: String) {
        AppLog.log("⚠ $msg")
        _state.update { it.copy(message = msg) }
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
