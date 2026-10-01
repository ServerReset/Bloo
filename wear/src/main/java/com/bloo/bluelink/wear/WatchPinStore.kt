package com.bloo.bluelink.wear

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import com.bloo.bluelink.data.PinLockout
import com.bloo.bluelink.data.PinRecord
import com.bloo.bluelink.data.WatchLockTiming

/**
 * The watch's PIN gate state: the PIN record the PHONE mirrored over the Data Layer, and the
 * watch's own lockout + session state.
 *
 * The PIN itself never leaves the phone in cleartext: the phone sends the already-stretched
 * [PinRecord] (salt + iterations + hash), and verification on the watch re-runs PBKDF2 with the
 * same parameters -- so the watch can check a PIN without ever holding it, and a wiped watch
 * cannot recover it. See [com.bloo.bluelink.data.PinCrypto].
 *
 * The lockout reuses the SAME pure [PinLockout] policy the phone uses, so the escalation curve
 * is identical and already covered by that class's own tests. Persisted here as its three
 * fields; verification reads the wall clock AND the monotonic clock exactly as the phone does.
 *
 * Stored in plain SharedPreferences: everything here is either public (the timing), a one-way
 * hash (the PIN record), or non-secret (lockout counters). No account credential is stored here (those live in the encrypted stores, see WearCredentialSync).
 */
class WatchPinStore(private val context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("bloo_watch_pin", Context.MODE_PRIVATE)

    /** When the phone says the watch should lock. Pushed over the Data Layer; OFF until then. */
    var timing: WatchLockTiming
        get() = WatchLockTiming.fromWire(prefs.getString(KEY_TIMING, null))
        set(value) = prefs.edit { putString(KEY_TIMING, value.wireKey) }

    /** The mirrored PIN record, or null when the phone has no PIN set. */
    var record: PinRecord?
        get() = PinRecord.decode(prefs.getString(KEY_RECORD, null))
        set(value) = prefs.edit {
            if (value == null) remove(KEY_RECORD) else putString(KEY_RECORD, value.encode())
        }

    /** Whether the phone has pushed a PIN to gate with. */
    val hasPin: Boolean get() = record != null

    private fun lockout(): PinLockout = PinLockout(
        failures = prefs.getInt(KEY_FAILURES, 0),
        lockedUntilEpochMs = prefs.getLong(KEY_LOCKED_UNTIL_WALL, 0L),
        lockedUntilElapsedMs = prefs.getLong(KEY_LOCKED_UNTIL_ELAPSED, 0L),
    )

    private fun save(l: PinLockout) = prefs.edit {
        putInt(KEY_FAILURES, l.failures)
        putLong(KEY_LOCKED_UNTIL_WALL, l.lockedUntilEpochMs)
        putLong(KEY_LOCKED_UNTIL_ELAPSED, l.lockedUntilElapsedMs)
    }

    /**
     * Verify [pin], updating the lockout policy through [PinLockout]'s own onFailure/onSuccess.
     * Returns the result so the caller can show the right message.
     */
    fun verify(pin: String): PinVerifyResult {
        val now = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val current = lockout()
        if (current.isLocked(now, nowElapsed)) {
            return PinVerifyResult.LockedOut(current.remainingMs(now, nowElapsed))
        }
        if (record?.verify(pin) == true) {
            save(current.onSuccess())
            return PinVerifyResult.Ok
        }
        val next = current.onFailure(now, nowElapsed)
        save(next)
        val remaining = next.remainingMs(now, nowElapsed)
        return if (remaining > 0) PinVerifyResult.LockedOut(remaining) else PinVerifyResult.Wrong
    }

    /** Seconds remaining on a lockout, or 0. */
    fun lockoutRemainingSeconds(): Long {
        val now = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        return (lockout().remainingMs(now, nowElapsed) / 1000L).coerceAtLeast(0L)
    }

    private companion object {
        const val KEY_TIMING = "timing"
        const val KEY_RECORD = "record"
        const val KEY_FAILURES = "failures"
        const val KEY_LOCKED_UNTIL_WALL = "locked_until_wall"
        const val KEY_LOCKED_UNTIL_ELAPSED = "locked_until_elapsed"
    }
}

/** Outcome of a watch PIN attempt. */
sealed interface PinVerifyResult {
    data object Ok : PinVerifyResult
    data object Wrong : PinVerifyResult
    data class LockedOut(val remainingMs: Long) : PinVerifyResult
}
