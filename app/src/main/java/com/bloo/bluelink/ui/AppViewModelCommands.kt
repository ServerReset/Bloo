package com.bloo.bluelink.ui

import com.bloo.bluelink.data.CarCommandGate
import com.bloo.bluelink.data.CarCommandIds
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.BlueLinkException
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.ReservChargeInfos
import com.bloo.bluelink.data.TargetSOC
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.saveClimate
import com.bloo.bluelink.data.toGeoLocation
import com.bloo.bluelink.rethrowIfCancellation
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

// --- Live device location + remote vehicle commands (extracted from AppViewModel) --
//
// Each command is a thin call to runCommand with a "vin:action" key (independent pending/double-tap guard
// per action), a success message, an optional optimistic status patch, and the suspend `block`.
// See runCommand for pending/optimistic/statusMutex/rollback behaviour.

/**
 * Starts the continuous [UiState.deviceLocation] subscription
 * ([com.bloo.bluelink.autolock.LocationHelper.liveUpdates]). [restart] replaces an already-running
 * one. The flow CLOSES with no location permission, so a permission-less start leaves a COMPLETED
 * job behind rather than a dead-but-"active" one -- [ensureLiveDeviceLocation] can then start a
 * working one the instant permission is granted.
 */
fun AppViewModel.beginLiveDeviceLocation(restart: Boolean = false) {
    if (!restart && liveLocationJob?.isActive == true) return
    liveLocationJob?.cancel()
    liveLocationJob = viewModelScope.launch {
        // Only while the app is on screen: the fused-location request is torn down when it leaves (the
        // flow's awaitClose removes the updates) and started again when it returns. It used to keep
        // asking the phone for a fix every 10 seconds for the life of the process, background included.
        appForeground.collectLatest { onScreen ->
            if (!onScreen) return@collectLatest
            // See MIN_DEVICE_LOCATION_INTERVAL_MS/_MOVE_METERS' own doc -- a real device OOM crash
            // traced back to this collector publishing every raw fused-location callback verbatim,
            // which a burst of near-identical fixes (GPS jitter, or the provider "catching up" right
            // after its first-ever fix) turned into hundreds of genuinely distinct GeoLocation values
            // in under a second.
            var lastPublished: android.location.Location? = null
            var lastPublishedAtMs = 0L
            var lastGeocodedAtMs = 0L
            var lastWeatherAtMs = 0L
            com.bloo.bluelink.autolock.LocationHelper.liveUpdates(getApplication()).collect { loc ->
                val now = System.currentTimeMillis()
                val last = lastPublished
                val tooSoonAndTooClose = last != null &&
                    now - lastPublishedAtMs < MIN_DEVICE_LOCATION_INTERVAL_MS &&
                    last.distanceTo(loc) < MIN_DEVICE_LOCATION_MOVE_METERS
                if (tooSoonAndTooClose) return@collect
                lastPublished = loc
                lastPublishedAtMs = now
                _state.update { it.copy(deviceLocation = loc.toDeviceGeoLocation()) }
                // Weather follows the phone, but a forecast does not change by the metre: at most once a
                // minute (each pass can geocode, write the store and fetch).
                val pollScale = com.bloo.bluelink.data.DeviceStatus.policy(getApplication()).pollIntervalMultiplier
                if (lastWeatherAtMs == 0L || now - lastWeatherAtMs >= DEVICE_GEOCODE_INTERVAL_MS * pollScale) {
                    lastWeatherAtMs = now
                    weather.refreshDeviceLocationForWeather(loc)
                    // Keep the weather card's "where you are" block tracking the live fix too, so its
                    // car-vs-you merge decision uses the current position, not a one-shot reading.
                    weather.loadPhoneWeather()
                }
                // Reverse-geocode the place name at most every DEVICE_GEOCODE_INTERVAL_MS (and always
                // until we have one at all), so the location label stays current without a geocode on
                // every fix.
                if (_state.value.devicePlace == null || now - lastGeocodedAtMs >= DEVICE_GEOCODE_INTERVAL_MS) {
                    lastGeocodedAtMs = now
                    reverseGeocode(loc.toDeviceGeoLocation())?.let { place ->
                        _state.update { it.copy(devicePlace = place.full) }
                    }
                }
            }
        }
    }
}

/**
 * Makes sure the live device-location collector is actually running: starts it when it isn't, and
 * does nothing when it already is. Called on every foreground, so a location permission granted from
 * system settings (rather than through the app's own dialog) is picked up without a cold restart.
 */
fun AppViewModel.ensureLiveDeviceLocation() {
    val app = getApplication<android.app.Application>()
    if (!app.hasLocationPermission()) {
        // Permission revoked: drop the collector so a later grant starts a fresh, working one.
        liveLocationJob?.cancel()
        liveLocationJob = null
        return
    }
    if (liveLocationJob?.isActive == true) return
    beginLiveDeviceLocation(restart = true)
}

fun AppViewModel.locate(v: Vehicle) = runCommand(v.vin, CarCommandIds.LOCATE, "Location updated", optimistic = null) {
    // "Locate" is the one button whose entire job is refreshing a position -- the car's, below --
    // so it refreshes the DEVICE's own right alongside it. Fire-and- forget: a slow/missing device
    // fix must not delay or fail the car locate this command exists for.
    refreshDeviceLocation()
    // GPS rides along with a status refresh; prefer it over the rate-limited findMyCar.
    val s = repoFor(v).status(v, refresh = true)
    s?.let { st ->
        _state.update {
            it.copy(
                statuses = it.statuses + (v.vin to st),
                lastFetched = it.lastFetched + (v.vin to System.currentTimeMillis()),
            )
        }
    }
    val statusLoc = s.toGeoLocation()
    // Only hit the rate-limited findMyCar if the status carried no GPS. If it then fails (e.g. the
    // daily locate limit) but we already have a fix, keep showing that rather than throwing a scary
    // error.
    val hadCached = _state.value.locations[v.vin] != null
    val loc = statusLoc ?: try {
        repoFor(v).location(v)
    } catch (e: BlueLinkException) {
        if (hadCached) null else throw e
    }
    when {
        loc != null -> {
            _state.update { it.copy(locations = it.locations.withCar(v.vin, loc)) }
            applyPlace(v, loc)
            loadCarWeather(v, force = true)
            persistCache()
            // persistCache() writes the PHONE's own status cache (statusCache) so the next cold
            // start shows this fix. It does not touch SnapshotStore, which is what the snapshot
            // surfaces read -- so Locate updated the map on screen and nothing else, until the next
            // status refresh happened to run persistSnapshots() for another reason.
            persistSnapshots()
        }
        hadCached -> _state.update {
            it.copy(message = "Showing last known location.", messageType = ToastKind.INFO)
        }
        else -> throw BlueLinkException(
            "Couldn't get the car's location.",
        )
    }
}

// --- Commands (per-action pending + optimistic state flip) -----------

/**
 * The endpoint family (EV vs ICE) comes from [v.isEv], but a user-marked PHEV that the API reports
 * as gas must use the EV climate/charge endpoints.
 */

// Lock/unlock share the "doors" action key, so a lock command in flight blocks a rapid-fire unlock
// (and vice versa) rather than letting them race each other through the API. Each optimistically
// flips VehicleStatus.doorLock the instant the command is accepted.
fun AppViewModel.lock(v: Vehicle) =
    runCommand(v.vin, "doors", "Locked", { it.copy(doorLock = true) }, confirm = { it.doorLock == true }) { repoFor(v).lock(v) }
fun AppViewModel.unlock(v: Vehicle) =
    runCommand(v.vin, "doors", "Unlocked", { it.copy(doorLock = false) }, confirm = { it.doorLock == false }) { repoFor(v).unlock(v) }

// Both share the "hornLights" action key (only one can run at a time) and have no boolean toggle to
// optimistically flip -- these are momentary actions (the car doesn't have a persistent "lights are
// flashing" state worth reflecting), so `optimistic` is null and the UI only shows the pending
// spinner until the command completes.
fun AppViewModel.flashLights(v: Vehicle) = runCommand(v.vin, "hornLights", "Lights flashing", null) { repoFor(v).flashLights(v) }
fun AppViewModel.hornAndLights(v: Vehicle) = runCommand(v.vin, "hornLights", "Horn & lights", null) { repoFor(v).hornAndLights(v) }

/** Turn climate off; optimistically flips [VehicleStatus.airCtrlOn]. */
fun AppViewModel.stopClimate(v: Vehicle) =
    runCommand(v.vin, "climate", "Climate off", { it.copy(airCtrlOn = false) }, confirm = { it.airCtrlOn == false }) {
        repoFor(v).stopClimate(v)
    }

/**
 * Start climate with the given [req] (temp/duration/defrost/seat heating/etc). Shares the "climate"
 * action key with [stopClimate] so starting and stopping can't race each other on the same car.
 */
fun AppViewModel.startClimate(v: Vehicle, req: ClimateRequest) =
    // degLabel converts and suffixes the °F value per the user's unit.
    runCommand(
        v.vin,
        "climate",
        "Climate on (${com.bloo.bluelink.data.degLabel(
            req.tempF.toString(),
            fahrenheit = appearance.value.useFahrenheit,
        )})",
        { it.copy(airCtrlOn = true) },
        confirm = { it.airCtrlOn == true },
    ) {
        repoFor(v).startClimate(v, req)
    }.also {
    persist { saveClimate(v.vin, req) }
}

// These hit EV-only endpoints, so route through electric(v) (forces isEv for user-marked PHEVs).

/** Begin charging; optimistically sets [VehicleStatus.evStatus]'s batteryCharge to true. */
fun AppViewModel.startCharge(v: Vehicle) =
    runCommand(
        v.vin, "charge", "Charging", { it.copy(evStatus = it.evStatus?.copy(batteryCharge = true)) },
        confirm = { it.evStatus?.batteryCharge == true },
    ) {
        repoFor(v).startCharge(electric(v, _state.value))
    }

/** Stop charging; optimistic mirror of [startCharge]. Shares the "charge" key. */
fun AppViewModel.stopCharge(v: Vehicle) =
    runCommand(
        v.vin, "charge", "Charging stopped", { it.copy(evStatus = it.evStatus?.copy(batteryCharge = false)) },
        confirm = { it.evStatus?.batteryCharge == false },
    ) {
        repoFor(v).stopCharge(electric(v, _state.value))
    }

/**
 * Set the AC (L2) and DC (fast) charge-target percentages. Uses its own "chargeLimit" key so it
 * does not block start/stop; no optimistic field maps onto the limits.
 */
fun AppViewModel.setChargeLimits(v: Vehicle, acPercent: Int, dcPercent: Int) =
    runCommand(
        v.vin, "chargeLimit", "Charge limits set (AC $acPercent% / DC $dcPercent%)",
        // Optimistic: the limit is the seam in the hero's charge bar and the live notification;
        // runCommand reverts on refusal.
        { st ->
            val ev = st.evStatus
            if (ev == null) {
                st
            } else {
                st.copy(
                    evStatus = ev.copy(
                        // Replaced wholesale: these two plug types are the entire list and
                        // setChargeTargets sends both.
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
        repoFor(v).setChargeTargets(electric(v, _state.value), acPercent, dcPercent)
    }

/**
 * Starts climate at the temperature the weather where the car is parked calls for -- for when no weather reading is loaded in the
 * app yet (smart climate must not depend on a pebble having been open). The lookup happens before the command takes the account
 * lock; with no location or no weather it says so instead of doing nothing.
 */
fun AppViewModel.startSmartClimate(v: Vehicle, onResolved: (ClimateRequest) -> Unit = {}) {
    viewModelScope.launch {
        val request = com.bloo.bluelink.data.smartClimateRequestFor(getApplication(), v.vin)
        if (request == null) {
            _state.update { it.copy(message = "Smart climate needs the car's location.", messageType = ToastKind.ERROR) }
            return@launch
        }
        // Told before the start goes out, so the sliders can bounce to what it sets.
        onResolved(request)
        startClimate(v, request)
    }
}

/**
 * Runs a command with a per-action spinner; on success logs, shows a message and optimistically
 * flips the cached status.
 */
internal fun AppViewModel.runCommand(
    vin: String,
    action: String,
    success: String,
    optimistic: ((VehicleStatus) -> VehicleStatus)?,
    // What the car's own status says once it has really done it. Given, the app watches for that after the server accepts the
    // command and says so (see [confirmWithCar]); without it the command is only ever "sent".
    confirm: ((VehicleStatus) -> Boolean)? = null,
    block: suspend () -> Unit,
) {
    val key = "$vin:$action"
    viewModelScope.launch {
        // One request per action at a time: a second press while the first is still in flight used to fire a second request
        // and raise its notice again. (The controls are disabled meanwhile; this covers every other way in.)
        if (key in _state.value.pending) return@launch
        // Refused too while a background path (an alert, the widget or the watch) is already sending this command.
        if (!CarCommandGate.begin(key)) return@launch
        val startedAt = System.currentTimeMillis()
        _state.update { it.copy(pending = it.pending + key, message = null) }
        // Snapshot the pre-command status so a failure can revert locally (no network).
        val prior = _state.value.statuses[vin]
        // Apply and persist the optimistic state before the network round-trip.
        if (optimistic != null) {
            _state.update { st ->
                if (st.statuses[vin] != null) {
                    st.copy(statuses = st.statuses + (vin to optimistic(st.statuses.getValue(vin))))
                } else st
            }
            persistSnapshots()
        }
        try {
            // Serialize with status fetches: Hyundai rejects overlapping requests.
            statusMutex.withLock { block() }
            AppLog.log(success)
            recordRemoteAction(vin, success, status = "Success")
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
            if (confirm != null) {
                _state.update { it.copy(awaitingCar = it.awaitingCar + key) }
                confirmWithCar(vin, key, success, confirm)
            }
            // Auto-AI: a command changed the car's state, refresh the summary.
            _state.value.vehicles.firstOrNull { it.vin == vin }?.let { autoSummarize(it) }
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            val msg = e.message ?: "Command failed"
            recordRemoteAction(vin, success, status = "Failed", details = msg)
            AppLog.log("⚠ $msg")
            _state.update { it.copy(message = msg, messageType = ToastKind.ERROR) }
            if (optimistic != null && prior != null) {
                _state.update { st -> st.copy(statuses = st.statuses + (vin to prior)) }
                persistSnapshots()
            }
            // Still schedule a refresh to reconcile; `prior` may be slightly stale.
            viewModelScope.launch {
                _state.value.vehicles.firstOrNull { it.vin == vin }?.let { refreshStatus(it) }
            }
        } finally {
            // Hold the lock for at least MIN_COMMAND_LOCK_MS so a double-tap cannot fire an
            // overlapping request.
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < MIN_COMMAND_LOCK_MS) kotlinx.coroutines.delay(MIN_COMMAND_LOCK_MS - elapsed)
            _state.update { it.copy(pending = it.pending - key) }
            CarCommandGate.end(key)
        }
    }
}

/**
 * Appends one entry to [UiState.remoteActionHistory] for [vin], newest first, in a rolling
 * [REMOTE_ACTION_HISTORY_DAYS]-day window. Called from both [runCommand] resolution paths.
 */
internal fun AppViewModel.recordRemoteAction(vin: String, action: String, status: String, details: String? = null) {
    val entry = RemoteAction(
        id = java.util.UUID.randomUUID().toString(), action = action, timestamp = java.time.Instant.now().toString(),
        status = status, details = details,
    )
    // Pruned by age on every write (the only sweep). An unparseable timestamp is kept rather than
    // dropped.
    val cutoff = java.time.Instant.now().minus(
        java.time.Duration.ofDays(REMOTE_ACTION_HISTORY_DAYS),
    )
    _state.update { st ->
        val existing = st.remoteActionHistory[vin].orEmpty()
        val kept = (listOf(entry) + existing)
            .filter { a ->
                runCatching { java.time.Instant.parse(a.timestamp).isAfter(cutoff) }
                    .getOrDefault(true)
            }
            .take(REMOTE_ACTION_HISTORY_MAX)
        st.copy(remoteActionHistory = st.remoteActionHistory + (vin to kept))
    }
}

/** How often, and for how long, the app asks the car's cloud status whether a command has really happened. */
private const val CONFIRM_INTERVAL_MS = 5_000L
private const val CONFIRM_ATTEMPTS = 9

/**
 * After a command is accepted, the car still has to do it (a lock takes a few seconds to tens of seconds to reach it). This
 * reads the car's own status every few seconds, without waking the car, until [confirm] holds, then says so in a toast
 * ("Locked, confirmed by the car"); if the car has not agreed within about 45 s it says the command was sent but not yet
 * confirmed, and brings the screen to the car's real state instead of the optimistic one. A newer command for the same action
 * replaces the watch, so a quick lock then unlock never reports the first.
 */
private fun AppViewModel.confirmWithCar(vin: String, key: String, success: String, confirm: (VehicleStatus) -> Boolean) {
    commandConfirmJobs[key]?.cancel()
    commandConfirmJobs[key] = viewModelScope.launch {
        val me = kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]
        try {
            val v = _state.value.vehicles.firstOrNull { it.vin == vin } ?: return@launch
            repeat(CONFIRM_ATTEMPTS) {
                kotlinx.coroutines.delay(CONFIRM_INTERVAL_MS)
                val seen = try {
                    statusMutex.withLock { repoFor(v).status(v, refresh = false) }
                } catch (e: Exception) {
                    rethrowIfCancellation(e)
                    null
                }
                if (seen != null && confirm(seen)) {
                    // Take the car's reading in (location, alerts and all), then say it is done.
                    loadStatus(v, refresh = false, errorMessage = "Refresh failed", surfaceErrors = false)
                    AppLog.log("$success: confirmed by the car")
                    _state.update { it.copy(message = "$success, confirmed by the car", messageType = ToastKind.SUCCESS) }
                    return@launch
                }
            }
            AppLog.log("$success: sent, the car has not confirmed yet")
            loadStatus(v, refresh = false, errorMessage = "Refresh failed", surfaceErrors = false)
            _state.update { it.copy(message = "$success was sent. The car hasn't confirmed yet", messageType = ToastKind.INFO) }
        } finally {
            // Only the newest confirmation clears the mark: one it replaced is cancelled and must not unblur a command it no longer owns.
            if (commandConfirmJobs[key] === me) _state.update { it.copy(awaitingCar = it.awaitingCar - key) }
        }
    }
}
