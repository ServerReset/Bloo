package com.bloo.bluelink.autolock

import com.bloo.bluelink.data.CarCommandResult
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.isDriving
import kotlinx.coroutines.delay

/** Outcome of evaluating a status snapshot against the auto-lock policy. */
sealed interface LockDecision {
    data object Lock : LockDecision
    data class Skip(val reason: String) : LockDecision
}

/**
 * Pure policy: should AutoLock attempt to lock, given this status? Ported from
 * i5-AutoLock's `LockPolicy`, onto Bloo's own [VehicleStatus] shape (`doorLock == true`
 * means locked here, matching the app's own lock command).
 */
object LockPolicy {
    /**
     * An open door or window does not stop the lock: a car locked around an open window is still better than one left unlocked.
     * A lock state the car has not reported is locked too: locking is harmless on a locked car, and skipping left an unlocked one open.
     * A running engine is the one thing that stops it: someone else may be driving the car the phone has just walked away from.
     */
    fun decide(status: VehicleStatus): LockDecision = when {
        status.doorLock == true -> LockDecision.Skip("Already locked")
        status.engine == true -> LockDecision.Skip("Engine running")
        status.isDriving -> LockDecision.Skip("Car is moving")
        else -> LockDecision.Lock
    }

    /**
     * The same rule over the glance snapshots the watch holds: the cars an auto lock would lock, in order. One rule for both devices;
     * the watch used to keep its own copy, which had drifted.
     */
    fun targets(vehicles: List<VehicleSnapshot>): List<String> =
        vehicles.filter { it.locked != true && it.engineOn != true && !it.isDriving }.map { it.vin }
}

/**
 * How a failed walk-away lock is retried: at most three attempts in all, 2.5 s and then 5 s apart. Not at all when retrying cannot help:
 * the same command already being sent by another path ([CarCommandResult.refused]), or the day's command limit being reached.
 */
object LockRetry {
    const val MAX_ATTEMPTS = 3

    /** The wait before the next attempt after attempt number [attempt] (1-based) ended with [result], or null to stop. */
    fun nextDelayMs(result: CarCommandResult?, attempt: Int): Long? = when {
        result?.ok == true -> null
        result?.refused == true -> null
        isLimit(result?.message) -> null
        attempt >= MAX_ATTEMPTS -> null
        attempt == 1 -> 2_500L
        else -> 5_000L
    }

    /** The server's "daily ... limit" wording (see AutoLockController.notifyLockFailed). */
    fun isLimit(message: String?): Boolean = message?.contains("limit", ignoreCase = true) == true

    /**
     * Runs [attempt] under this policy: again after 2.5 s and then 5 s while it fails in a way a retry can help, and returns the last
     * result with how many attempts were made. A null result means "no answer"; [stopOnNull] ends the run there instead, for a caller
     * whose null means the request was handed on (the watch relaying to the phone) rather than lost.
     */
    suspend fun run(stopOnNull: Boolean = false, attempt: suspend () -> CarCommandResult?): Pair<CarCommandResult?, Int> {
        var result = attempt()
        var n = 1
        while (!(stopOnNull && result == null)) {
            val wait = nextDelayMs(result, n) ?: break
            delay(wait)
            result = attempt()
            n++
        }
        return result to n
    }
}
