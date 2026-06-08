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
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.Credentials
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.SeatCapability
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.VehicleStatus
import androidx.glance.appwidget.updateAll
import com.bloo.bluelink.widget.BlooGlanceWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val ventilated: Map<String, Boolean> = emptyMap(),
    val imageUrls: Map<String, String> = emptyMap(),
    val placeNames: Map<String, String> = emptyMap(),
    /** In-flight commands, keyed "vin:action", so each control can show its own spinner. */
    val pending: Set<String> = emptySet(),
    val credentials: Credentials? = null,
    val message: String? = null,
) {
    fun statusFor(v: Vehicle): VehicleStatus? = statuses[v.vin]

    fun isPending(vin: String, action: String): Boolean = "$vin:$action" in pending

    fun seatCapabilityFor(v: Vehicle): SeatCapability {
        val s = statuses[v.vin]?.seatHeaterVentState ?: return SeatCapability()
        return SeatCapability(
            frontLeft = s.flSeatHeatState != null,
            frontRight = s.frSeatHeatState != null,
            rearLeft = s.rlSeatHeatState != null,
            rearRight = s.rrSeatHeatState != null,
        )
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SessionStore(app)
    private val settingsStore = SettingsStore(app)
    private val credentialStore = CredentialStore(app)
    private val snapshotStore = SnapshotStore(app)
    private val repo = BlueLinkRepository(BlueLinkApi(), store)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

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

    fun login(username: String, password: String, pin: String) {
        if (username.isBlank() || password.isBlank() || pin.isBlank()) {
            _state.update { it.copy(message = "Email, password and PIN are all required") }
            return
        }
        launchBusy {
            repo.login(username.trim(), password, pin.trim())
            val creds = Credentials(username.trim(), password, pin.trim())
            credentialStore.save(creds)
            AppLog.log("Signed in as ${creds.email}")
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
        val ventilated = vehicles.associate { it.vin to store.ventilatedSeats(it.vin) }
        val images = vehicles.mapNotNull { v -> settingsStore.imageUrl(v.vin)?.let { v.vin to it } }.toMap()
        val lastVin = settingsStore.lastVehicleVin()
        val index = vehicles.indexOfFirst { it.vin == lastVin }.let { if (it < 0) 0 else it }
        _state.update {
            it.copy(
                vehicles = vehicles,
                ventilated = ventilated,
                imageUrls = images,
                currentIndex = index,
                screen = Screen.Garage,
            )
        }
        // Fetch current car first for an immediate view, then prefetch the rest
        // (REFRESH=false -> cached last-known status, doesn't burn remote quota).
        ensureStatus(vehicles[index])
        viewModelScope.launch {
            vehicles.forEachIndexed { i, v -> if (i != index) ensureStatus(v) }
        }
    }

    /** Switch the visible car (swipe). Uses cache, so no loading flash. */
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
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            try {
                repo.status(v, refresh = false)?.let { s ->
                    _state.update { st -> st.copy(statuses = st.statuses + (v.vin to s)) }
                    persistSnapshots()
                }
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "Couldn't load status") }
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun refreshStatus(v: Vehicle) {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            try {
                repo.status(v, refresh = true)?.let { s ->
                    _state.update { st -> st.copy(statuses = st.statuses + (v.vin to s)) }
                    persistSnapshots()
                }
                AppLog.log("Status refreshed for ${v.name}")
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "Refresh failed") }
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    /** Move a car up/down in the user's order and persist it. */
    fun moveVehicle(vin: String, up: Boolean) {
        val list = _state.value.vehicles.toMutableList()
        val i = list.indexOfFirst { it.vin == vin }
        if (i < 0) return
        val j = if (up) i - 1 else i + 1
        if (j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        _state.update { it.copy(vehicles = list) }
        viewModelScope.launch {
            settingsStore.setVehicleOrder(list.map { it.vin })
            persistSnapshots(list)
        }
    }

    private suspend fun persistSnapshots(vehicles: List<Vehicle> = _state.value.vehicles) {
        snapshotStore.saveVehicles(vehicles.map { snapshotOf(it, _state.value.statuses[it.vin]) })
        runCatching { BlooGlanceWidget().updateAll(getApplication()) }
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

    fun setVentilatedSeats(v: Vehicle, value: Boolean) {
        _state.update { it.copy(ventilated = it.ventilated + (v.vin to value)) }
        viewModelScope.launch { store.setVentilatedSeats(v.vin, value) }
    }

    fun locate(v: Vehicle) = runCommand(v.vin, "locate", "Location updated", optimistic = null) {
        val loc = repo.location(v) ?: throw BlueLinkException("Could not get the car's location")
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

    fun lock(v: Vehicle) = runCommand(v.vin, "doors", "Locked", { it.copy(doorLock = true) }) { repo.lock(v) }
    fun unlock(v: Vehicle) = runCommand(v.vin, "doors", "Unlocked", { it.copy(doorLock = false) }) { repo.unlock(v) }

    fun stopClimate(v: Vehicle) =
        runCommand(v.vin, "climate", "Climate off", { it.copy(airCtrlOn = false) }) { repo.stopClimate(v) }

    fun startClimate(v: Vehicle, req: ClimateRequest) =
        runCommand(v.vin, "climate", "Climate on (${req.tempF}°F)", { it.copy(airCtrlOn = true) }) {
            repo.startClimate(v, req)
        }

    /** Remote engine/climate start with defaults (the primary Climate action). */
    fun engineStart(v: Vehicle) =
        runCommand(v.vin, "climate", "Climate on", { it.copy(airCtrlOn = true) }) {
            repo.startClimate(v, ClimateRequest(tempF = 72, defrost = false, durationMinutes = 10))
        }

    fun startCharge(v: Vehicle) =
        runCommand(v.vin, "charge", "Charging", { it.copy(evStatus = it.evStatus?.copy(batteryCharge = true)) }) {
            repo.startCharge(v)
        }

    fun stopCharge(v: Vehicle) =
        runCommand(v.vin, "charge", "Charging stopped", { it.copy(evStatus = it.evStatus?.copy(batteryCharge = false)) }) {
            repo.stopCharge(v)
        }

    fun setChargeLimits(v: Vehicle, acPercent: Int, dcPercent: Int) =
        runCommand(v.vin, "chargeLimit", "Charge limits set (AC $acPercent% / DC $dcPercent%)", null) {
            repo.setChargeTargets(v, acPercent, dcPercent)
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
                _state.update { st ->
                    val statuses = if (optimistic != null && st.statuses[vin] != null) {
                        st.statuses + (vin to optimistic(st.statuses.getValue(vin)))
                    } else {
                        st.statuses
                    }
                    st.copy(message = success, statuses = statuses)
                }
                persistSnapshots()
            } catch (e: Exception) {
                _state.update { it.copy(message = e.message ?: "Command failed") }
            } finally {
                _state.update { it.copy(pending = it.pending - key) }
            }
        }
    }

    // --- Settings / nav --------------------------------------------------

    fun openSettings() = _state.update { it.copy(screen = Screen.Settings) }
    fun closeSettings() {
        _state.update { it.copy(screen = if (it.vehicles.isEmpty()) Screen.Empty else Screen.Garage) }
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
                _state.update { it.copy(message = e.message ?: "Something went wrong") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
