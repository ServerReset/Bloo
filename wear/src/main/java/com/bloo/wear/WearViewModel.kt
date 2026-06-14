package com.bloo.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.BlueLinkGate
import com.bloo.bluelink.data.BlueLinkRepository
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.Credentials
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.DoorOpen
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.StatusCache
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleRepository
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.WindowOpen
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.openLabels
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.repositoryFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

enum class WearScreen { Loading, SignedOut, Ready }

/** A fully resolved per-car view, merging live status with the phone snapshot. */
data class CarView(
    val vin: String,
    val name: String,
    val model: String,
    val hasBattery: Boolean,
    val percent: Int?,
    val rangeMi: Int?,
    val locked: Boolean?,
    val climateOn: Boolean?,
    val charging: Boolean?,
    val pluggedIn: Boolean?,
    val tempSetting: String?,
    val fetchedAt: Long?,
    val doorsOpen: List<String>,
    val windowsOpen: List<String>,
    val tireWarning: Boolean,
    val battery12v: Int?,
    val odometer: String?,
    val hasLiveStatus: Boolean,
)

data class WearUi(
    val screen: WearScreen = WearScreen.Loading,
    val cars: List<CarView> = emptyList(),
    val pending: Set<String> = emptySet(),
    val busy: Boolean = false,
    val message: String? = null,
    val accounts: List<String> = emptyList(),
    val phoneConnected: Boolean = false,
    val climateTempF: Int = 72,
)

class WearViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()
    private val sessionStore = SessionStore(ctx)
    private val credentialStore = CredentialStore(ctx)
    private val snapshotStore = SnapshotStore(ctx)
    private val statusCache = StatusCache(ctx)
    private val repos = mutableMapOf<Brand, VehicleRepository>()

    // Raw state; the published [WearUi.cars] is derived from these.
    private var vehicles: List<Vehicle> = emptyList()
    private var statuses: Map<String, VehicleStatus> = emptyMap()
    private var snapshots: Map<String, com.bloo.bluelink.data.VehicleSnapshot> = emptyMap()
    private var fetchedAt: Map<String, Long> = emptyMap()
    private var pending: Set<String> = emptySet()

    private val _ui = MutableStateFlow(WearUi())
    val ui = _ui.asStateFlow()

    private fun repoFor(brand: Brand) =
        repos.getOrPut(brand) { repositoryFor(brand, sessionStore, credentialStore) }

    init { bootstrap() }

    private fun bootstrap() {
        viewModelScope.launch {
            runCatching { WearComms.pullLatest(ctx) }
            refreshConnection()
            snapshots = snapshotStore.current().vehicles.associateBy { it.vin }
            val cached = statusCache.load()
            statuses = cached.statuses
            fetchedAt = cached.fetched
            val brands = sessionStore.loggedInBrands()
            if (brands.isEmpty()) {
                _ui.update { it.copy(screen = WearScreen.SignedOut) }
            } else {
                _ui.update { it.copy(accounts = credentialStore.loadAll().map { c -> c.email }) }
                publish(WearScreen.Ready)
                loadGarage()
            }
        }
    }

    fun refreshConnection() {
        viewModelScope.launch {
            _ui.update { it.copy(phoneConnected = WearComms.phoneNodeId(ctx) != null) }
        }
    }

    // ---- Sign in / out ----------------------------------------------------

    fun login(brand: Brand, email: String, password: String, pin: String) {
        if (email.isBlank() || password.isBlank() || pin.isBlank()) {
            _ui.update { it.copy(message = "Email, password and PIN are required") }
            return
        }
        if (brand == Brand.KIA) {
            _ui.update { it.copy(message = "Sign in to Kia on your phone, then it syncs here") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = null) }
            runCatching {
                (repoFor(brand) as BlueLinkRepository).login(email.trim(), password, pin.trim())
                credentialStore.save(Credentials(email.trim(), password, pin.trim(), brand))
            }.onSuccess {
                AppLog.log("Watch sign-in: ${email.trim()} (${brand.label})")
                _ui.update {
                    it.copy(busy = false, screen = WearScreen.Ready, accounts = credentialStore.loadAll().map { c -> c.email })
                }
                loadGarage()
            }.onFailure { e ->
                _ui.update { it.copy(busy = false, message = e.message ?: "Sign-in failed") }
            }
        }
    }

    fun signOutAll() {
        viewModelScope.launch {
            sessionStore.loggedInBrands().forEach { b ->
                runCatching { repoFor(b).logout() }
                runCatching { credentialStore.clear(b) }
            }
            repos.clear()
            vehicles = emptyList(); statuses = emptyMap()
            _ui.update { it.copy(screen = WearScreen.SignedOut, cars = emptyList(), accounts = emptyList()) }
        }
    }

    // ---- Loading ----------------------------------------------------------

    private suspend fun loadGarage() {
        val brands = sessionStore.loggedInBrands()
        val fetched = brands.flatMap { b ->
            runCatching { BlueLinkGate.statusMutex.withLock { repoFor(b).vehicles() } }.getOrElse { emptyList() }
        }
        if (fetched.isNotEmpty()) {
            vehicles = fetched
            publish(WearScreen.Ready)
            fetched.forEach { refreshStatus(it.vin, surface = false) }
        } else if (snapshots.isNotEmpty()) {
            // No network but the phone already synced cars — show those.
            vehicles = snapshots.values.map { it.toVehicle() }
            publish(WearScreen.Ready)
        } else {
            publish(WearScreen.Ready)
        }
    }

    fun refreshAll() {
        vehicles.forEach { refreshStatus(it.vin, surface = false) }
        refreshConnection()
    }

    fun refreshStatus(vin: String, surface: Boolean = true) {
        val v = vehicles.firstOrNull { it.vin == vin } ?: return
        mark("$vin:refresh") {
            runCatching {
                BlueLinkGate.statusMutex.withLock { repoFor(v.brand).status(v, refresh = true) }
            }.onSuccess { s ->
                if (s != null) {
                    statuses = statuses + (vin to s)
                    fetchedAt = fetchedAt + (vin to System.currentTimeMillis())
                    persistCache()
                    publish()
                }
            }.onFailure { e ->
                if (surface) _ui.update { it.copy(message = "Couldn't refresh") }
                AppLog.log("Watch refresh failed: ${e.message}")
            }
        }
    }

    // ---- Commands ---------------------------------------------------------

    fun toggleLock(vin: String) = command(vin, "doors") { v, repo, st ->
        if (st?.doorLock == true) { repo.unlock(v); flip(vin) { it.copy(doorLock = false) } }
        else { repo.lock(v); flip(vin) { it.copy(doorLock = true) } }
    }

    fun toggleClimate(vin: String) = command(vin, "climate") { v, repo, st ->
        if (st?.airCtrlOn == true) { repo.stopClimate(v); flip(vin) { it.copy(airCtrlOn = false) } }
        else {
            repo.startClimate(v, ClimateRequest(tempF = _ui.value.climateTempF, defrost = false, durationMinutes = 10))
            flip(vin) { it.copy(airCtrlOn = true) }
        }
    }

    fun toggleCharge(vin: String) = command(vin, "charge") { v, repo, st ->
        if (st?.evStatus?.batteryCharge == true) { repo.stopCharge(v) } else { repo.startCharge(v) }
    }

    fun setClimateTemp(delta: Int) {
        _ui.update { it.copy(climateTempF = (it.climateTempF + delta).coerceIn(62, 82)) }
    }

    private fun command(vin: String, action: String, block: suspend (Vehicle, VehicleRepository, VehicleStatus?) -> Unit) {
        val v = vehicles.firstOrNull { it.vin == vin } ?: return
        mark("$vin:$action") {
            runCatching {
                BlueLinkGate.statusMutex.withLock { block(v, repoFor(v.brand), statuses[vin]) }
            }.onSuccess {
                publish()
                refreshStatus(vin, surface = false)
            }.onFailure { e ->
                // No watch connectivity? Let the phone run it instead.
                val relayed = runCatching { WearComms.send(ctx, toWearCommand(vin, action)) }.isSuccess
                if (!relayed) _ui.update { it.copy(message = e.message ?: "Command failed") }
                AppLog.log("Watch command ${action} failed: ${e.message}")
            }
        }
    }

    private fun toWearCommand(vin: String, action: String) = com.bloo.bluelink.data.WearCommand(
        vin = vin,
        action = when (action) {
            "doors" -> com.bloo.bluelink.data.WearAction.TOGGLE_LOCK
            "climate" -> com.bloo.bluelink.data.WearAction.TOGGLE_CLIMATE
            "charge" -> com.bloo.bluelink.data.WearAction.TOGGLE_CHARGE
            else -> com.bloo.bluelink.data.WearAction.REFRESH
        },
        tempF = _ui.value.climateTempF,
    )

    private fun flip(vin: String, change: (VehicleStatus) -> VehicleStatus) {
        val cur = statuses[vin] ?: VehicleStatus()
        statuses = statuses + (vin to change(cur))
    }

    // ---- Plumbing ---------------------------------------------------------

    private fun mark(key: String, block: suspend () -> Unit) {
        pending = pending + key
        publish()
        viewModelScope.launch {
            try { block() } finally {
                pending = pending - key
                publish()
            }
        }
    }

    private suspend fun persistCache() {
        runCatching { statusCache.save(statuses, emptyMap(), emptyMap(), fetchedAt) }
    }

    private fun publish(screen: WearScreen? = null) {
        _ui.update { cur ->
            cur.copy(
                screen = screen ?: cur.screen,
                cars = vehicles.map { buildCarView(it) },
                pending = pending,
            )
        }
    }

    private fun buildCarView(v: Vehicle): CarView {
        val s = statuses[v.vin]
        val snap = snapshots[v.vin]
        val hasBattery = v.isEv
        val tire = s?.tirePressureLamp?.hasWarning == true
        return CarView(
            vin = v.vin,
            name = v.name,
            model = v.model,
            hasBattery = hasBattery,
            percent = s?.percentFor(hasBattery) ?: snap?.percent,
            rangeMi = s?.rangeMiFor(hasBattery) ?: snap?.rangeMi,
            locked = s?.doorLock ?: snap?.locked,
            climateOn = s?.airCtrlOn ?: snap?.climateOn,
            charging = s?.evStatus?.batteryCharge ?: snap?.charging,
            pluggedIn = s?.evStatus?.batteryPlugin?.let { it != 0 },
            tempSetting = s?.airTemp?.value,
            fetchedAt = fetchedAt[v.vin],
            doorsOpen = (s?.doorOpen ?: DoorOpen()).openLabels(),
            windowsOpen = (s?.windowOpen ?: WindowOpen()).openLabels(),
            tireWarning = tire,
            battery12v = s?.battery?.batSoc,
            odometer = v.odometer,
            hasLiveStatus = s != null,
        )
    }
}
