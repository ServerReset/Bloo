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

    /** Reconnect or user cancel: abort a pending evaluation for [vin], if any. */
    fun cancel(vin: String) {
        jobs[vin]?.cancel()
        _state.update { it + (vin to AutoLockEvalState(detection = DetectionState.ABORTED)) }
        AppLog.log("AutoLock: cancelled for $vin")
    }

    private suspend fun runEvaluation(context: Context, vin: String) = mutexFor(vin).withLock {
        try {
            skipGrace.remove(vin)
            walkAwayConfirmed.remove(vin)
            val settings = SettingsStore(context).autoLockConfig(vin)
            if (!settings.enabled) return@withLock

            val sm = LockStateMachine(settings.useActivityRecognition, settings.useGeofence).also { machines[vin] = it }
            advance(vin, sm.next(DetectionState.IDLE, DetectionEvent.CarBluetoothDisconnected))
            AppLog.log("AutoLock: left the car ($vin) — evaluating.")

            if (settings.useActivityRecognition) ActivityRecognitionManager.start(context)

            // Wait for corroborating signals (activity + geofence) to promote CONFIRMING -> GRACE,
            // or time out and either proceed on the Bluetooth signal alone or skip, per settings.
            val confirmDeadline = System.currentTimeMillis() + CONFIRM_TIMEOUT_MS
            while (stateFor(vin).detection == DetectionState.CONFIRMING) {
                if (System.currentTimeMillis() > confirmDeadline) {
                    val confirmed = walkAwayConfirmed.contains(vin)
                    if ((settings.useActivityRecognition || settings.useGeofence) && !confirmed) {
                        _state.update { it + (vin to AutoLockEvalState(detection = DetectionState.SKIPPED)) }
                        AppLog.log("AutoLock: no walk-away confirmation for $vin — not locking.")
                        return@withLock
                    }
                    advance(vin, DetectionState.GRACE)
                    break
                }
                delay(250)
            }
            if (stateFor(vin).detection == DetectionState.ABORTED) {
                AppLog.log("AutoLock: aborted before grace period ($vin).")
                return@withLock
            }

            // Grace countdown, skippable via "Lock now" or cut short by a cancel/reconnect.
            for (remaining in settings.graceSeconds downTo 1) {
                if (stateFor(vin).detection == DetectionState.ABORTED) return@withLock
                if (skipGrace.contains(vin)) break
                _state.update { it + (vin to AutoLockEvalState(detection = DetectionState.GRACE, graceRemaining = remaining)) }
                delay(1000)
            }
            if (stateFor(vin).detection == DetectionState.ABORTED) return@withLock
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
            // running, leaving Activity Recognition updates registered indefinitely.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                if (settingsUseActivityRecognition(context, vin)) ActivityRecognitionManager.stop(context)
            }
        }
    }

    private suspend fun settingsUseActivityRecognition(context: Context, vin: String): Boolean =
        runCatching { SettingsStore(context).autoLockConfig(vin).useActivityRecognition }.getOrDefault(false)

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
