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
import com.bloo.bluelink.data.toSnapshot
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.CarCommand
import com.bloo.bluelink.data.CarCommandRunner
import com.bloo.bluelink.data.Notifications
import com.bloo.bluelink.data.notificationPrefs
import com.bloo.bluelink.data.autoLockConfig
import com.bloo.bluelink.data.repositoryFor
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
    val lastOutcome: String? = null,
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
    /** Sign-out: forget every car in [vins] -- cancel its evaluation and drop its state. */
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
        var startedActivityRecognition = false
        try {
            skipGrace.remove(vin)
            walkAwayConfirmed.remove(vin)
            // Physical detection is mandatory: a walk must be confirmed by Activity Recognition before a lock, whatever is saved.
            val settings = SettingsStore(context).autoLockConfig(vin).copy(useActivityRecognition = true)
            if (!settings.enabled) return@withLock

            // Without Activity Recognition no walk can ever be confirmed, so every evaluation would end in silence. Say so instead.
            if (!ActivityRecognitionManager.hasPermission(context)) {
                val carName = carNameFor(context, vin)
                _state.update {
                    it + (vin to AutoLockEvalState(
                        detection = DetectionState.ERROR,
                        lastOutcome = "Can't detect walking. Turn on Activity Recognition for this app.",
                    ))
                }
                AppLog.log("AutoLock: Activity Recognition permission is off, so $vin cannot be locked.")
                notifyNotDetected(context, vin, carName, "Activity Recognition is off, so AutoLock can't tell that you walked away. Turn it on in Settings.")
                return@withLock
            }

            val sm = LockStateMachine(settings.useActivityRecognition, settings.useGeofence).also { machines[vin] = it }
            advance(vin, sm.next(DetectionState.IDLE, DetectionEvent.CarBluetoothDisconnected))
            AppLog.log("AutoLock: left the car ($vin) — evaluating.")

            ActivityRecognitionManager.start(context)
            startedActivityRecognition = true

            // Wait for corroborating signals (activity + geofence) to promote CONFIRMING -> GRACE,
            // or time out and either proceed on the Bluetooth signal alone or skip, per settings.
            val confirmDeadline = System.currentTimeMillis() + CONFIRM_TIMEOUT_MS
            while (stateFor(vin).detection == DetectionState.CONFIRMING) {
                if (System.currentTimeMillis() > confirmDeadline) {
                    val confirmed = walkAwayConfirmed.contains(vin)
                    if ((settings.useActivityRecognition || settings.useGeofence) && !confirmed) {
                        _state.update {
                            it + (vin to AutoLockEvalState(
                                detection = DetectionState.SKIPPED,
                                lastOutcome = "Skipped. No walk was detected, so the car was not locked.",
                            ))
                        }
                        AppLog.log("AutoLock: no walk-away confirmation for $vin — not locking.")
                        notifyNotDetected(context, vin, carNameFor(context, vin), "No walk was detected after you left the car, so it was not locked. Lock it manually if needed.")
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
            val v = ensureVehicleCached(context, vin)?.toVehicle()
            if (v == null) {
                fail(context, vin, "Car no longer found.")
                return@withLock
            }
            val status = runCatching { statusFor(context, v) }.getOrElse {
                fail(context, vin, "Could not read vehicle status: ${it.message}")
                return@withLock
            }
            if (status == null) {
                fail(context, vin, "Could not read vehicle status.")
                return@withLock
            }

            when (val decision = LockPolicy.decide(status, dontLockIfOpen = settings.dontLockIfOpen)) {
                is LockDecision.Skip -> {
                    _state.update {
                        it + (vin to AutoLockEvalState(detection = DetectionState.SKIPPED, lastOutcome = "Skipped. ${decision.reason}."))
                    }
                    AppLog.log("AutoLock: skipped for $vin — ${decision.reason}.")
                }
                LockDecision.Lock -> performLock(context, vin, v.name, settings)
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            fail(context, vin, "AutoLock hit an error: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            // NonCancellable: this cleanup must run even when the finally is reached because
            // cancel(vin) already cancelled this exact coroutine (a reconnect during the
            // evaluation) -- a plain suspend call here would throw immediately instead of
            // running, leaving Activity Recognition updates registered indefinitely.
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                if (startedActivityRecognition) ActivityRecognitionManager.stop(context)
            }
        }
    }

    private suspend fun ensureVehicleCached(context: Context, vin: String): VehicleSnapshot? {
        val store = SnapshotStore(context)
        store.current().vehicles.firstOrNull { it.vin == vin }?.let { return it }
        val sessions = SessionStore(context)
        val credentials = CredentialStore(context)
        for (brand in runCatching { sessions.loggedInBrands() }.getOrDefault(emptyList())) {
            // Inside the shared request lock like every other car request: BlueLink answers 502 to overlapping
            // ones, and an empty result here would read as "car not found" and skip the lock.
            val fetched = runCatching { BlueLinkGate.statusMutex.withLock { repositoryFor(brand, sessions, credentials).vehicles() } }
                .getOrDefault(emptyList())
            if (fetched.isEmpty()) continue
            runCatching { store.saveVehiclesKeepingStatus(fetched.map { it.toSnapshot() }) }
            fetched.firstOrNull { it.vin == vin }?.let { return it.toSnapshot() }
        }
        return null
    }

    private suspend fun statusFor(context: Context, v: Vehicle): VehicleStatus? {
        val repo = repositoryFor(Brand.fromIndicator(v.brandIndicator), SessionStore(context), CredentialStore(context))
        return withTimeoutOrNull(30_000) { BlueLinkGate.statusMutex.withLock { repo.status(v, refresh = false) } }
    }

    private suspend fun performLock(context: Context, vin: String, carName: String, settings: AutoLockConfig) {
        // The outcome notification is the user's to silence (Settings -> Notifications); a failure
        // notification below is not.
        val announce = runCatching { SettingsStore(context).notificationPrefs().autoLockAlerts }.getOrDefault(true)
        _state.update { it + (vin to AutoLockEvalState(detection = DetectionState.LOCKING)) }
        if (settings.dryRun) {
            AppLog.log("AutoLock DRY RUN: would have locked $vin now. (No command sent.)")
            // Dry run is otherwise SILENT on the phone (it only ever reached AppLog), so a
            // user evaluating AutoLock had nothing to notice. This is the "it would have
            // locked" notification, on its own channel with the car-lock sound.
            if (announce) AutoLockEventNotifier.notifyLocked(context, vin, carName, dryRun = true)
            _state.update {
                it + (vin to AutoLockEvalState(
                    detection = DetectionState.LOCKED,
                    lastLockAtEpochMs = System.currentTimeMillis(),
                    lastOutcome = "Would have locked (dry run).",
                ))
            }
            return
        }
        // ONE retry, and only here. A walk-away lock is the moment to spend it: the alternative is
        // that a single network blip, or a token that expired a minute ago, leaves the car open and
        // reports an error. Nothing else in this flow is retried, and this is bounded at one.
        // Up to three attempts in all, 2.5s and then 5s apart: a network blip, or a token that expired a minute ago, should not leave
        // the car open. Every failed attempt is logged, so the last one's reason is never the only trace.
        var result = runCatching { CarCommandRunner.execute(context, CarCommand(vin, CarAction.LOCK)) }.getOrNull()
        var attempt = 1
        while (result?.ok != true && attempt < 3) {
            AppLog.log("AutoLock: lock attempt $attempt failed for $vin (${result?.message ?: "no response"}); trying again.")
            delay(if (attempt == 1) 2_500L else 5_000L)
            result = runCatching { CarCommandRunner.execute(context, CarCommand(vin, CarAction.LOCK)) }.getOrNull()
            attempt++
        }
        if (result?.ok == true) {
            AppLog.log("AutoLock: car locked automatically ($vin).")
            if (announce) AutoLockEventNotifier.notifyLocked(context, vin, carName, dryRun = false)
            _state.update {
                it + (vin to AutoLockEvalState(
                    detection = DetectionState.LOCKED,
                    lastLockAtEpochMs = System.currentTimeMillis(),
                    lastOutcome = "Locked automatically.",
                ))
            }
        } else {
            // notifyLockFailed below is this failure's notification; fail() must not post a second one for it.
            fail(context, vin, "AutoLock: lock command failed after $attempt attempts${result?.message?.let { " - $it" } ?: ""}.", notify = false)
            notifyLockFailed(context, vin, carName, result?.message)
        }
    }

    /**
     * Records a failure that stopped an evaluation from locking the car, and tells the user about it. Before this, these went only to
     * the log and the in-app line, so an evaluation that could not lock looked like one that never ran.
     */
    private suspend fun carNameFor(context: Context, vin: String): String =
        runCatching { SnapshotStore(context).current().vehicles.firstOrNull { it.vin == vin }?.name }.getOrNull() ?: "Your car"

    /** Tells the person AutoLock did not lock a car when it should have been able to, with the reason. */
    private fun notifyNotDetected(context: Context, vin: String, carName: String, text: String) {
        Notifications.post(
            context,
            id = AutoLockNotification.notificationId(vin) + 3,
            title = "AutoLock didn't lock $carName",
            text = text,
            clears = false,
        )
    }

    private fun fail(context: Context, vin: String, message: String, notify: Boolean = true) {
        AppLog.log("⚠ $message")
        _state.update { it + (vin to AutoLockEvalState(detection = DetectionState.ERROR, lastOutcome = message)) }
        if (notify) {
            Notifications.post(
                context,
                id = AutoLockNotification.notificationId(vin) + 2,
                title = "AutoLock couldn't lock your car",
                text = message,
                clears = false,
            )
        }
    }

    /**
     * A real, standalone user notification for a failed automatic lock -- not just
     * AutoLockNotification's own low-priority "evaluation in progress" foreground
     * update, which the ERROR state above already quietly turns into (AutoLockService's
     * own state collector) but which can come and go in the status bar before anyone
     * glances at it, and never explains WHY. Reported directly: wants to actually be
     * told the car could not lock, e.g. after hitting the day's remote-command limit.
     *
     * `reason` is whatever server-provided message the failed command carried, checked
     * for the same "limit" wording AppViewModel.locate() already recognises from real
     * BlueLink error text ("...daily location-lookup limit...", "...daily remote
     * service request limit..."), so this can say something more useful than "it
     * failed" when that's specifically what happened.
     */
    private fun notifyLockFailed(context: Context, vin: String, carName: String, reason: String?) {
        val limited = reason?.contains("limit", ignoreCase = true) == true
        val text = if (limited) {
            "$carName may be over today's command limit. Lock it manually."
        } else {
            "AutoLock couldn't lock $carName" + (reason?.let { " ($it)" } ?: "") + ". Lock it manually if needed."
        }
        // +1, distinct from AutoLockNotification.notificationId(vin) -- that id is the
        // live "evaluation in progress" foreground notification (AutoLockService owns
        // its lifecycle entirely, cancelling/replacing it on every state change); this
        // is a separate, ordinary notification that should stick around on its own
        // until the user dismisses it, not get silently swapped out by the next
        // evaluation's own notification traffic.
        Notifications.post(
            context,
            id = AutoLockNotification.notificationId(vin) + 1,
            title = "$carName didn't lock",
            text = text,
            // Stays until it is dealt with, as above; the others about AutoLock clear themselves.
            clears = false,
        )
    }

    private fun advance(vin: String, next: DetectionState) {
        _state.update { it + (vin to it[vin].let { s -> (s ?: AutoLockEvalState()).copy(detection = next) }) }
    }

    private const val CONFIRM_TIMEOUT_MS = 120_000L
}
