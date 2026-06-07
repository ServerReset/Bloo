package com.bloo.bluelink.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.BlueLinkApi
import com.bloo.bluelink.data.BlueLinkRepository
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.SeatCapability
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Login : Screen
    data object Vehicles : Screen
    data class Detail(val index: Int) : Screen
}

data class UiState(
    val screen: Screen = Screen.Login,
    val loading: Boolean = false,
    val vehicles: List<Vehicle> = emptyList(),
    val status: VehicleStatus? = null,
    val location: GeoLocation? = null,
    val seatCapability: SeatCapability = SeatCapability(),
    val ventilatedSeats: Boolean = false,
    val message: String? = null,
) {
    val currentVehicle: Vehicle?
        get() = (screen as? Screen.Detail)?.index?.let { vehicles.getOrNull(it) }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SessionStore(app)
    private val repo = BlueLinkRepository(BlueLinkApi(), store)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (store.load() != null) loadVehicles()
        }
    }

    fun login(username: String, password: String, pin: String) {
        if (username.isBlank() || password.isBlank() || pin.isBlank()) {
            _state.update { it.copy(message = "Email, password and PIN are all required") }
            return
        }
        launchBusy {
            repo.login(username.trim(), password, pin.trim())
            loadVehiclesInternal()
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            _state.value = UiState(screen = Screen.Login)
        }
    }

    fun loadVehicles() = launchBusy { loadVehiclesInternal() }

    private suspend fun loadVehiclesInternal() {
        val vehicles = repo.vehicles()
        _state.update {
            it.copy(
                vehicles = vehicles,
                screen = Screen.Vehicles,
                message = if (vehicles.isEmpty()) "No enrolled vehicles found on this account" else null,
            )
        }
    }

    /** Open the detail/pager at the given vehicle. Status is loaded by the pager. */
    fun openVehicle(v: Vehicle) {
        val index = _state.value.vehicles.indexOfFirst { it.vin == v.vin }.coerceAtLeast(0)
        _state.update {
            it.copy(
                screen = Screen.Detail(index),
                status = null,
                location = null,
                seatCapability = SeatCapability(),
            )
        }
    }

    /**
     * Called when the pager settles on a page. Loads that car's status once;
     * a no-op if we're already showing it (avoids burning remote-request quota).
     */
    fun onPageSettled(index: Int) {
        val v = _state.value.vehicles.getOrNull(index) ?: return
        val current = (_state.value.screen as? Screen.Detail)?.index
        if (current == index && _state.value.status != null) return
        _state.update {
            it.copy(
                screen = Screen.Detail(index),
                status = null,
                location = null,
                seatCapability = SeatCapability(),
            )
        }
        viewModelScope.launch { _state.update { it.copy(ventilatedSeats = store.ventilatedSeats(v.vin)) } }
        refreshStatus(v, forceRefresh = false)
    }

    fun back() {
        _state.update {
            it.copy(screen = Screen.Vehicles, status = null, location = null, message = null)
        }
    }

    fun setVentilatedSeats(v: Vehicle, value: Boolean) {
        _state.update { it.copy(ventilatedSeats = value) }
        viewModelScope.launch { store.setVentilatedSeats(v.vin, value) }
    }

    fun locate(v: Vehicle) = launchBusy {
        val loc = repo.location(v)
        _state.update {
            it.copy(
                location = loc,
                message = if (loc == null) "Could not get the car's location" else "Location updated",
            )
        }
    }

    fun refreshStatus(v: Vehicle, forceRefresh: Boolean) = launchBusy {
        val status = repo.status(v, forceRefresh)
        _state.update { it.copy(status = status, seatCapability = capabilityOf(status)) }
    }

    private fun capabilityOf(status: VehicleStatus?): SeatCapability {
        val s = status?.seatHeaterVentState ?: return SeatCapability()
        return SeatCapability(
            frontLeft = s.flSeatHeatState != null,
            frontRight = s.frSeatHeatState != null,
            rearLeft = s.rlSeatHeatState != null,
            rearRight = s.rrSeatHeatState != null,
        )
    }

    fun lock(v: Vehicle) = command("Lock requested") { repo.lock(v) }
    fun unlock(v: Vehicle) = command("Unlock requested") { repo.unlock(v) }
    fun stopClimate(v: Vehicle) = command("Climate stop requested") { repo.stopClimate(v) }

    fun startClimate(v: Vehicle, req: ClimateRequest) =
        command("Climate start requested (${req.tempF}°F)") { repo.startClimate(v, req) }

    fun setChargeLimits(v: Vehicle, acPercent: Int, dcPercent: Int) =
        command("Charge limits set (AC $acPercent% / DC $dcPercent%)") {
            repo.setChargeTargets(v, acPercent, dcPercent)
        }

    private fun command(success: String, block: suspend () -> Unit) = launchBusy {
        block()
        _state.update { it.copy(message = success) }
    }

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
