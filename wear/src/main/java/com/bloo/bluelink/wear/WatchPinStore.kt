package com.bloo.bluelink.wear

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.PinLockout
import com.bloo.bluelink.data.PinRecord
import com.bloo.bluelink.data.SecureVault
import com.bloo.bluelink.data.SessionStore
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
 * Kept in the same encrypted [SecureVault] as the watch's accounts (AES-256-GCM, Keystore-backed), so
 * the lockout counters cannot be reset by editing a file. After 10 wrong PINs in a row the watch signs
 * itself out of its accounts.
 */
class WatchPinStore(private val context: Context) {
    private val vault = SecureVault.of(context)

    init { importOldPrefs() }

    /** One-time move of the old plain SharedPreferences into the encrypted vault, then wiped. */
    private fun importOldPrefs() {
        val old = context.applicationContext.getSharedPreferences("bloo_watch_pin", Context.MODE_PRIVATE)
        if (old.all.isEmpty()) return
        vault.edit {
            old.getString(KEY_TIMING, null)?.let { put(K_TIMING, it) }
            old.getString(KEY_RECORD, null)?.let { put(K_RECORD, it) }
            put(K_FAILURES, old.getInt(KEY_FAILURES, 0).toString())
            put(K_WALL, old.getLong(KEY_LOCKED_UNTIL_WALL, 0L).toString())
            put(K_ELAPSED, old.getLong(KEY_LOCKED_UNTIL_ELAPSED, 0L).toString())
        }
        old.edit { clear() }
    }

    /** When the phone says the watch should lock. Pushed over the Data Layer; OFF until then. */
    var timing: WatchLockTiming
        get() = WatchLockTiming.fromWire(vault.getString(K_TIMING))
        set(value) = vault.edit { put(K_TIMING, value.wireKey) }

    /** The mirrored PIN record, or null when the phone has no PIN set. */
    var record: PinRecord?
        get() = PinRecord.decode(vault.getString(K_RECORD))
        set(value) = vault.edit {
            if (value == null) remove(K_RECORD) else put(K_RECORD, value.encode())
        }

    /** Whether the phone has pushed a PIN to gate with. */
    val hasPin: Boolean get() = record != null

    private fun lockout(): PinLockout = PinLockout(
        failures = vault.getString(K_FAILURES)?.toIntOrNull() ?: 0,
        lockedUntilEpochMs = vault.getString(K_WALL)?.toLongOrNull() ?: 0L,
        lockedUntilElapsedMs = vault.getString(K_ELAPSED)?.toLongOrNull() ?: 0L,
    )

    private fun save(l: PinLockout) = vault.edit {
        put(K_FAILURES, l.failures.toString())
        put(K_WALL, l.lockedUntilEpochMs.toString())
        put(K_ELAPSED, l.lockedUntilElapsedMs.toString())
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
        // Too many wrong guesses in a row: sign the watch out of everything it holds. The phone can
        // sign it in again; a thief cannot keep guessing.
        if (next.failures >= WIPE_AFTER_FAILURES) wipeAccounts()
        val remaining = next.remainingMs(now, nowElapsed)
        return if (remaining > 0) PinVerifyResult.LockedOut(remaining) else PinVerifyResult.Wrong
    }

    /** Seconds remaining on a lockout, or 0. */
    fun lockoutRemainingSeconds(): Long {
        val now = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        return (lockout().remainingMs(now, nowElapsed) / 1000L).coerceAtLeast(0L)
    }

    /** Removes every account and session this watch holds. */
    private fun wipeAccounts() {
        val app = context.applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val creds = CredentialStore(app)
            val sessions = SessionStore(app)
            Brand.values().forEach { b ->
                runCatching { creds.clear(b) }
                runCatching { sessions.clear(b) }
            }
        }
    }

    private companion object {
        /** Wrong PINs in a row before the watch signs itself out. */
        const val WIPE_AFTER_FAILURES = 10

        // Vault keys: the "app_pin" prefix is the part of the vault that stays readable while the
        // watch is sealed after a restart (the PIN record is what unseals it).
        const val K_TIMING = "app_pin_watch_timing"
        const val K_RECORD = "app_pin_watch_record"
        const val K_FAILURES = "app_pin_watch_failures"
        const val K_WALL = "app_pin_watch_locked_wall"
        const val K_ELAPSED = "app_pin_watch_locked_elapsed"

        // The old SharedPreferences names, read once to migrate.
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
