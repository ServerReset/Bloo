package com.bloo.wear

import android.app.Application
import android.location.Geocoder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.BlueLinkGate
import com.bloo.bluelink.data.BlueLinkRepository
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.Credentials
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.DoorOpen
import com.bloo.bluelink.data.EvTrip
import com.bloo.bluelink.data.SeatLevel
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.StatusCache
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleRepository
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.WearClimateState
import com.bloo.bluelink.data.WindowOpen
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.openLabels
import com.bloo.bluelink.data.percentFor
import com.bloo.bluelink.data.rangeMiFor
import com.bloo.bluelink.data.repositoryFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale

enum class WearScreen { Loading, SignedOut, Ready }

/** A fully resolved per-car view, merging live status with the phone snapshot. */
data class CarView(
    val vin: String,
    val name: String,
    val model: String,
    val brand: Brand,
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
    val locationName: String?,
    val tripsSupported: Boolean,
    val engineOn: Boolean,
    val accessoryOn: Boolean,
    val defrostOn: Boolean,
    val tempSetting: String?,
    val tireAll: Int?,
    val tireFl: Boolean,
    val tireFr: Boolean,
    val tireRl: Boolean,
    val tireRr: Boolean,
    val steerHeat: Boolean,
    val mirrorHeat: Boolean,
    val rearDefrost: Boolean,
    val seatFl: Int?,
    val seatFr: Int?,
    val seatRl: Int?,
    val seatRr: Int?,
    val battery12vHealth: String?,
    val fuelLevel: Int?,
    val hasLiveStatus: Boolean,
)

/** The editable climate settings for one car (seats are 0–3 heat steps). */
data class ClimateDraft(
    val tempF: Int = 72,
    val duration: Int = 10,
    val defrost: Boolean = false,
    val steering: Boolean = false,
    val seatDriver: Int = 0,   // 0 off, 1 low, 2 med, 3 high (heat)
    val seatPassenger: Int = 0,
    val seatRearLeft: Int = 0,
    val seatRearRight: Int = 0,
    /** Which saved preset (if any) is currently applied; cleared once a control
     *  drifts away from it. */
    val activePresetId: String? = null,
)

data class WearUi(
    val screen: WearScreen = WearScreen.Loading,
    val cars: List<CarView> = emptyList(),
    val trips: Map<String, List<EvTrip>> = emptyMap(),
    val pending: Set<String> = emptySet(),
    val busy: Boolean = false,
    val message: String? = null,
    val presets: Map<String, List<ClimatePreset>> = emptyMap(),
    val extras: com.bloo.bluelink.data.WearExtras = com.bloo.bluelink.data.WearExtras(),
    val accounts: List<String> = emptyList(),
    val phoneConnected: Boolean = false,
    /** Per-car climate draft (sliders/toggles), so each car remembers its own. */
    val climateDrafts: Map<String, ClimateDraft> = emptyMap(),
    val acLimitDraft: Int? = null,
    val dcLimitDraft: Int? = null,
    val settings: com.bloo.bluelink.data.WearSettingsPayload? = null,
    val localSettings: WearLocalSettings = WearLocalSettings(),
    /** Optimistic per-car pebble orders the watch just set, held until the phone
     *  echoes the same order back via [settings]. */
    val pebbleOverride: Map<String, List<String>> = emptyMap(),
) {
    fun draftFor(vin: String): ClimateDraft = climateDrafts[vin] ?: ClimateDraft()

    /** This car's effective pebble order: a pending local change wins, else the
     *  phone-synced order, else the default. */
    fun pebbleOrderFor(vin: String): List<String> =
        pebbleOverride[vin] ?: settings?.pebbleOrders?.get(vin) ?: WearPebbles.DEFAULT_ORDER
}

private fun seatLevelOf(step: Int): SeatLevel = when (step) {
    1 -> SeatLevel.LOW_HEAT
    2 -> SeatLevel.MED_HEAT
    3 -> SeatLevel.HIGH_HEAT
    else -> SeatLevel.OFF
}

/** Inverse of [seatLevelOf]: map a seat level back to the watch's 0–3 heat step
 *  (the watch UI is heat-only, so cooling collapses to off). */
private fun seatStepOf(level: SeatLevel): Int = when (level) {
    SeatLevel.LOW_HEAT -> 1
    SeatLevel.MED_HEAT -> 2
    SeatLevel.HIGH_HEAT -> 3
    else -> 0
}

val seatStepLabels = listOf("Off", "Low", "Med", "High")

class WearViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()
    private val sessionStore = SessionStore(ctx)
    private val credentialStore = CredentialStore(ctx)
    private val snapshotStore = SnapshotStore(ctx)
    private val statusCache = StatusCache(ctx)
    private val repos = mutableMapOf<Brand, VehicleRepository>()
    private val localStore = WearLocalStore(ctx)

    private var vehicles: List<Vehicle> = emptyList()
    private var statuses: Map<String, VehicleStatus> = emptyMap()
    private var snapshots: Map<String, com.bloo.bluelink.data.VehicleSnapshot> = emptyMap()
    private var fetchedAt: Map<String, Long> = emptyMap()
    private var trips: Map<String, List<EvTrip>> = emptyMap()
    private var placeNames: Map<String, String> = emptyMap()
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
            WearSettingsStore(ctx).flow.collect { s ->
                _ui.update { u ->
                    // Drop any optimistic override once the phone has echoed the
                    // same order back, so the synced value takes over cleanly.
                    val stillPending = u.pebbleOverride.filterKeys { vin ->
                        WearPebbles.normalize(s?.pebbleOrders?.get(vin) ?: emptyList()) != u.pebbleOverride[vin]
                    }
                    u.copy(settings = s, pebbleOverride = stillPending)
                }
            }
        }
        viewModelScope.launch {
            WearPresetsStore(ctx).flow.collect { p -> _ui.update { it.copy(presets = p.byVin) } }
        }
        viewModelScope.launch {
            WearExtrasStore(ctx).flow.collect { e -> _ui.update { it.copy(extras = e) } }
        }
        viewModelScope.launch {
            localStore.flow.collect { s -> _ui.update { it.copy(localSettings = s) } }
        }
        viewModelScope.launch {
            WearClimateStore(ctx).flow.collect { remote -> mergeRemoteClimate(remote) }
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
        // Companion mode: rely on phone-synced snapshots. Request a push if we have nothing.
        vehicles = if (snapshots.isNotEmpty()) {
            snapshots.values.map { it.toVehicle() }
        } else {
            runCatching { WearComms.requestSync(ctx, "", refresh = false) }
            emptyList()
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

    /** Re-pull snapshots, sessions and settings the phone has published. */
    fun resync() {
        viewModelScope.launch {
            runCatching { WearComms.pullLatest(ctx) }
            snapshots = snapshotStore.current().vehicles.associateBy { it.vin }
            refreshConnection()
            if (vehicles.isEmpty() && sessionStore.loggedInBrands().isNotEmpty()) loadGarage() else publish()
        }
    }

    fun refreshStatus(vin: String, surface: Boolean = true) {
        mark("$vin:refresh") {
            // Companion-first: ask the phone to refresh and push updated data.
            val relayed = runCatching { WearComms.requestSync(ctx, vin, refresh = true) }.isSuccess
            if (!relayed && surface) _ui.update { it.copy(message = "Couldn't refresh") }
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
        if (st?.airCtrlOn == true) {
            repo.stopClimate(v); flip(vin) { it.copy(airCtrlOn = false) }
            updateDraft(vin) { it.copy(activePresetId = null) }
        } else {
            val d = _ui.value.draftFor(vin)
            repo.startClimate(v, ClimateRequest(
                tempF = d.tempF,
                defrost = d.defrost,
                durationMinutes = d.duration,
                steeringWheelHeat = d.steering,
                seatFrontLeft = seatLevelOf(d.seatDriver),
                seatFrontRight = seatLevelOf(d.seatPassenger),
                seatRearLeft = seatLevelOf(d.seatRearLeft),
                seatRearRight = seatLevelOf(d.seatRearRight),
            ))
            // A manual start isn't a saved preset.
            flip(vin) { it.copy(airCtrlOn = true) }
            updateDraft(vin) { it.copy(activePresetId = null) }
        }
    }

    fun toggleCharge(vin: String) = command(vin, "charge") { v, repo, st ->
        if (st?.evStatus?.batteryCharge == true) repo.stopCharge(v) else repo.startCharge(v)
    }

    /** Apply a saved climate preset (start climate with its exact settings). Also
     *  seeds the sliders so the controls reflect what's running. */
    fun applyPreset(vin: String, preset: ClimatePreset) = command(vin, "climate") { v, repo, _ ->
        repo.startClimate(v, preset.request)
        flip(vin) { it.copy(airCtrlOn = true) }
        val r = preset.request
        updateDraft(vin) {
            it.copy(
                activePresetId = preset.id,
                tempF = r.tempF,
                duration = r.durationMinutes,
                defrost = r.defrost,
                steering = r.steeringWheelHeat,
                seatDriver = seatStepOf(r.seatFrontLeft),
                seatPassenger = seatStepOf(r.seatFrontRight),
                seatRearLeft = seatStepOf(r.seatRearLeft),
                seatRearRight = seatStepOf(r.seatRearRight),
            )
        }
    }

    /** Reverse-geocode the car's coordinates to a human place name. */
    private fun geocode(vin: String, lat: Double, lon: Double) {
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
                    a?.let {
                        listOfNotNull(it.locality ?: it.subAdminArea, it.adminArea)
                            .joinToString(", ").ifBlank { it.getAddressLine(0) }
                    }
                }.getOrNull()
            }
            if (!name.isNullOrBlank()) {
                placeNames = placeNames + (vin to name)
                publish()
            }
        }
    }

    /** Push the AC/DC charge-limit sliders to the car. */
    fun applyChargeLimits(vin: String) = command(vin, "chargeLimit") { v, repo, _ ->
        val u = _ui.value
        repo.setChargeTargets(v, u.acLimitDraft ?: 80, u.dcLimitDraft ?: 90)
    }

    private fun updateDraft(vin: String, f: (ClimateDraft) -> ClimateDraft) {
        _ui.update { u -> u.copy(climateDrafts = u.climateDrafts + (vin to f(u.draftFor(vin)))) }
        publishClimateDrafts()
    }

    /** Mirror the phone's climate draft in, without re-publishing (no echo). */
    private fun mergeRemoteClimate(remote: WearClimateState) {
        if (remote.byVin.isEmpty()) return
        _ui.update { u ->
            val merged = u.climateDrafts.toMutableMap()
            remote.byVin.forEach { (vin, cs) ->
                merged[vin] = ClimateDraft(
                    tempF = cs.tempF,
                    duration = cs.durationMinutes,
                    defrost = cs.defrost,
                    steering = cs.steering,
                    seatDriver = seatStepOf(SeatLevel.fromApi(cs.seatFrontLeft)),
                    seatPassenger = seatStepOf(SeatLevel.fromApi(cs.seatFrontRight)),
                    seatRearLeft = seatStepOf(SeatLevel.fromApi(cs.seatRearLeft)),
                    seatRearRight = seatStepOf(SeatLevel.fromApi(cs.seatRearRight)),
                    activePresetId = cs.activePresetId,
                )
            }
            u.copy(climateDrafts = merged)
        }
    }

    /** Save the current draft as a new named preset, then sync it to the phone. */
    fun saveCurrentAsPreset(vin: String, name: String) {
        val d = _ui.value.draftFor(vin)
        val preset = ClimatePreset(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Preset" },
            request = ClimateRequest(
                tempF = d.tempF,
                defrost = d.defrost,
                durationMinutes = d.duration,
                steeringWheelHeat = d.steering,
                seatFrontLeft = seatLevelOf(d.seatDriver),
                seatFrontRight = seatLevelOf(d.seatPassenger),
                seatRearLeft = seatLevelOf(d.seatRearLeft),
                seatRearRight = seatLevelOf(d.seatRearRight),
            ),
        )
        val updated = _ui.value.presets + (vin to (_ui.value.presets[vin].orEmpty() + preset))
        _ui.update { it.copy(presets = updated) }
        persistAndPublishPresets(updated)
    }

    fun deletePreset(vin: String, id: String) {
        val updated = _ui.value.presets + (vin to _ui.value.presets[vin].orEmpty().filter { it.id != id })
        _ui.update { it.copy(presets = updated) }
        persistAndPublishPresets(updated)
    }

    fun reorderPresets(vin: String, ordered: List<ClimatePreset>) {
        val updated = _ui.value.presets + (vin to ordered)
        _ui.update { it.copy(presets = updated) }
        persistAndPublishPresets(updated)
    }

    private fun persistAndPublishPresets(byVin: Map<String, List<ClimatePreset>>) {
        val wp = com.bloo.bluelink.data.WearPresets(byVin)
        viewModelScope.launch {
            runCatching { WearPresetsStore(ctx).save(com.bloo.bluelink.data.WearSync.encodePresets(wp)) }
            runCatching { WearComms.publishPresets(ctx, wp) }
        }
    }

    /** Push every car's draft to the phone over the shared climate channel. */
    private fun publishClimateDrafts() {
        val byVin = _ui.value.climateDrafts.mapValues { (_, d) ->
            com.bloo.bluelink.data.ClimateSync(
                activePresetId = d.activePresetId,
                tempF = d.tempF,
                durationMinutes = d.duration,
                defrost = d.defrost,
                steering = d.steering,
                seatFrontLeft = seatLevelOf(d.seatDriver).apiValue,
                seatFrontRight = seatLevelOf(d.seatPassenger).apiValue,
                seatRearLeft = seatLevelOf(d.seatRearLeft).apiValue,
                seatRearRight = seatLevelOf(d.seatRearRight).apiValue,
            )
        }
        viewModelScope.launch { runCatching { WearComms.publishClimate(ctx, WearClimateState(byVin)) } }
    }

    fun setClimateTemp(vin: String, value: Int) = updateDraft(vin) { it.copy(tempF = value.coerceIn(62, 82), activePresetId = null) }
    fun setClimateDuration(vin: String, value: Int) = updateDraft(vin) { it.copy(duration = value.coerceIn(1, 10), activePresetId = null) }
    fun toggleDefrost(vin: String) = updateDraft(vin) { it.copy(defrost = !it.defrost, activePresetId = null) }
    fun toggleSteering(vin: String) = updateDraft(vin) { it.copy(steering = !it.steering, activePresetId = null) }
    fun setSeatDriver(vin: String, step: Int) = updateDraft(vin) { it.copy(seatDriver = step.coerceIn(0, 3), activePresetId = null) }
    fun setSeatPassenger(vin: String, step: Int) = updateDraft(vin) { it.copy(seatPassenger = step.coerceIn(0, 3), activePresetId = null) }
    fun setSeatRearLeft(vin: String, step: Int) = updateDraft(vin) { it.copy(seatRearLeft = step.coerceIn(0, 3), activePresetId = null) }
    fun setSeatRearRight(vin: String, step: Int) = updateDraft(vin) { it.copy(seatRearRight = step.coerceIn(0, 3), activePresetId = null) }
    fun setAcLimit(value: Int) { _ui.update { it.copy(acLimitDraft = value.coerceIn(50, 100)) } }
    fun setDcLimit(value: Int) { _ui.update { it.copy(dcLimitDraft = value.coerceIn(50, 100)) } }
    fun dismissMessage() { _ui.update { it.copy(message = null) } }

    fun setFontScale(scale: Float) {
        viewModelScope.launch {
            val clamped = scale.coerceIn(0.8f, 1.4f)
            localStore.setFontScale(clamped)
            // Push back to phone so its Settings slider stays in sync.
            WearComms.publishLocalSettings(ctx, clamped)
        }
    }

    /**
     * Persist a car's reordered pebble order: apply it optimistically so the
     * tiles rearrange instantly, then push it to the phone, which saves it as
     * that car's section order and mirrors it back to every device.
     */
    fun savePebbleOrder(vin: String, order: List<String>) {
        val normalized = WearPebbles.normalize(order)
        _ui.update { it.copy(pebbleOverride = it.pebbleOverride + (vin to normalized)) }
        viewModelScope.launch {
            runCatching { WearComms.publishPebbleOrder(ctx, vin, normalized) }
        }
    }

    /**
     * Smart climate: reads the weather at the car's location (or home weather as
     * fallback), then starts climate at [offset]°F cooler than ambient on a hot
     * day or [offset]°F warmer than ambient on a cold day. Threshold is 70°F.
     */
    fun smartClimate(vin: String, offset: Int = 10) {
        val extras = _ui.value.extras
        val weather = extras.carWeather[vin] ?: extras.homeWeather ?: run {
            _ui.update { it.copy(message = "No weather data — can't run smart climate") }
            return
        }
        val ambientF = ((weather.tempC * 9.0 / 5.0) + 32).toInt()
        val targetF = if (ambientF >= 70) (ambientF - offset).coerceIn(60, 85)
                      else (ambientF + offset).coerceIn(60, 85)
        command(vin, "climate") { v, repo, st ->
            if (st?.airCtrlOn == true) {
                repo.stopClimate(v); flip(vin) { it.copy(airCtrlOn = false) }
                updateDraft(vin) { it.copy(activePresetId = null) }
            } else {
                val d = _ui.value.draftFor(vin)
                repo.startClimate(v, ClimateRequest(
                    tempF = targetF,
                    defrost = false,
                    durationMinutes = d.duration,
                    steeringWheelHeat = d.steering,
                    seatFrontLeft = seatLevelOf(d.seatDriver),
                    seatFrontRight = seatLevelOf(d.seatPassenger),
                    seatRearLeft = seatLevelOf(d.seatRearLeft),
                    seatRearRight = seatLevelOf(d.seatRearRight),
                ))
                flip(vin) { it.copy(airCtrlOn = true) }
                updateDraft(vin) { it.copy(tempF = targetF, activePresetId = null) }
            }
        }
    }

    /** Send an AI summary request to the paired phone via the command channel. */
    fun requestAiSummary(vin: String) {
        val key = "$vin:ai_summary"
        mark(key) {
            runCatching {
                WearComms.send(
                    ctx,
                    com.bloo.bluelink.data.WearCommand(vin = vin, action = com.bloo.bluelink.data.WearAction.AI_SUMMARY),
                )
            }.onFailure { e ->
                _ui.update { it.copy(message = e.message ?: "Could not request summary") }
                AppLog.log("AI summary request failed: ${e.message}")
            }
        }
    }

    private fun command(vin: String, action: String, block: suspend (Vehicle, VehicleRepository, VehicleStatus?) -> Unit) {
        val v = vehicles.firstOrNull { it.vin == vin } ?: return
        mark("$vin:$action") {
            // Companion-first: relay to phone. Only execute locally if no phone is reachable.
            val relayed = runCatching { WearComms.send(ctx, toWearCommand(vin, action)) }.isSuccess
            if (relayed) {
                publish()
                sessionFetched.remove(vin)
                runCatching {
                    androidx.wear.tiles.TileService.getUpdater(ctx)
                        .requestUpdate(com.bloo.wear.tile.BlooTileService::class.java)
                }
                com.bloo.wear.complication.ComplicationLink.requestUpdate(ctx)
            } else {
                // Phone unreachable — fall back to standalone
                runCatching {
                    BlueLinkGate.statusMutex.withLock { block(v, repoFor(v.brand), statuses[vin]) }
                }.onSuccess {
                    publish()
                    sessionFetched.remove(vin)
                    refreshStatus(vin, surface = false)
                    runCatching {
                        androidx.wear.tiles.TileService.getUpdater(ctx)
                            .requestUpdate(com.bloo.wear.tile.BlooTileService::class.java)
                    }
                    com.bloo.wear.complication.ComplicationLink.requestUpdate(ctx)
                }.onFailure { e ->
                    _ui.update { it.copy(message = e.message ?: "Command failed") }
                    AppLog.log("Watch command $action failed: ${e.message}")
                }
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
        tempF = _ui.value.draftFor(vin).tempF,
        defrost = _ui.value.draftFor(vin).defrost,
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
        val lamp = s?.tirePressureLamp
        val gen = v.generation.trim().toIntOrNull() ?: 3
        val gen5w = v.brand != Brand.KIA && gen < 3
        val seats = s?.seatHeaterVentState
        return CarView(
            vin = v.vin,
            name = v.name,
            model = v.model,
            brand = v.brand,
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
            locationName = placeNames[v.vin],
            tripsSupported = !gen5w,
            engineOn = s?.engine == true,
            accessoryOn = s?.acc == true,
            defrostOn = s?.defrost == true,
            tempSetting = s?.airTemp?.value,
            tireAll = s?.tirePressure?.all,
            tireFl = (lamp?.frontLeft ?: 0) != 0,
            tireFr = (lamp?.frontRight ?: 0) != 0,
            tireRl = (lamp?.rearLeft ?: 0) != 0,
            tireRr = (lamp?.rearRight ?: 0) != 0,
            steerHeat = (s?.steerWheelHeat ?: 0) != 0,
            mirrorHeat = (s?.sideMirrorHeat ?: 0) != 0,
            rearDefrost = (s?.sideBackWindowHeat ?: 0) != 0,
            seatFl = seats?.flSeatHeatState,
            seatFr = seats?.frSeatHeatState,
            seatRl = seats?.rlSeatHeatState,
            seatRr = seats?.rrSeatHeatState,
            battery12vHealth = s?.battery?.health,
            fuelLevel = s?.fuelLevel,
            hasLiveStatus = s != null,
        )
    }
}
