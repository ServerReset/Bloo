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
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.Credentials
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

sealed interface Screen {
    data object Login : Screen
    data object Locked : Screen
    /** No vehicles enrolled (or still loading the first time). */
    data object Empty : Screen
    /** Main screen: the car carousel/grid. */
    data object Garage : Screen
    data object Settings : Screen
}

data class UiState(
    val screen: Screen = Screen.Login,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val vehicles: List<Vehicle> = emptyList(),
    val currentIndex: Int = 0,
    /** On large screens, the index expanded to full screen (null = grid view). */
    val expandedIndex: Int? = null,
    val statuses: Map<String, VehicleStatus> = emptyMap(),
    val locations: Map<String, GeoLocation> = emptyMap(),
    val seatConfigs: Map<String, SeatConfig> = emptyMap(),
    val powertrains: Map<String, Powertrain> = emptyMap(),
    val sectionOrders: Map<String, List<String>> = emptyMap(),
    val imageUrls: Map<String, String> = emptyMap(),
    val placeNames: Map<String, String> = emptyMap(),
    /** In-flight commands, keyed "vin:action", so each control can show its own spinner. */
    val pending: Set<String> = emptySet(),
    /** Collapsed pebbles, keyed "vin:section". Absent = expanded. */
    val collapsedPebbles: Set<String> = emptySet(),
    /** Show the first-run "configure your car" prompt. */
    val showOnboarding: Boolean = false,
    val credentials: Credentials? = null,
    val message: String? = null,
) {
    fun statusFor(v: Vehicle): VehicleStatus? = statuses[v.vin]

    fun isPending(vin: String, action: String): Boolean = "$vin:$action" in pending

    fun isPebbleExpanded(vin: String, section: String): Boolean = "$vin:$section" !in collapsedPebbles

    fun seatConfigFor(v: Vehicle): SeatConfig = seatConfigs[v.vin] ?: SeatConfig()

    fun sectionsFor(v: Vehicle): List<String> = sectionOrders[v.vin] ?: DEFAULT_SECTIONS

    /** Effective powertrain: user override, else EV/gas inferred from the API. */
    fun powertrainOf(v: Vehicle): Powertrain =
        powertrains[v.vin] ?: if (v.isEv) Powertrain.EV else Powertrain.GAS

    /** Has a high-voltage battery you can charge (EV or plug-in hybrid). */
    fun hasBattery(v: Vehicle): Boolean = powertrainOf(v) == Powertrain.EV || powertrainOf(v) == Powertrain.PHEV

    /** Burns fuel (everything except a pure EV). */
    fun hasFuel(v: Vehicle): Boolean = powertrainOf(v) != Powertrain.EV

    /** Powertrain label for the header. */
    fun powertrainLabel(v: Vehicle): String = when (powertrainOf(v)) {
        Powertrain.GAS -> "Gas"
        Powertrain.HYBRID -> "Hybrid"
        Powertrain.PHEV -> "PHEV"
        Powertrain.EV -> "EV"
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SessionStore(app)
    private val settingsStore = SettingsStore(app)
    private val credentialStore = CredentialStore(app)
    private val snapshotStore = SnapshotStore(app)
    // Rebuilt whenever the brand becomes known (Hyundai vs Genesis use different
    // hosts + OAuth clients).
    private var repo = BlueLinkRepository(BlueLinkApi(), store)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Serializes ALL vehicleStatus calls account-wide. Blue Link rejects
     * overlapping requests with `502 ... a previous request is pending`, so
     * status fetches must run strictly one at a time.
     */
    private val statusMutex = Mutex()

    /** VINs with a status request currently queued or running (de-dupes). */
    private val statusInFlight = mutableSetOf<String>()

    /** Copy-pasteable activity log shown in Settings. */
    val logs: StateFlow<List<String>> = AppLog.lines

    val appearance: StateFlow<SettingsStore.Appearance> =
        settingsStore.appearance.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            SettingsStore.Appearance(),
        )

    init {
        viewModelScope.launch {
            val session = store.load() ?: return@launch
            repo = BlueLinkRepository(BlueLinkApi(session.brand), store)
            _state.update { it.copy(credentials = credentialStore.load()) }
            val locked = settingsStore.appearance.first().biometricLock && canUseBiometrics()
            if (locked) {
                _state.update { it.copy(screen = Screen.Locked) }
            } else {
                loadGarage()
            }
        }
    }

    // --- Auth ------------------------------------------------------------

    fun login(username: String, password: String, pin: String, brand: Brand) {
        if (username.isBlank() || password.isBlank() || pin.isBlank()) {
            _state.update { it.copy(message = "Email, password and PIN are all required") }
            return
        }
        launchBusy {
            repo = BlueLinkRepository(BlueLinkApi(brand), store)
            repo.login(brand, username.trim(), password, pin.trim())
            val creds = Credentials(username.trim(), password, pin.trim(), brand)
            credentialStore.save(creds)
            AppLog.log("Signed in as ${creds.email} (${brand.label})")
            _state.update { it.copy(credentials = creds) }
            loadGarageInternal()
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            credentialStore.clear()
            AppLog.log("Signed out")
            _state.value = UiState(screen = Screen.Login)
        }
    }

    fun unlocked() = loadGarage()

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
        val fetched = repo.vehicles()
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
        val lastVin = settingsStore.lastVehicleVin()
        val index = vehicles.indexOfFirst { it.vin == lastVin }.let { if (it < 0) 0 else it }
        val showOnboarding = !settingsStore.onboardingSeen()
        _state.update {
            it.copy(
                vehicles = vehicles,
                seatConfigs = seatConfigs,
                powertrains = powertrains,
                sectionOrders = sectionOrders,
                imageUrls = images,
                currentIndex = index,
                screen = Screen.Garage,
                showOnboarding = showOnboarding,
            )
        }
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

    /** Loads status only if we don't already have it cached (no UI flash). */
    private fun ensureStatus(v: Vehicle) {
        if (_state.value.statuses.containsKey(v.vin)) return
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
                    repo.status(v, refresh = refresh)?.let { s ->
                        _state.update { st -> st.copy(statuses = st.statuses + (v.vin to s)) }
                        persistSnapshots()
                    }
                }
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
        val percent = if (v.isEv) status?.evStatus?.batteryStatus else status?.fuelLevel
        val range = (status?.evStatus?.drvDistance?.firstOrNull()
            ?.rangeByFuel?.totalAvailableRange?.value ?: status?.dte?.value)?.toInt()
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

    /** Toggle a pebble (detail section) open/closed for a car. */
    fun togglePebble(v: Vehicle, section: String) {
        val key = "${v.vin}:$section"
        _state.update {
            it.copy(
                collapsedPebbles = if (key in it.collapsedPebbles) it.collapsedPebbles - key
                else it.collapsedPebbles + key,
            )
        }
    }

    fun dismissOnboarding(openSettings: Boolean) {
        _state.update { it.copy(showOnboarding = false) }
        viewModelScope.launch { settingsStore.setOnboardingSeen() }
        if (openSettings) openSettings()
    }

    fun setPowertrain(v: Vehicle, value: Powertrain) {
        _state.update { it.copy(powertrains = it.powertrains + (v.vin to value)) }
        viewModelScope.launch { settingsStore.setPowertrain(v.vin, value) }
    }

    /** Persist a new pebble order for a car (drag-and-drop on the card). */
    fun setSectionOrder(v: Vehicle, order: List<String>) {
        _state.update { it.copy(sectionOrders = it.sectionOrders + (v.vin to order)) }
        viewModelScope.launch { settingsStore.setSectionOrder(v.vin, order) }
    }

    fun locate(v: Vehicle) = runCommand(v.vin, "locate", "Location updated", optimistic = null) {
        val loc = repo.location(v) ?: throw BlueLinkException(
            "Couldn't get the car's location — it may be asleep, out of coverage, or over " +
                "the daily location-lookup limit. Try again later.",
        )
        _state.update { it.copy(locations = it.locations + (v.vin to loc)) }
        reverseGeocode(loc)?.let { place ->
            _state.update { it.copy(placeNames = it.placeNames + (v.vin to place)) }
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

    fun lock(v: Vehicle) = runCommand(v.vin, "doors", "Locked", { it.copy(doorLock = true) }) { repo.lock(v) }
    fun unlock(v: Vehicle) = runCommand(v.vin, "doors", "Unlocked", { it.copy(doorLock = false) }) { repo.unlock(v) }

    fun stopClimate(v: Vehicle) =
        runCommand(v.vin, "climate", "Climate off", { it.copy(airCtrlOn = false) }) { repo.stopClimate(electric(v)) }

    fun startClimate(v: Vehicle, req: ClimateRequest) =
        runCommand(v.vin, "climate", "Climate on (${req.tempF}°F)", { it.copy(airCtrlOn = true) }) {
            repo.startClimate(electric(v), req)
        }

    /** Remote engine/climate start with defaults (the primary Climate action). */
    fun engineStart(v: Vehicle) =
        runCommand(v.vin, "climate", "Climate on", { it.copy(airCtrlOn = true) }) {
            repo.startClimate(electric(v), ClimateRequest(tempF = 72, defrost = false, durationMinutes = 10))
        }

    fun startCharge(v: Vehicle) =
        runCommand(v.vin, "charge", "Charging", { it.copy(evStatus = it.evStatus?.copy(batteryCharge = true)) }) {
            repo.startCharge(electric(v))
        }

    fun stopCharge(v: Vehicle) =
        runCommand(v.vin, "charge", "Charging stopped", { it.copy(evStatus = it.evStatus?.copy(batteryCharge = false)) }) {
            repo.stopCharge(electric(v))
        }

    fun setChargeLimits(v: Vehicle, acPercent: Int, dcPercent: Int) =
        runCommand(v.vin, "chargeLimit", "Charge limits set (AC $acPercent% / DC $dcPercent%)", null) {
            repo.setChargeTargets(electric(v), acPercent, dcPercent)
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
            _state.update { it.copy(pending = it.pending + key, message = null) }
            try {
                block()
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
            } catch (e: Exception) {
                val msg = e.message ?: "Command failed"
                AppLog.log("⚠ $msg")
                _state.update { it.copy(message = msg) }
            } finally {
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
            )
        }
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(mode) }
    fun setFontChoice(choice: FontChoice) = viewModelScope.launch { settingsStore.setFontChoice(choice) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsStore.setDynamicColor(enabled) }

    fun clearLogs() = AppLog.clear()
    fun clearMessage() = _state.update { it.copy(message = null) }

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
