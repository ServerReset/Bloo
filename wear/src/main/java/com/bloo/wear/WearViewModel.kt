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
import com.bloo.bluelink.data.EvTrip
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
    val chargerLabel: String?,
    val timeToFullMin: Int?,
    val acLimit: Int?,
    val dcLimit: Int?,
    val fetchedAt: Long?,
    val doorsOpen: List<String>,
    val windowsOpen: List<String>,
    val trunkOpen: Boolean,
    val hoodOpen: Boolean,
    val tireWarning: Boolean,
    val battery12v: Int?,
    val lowFuel: Boolean,
    val washerLow: Boolean,
    val brakeLow: Boolean,
    val keyFobLow: Boolean,
    val odometer: String?,
    val lat: Double?,
    val lon: Double?,
    val hasLiveStatus: Boolean,
)

data class WearUi(
    val screen: WearScreen = WearScreen.Loading,
    val cars: List<CarView> = emptyList(),
    val trips: Map<String, List<EvTrip>> = emptyMap(),
    val pending: Set<String> = emptySet(),
    val busy: Boolean = false,
    val message: String? = null,
    val accounts: List<String> = emptyList(),
    val phoneConnected: Boolean = false,
    val climateTempF: Int = 72,
    val climateDefrost: Boolean = false,
    val settings: com.bloo.bluelink.data.WearSettingsPayload? = null,
)

class WearViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()
    private val sessionStore = SessionStore(ctx)
    private val credentialStore = CredentialStore(ctx)
    private val snapshotStore = SnapshotStore(ctx)
    private val statusCache = StatusCache(ctx)
    private val repos = mutableMapOf<Brand, VehicleRepository>()

    private var vehicles: List<Vehicle> = emptyList()
    private var statuses: Map<String, VehicleStatus> = emptyMap()
    private var snapshots: Map<String, com.bloo.bluelink.data.VehicleSnapshot> = emptyMap()
    private var fetchedAt: Map<String, Long> = emptyMap()
    private var trips: Map<String, List<EvTrip>> = emptyMap()
    private var pending: Set<String> = emptySet()

    // Cars whose status we've already fetched this session, so paging back and
    // forth doesn't re-hit the (rate-limited, battery-hungry) network each time.
    private val sessionFetched = mutableSetOf<String>()
    private val tripsFetched = mutableSetOf<String>()

    private val _ui = MutableStateFlow(WearUi())
    val ui = _ui.asStateFlow()

    private fun repoFor(brand: Brand) =
        repos.getOrPut(brand) { repositoryFor(brand, sessionStore, credentialStore) }

    init {
        viewModelScope.launch {
            WearSettingsStore(ctx).flow.collect { s -> _ui.update { it.copy(settings = s) } }
        }
        bootstrap()
    }

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
            sessionFetched.clear(); tripsFetched.clear()
            vehicles = emptyList(); statuses = emptyMap(); trips = emptyMap()
            _ui.update { it.copy(screen = WearScreen.SignedOut, cars = emptyList(), trips = emptyMap(), accounts = emptyList()) }
        }
    }

    // ---- Loading ----------------------------------------------------------

    private suspend fun loadGarage() {
        val brands = sessionStore.loggedInBrands()
        val fetched = brands.flatMap { b ->
            runCatching { BlueLinkGate.statusMutex.withLock { repoFor(b).vehicles() } }.getOrElse { emptyList() }
        }
        vehicles = when {
            fetched.isNotEmpty() -> fetched
            // No network but the phone already synced cars — show those.
            snapshots.isNotEmpty() -> snapshots.values.map { it.toVehicle() }
            else -> emptyList()
        }
        publish(WearScreen.Ready)
        // Status is fetched lazily, per car, as pages are shown (see onCarShown).
    }

    /** Called when a car page becomes visible — fetch its status once per session. */
    fun onCarShown(vin: String) {
        if (vin in sessionFetched) return
        if (vehicles.none { it.vin == vin }) return
        sessionFetched.add(vin)
        refreshStatus(vin, surface = false)
    }

    fun refreshAll() {
        sessionFetched.clear()
        vehicles.firstOrNull()?.let { onCarShown(it.vin) }
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
                sessionFetched.remove(vin) // allow a retry
                if (surface) _ui.update { it.copy(message = "Couldn't refresh") }
                AppLog.log("Watch refresh failed: ${e.message}")
            }
        }
    }

    /** Recent EV trips, fetched lazily the first time the Trips screen opens. */
    fun loadTrips(vin: String) {
        if (vin in tripsFetched) return
        val v = vehicles.firstOrNull { it.vin == vin } ?: return
        tripsFetched.add(vin)
        mark("$vin:trips") {
            runCatching { BlueLinkGate.statusMutex.withLock { repoFor(v.brand).trips(v) } }
                .onSuccess { list -> trips = trips + (vin to list); publish() }
                .onFailure { tripsFetched.remove(vin); AppLog.log("Watch trips failed: ${it.message}") }
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
            repo.startClimate(v, ClimateRequest(
                tempF = _ui.value.climateTempF,
                defrost = _ui.value.climateDefrost,
                durationMinutes = 10,
            ))
            flip(vin) { it.copy(airCtrlOn = true) }
        }
    }

    fun toggleCharge(vin: String) = command(vin, "charge") { v, repo, st ->
        if (st?.evStatus?.batteryCharge == true) repo.stopCharge(v) else repo.startCharge(v)
    }

    fun setClimateTemp(delta: Int) {
        _ui.update { it.copy(climateTempF = (it.climateTempF + delta).coerceIn(62, 82)) }
    }

    fun toggleDefrost() {
        _ui.update { it.copy(climateDefrost = !it.climateDefrost) }
    }

    private fun command(vin: String, action: String, block: suspend (Vehicle, VehicleRepository, VehicleStatus?) -> Unit) {
        val v = vehicles.firstOrNull { it.vin == vin } ?: return
        mark("$vin:$action") {
            runCatching {
                BlueLinkGate.statusMutex.withLock { block(v, repoFor(v.brand), statuses[vin]) }
            }.onSuccess {
                publish()
                sessionFetched.remove(vin)
                refreshStatus(vin, surface = false)
            }.onFailure { e ->
                val relayed = runCatching { WearComms.send(ctx, toWearCommand(vin, action)) }.isSuccess
                if (!relayed) _ui.update { it.copy(message = e.message ?: "Command failed") }
                AppLog.log("Watch command $action failed: ${e.message}")
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
        defrost = _ui.value.climateDefrost,
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
                trips = trips,
                pending = pending,
            )
        }
    }

    private fun buildCarView(v: Vehicle): CarView {
        val s = statuses[v.vin]
        val snap = snapshots[v.vin]
        val hasBattery = v.isEv
        val ev = s?.evStatus
        val coord = s?.vehicleLocation?.coord
        return CarView(
            vin = v.vin,
            name = v.name,
            model = v.model,
            hasBattery = hasBattery,
            percent = s?.percentFor(hasBattery) ?: snap?.percent,
            rangeMi = s?.rangeMiFor(hasBattery) ?: snap?.rangeMi,
            locked = s?.doorLock ?: snap?.locked,
            climateOn = s?.airCtrlOn ?: snap?.climateOn,
            charging = ev?.batteryCharge ?: snap?.charging,
            pluggedIn = ev?.batteryPlugin?.let { it != 0 },
            chargerLabel = when (ev?.batteryPlugin) { 1 -> "DC fast"; 2 -> "AC"; else -> null },
            timeToFullMin = ev?.remainTime2?.atc?.value?.toInt(),
            acLimit = ev?.reservChargeInfos?.level(1),
            dcLimit = ev?.reservChargeInfos?.level(0),
            fetchedAt = fetchedAt[v.vin],
            doorsOpen = (s?.doorOpen ?: DoorOpen()).openLabels(),
            windowsOpen = (s?.windowOpen ?: WindowOpen()).openLabels(),
            trunkOpen = s?.trunkOpen == true,
            hoodOpen = s?.hoodOpen == true,
            tireWarning = s?.tirePressureLamp?.hasWarning == true,
            battery12v = s?.battery?.batSoc,
            lowFuel = s?.lowFuelLight == true,
            washerLow = s?.washerFluidStatus == true,
            brakeLow = s?.breakOilStatus == true,
            keyFobLow = s?.smartKeyBatteryWarning == true,
            odometer = v.odometer,
            lat = coord?.lat,
            lon = coord?.lon,
            hasLiveStatus = s != null,
        )
    }
}
