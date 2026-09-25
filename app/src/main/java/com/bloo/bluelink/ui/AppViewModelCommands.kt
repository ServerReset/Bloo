package com.bloo.bluelink.ui

import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.BlueLinkException
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.ReservChargeInfos
import com.bloo.bluelink.data.TargetSOC
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.toGeoLocation
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

// --- Live device location + remote vehicle commands (extracted from AppViewModel) --
//
// Every remote command below (lock/unlock, lights, climate, charging) is a
// thin one-liner that just calls runCommand with:
//   - a "vin:action" key so its own pending-spinner / MIN_COMMAND_LOCK_MS
//     double-tap guard is independent of every other action on the car
//     (locking doesn't block a simultaneous climate command, etc.),
//   - a success message logged to AppLog and surfaced as a toast,
//   - an optional "optimistic" lambda that patches the cached
//     VehicleStatus immediately (before the network call returns) so the
//     UI flips state right away instead of waiting out a full round trip
//     -- null for commands with no simple boolean to flip (lights,
//     charge-limit), and
//   - the suspend `block` that actually calls the repository.
// See runCommand's own doc comment below for exactly how the pending set,
// the optimistic patch, the statusMutex serialization, and the failure
// rollback (a follow-up refreshStatus) all fit together.

/**
 * Starts a continuous [UiState.deviceLocation] subscription -- see
 * [com.bloo.bluelink.autolock.LocationHelper.liveUpdates] -- for the rest
 * of this ViewModel's (i.e. the app's) lifetime. Reported directly: the
 * map's own "you are here" dot and weather's distance-to-car only ever
 * reflected wherever the phone was at the last pull-to-refresh, not
 * "every minute or two while inside the app" as asked for. Called once
 * from [loadGarageInner] (app open); no matching stop call exists on
 * purpose -- same as the one-shot [refreshDeviceLocation], this rides out
 * the process's own lifetime rather than a composable's.
 *
 * [restart] forces a fresh subscription even if one is already "active". Needed
 * because [com.bloo.bluelink.autolock.LocationHelper.liveUpdates] emits nothing at
 * all without permission but never actually completes either (its own doc explains
 * why) -- so a job started before the user had granted ACCESS_FINE_LOCATION sits
 * "active" forever, silently producing nothing, and the plain no-arg guard below
 * would refuse to ever replace it even once permission exists. [rememberLocateAction]
 * passes true right after a fresh grant so the live dot actually starts working that
 * same session instead of needing an app restart -- reported as "the map still
 * doesn't show the location of the phone" even after granting the permission.
 */
fun AppViewModel.beginLiveDeviceLocation(restart: Boolean = false) {
    if (!restart && liveLocationJob?.isActive == true) return
    liveLocationJob?.cancel()
    liveLocationJob = viewModelScope.launch {
        // See MIN_DEVICE_LOCATION_INTERVAL_MS/_MOVE_METERS' own doc -- a real device OOM
        // crash traced back to this collector publishing every raw fused-location callback
        // verbatim, which a burst of near-identical fixes (GPS jitter, or the provider
        // "catching up" right after its first-ever fix) turned into hundreds of genuinely
        // distinct GeoLocation values in under a second, each one recomposing LocationPebble
        // (and, on a real report, its whole host page) via its own stateSlice.
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
            _state.update {
                it.copy(
                    deviceLocation = GeoLocation(
                        loc.latitude,
                        loc.longitude,
                        if (loc.hasSpeed()) loc.speed.toDouble() else null,
                    ),
                )
            }
            weather.refreshDeviceLocationForWeather(loc)
        }
    }
}

fun AppViewModel.locate(v: Vehicle) = runCommand(v.vin, "locate", "Location updated", optimistic = null) {
    // "Locate" is the one button whose entire job is refreshing a position -- the
    // car's, below -- so it refreshes the DEVICE's own right alongside it. Fire-and-
    // forget: a slow/missing device fix must not delay or fail the car locate this
    // command exists for.
    refreshDeviceLocation()
    // The GPS rides along with a status refresh (this is what the official app
    // uses); prefer it over the heavily rate-limited findMyCar, which is the
    // thing that throws "exceeded the daily remote service request limit".
    val s = repoFor(v).status(v, refresh = true)
    s?.let { st ->
        // Advance lastFetched too (like loadStatus does) -- otherwise the
        // card's "updated X ago" stays stuck at the old time and maybeRelock's
        // stale check can wrongly nudge "pull to refresh" right after a Locate.
        _state.update {
            it.copy(
                statuses = it.statuses + (v.vin to st),
                lastFetched = it.lastFetched + (v.vin to System.currentTimeMillis()),
            )
        }
    }
    val statusLoc = s.toGeoLocation()
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
                _state.update {
                    it.copy(
                        placeNames = it.placeNames + (v.vin to place.full),
                        placeZips = it.placeZips + (v.vin to place.compact),
                    )
                }
            }
            loadCarWeather(v, force = true)
            persistCache()
            // persistCache() writes the PHONE's own status cache (statusCache) so the
            // next cold start shows this fix. It does not touch SnapshotStore, which is
            // what the snapshot surfaces read -- so Locate updated the
            // map on screen and nothing else, until the next status refresh happened to
            // run persistSnapshots() for another reason. Publish it here too.
            persistSnapshots()
        }
        hadCached -> _state.update {
            it.copy(message = "Showing last-known location. A live locate is over today's limit. Try again later.", messageType = "info")
        }
        else -> throw BlueLinkException(
            "Couldn't get the car's location. It may be asleep, out of coverage, or over " +
                "the daily location-lookup limit. Try again later.",
        )
    }
}

// --- Commands (per-action pending + optimistic state flip) -----------

/**
 * The endpoint family (EV vs ICE) is chosen from [v.isEv], but the user can
 * mark a car as a plug-in hybrid that the API reports as gas. Honour that so
 * PHEVs use the EV climate/charge endpoints.
 */

// Lock/unlock share the "doors" action key, so a lock command in flight
// blocks a rapid-fire unlock (and vice versa) rather than letting them
// race each other through the API. Each optimistically flips
// VehicleStatus.doorLock the instant the command is accepted.
fun AppViewModel.lock(v: Vehicle) = runCommand(v.vin, "doors", "Locked", { it.copy(doorLock = true) }) { repoFor(v).lock(v) }
fun AppViewModel.unlock(v: Vehicle) = runCommand(v.vin, "doors", "Unlocked", { it.copy(doorLock = false) }) { repoFor(v).unlock(v) }

// Both share the "hornLights" action key (only one can run at a time) and
// have no boolean toggle to optimistically flip -- these are momentary
// actions (the car doesn't have a persistent "lights are flashing" state
// worth reflecting), so `optimistic` is null and the UI only shows the
// pending spinner until the command completes.
fun AppViewModel.flashLights(v: Vehicle) = runCommand(v.vin, "hornLights", "Lights flashing", null) { repoFor(v).flashLights(v) }
fun AppViewModel.hornAndLights(v: Vehicle) = runCommand(v.vin, "hornLights", "Horn & lights", null) { repoFor(v).hornAndLights(v) }

/** Turn climate off; optimistically flips [VehicleStatus.airCtrlOn] to
 *  false so the climate toggle in the UI responds immediately. Also cancels
 *  any pending [ClimateExtendWorker] chain for this car -- otherwise a
 *  scheduled follow-up command from an earlier, longer request could fire
 *  minutes later and silently turn climate back on after the user just
 *  turned it off. */
fun AppViewModel.stopClimate(v: Vehicle) =
    runCommand(v.vin, "climate", "Climate off", { it.copy(airCtrlOn = false) }) {
        com.bloo.bluelink.work.ClimateExtendWorker.cancel(getApplication(), v.vin)
        repoFor(v).stopClimate(v)
    }

/**
 * Start climate with the given [req] (temp/duration/defrost/seat
 * heating/etc). Shares the "climate" action key with [stopClimate] so
 * starting and stopping can't race each other on the same car.
 *
 * The vendor API caps a single remote-start command's duration at
 * [com.bloo.bluelink.data.CLIMATE_DURATION_RANGE]'s upper bound (10
 * minutes) -- there's no such thing as a car-side "run for 25 minutes"
 * command. A longer [req.durationMinutes] (the "Run time" slider now goes
 * up to [com.bloo.bluelink.data.CLIMATE_EXTENDED_DURATION_RANGE]'s 30) is
 * chained instead: [com.bloo.bluelink.data.climateChunks] splits it into
 * chunks the car CAN run one at a time, this sends the first chunk right
 * now same as ever, and schedules [ClimateExtendWorker] to send each
 * following chunk the moment the one before it elapses -- so from the
 * car's perspective climate just keeps running past what any single
 * command could hold it at.
 */
fun AppViewModel.startClimate(v: Vehicle, req: ClimateRequest) =
    // degLabel, not an inline "°F". req.tempF is a °F Int, but this status label was
    // appending "°F" unconditionally -- so a metric user saw "Climate on (72°F)" while every
    // other temperature in the app respected their unit. degLabel converts and suffixes per
    // the chosen unit, and owns the rounding rule the rest of the app already routes through.
    runCommand(
        v.vin,
        "climate",
        "Climate on (${com.bloo.bluelink.data.degLabel(
            req.tempF.toString(),
            fahrenheit = appearance.value.unitSystem != "metric",
        )})",
        { it.copy(airCtrlOn = true) },
    ) {
        val chunks = com.bloo.bluelink.data.climateChunks(req.durationMinutes)
        // Unchanged behavior for every request already within the single-
        // command cap: chunks is just [req.durationMinutes] and this is
        // the same call it always was.
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
            // This request alone covers everything -- clear any chain a
            // PRIOR, longer-running request might still have pending, so
            // it can't extend climate past what this shorter run intends.
            com.bloo.bluelink.work.ClimateExtendWorker.cancel(ctx, v.vin)
        }
    }

// startCharge/stopCharge/setChargeLimits all route the vehicle through
// electric(v) before calling the repository -- unlike the commands above,
// these hit EV-only endpoints, so a car the user has manually marked as a
// PHEV (which the API itself may still report as gas/isEv=false) needs
// isEv forced true here or the call would go to the wrong (ICE) endpoint.

/**
 * One-tap climate, for surfaces with no room for the full Climate pebble
 * (the flip cover's action bar).
 *
 * Starting climate needs a whole [ClimateRequest]; every other one-tap
 * surface resolves that the same way, from the car's last-saved settings (see
 * [com.bloo.bluelink.data.CarAction.TOGGLE_CLIMATE]). This does the same
 * rather than inventing a second answer, falling back to a plain 72F /
 * 10-minute run only when the car has never had climate configured at all.
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

/** Begin charging; optimistically sets [VehicleStatus.evStatus]'s
 *  batteryCharge to true (a no-op patch if evStatus is itself null, since
 *  the nested `?.copy` on a null receiver stays null). */
fun AppViewModel.startCharge(v: Vehicle) =
    runCommand(v.vin, "charge", "Charging", { it.copy(evStatus = it.evStatus?.copy(batteryCharge = true)) }) {
        repoFor(v).startCharge(electric(v, _state.value))
    }

/** Stop charging; mirrors [startCharge]'s optimistic-patch shape but with
 *  the flag flipped false. Shares the "charge" action key with it. */
fun AppViewModel.stopCharge(v: Vehicle) =
    runCommand(v.vin, "charge", "Charging stopped", { it.copy(evStatus = it.evStatus?.copy(batteryCharge = false)) }) {
        repoFor(v).stopCharge(electric(v, _state.value))
    }

/** Set the AC (slow/L2) and DC (fast) charge-target percentages. Its own
 *  "chargeLimit" action key (distinct from "charge") so setting limits
 *  doesn't block a concurrent start/stop-charge tap, and vice versa; no
 *  optimistic patch since VehicleStatus doesn't carry a single field that
 *  maps cleanly onto "the limits are now X/Y" the way charging on/off does. */
fun AppViewModel.setChargeLimits(v: Vehicle, acPercent: Int, dcPercent: Int) =
    runCommand(
        v.vin, "chargeLimit", "Charge limits set (AC $acPercent% / DC $dcPercent%)",
        // Optimistic, like every other command here. The limit isn't just a
        // number in a settings row any more -- it's the seam in the hero's
        // charge bar, and the Point on the live notification. Waiting for a round-trip and
        // a poll before any of those move makes tapping Set look like it
        // did nothing. Reverted locally by runCommand if the car refuses.
        { st ->
            val ev = st.evStatus
            if (ev == null) {
                st
            } else {
                st.copy(
                    evStatus = ev.copy(
                        // Replaced wholesale rather than merged: these two
                        // plug types are the entire list the API reports,
                        // and setChargeTargets always sends both.
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
 * Runs a command tracking a per-action spinner. On success it logs, shows a
 * message, and optimistically flips the cached status so the toggle updates.
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
        // Snapshot the pre-command status so a failed command can be reverted
        // LOCALLY (no network) — see the catch block. Without this, a command
        // that fails offline left the optimistic value ("Locked") persisted to
        // the snapshot with no way back except a successful poll.
        val prior = _state.value.statuses[vin]
        // Apply the optimistic state and persist it immediately so the
        // snapshot reflects the expected outcome before the network round-trip completes.
        if (optimistic != null) {
            _state.update { st ->
                if (st.statuses[vin] != null) {
                    st.copy(statuses = st.statuses + (vin to optimistic(st.statuses.getValue(vin))))
                } else st
            }
            persistSnapshots()
        }
        try {
            // Serialize with status fetches: Hyundai rejects overlapping
            // requests with "a previous request is pending".
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
            val msg = e.message ?: "Command failed"
            recordRemoteAction(vin, success, status = "Failed", details = msg)
            AppLog.log("⚠ $msg")
            _state.update { it.copy(message = msg, messageType = "error") }
            // Revert the optimistic flip LOCALLY first, then re-persist, so every
            // surface (app + snapshot) returns to last-known-good
            // immediately — without depending on a network refresh that will
            // usually fail for the same reason the command did. Guarded on
            // `prior != null` (a null prior means nothing was flipped, since the
            // optimistic patch only applies when statuses[vin] != null; leave state
            // untouched rather than dropping a status a concurrent poll just added).
            if (optimistic != null && prior != null) {
                _state.update { st -> st.copy(statuses = st.statuses + (vin to prior)) }
                persistSnapshots()
            }
            // Still schedule a refresh as follow-up reconciliation: `prior` may be
            // slightly stale vs live data, but if the refresh also fails/returns
            // null every surface now sits at last-known-good, not the wrong value.
            viewModelScope.launch {
                _state.value.vehicles.firstOrNull { it.vin == vin }?.let { refreshStatus(it) }
            }
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

/** Appends one entry to [UiState.remoteActionHistory] for [vin], newest
 *  first, keeping a rolling [REMOTE_ACTION_HISTORY_DAYS]-day window. Called from the two
 *  places [runCommand] resolves (success/catch) -- every remote command
 *  the app issues passes through there, so this one hook covers all of
 *  them without touching each individual call site. */
internal fun AppViewModel.recordRemoteAction(vin: String, action: String, status: String, details: String? = null) {
    val entry = RemoteAction(
        id = java.util.UUID.randomUUID().toString(),
        action = action,
        timestamp = java.time.Instant.now().toString(),
        status = status,
        details = details,
    )
    // Pruned by AGE on every write, which is also what retires entries for a car that is
    // simply not being used any more -- there is no other sweep, so if this did not do it
    // nothing would. An unparseable timestamp is KEPT rather than dropped: it can only come
    // from an entry this app wrote, and silently deleting history because a string did not
    // parse is worse than carrying one stale row until the count backstop takes it.
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
