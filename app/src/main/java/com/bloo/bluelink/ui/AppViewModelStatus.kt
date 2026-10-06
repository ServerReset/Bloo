package com.bloo.bluelink.ui

import android.location.Geocoder
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.toGeoLocation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import com.bloo.bluelink.data.liveChargeDismissed
import com.bloo.bluelink.data.notificationPrefs
import com.bloo.bluelink.data.setLiveChargeDismissed

// --- Per-car status loading and the live-charge bar (extracted from AppViewModel) --

/**
 * Fetches fresh status once per session. Disk-cached data is shown instantly (so no UI flash), but
 * we still pull a live update — otherwise a warm cache would leave the garage permanently stale
 * until a manual refresh.
 */
internal fun AppViewModel.ensureStatus(
    v: Vehicle,
    /**
     * Startup-only instrumentation: when true, logs how long THIS fetch actually took once it
     * succeeds, so [loadGarageInner]'s own cold-start timeline can report when the CURRENT car's
     * status (the one gating its first-visible pebbles) was actually ready, not just when the fetch
     * was dispatched.
     */
    logStartupTiming: Boolean = false,
) {
    if (v.vin in sessionFetched) return
    val startedAt = System.currentTimeMillis()
    // Background load: log failures but don't interrupt with a toast (one flaky car shouldn't spam
    // errors over the others).
    loadStatus(
        v, refresh = false, errorMessage = "Couldn't load status", surfaceErrors = false,
        // onStatusApplied, NOT logSuccess: logSuccess only fires once loadStatus's whole function
        // body finishes, which includes reverseGeocode -- documented as taking up to
        // GEOCODE_TIMEOUT_MS (6 SECONDS) -- plus checkAlerts/persistCache/autoSummarize, none of
        // which the pebbles need. onStatusApplied fires the instant the fetched status actually
        // lands in _state, which is what "ready" needs to mean here.
        onStatusApplied = if (logStartupTiming) {
            {
                logStartup(
                    "loadGarageInner: current car's status applied to state in " +
                        "${System.currentTimeMillis() - startedAt}ms",
                )
            }
        } else null,
        logSuccess = if (logStartupTiming) {
            {
                "loadGarageInner: current car's status fetch FULLY done (incl. geocode/" +
                    "alerts/persist) in ${System.currentTimeMillis() - startedAt}ms " +
                    "(+${System.currentTimeMillis() - coldStartAt}ms since app start)"
            }
        } else null,
        logStartupTiming = logStartupTiming,
    )
}

fun AppViewModel.refreshStatus(v: Vehicle) {
    loadStatus(
        v, refresh = true, errorMessage = "Refresh failed",
        logSuccess = { "Status refreshed for ${v.name}" }, surfaceErrors = true,
    )
    // A manual pull-to-refresh is exactly the moment someone's actively looking at the app and
    // wants everything current -- piggyback the (internally debounced, so this doesn't hammer the
    // network on rapid refreshes) update check here instead of only ever firing once at cold start,
    // which could go a whole session without re-checking.
    checkForUpdate()
    refreshDeviceLocation()
}

/** Fetches one car's status. */
internal fun AppViewModel.loadStatus(
    v: Vehicle,
    refresh: Boolean,
    errorMessage: String,
    // A lazy supplier, not a plain String: a caller timing this fetch (see ensureStatus's own
    // logSuccess param) needs to measure elapsed time at COMPLETION, and a plain String argument is
    // evaluated eagerly at the call site -- before the fetch this is supposedly timing has even
    // started -- which would always read "0ms".
    logSuccess: (() -> String)? = null,
    surfaceErrors: Boolean = true,
    // Fires the instant the fetched status actually lands in `_state` -- i.e. the moment a car's
    // pebbles have real data to show -- NOT when this whole function finishes.
    onStatusApplied: (() -> Unit)? = null,
    // Startup-only: when true, logs two extra checkpoints inside the viewModelScope.launch below --
    // when that coroutine actually starts running, and when it actually acquires statusMutex -- so
    // a slow "status applied" number (measured from ensureStatus's own call, further back) can be
    // split into "time waiting for a coroutine dispatch onto a busy Main thread" vs "time waiting
    // for the mutex" vs "actual network+processing time", instead of one lump sum that could be any
    // of the three.
    logStartupTiming: Boolean = false,
) {
    val calledAt = System.currentTimeMillis()
    // Key the in-flight set on (vin, refresh) so a user pull-to-refresh (refresh=true) is never
    // deduped behind an already-queued background fetch (refresh=false) for the same car --
    // otherwise the manual call returned instantly with no spinner and no live poll.
    val inFlightKey = "${v.vin}:$refresh"
    synchronized(statusInFlight) {
        if (!statusInFlight.add(inFlightKey)) return
        if (surfaceErrors) surfaceInFlight.add(v.vin)
    }
    // Only show the spinner/settle-haptic for user-triggered refreshes; silent background fetches
    // (ensureStatus) run without touching refreshing so the UI stays still and no settle haptic
    // fires when they complete.
    if (surfaceErrors) _state.update { it.copy(refreshing = true) }
    viewModelScope.launch {
        if (logStartupTiming) {
            logStartup(
                "loadStatus: coroutine dispatched in ${System.currentTimeMillis() - calledAt}ms " +
                    "(time for Main to schedule it -- a big number here means Main was busy " +
                    "with something else, not the network)",
            )
        }
        try {
            // Only the network status() call needs the account-wide mutex (Blue Link 502s on
            // overlapping requests). Capture the result and EXIT the lock before running the slow,
            // purely-local follow-up work (checkAlerts' DataStore read, the blocking Geocoder) so
            // it doesn't stall every other car's fetch and the background poller behind it.
            val beforeLockAt = System.currentTimeMillis()
            val s = statusMutex.withLock {
                if (logStartupTiming) {
                    logStartup("loadStatus: statusMutex acquired in ${System.currentTimeMillis() - beforeLockAt}ms")
                }
                repoFor(v).status(v, refresh = refresh)
            }
            s?.let { status ->
                // The status payload carries last-known GPS for free — use it so the map/location
                // works without the rate-limited findMyCar call (this is what the official app
                // does).
                val statusLoc = status.toGeoLocation()
                _state.update { st ->
                    st.copy(
                        statuses = st.statuses + (v.vin to status),
                        lastFetched = st.lastFetched + (v.vin to System.currentTimeMillis()),
                        locations = if (statusLoc != null) {
                            st.locations + (v.vin to statusLoc)
                        } else st.locations,
                    )
                }
                onStatusApplied?.invoke()
                persistSnapshots()
                persistCache()
                checkAlerts(v, status)
                // Auto-AI: refresh the summary off the new data if enabled.
                autoSummarize(v)
                statusLoc?.let { loc ->
                    if (applyPlace(v, loc)) {
                        // Re-persist. Next cold start then showed the wrong place, or none, beside
                        // a correct position. This second write only happens when a label actually
                        // arrived, so the common no-location path pays nothing.
                        persistCache()
                    }
                }
                // Only mark fetched once a non-null status actually arrived, so a car that returned
                // null (e.g. asleep) is retried when viewed.
                sessionFetched.add(v.vin)
            }
            logSuccess?.let { AppLog.log(it()) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val msg = com.bloo.bluelink.data.ResponseFraming.userMessage(e)
                ?: e.message
                ?: errorMessage
            AppLog.log("⚠ ${v.name}: $msg")
            if (surfaceErrors) _state.update { it.copy(message = "${v.name}: $msg", messageType = "error") }
        } finally {
            // Clear refreshing only when no more user-visible (surfaceErrors) fetches remain.
            // Background fetches finishing after a user refresh must not prematurely clear the
            // spinner or trigger the settle haptic.
            val noMoreSurface = synchronized(statusInFlight) {
                statusInFlight.remove(inFlightKey)
                // Only clear this VIN's surface entry if THIS call added it (surfaceErrors=true).
                if (surfaceErrors) surfaceInFlight.remove(v.vin)
                surfaceInFlight.isEmpty()
            }
            if (noMoreSurface) _state.update { it.copy(refreshing = false) }
        }
    }
}

/**
 * Brings the live-charge notification (see [LiveCharge]) in step with whatever the app itself just
 * fetched -- otherwise only the background workers ever wrote it, and the one moment the user has
 * the freshest data (having just pulled to refresh, standing in the app) was the one moment the bar
 * in the shade didn't move.
 */
internal suspend fun AppViewModel.refreshLiveChargeBar(vehicles: List<Vehicle>) {
    if (!settingsStore.notificationPrefs().charging) return
    if (!liveChargeDismissalsResetThisSession) {
        liveChargeDismissalsResetThisSession = true
        vehicles.forEach { v ->
            runCatching {
                if (settingsStore.liveChargeDismissed(v.vin)) settingsStore.setLiveChargeDismissed(v.vin, false)
            }
        }
    }
    val statuses = _state.value.statuses
    var anyCharging = false
    vehicles.forEach { v ->
        // Only cars we actually hold a status for. LiveCharge.update CANCELS the notification when
        // told charging = false, and a missing status produced exactly that -- so opening the app
        // before its own first fetch landed could delete a live bar a background worker had
        // correctly posted, purely because this in-memory map was still empty.
        val status = statuses[v.vin] ?: return@forEach
        val ev = status.evStatus
        if (ev?.batteryCharge == true) anyCharging = true
        runCatching {
            LiveCharge.sync(
                context = getApplication(),
                settings = settingsStore,
                vin = v.vin,
                carName = v.name,
                ev = ev,
            )
        }
    }
    if (anyCharging) com.bloo.bluelink.work.LiveChargePollWorker.kick(getApplication())
}
