package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.BlueLinkException
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.ReservChargeInfos
import com.bloo.bluelink.data.TargetSOC
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.toGeoLocation
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import com.bloo.bluelink.data.saveClimate

// --- Live device location + remote vehicle commands (extracted from AppViewModel) --
//
// Each command is a thin call to runCommand with a "vin:action" key (independent pending/double-tap guard
// per action), a success message, an optional optimistic status patch, and the suspend `block`.
// See runCommand for pending/optimistic/statusMutex/rollback behaviour.

/**
 * Starts a continuous [UiState.deviceLocation] subscription ([com.bloo.bluelink.autolock.LocationHelper.liveUpdates])
 * for the app's lifetime; called once from [loadGarageInner], with no matching stop.
 *
 * [restart] replaces an "active" job: liveUpdates emits nothing without permission but never completes,
 * so a job started before the grant would otherwise never be replaced. [rememberLocateAction] passes true after a grant.
 */
fun AppViewModel.beginLiveDeviceLocation(restart: Boolean = false) {
    if (!restart && liveLocationJob?.isActive == true) return
    liveLocationJob?.cancel()
    liveLocationJob = viewModelScope.launch {
        // See MIN_DEVICE_LOCATION_INTERVAL_MS/_MOVE_METERS: publishing every raw fix caused OOM from recompositions.
        var lastPublished: android.location.Location? = null
        var lastPublishedAtMs = 0L
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
            weather.refreshDeviceLocationForWeather(loc)
        }
    }
}

fun AppViewModel.locate(v: Vehicle) = runCommand(v.vin, "locate", "Location updated", optimistic = null) {
    // "Locate" refreshes the device position alongside the car's; fire-and-forget so a slow device fix never delays the car locate.
    refreshDeviceLocation()
    // GPS rides along with a status refresh; prefer it over the rate-limited findMyCar.
    val s = repoFor(v).status(v, refresh = true)
    s?.let { st ->
        // Advance lastFetched too so "updated X ago" and maybeRelock's stale check stay correct.
        _state.update {
            it.copy(
                statuses = it.statuses + (v.vin to st),
                lastFetched = it.lastFetched + (v.vin to System.currentTimeMillis()),
            )
        }
    }
    val statusLoc = s.toGeoLocation()
    // Only call the rate-limited findMyCar if the status had no GPS; on failure keep the existing fix.
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
                _state.update {
                    it.copy(
                        placeNames = it.placeNames + (v.vin to place.full),
                        placeZips = it.placeZips + (v.vin to place.compact),
                    )
                }
            }
            loadCarWeather(v, force = true)
            persistCache()
            // persistCache() updates only the phone's status cache, not SnapshotStore (read by snapshot surfaces), so publish here too.
            persistSnapshots()
        }
        hadCached -> _state.update {
            it.copy(message = "Showing last-known location. Today's live-locate limit is used up.", messageType = "info")
        }
        else -> throw BlueLinkException(
            "Couldn't get the car's location. It may be asleep, out of coverage, or over today's limit.",
        )
    }
}

// --- Commands (per-action pending + optimistic state flip) -----------

/**
 * The endpoint family (EV vs ICE) comes from [v.isEv], but a user-marked PHEV that the API reports as gas
 * must use the EV climate/charge endpoints.
 */

// Lock/unlock share the "doors" key so they cannot race; each optimistically flips VehicleStatus.doorLock.
fun AppViewModel.lock(v: Vehicle) = runCommand(v.vin, "doors", "Locked", { it.copy(doorLock = true) }) { repoFor(v).lock(v) }
fun AppViewModel.unlock(v: Vehicle) = runCommand(v.vin, "doors", "Unlocked", { it.copy(doorLock = false) }) { repoFor(v).unlock(v) }

// Share the "hornLights" key; momentary actions with no state to flip, so `optimistic` is null.
fun AppViewModel.flashLights(v: Vehicle) = runCommand(v.vin, "hornLights", "Lights flashing", null) { repoFor(v).flashLights(v) }
fun AppViewModel.hornAndLights(v: Vehicle) = runCommand(v.vin, "hornLights", "Horn & lights", null) { repoFor(v).hornAndLights(v) }

/** Turn climate off; optimistically flips [VehicleStatus.airCtrlOn] and cancels any pending
 *  [ClimateExtendWorker] chain so a scheduled follow-up cannot turn climate back on. */
fun AppViewModel.stopClimate(v: Vehicle) =
    runCommand(v.vin, "climate", "Climate off", { it.copy(airCtrlOn = false) }) {
        com.bloo.bluelink.work.ClimateExtendWorker.cancel(getApplication(), v.vin)
        repoFor(v).stopClimate(v)
    }

/**
 * Start climate with [req]. Shares the "climate" key with [stopClimate] so they cannot race.
 *
 * The vendor API caps one command at [com.bloo.bluelink.data.CLIMATE_DURATION_RANGE]'s upper bound (10 min);
 * longer runs are split by [com.bloo.bluelink.data.climateChunks]: the first chunk is sent now and
 * [ClimateExtendWorker] sends each next one when the previous elapses.
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
    ) {
        val chunks = com.bloo.bluelink.data.climateChunks(req.durationMinutes)
        // Within the single-command cap: chunks is just [req.durationMinutes].
        repoFor(v).startClimate(v, req.copy(durationMinutes = chunks.first()))
        val remaining = chunks.drop(1).sum()
        val ctx = getApplication<android.app.Application>()
        if (remaining > 0) {
            com.bloo.bluelink.work.ClimateExtendWorker.schedule(
                context = ctx,
                vin = v.vin,
                remainingMinutes = remaining,
                tempF = req.tempF,
                defrost = req.defrost,
                steeringWheelHeat = req.steeringWheelHeat.apiValue,
                seatFrontLeft = req.seatFrontLeft.apiValue,
                seatFrontRight = req.seatFrontRight.apiValue,
                seatRearLeft = req.seatRearLeft.apiValue,
                seatRearRight = req.seatRearRight.apiValue,
                delayMinutes = chunks.first(),
            )
        } else {
            // This request covers everything; clear any chain from a prior, longer request.
            com.bloo.bluelink.work.ClimateExtendWorker.cancel(ctx, v.vin)
        }
    }.also {
    // "Last used" is what the car was last told to do, not where a slider was left.
    viewModelScope.launch { settingsStore.saveClimate(v.vin, req) }
}

// These hit EV-only endpoints, so route through electric(v) (forces isEv for user-marked PHEVs).

/**
 * One-tap climate for surfaces with no room for the Climate pebble (the flip cover's action bar).
 * Resolves the request from the car's last-saved settings (see [com.bloo.bluelink.data.CarAction.TOGGLE_CLIMATE]),
 * falling back to 72F / 10 min if never configured.
 */
fun AppViewModel.toggleClimate(v: Vehicle) {
    if (_state.value.statusFor(v)?.airCtrlOn == true) {
        stopClimate(v)
        return
    }
    viewModelScope.launch {
        val saved = runCatching { loadSavedClimate(v) }.getOrNull()
        startClimate(v, saved ?: ClimateRequest(tempF = 72, defrost = false, durationMinutes = 10))
    }
}

/** Begin charging; optimistically sets [VehicleStatus.evStatus]'s batteryCharge to true. */
fun AppViewModel.startCharge(v: Vehicle) =
    runCommand(v.vin, "charge", "Charging", { it.copy(evStatus = it.evStatus?.copy(batteryCharge = true)) }) {
        repoFor(v).startCharge(electric(v, _state.value))
    }

/** Stop charging; optimistic mirror of [startCharge]. Shares the "charge" key. */
fun AppViewModel.stopCharge(v: Vehicle) =
    runCommand(v.vin, "charge", "Charging stopped", { it.copy(evStatus = it.evStatus?.copy(batteryCharge = false)) }) {
        repoFor(v).stopCharge(electric(v, _state.value))
    }

/** Set the AC (L2) and DC (fast) charge-target percentages. Uses its own "chargeLimit" key so it does not
 *  block start/stop; no optimistic field maps onto the limits. */
fun AppViewModel.setChargeLimits(v: Vehicle, acPercent: Int, dcPercent: Int) =
    runCommand(
        v.vin, "chargeLimit", "Charge limits set (AC $acPercent% / DC $dcPercent%)",
        // Optimistic: the limit is the seam in the hero's charge bar and the live notification; runCommand reverts on refusal.
        { st ->
            val ev = st.evStatus
            if (ev == null) {
                st
            } else {
                st.copy(
                    evStatus = ev.copy(
                        // Replaced wholesale: these two plug types are the entire list and setChargeTargets sends both.
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
 * Runs a command with a per-action spinner; on success logs, shows a message and optimistically flips the cached status.
 */
internal fun AppViewModel.runCommand(
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
            // Auto-AI: a command changed the car's state, refresh the summary.
            _state.value.vehicles.firstOrNull { it.vin == vin }?.let { autoSummarize(it) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val msg = e.message ?: "Command failed"
            recordRemoteAction(vin, success, status = "Failed", details = msg)
            AppLog.log("⚠ $msg")
            _state.update { it.copy(message = msg, messageType = "error") }
            // Revert the optimistic flip locally and re-persist so every surface returns to last-known-good;
            // skipped when `prior` is null (nothing was flipped).
            if (optimistic != null && prior != null) {
                _state.update { st -> st.copy(statuses = st.statuses + (vin to prior)) }
                persistSnapshots()
            }
            // Still schedule a refresh to reconcile; `prior` may be slightly stale.
            viewModelScope.launch {
                _state.value.vehicles.firstOrNull { it.vin == vin }?.let { refreshStatus(it) }
            }
        } finally {
            // Hold the lock for at least MIN_COMMAND_LOCK_MS so a double-tap cannot fire an overlapping request.
            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed < MIN_COMMAND_LOCK_MS) {
                kotlinx.coroutines.delay(MIN_COMMAND_LOCK_MS - elapsed)
            }
            _state.update { it.copy(pending = it.pending - key) }
        }
    }
}

/** Appends one entry to [UiState.remoteActionHistory] for [vin], newest first, in a rolling
 *  [REMOTE_ACTION_HISTORY_DAYS]-day window. Called from both [runCommand] resolution paths. */
internal fun AppViewModel.recordRemoteAction(vin: String, action: String, status: String, details: String? = null) {
    val entry = RemoteAction(
        id = java.util.UUID.randomUUID().toString(),
        action = action,
        timestamp = java.time.Instant.now().toString(),
        status = status,
        details = details,
    )
    // Pruned by age on every write (the only sweep). An unparseable timestamp is kept rather than dropped.
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
