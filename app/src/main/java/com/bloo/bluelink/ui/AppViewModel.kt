package com.bloo.bluelink.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.BlueLinkApi
import com.bloo.bluelink.data.BlueLinkRepository
import com.bloo.bluelink.data.GeoLocation
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
    data class Detail(val vehicle: Vehicle) : Screen
}

data class UiState(
    val screen: Screen = Screen.Login,
    val loading: Boolean = false,
    val vehicles: List<Vehicle> = emptyList(),
    val status: VehicleStatus? = null,
    val location: GeoLocation? = null,
    val message: String? = null,
)

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

    fun openVehicle(v: Vehicle) {
        _state.update { it.copy(screen = Screen.Detail(v), status = null, location = null) }
        refreshStatus(v, forceRefresh = false)
    }

    fun back() {
        _state.update { it.copy(screen = Screen.Vehicles, status = null, location = null, message = null) }
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
        _state.update { it.copy(status = status) }
    }

    fun lock(v: Vehicle) = command("Lock requested") { repo.lock(v) }
    fun unlock(v: Vehicle) = command("Unlock requested") { repo.unlock(v) }
    fun stopClimate(v: Vehicle) = command("Climate stop requested") { repo.stopClimate(v) }
    fun startClimate(v: Vehicle, tempF: Int, defrost: Boolean, minutes: Int) =
        command("Climate start requested ($tempF°F)") {
            repo.startClimate(v, tempF, defrost, minutes)
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
