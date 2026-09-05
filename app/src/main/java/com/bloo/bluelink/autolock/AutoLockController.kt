package com.bloo.bluelink.autolock

import android.content.Context
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.BlueLinkGate
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.WearAction
import com.bloo.bluelink.data.WearCommand
import com.bloo.bluelink.data.repositoryFor
import com.bloo.bluelink.data.runCarCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Snapshot the UI/notification observe for one car's in-flight (or last) evaluation. */
data class AutoLockEvalState(
    val detection: DetectionState = DetectionState.IDLE,
    val graceRemaining: Int = 0,
    val statusSummary: String? = null,
    val lastLockAtEpochMs: Long? = null,
)

/**
 * Single source of truth for AutoLock evaluations, one per VIN. Ported from i5-AutoLock's
 * `AutoLockController` (github.com/Vel-San/i5-AutoLock) onto Bloo's own multi-brand vehicle
 * plumbing: [repositoryFor]/[BlueLinkGate] to read status, [runCarCommand] (the same path
 * the app UI, widgets, QS tiles and the watch already use) to send the lock.
 *
 * A plain object, not a Hilt-injected singleton -- matching how every other cross-cutting
 * background feature in this app (`LiveCharge`, `CarAlerts`, `Notifications`) is a bare
 * object taking a [Context] per call, since Bloo has no DI framework.
 *
 * Per-VIN state lets two cars with AutoLock configured (each paired to a different car
 * Bluetooth device) run independent evaluations without racing each other.
 */
object AutoLockController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // ConcurrentHashMap, not a plain HashMap guarded by nothing: the Bluetooth receiver's
    // coroutine, the service's, and a geofence/activity receiver's can all touch these for
    // different VINs (or race the same one) from different threads.
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val machines = java.util.concurrent.ConcurrentHashMap<String, LockStateMachine>()

    private val skipGrace = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val walkAwayConfirmed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val _state = MutableStateFlow<Map<String, AutoLockEvalState>>(emptyMap())
    val state: StateFlow<Map<String, AutoLockEvalState>> = _state

    fun stateFor(vin: String): AutoLockEvalState = _state.value[vin] ?: AutoLockEvalState()

    private fun mutexFor(vin: String): Mutex = locks.getOrPut(vin) { Mutex() }

    /** Bluetooth (or manual "simulate leaving") trigger: begin an evaluation for [vin]. */
    fun onTriggerFired(context: Context, vin: String) {
        jobs[vin]?.cancel()
        jobs[vin] = scope.launch { runEvaluation(context.applicationContext, vin) }
    }

    /** Skip the remaining grace period and lock immediately (notification "Lock now"). */
    fun lockNow(context: Context, vin: String) {
        skipGrace.add(vin)
        if (jobs[vin]?.isActive != true) onTriggerFired(context, vin)
    }

    fun onWalkingConfirmed(vin: String) {
        walkAwayConfirmed.add(vin)
        machines[vin]?.let { advance(vin, it.next(stateFor(vin).detection, DetectionEvent.WalkingConfirmed)) }
    }

    /** Activity Recognition transitions are app-wide (one Google Play Services request, not
     *  per-VIN), so a "walking" transition confirms every evaluation currently waiting on it. */
    fun onWalkingConfirmedAny() {
        _state.value.filterValues { it.detection == DetectionState.CONFIRMING }.keys.forEach { onWalkingConfirmed(it) }
    }

    fun onMovedBeyondGeofence(vin: String) {
        walkAwayConfirmed.add(vin)
        machines[vin]?.let { advance(vin, it.next(stateFor(vin).detection, DetectionEvent.MovedBeyondGeofence)) }
    }

    /** Reconnect or user cancel: abort a pending evaluation for [vin], if there is one.
     *
     * The Bluetooth receiver calls this on EVERY reconnect to a car with AutoLock enabled --
     * which, for most drivers most days, means "you just got in your car with nothing
     * pending", not "an evaluation was actually running". Guarded on the job actually being
     * active so that ordinary case doesn't log a misleading "cancelled" for a car that was
     * never mid-evaluation, and doesn't push a spurious ABORTED entry into `state` (and
     * therefore into the Settings screen's live status line) for no reason. */
    fun cancel(vin: String) {
        val job = jobs[vin] ?: return
        if (!job.isActive) return
        job.cancel()
        _state.update { it + (vin to AutoLockEvalState(detection = DetectionState.ABORTED)) }
        AppLog.log("AutoLock: cancelled for $vin")
    }

    /** Full sign-out: drop every trace of these cars from live memory -- an in-flight
     *  evaluation, its state machine, and its notification/UI state -- rather than only
     *  [cancel]ling and leaving stale [DetectionState.ABORTED] entries and machines behind
     *  for VINs that no longer exist. Settings' own persisted config is cleared separately
     *  (see [com.bloo.bluelink.data.SettingsStore.clearAllAutoLockConfigs]). */
    fun forgetAll(vins: Collection<String>) {
        if (vins.isEmpty()) return
        vins.forEach { vin ->
            jobs.remove(vin)?.cancel()
            locks.remove(vin)
            machines.remove(vin)
            skipGrace.remove(vin)
            walkAwayConfirmed.remove(vin)
        }
        _state.update { it - vins.toSet() }
    }

    private suspend fun runEvaluation(context: Context, vin: String) = mutexFor(vin).withLock {
        // Declared OUTSIDE the try block (and so, unlike a val inside it, visible to the
        // finally below) and only ever flipped true at the exact point start() is actually
        // called -- the finally's stop() call is paired against what THIS evaluation really
        // started, not a fresh settings re-read that could disagree if the user changed the
        // "confirm with walking" toggle for this car mid-evaluation. A start()/stop() pair
        // that can silently go unbalanced defeats the whole point of the reference count in
        // ActivityRecognitionManager.
        var startedActivityRecognition = false
        try {
            skipGrace.remove(vin)
            walkAwayConfirmed.remove(vin)
            val settings = SettingsStore(context).autoLockConfig(vin)
            if (!settings.enabled) return@withLock

            val sm = LockStateMachine(settings.useActivityRecognition, settings.useGeofence).also { machines[vin] = it }
            advance(vin, sm.next(DetectionState.IDLE, DetectionEvent.CarBluetoothDisconnected))
            AppLog.log("AutoLock: left the car ($vin) — evaluating.")

            if (settings.useActivityRecognition) {
                ActivityRecognitionManager.start(context)
                startedActivityRecognition = true
            }

            // Wait for corroborating signals (activity + geofence) to promote CONFIRMING -> GRACE,
            // or time out and either proceed on the Bluetooth signal alone or skip, per settings.
            // Suspends on the state flow itself rather than polling on a timer: a confirmation
            // (onWalkingConfirmed/onMovedBeyondGeofence) resumes this immediately instead of up
            // to 250ms late, and the coroutine does no work at all in between -- no wakeups, no
            // CPU, for however long the confirmation window is open.
            //
            // No explicit ABORTED check follows this (or the grace countdown below): cancel(vin)
            // cancels THIS coroutine directly, so a reconnect/manual cancel during either wait
            // unwinds via CancellationException at the next suspension point, straight out to the
            // catch clause below -- there is no path that reaches ABORTED without also cancelling
            // the very job that would otherwise go on to check for it.
            val leftConfirming = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) {
                _state.first { (it[vin]?.detection ?: DetectionState.IDLE) != DetectionState.CONFIRMING }
            }
            if (leftConfirming == null) {
                // Timed out still CONFIRMING: proceed on the Bluetooth signal alone unless a
                // still-enabled confirmation signal was required and never arrived.
                if ((settings.useActivityRecognition || settings.useGeofence) && !walkAwayConfirmed.contains(vin)) {
                    _state.update { it + (vin to AutoLockEvalState(detection = DetectionState.SKIPPED)) }
                    AppLog.log("AutoLock: no walk-away confirmation for $vin — not locking.")
                    return@withLock
                }
                advance(vin, DetectionState.GRACE)
            }

            // Grace countdown, skippable via "Lock now" (skipGrace) or cut short by a cancel/
            // reconnect (job cancellation, see above). This loop DOES need its own per-second
            // tick, unlike the wait above -- it's driving a countdown the notification and
            // Settings UI actually display, not just watching for a state change.
            for (remaining in settings.graceSeconds downTo 1) {
                if (skipGrace.contains(vin)) break
                _state.update { it + (vin to AutoLockEvalState(detection = DetectionState.GRACE, graceRemaining = remaining)) }
                delay(1000)
            }
            advance(vin, DetectionState.VERIFYING)

            // Read the CACHED status -- never wake the car for the pre-lock check, matching
            // AlertWorker's own refresh=false convention for background polls.
            val v = SnapshotStore(context).current().vehicles.firstOrNull { it.vin == vin }?.toVehicle()
            if (v == null) {
                fail(vin, "Car no longer found.")
                return@withLock
            }
            val status = runCatching { statusFor(context, v) }.getOrElse {
                fail(vin, "Could not read vehicle status: ${it.message}")
                return@withLock
            }
            if (status == null) {
                fail(vin, "Could not read vehicle status.")
                return@withLock
            }

            when (val decision = LockPolicy.decide(status, dontLockIfOpen = settings.dontLockIfOpen)) {
                is LockDecision.Skip -> {
                    _state.update { it + (vin to AutoLockEvalState(detection = DetectionState.SKIPPED)) }
                    AppLog.log("AutoLock: skipped for $vin — ${decision.reason}.")
                }
                LockDecision.Lock -> performLock(context, vin, settings)
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            fail(vin, "AutoLock hit an error: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            // NonCancellable: this cleanup must run even when the finally is reached because
            // cancel(vin) already cancelled this exact coroutine (a reconnect during the
            // evaluation) -- a plain suspend call here would throw immediately instead of
            // running, leaving Activity Recognition updates registered indefinitely (or, with
            // two cars concurrently confirming, permanently unbalancing the reference count
            // in ActivityRecognitionManager -- see its own doc).
            if (startedActivityRecognition) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    ActivityRecognitionManager.stop(context)
                }
            }
        }
    }

    private suspend fun statusFor(context: Context, v: Vehicle): VehicleStatus? {
        val repo = repositoryFor(Brand.fromIndicator(v.brandIndicator), SessionStore(context), CredentialStore(context))
        return withTimeoutOrNull(30_000) { BlueLinkGate.statusMutex.withLock { repo.status(v, refresh = false) } }
    }

    private suspend fun performLock(context: Context, vin: String, settings: AutoLockConfig) {
        _state.update { it + (vin to AutoLockEvalState(detection = DetectionState.LOCKING)) }
        if (settings.dryRun) {
            AppLog.log("AutoLock DRY RUN: would have locked $vin now. (No command sent.)")
            _state.update {
                it + (vin to AutoLockEvalState(detection = DetectionState.LOCKED, lastLockAtEpochMs = System.currentTimeMillis()))
            }
            return
        }
        val result = runCatching { runCarCommand(context, WearCommand(vin, WearAction.LOCK)) }.getOrNull()
        if (result?.ok == true) {
            AppLog.log("AutoLock: car locked automatically ($vin).")
            _state.update {
                it + (vin to AutoLockEvalState(detection = DetectionState.LOCKED, lastLockAtEpochMs = System.currentTimeMillis()))
            }
        } else {
            fail(vin, "AutoLock: lock command failed${result?.message?.let { " — $it" } ?: ""}.")
        }
    }

    private fun fail(vin: String, message: String) {
        AppLog.log("⚠ $message")
        _state.update { it + (vin to AutoLockEvalState(detection = DetectionState.ERROR)) }
    }

    private fun advance(vin: String, next: DetectionState) {
        _state.update { it + (vin to it[vin].let { s -> (s ?: AutoLockEvalState()).copy(detection = next) }) }
    }

    private const val CONFIRM_TIMEOUT_MS = 20_000L
}
