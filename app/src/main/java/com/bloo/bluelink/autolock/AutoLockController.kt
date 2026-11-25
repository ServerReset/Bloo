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
import com.bloo.bluelink.suspendRunCatching

/** Snapshot the UI/notification observe for one car's in-flight (or last) evaluation. */
data class AutoLockEvalState(
    val detection: DetectionState = DetectionState.IDLE,
    val graceRemaining: Int = 0,
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
    // coroutine, the service's, and the Activity Recognition receiver can all touch these for
    // different VINs (or race the same one) from different threads.
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val jobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val machines = java.util.concurrent.ConcurrentHashMap<String, LockStateMachine>()

    private val skipGrace = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    /**
     * The cars whose evaluation currently needs Activity Recognition. It is ONE app-wide registration, so it is stopped only when the
     * last of them is done: stopping it when the first car finished left a second car still waiting for a walk that could never come.
     */
    private val arUsers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val walkAwayConfirmed = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val _state = MutableStateFlow<Map<String, AutoLockEvalState>>(emptyMap())
    val state: StateFlow<Map<String, AutoLockEvalState>> = _state

    fun stateFor(vin: String): AutoLockEvalState = _state.value[vin] ?: AutoLockEvalState()

    init {
        // Debug builds only: every state change as a test line (no VIN or car name), for the emulator's real-account test.
        if (com.bloo.bluelink.BuildConfig.DEBUG) {
            scope.launch {
                var last = ""
                state.collect { all ->
                    val line = all.values.joinToString(" | ") { s ->
                        "detection=${s.detection} remaining=${s.graceRemaining} outcome=\"${s.lastOutcome.orEmpty()}\""
                    }
                    if (line != last && line.isNotEmpty()) {
                        last = line
                        com.bloo.bluelink.ui.DebugProbe.mark("autolock: $line")
                    }
                }
            }
        }
    }

    private fun mutexFor(vin: String): Mutex = locks.getOrPut(vin) { Mutex() }

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

    /**
     * Bluetooth (or manual "simulate leaving") trigger: begin an evaluation for [vin]. The same departure arriving again (the manifest
     * receiver and the watcher both see it) is ignored while it is still being judged, rather than restarting it and losing a walk
     * already seen (see [AutoLockEvents.startsNewEvaluation]).
     */
    fun onTriggerFired(context: Context, vin: String) {
        synchronized(jobs) {
            val running = jobs[vin]?.isActive == true
            if (!AutoLockEvents.startsNewEvaluation(_state.value[vin]?.detection, running)) {
                AppLog.log("AutoLock: $vin is already being evaluated for this departure; the repeat is ignored.")
                return
            }
            jobs[vin]?.cancel()
            AutoLockPending.record(context.applicationContext, vin)
            jobs[vin] = scope.launch { runEvaluation(context.applicationContext, vin) }
        }
    }

    /**
     * A walk arrived in a process that has no evaluation waiting for it (Android stopped the one the disconnect started): resume each
     * departure still on record (see [AutoLockPending]), with the walk already counted.
     */
    private fun resumeAfterWalk(context: Context): Boolean {
        val open = AutoLockPending.open(context, System.currentTimeMillis(), CONFIRM_TIMEOUT_MS)
            .filter { vin -> jobs[vin]?.isActive != true }
        open.forEach { vin ->
            AppLog.log("AutoLock: a walk arrived after the app was restarted; resuming the departure for $vin.")
            // Best effort, as for a fresh departure: the progress notification, and with it a foreground process.
            AutoLockService.start(context.applicationContext, vin)
            jobs[vin] = scope.launch { runEvaluation(context.applicationContext, vin, walked = true) }
        }
        return open.isNotEmpty()
    }

    /** Skip the remaining grace period and lock immediately (notification "Lock now"). */
    fun lockNow(context: Context, vin: String) {
        skipGrace.add(vin)
        if (jobs[vin]?.isActive != true) onTriggerFired(context, vin)
    }

    fun onWalkingConfirmed(vin: String) {
        walkAwayConfirmed.add(vin)
        val machine = machines[vin] ?: return
        // Decided and written in ONE atomic update: read first and written after, a cancel landing in between was overwritten with GRACE,
        // and a countdown nothing was running any more stayed on screen.
        _state.update { all ->
            val current = all[vin] ?: return@update all
            val next = machine.next(current.detection, DetectionEvent.WalkingConfirmed)
            if (next == current.detection) all else all + (vin to current.copy(detection = next))
        }
    }

    /** Activity Recognition transitions are app-wide (one Google Play Services request, not
     *  per-VIN), so a "walking" transition confirms every evaluation currently waiting on it. */
    fun onWalkingConfirmedAny(context: Context? = null) {
        val waiting = _state.value.filterValues { it.detection == DetectionState.CONFIRMING }.keys
        waiting.forEach { onWalkingConfirmed(it) }
        if (waiting.isEmpty() && context != null) resumeAfterWalk(context)
    }

    /**
     * Reconnect or user cancel: abort a pending evaluation for [vin], if any. Once the lock command is on its way it is not interrupted
     * (the car may already have locked, and "the car wasn't touched" would then be untrue), and with nothing running the last outcome
     * is left as it was.
     */
    fun cancel(vin: String) {
        val job = jobs[vin]
        if (job?.isActive != true) {
            AppLog.log("AutoLock: cancel for $vin with nothing running; nothing to do.")
            return
        }
        if (stateFor(vin).detection == DetectionState.LOCKING) {
            AppLog.log("AutoLock: the lock command for $vin is already on its way; it is not cancelled.")
            return
        }
        job.cancel()
        _state.update { it + (vin to AutoLockEvalState(detection = DetectionState.ABORTED)) }
        AppLog.log("AutoLock: cancelled for $vin")
    }

    private suspend fun runEvaluation(context: Context, vin: String, walked: Boolean = false) = mutexFor(vin).withLock {
        var startedActivityRecognition = false
        try {
            skipGrace.remove(vin)
            walkAwayConfirmed.remove(vin)
            if (walked) walkAwayConfirmed.add(vin)
            // Physical detection is mandatory: a walk must be confirmed by Activity Recognition before a lock, whatever is saved.
            val settings = SettingsStore(context).autoLockConfig(vin)
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

            val sm = LockStateMachine().also { machines[vin] = it }
            // A resumed departure already has its walk: the machine then goes straight to the countdown.
            if (walked) sm.next(DetectionState.IDLE, DetectionEvent.WalkingConfirmed)
            advance(vin, sm.next(DetectionState.IDLE, DetectionEvent.CarBluetoothDisconnected))
            AppLog.log("AutoLock: left the car ($vin) — evaluating.")

            arUsers.add(vin)
            ActivityRecognitionManager.start(context)
            startedActivityRecognition = true

            // Wait for Activity Recognition to confirm a walk (CONFIRMING -> GRACE). With no walk by the deadline, skip: a Bluetooth
            // disconnect alone never locks.
            val confirmDeadline = System.currentTimeMillis() + CONFIRM_TIMEOUT_MS
            while (stateFor(vin).detection == DetectionState.CONFIRMING) {
                if (System.currentTimeMillis() > confirmDeadline) {
                    val confirmed = walkAwayConfirmed.contains(vin)
                    if (!confirmed) {
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
            // Still beside the car (unloading, children getting out)? Then hold, and look again, before locking. Only good evidence
            // holds it back (see WalkAwayGeometry); with no usable fix this goes straight on, as it always did.
            if (!skipGrace.contains(vin) && besideCar(context, vin)) {
                AppLog.log("AutoLock: you're still beside $vin; waiting before locking.")
                // The countdown is over but it is waiting: graceRemaining 0 is what the notification shows as "waiting for you to step away".
                _state.update { it + (vin to AutoLockEvalState(detection = DetectionState.GRACE, graceRemaining = 0)) }
                val until = System.currentTimeMillis() + BESIDE_WAIT_MS
                var beside = true
                while (beside && System.currentTimeMillis() < until) {
                    delay(BESIDE_RECHECK_MS)
                    if (stateFor(vin).detection == DetectionState.ABORTED) return@withLock
                    if (skipGrace.contains(vin)) break
                    beside = besideCar(context, vin)
                }
                if (beside && !skipGrace.contains(vin)) {
                    _state.update {
                        it + (vin to AutoLockEvalState(
                            detection = DetectionState.SKIPPED,
                            lastOutcome = "Skipped. You were still beside the car.",
                        ))
                    }
                    AppLog.log("AutoLock: still beside $vin after ${BESIDE_WAIT_MS / 1000}s; not locking.")
                    notifyNotDetected(context, vin, carNameFor(context, vin), "You were still beside it, so AutoLock didn't lock it. Lock it yourself when you leave.")
                    return@withLock
                }
            }
            advance(vin, DetectionState.VERIFYING)

            // Read the CACHED status -- never wake the car for the pre-lock check, matching
            // AlertWorker's own refresh=false convention for background polls.
            val v = ensureVehicleCached(context, vin)?.toVehicle()
            if (v == null) {
                fail(context, vin, "Car no longer found.")
                return@withLock
            }
            val status = suspendRunCatching { statusFor(context, v) }.getOrElse {
                fail(context, vin, "Could not read vehicle status: ${it.message}")
                return@withLock
            }
            if (status == null) {
                fail(context, vin, "Could not read vehicle status.")
                return@withLock
            }

            when (val decision = LockPolicy.decide(status)) {
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
                // Settled one way or another: nothing for a later walk to resume.
                AutoLockPending.clear(context, vin)
                if (startedActivityRecognition) {
                    arUsers.remove(vin)
                    if (arUsers.isEmpty()) ActivityRecognitionManager.stop(context)
                }
            }
        }
    }

    /** Whether a fresh, trustworthy location fix shows the phone beside [vin]'s last known place (see WalkAwayGeometry). */
    private suspend fun besideCar(context: Context, vin: String): Boolean {
        val car = suspendRunCatching { SnapshotStore(context).current().vehicles.firstOrNull { it.vin == vin } }.getOrNull() ?: return false
        val loc = withTimeoutOrNull(10_000) { suspendRunCatching { LocationHelper.currentLocation(context) }.getOrNull() } ?: return false
        val ageMs = (android.os.SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000L
        val mock = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) loc.isMock else @Suppress("DEPRECATION") loc.isFromMockProvider
        // No accuracy reported is "unknown", which is too vague to hold a lock back.
        val accuracy = if (loc.hasAccuracy()) loc.accuracy.toDouble() else Double.MAX_VALUE
        val fix = WalkAwayGeometry.Fix(loc.latitude, loc.longitude, accuracy, ageMs, mock)
        return WalkAwayGeometry.stillBesideCar(fix, car.lat, car.lon)
    }

    private suspend fun ensureVehicleCached(context: Context, vin: String): VehicleSnapshot? {
        val store = SnapshotStore(context)
        store.current().vehicles.firstOrNull { it.vin == vin }?.let { return it }
        val sessions = SessionStore(context)
        val credentials = CredentialStore(context)
        for (brand in suspendRunCatching { sessions.loggedInBrands() }.getOrDefault(emptyList())) {
            // Inside the shared request lock like every other car request: BlueLink answers 502 to overlapping
            // ones, and an empty result here would read as "car not found" and skip the lock.
            val fetched = suspendRunCatching { BlueLinkGate.statusMutex.withLock { repositoryFor(brand, sessions, credentials).vehicles() } }
                .getOrDefault(emptyList())
            if (fetched.isEmpty()) continue
            suspendRunCatching { store.saveVehiclesKeepingStatus(fetched.map { it.toSnapshot() }) }
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
        val announce = suspendRunCatching { SettingsStore(context).notificationPrefs().autoLockAlerts }.getOrDefault(true)
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
        // Up to three attempts, 2.5s and then 5s apart (see LockRetry): a network blip, or a token that expired a minute ago, should not
        // leave the car open. Not retried when it cannot help: the same lock already being sent by another path, or the day's limit.
        // Every failed attempt is logged, so the last one's reason is never the only trace.
        var result = suspendRunCatching { CarCommandRunner.execute(context, CarCommand(vin, CarAction.LOCK)) }.getOrNull()
        var attempt = 1
        while (true) {
            val wait = LockRetry.nextDelayMs(result, attempt) ?: break
            AppLog.log("AutoLock: lock attempt $attempt failed for $vin (${result?.message ?: "no response"}); trying again.")
            delay(wait)
            result = suspendRunCatching { CarCommandRunner.execute(context, CarCommand(vin, CarAction.LOCK)) }.getOrNull()
            attempt++
        }
        if (result?.refused == true) {
            // Another path (the app, a widget, the watch) is sending this very lock right now: its own result is the one to report.
            AppLog.log("AutoLock: the lock for $vin is already being sent by another path; leaving the outcome to it.")
            _state.update { it + (vin to AutoLockEvalState(detection = DetectionState.SKIPPED, lastOutcome = "Skipped. The car is already being locked.")) }
            return
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
        suspendRunCatching { SnapshotStore(context).current().vehicles.firstOrNull { it.vin == vin }?.name }.getOrNull() ?: "Your car"

    /** Tells the person AutoLock did not lock a car when it should have been able to, with the reason. */
    private fun notifyNotDetected(context: Context, vin: String, carName: String, text: String) {
        Notifications.post(
            context,
            id = AutoLockNotification.notificationId(vin) + 3,
            title = "AutoLock didn't lock $carName",
            text = text,
            clears = false,
            channelId = Notifications.CHANNEL_AUTOLOCK_PROBLEMS,
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
                channelId = Notifications.CHANNEL_AUTOLOCK_PROBLEMS,
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
        val limited = LockRetry.isLimit(reason)
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
            channelId = Notifications.CHANNEL_AUTOLOCK_PROBLEMS,
        )
    }

    private fun advance(vin: String, next: DetectionState) {
        _state.update { it + (vin to it[vin].let { s -> (s ?: AutoLockEvalState()).copy(detection = next) }) }
    }

    private const val CONFIRM_TIMEOUT_MS = 120_000L

    /** How long a lock waits while the phone is still beside the car, and how often it looks again. */
    private const val BESIDE_WAIT_MS = 120_000L
    private const val BESIDE_RECHECK_MS = 15_000L
}
