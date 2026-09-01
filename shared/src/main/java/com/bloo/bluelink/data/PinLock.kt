package com.bloo.bluelink.data

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The app PIN's cryptographic core -- pure Kotlin, no Android/Compose
 * dependencies, so the whole security policy is unit-testable alongside
 * everything else in :shared.
 *
 * Why PBKDF2 on top of EncryptedSharedPreferences (see
 * [CredentialStore]): the PIN still lives inside the AES-GCM-encrypted
 * prefs file, but it should not live there as plaintext any more than
 * ac password would. The PIN's real entropy is tiny (at most 10^8 for an
 * 8-digit PIN), so resistance comes from three directions at once:
 *  - PBKDF2-HMAC-SHA256 ([PIN_DEFAULT_ITERATIONS]) stretches each guess;
 *  - a random per-record salt ([PinRecord.salt]) kills rainbow tables and
 *    lets the same PIN legitimately re-enter on re-set;
 *  - the app never asks faster than the human can type, and the
 *    [PinLockout] policy makes batch guessing exponentially expensive.
 *
 * Comparison is [constantTimeEquals]: the PIN check result must not be
 * observable through timing. The configured PBKDF2 iteration count lives
 * inside the record itself (so it can be raised in a future release
 * without breaking existing records) -- [PinRecord.decode] validates and
 * clamps it.
 */
object PinCrypto {

    /** Default work factor. Current guidance is ≥600k for PBKDF2-SHA256
     *  against password-cracking rigs; 150k is a deliberate balance for a
     *  4-8 digit PIN check that still has to feel instant on mid phones --
     *  the lockout policy, not the hash, is the primary defence here. */
    const val PIN_DEFAULT_ITERATIONS = 150_000
    const val PIN_MIN_ITERATIONS = 50_000
    const val PIN_MAX_ITERATIONS = 2_000_000
    const val SALT_BYTES = 16
    const val HASH_BYTES = 32
    const val PIN_MIN_DIGITS = 4
    const val PIN_MAX_DIGITS = 8

    private val random = SecureRandom()

    /** A fresh random salt for a new PIN record. */
    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also { random.nextBytes(it) }

    /** PBKDF2-HMAC-SHA256 stretching of [pin] with [salt] for [iterations]. */
    fun hash(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Timing-safe comparison -- a failed PIN must not answer measurably
     *  faster than a successful one. */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) {
            // Still chew the same work for the early-exit case, so the
            // existing-correct-length path can't be distinguished either.
            MessageDigest.getInstance("SHA-256").digest(a)
            return false
        }
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}

/** One stored PIN record: salt, work factor, and the stretched hash. */
data class PinRecord(
    val salt: ByteArray,
    val iterations: Int,
    val hash: ByteArray,
) {
    /** Encodes as `base64(salt)|iterations|base64(hash)` for storage. */
    fun encode(): String {
        val enc = java.util.Base64.getEncoder()
        return "${enc.encodeToString(salt)}|" + iterations + "|" + enc.encodeToString(hash)
    }

    fun verify(pin: String): Boolean =
        PinCrypto.constantTimeEquals(hash, PinCrypto.hash(pin, salt, iterations))

    companion object {
        /** Decodes a record string; returns null for anything malformed or
         *  out-of-range (a tampered file should fail closed, not throw). */
        fun decode(raw: String?): PinRecord? {
            if (raw == null) return null
            val parts = raw.split("|")
            if (parts.size != 3) return null
            val dec = java.util.Base64.getDecoder()
            val salt = runCatching { dec.decode(parts[0]) }.getOrNull() ?: return null
            val hash = runCatching { dec.decode(parts[2]) }.getOrNull() ?: return null
            val iterations = parts[1].toIntOrNull() ?: return null
            if (salt.size != PinCrypto.SALT_BYTES) return null
            if (iterations < PinCrypto.PIN_MIN_ITERATIONS || iterations > PinCrypto.PIN_MAX_ITERATIONS) return null
            if (hash.size != PinCrypto.HASH_BYTES) return null
            return PinRecord(salt, iterations, hash)
        }
    }
}

/**
 * The wrong-PIN lockout policy, as a pure state machine so the exact
 * escalation curve is testable:
 *
 *  - Every fifth consecutive failure triggers a rejection window.
 *  - The FIRST window is 30s; each further window DOUBLES (30s, 1m, 2m,
 *    4m, ...) -- "the rejection time gets longer" after every batch,
 *    so sustained guessing costs exponentially more wall-clock time
 *    per attempt batch.
 *  - A window carries over: failing twice more after a window already
 *    started does not shorten or extend it, only the 5th-in-a-row does.
 *  - Any successful unlock resets the whole counter and window.
 *  - State is persisted by the caller ([CredentialStore]) so a restart
 *    does not throw the escalation away.
 */
data class PinLockout(
    /** Total consecutive failures since the last successful unlock. */
    val failures: Int = 0,
    /** Wall-clock epoch ms until which the next attempt may not proceed. */
    val lockedUntilEpochMs: Long = 0L,
    /**
     * The same deadline on the MONOTONIC clock (`SystemClock.elapsedRealtime`), which no
     * setting can move.
     *
     * The wall-clock deadline alone was the whole check, and the threat model for an app-lock
     * PIN is someone holding the device -- who can open Settings and move the clock forward,
     * which retires every rejection window instantly. That defeats the control this class's own
     * doc calls the primary defence, chosen over more PBKDF2 iterations precisely because the
     * escalation was doing the work.
     *
     * 0 means "no monotonic anchor": state written by an older build, or a value the reboot
     * check below has discarded. Then the wall clock is all there is, which is exactly where
     * this started -- never worse.
     */
    val lockedUntilElapsedMs: Long = 0L,
) {
    /**
     * Locked if EITHER clock says so, so beating it requires moving both -- and one of them
     * cannot be moved.
     */
    fun isLocked(nowEpochMs: Long, nowElapsedMs: Long = 0L): Boolean =
        lockedUntilEpochMs > nowEpochMs || elapsedRemainingMs(nowElapsedMs) > 0L

    fun remainingMs(nowEpochMs: Long, nowElapsedMs: Long = 0L): Long =
        maxOf((lockedUntilEpochMs - nowEpochMs), elapsedRemainingMs(nowElapsedMs))
            .coerceAtLeast(0L)

    /**
     * What the monotonic anchor still has to run, or 0 if it cannot be trusted.
     *
     * elapsedRealtime resets to ~0 on reboot, so a persisted anchor from a previous boot would
     * otherwise read as a deadline far in the future and lock the user out for its whole
     * remaining length. A remainder longer than the largest window we ever issue can only mean
     * the clock it was measured against no longer exists, so it is discarded rather than
     * trusted -- failing back to the wall clock instead of failing the user out.
     */
    private fun elapsedRemainingMs(nowElapsedMs: Long): Long {
        if (lockedUntilElapsedMs <= 0L || nowElapsedMs <= 0L) return 0L
        val remaining = lockedUntilElapsedMs - nowElapsedMs
        if (remaining <= 0L) return 0L
        return if (remaining > MAX_WINDOW_MS) 0L else remaining
    }

    /** How many attempts remain in the current batch before the next window
     *  starts, or null while rejected. */
    fun attemptsRemainingInBatch(nowEpochMs: Long, nowElapsedMs: Long = 0L): Int? =
        if (isLocked(nowEpochMs, nowElapsedMs)) null
        else STRIKES_PER_BATCH - (failures % STRIKES_PER_BATCH)

    fun onFailure(nowEpochMs: Long, nowElapsedMs: Long = 0L): PinLockout {
        val nextFailures = failures + 1
        return if (nextFailures % STRIKES_PER_BATCH == 0) {
            val batch = nextFailures / STRIKES_PER_BATCH
            val durationMs = windowMs(batch)
            PinLockout(
                nextFailures,
                nowEpochMs + durationMs,
                // Only anchored when a real monotonic reading was supplied; 0 stays 0 so a
                // caller that has none does not manufacture a deadline at the epoch.
                if (nowElapsedMs > 0L) nowElapsedMs + durationMs else 0L,
            )
        } else {
            PinLockout(nextFailures, lockedUntilEpochMs, lockedUntilElapsedMs)
        }
    }

    fun onSuccess(): PinLockout = PinLockout(0, 0L, 0L)

    companion object {
        const val STRIKES_PER_BATCH = 5
        const val BASE_WINDOW_MS = 30_000L

        /**
         * The escalation is capped. Doubling forever overflows into nonsense (batch 60 is
         * longer than the age of the universe), and a window nobody can outlast is a bricked
         * app rather than a deterrent. It also gives [elapsedRemainingMs] a bound to sanity
         * check a restored anchor against.
         */
        const val MAX_WINDOW_MS = 60L * 60_000L

        /** The window length for batch number [batch] (1-based). */
        fun windowMs(batch: Int): Long =
            if (batch >= 32) MAX_WINDOW_MS
            else (BASE_WINDOW_MS * (1L shl (batch - 1))).coerceAtMost(MAX_WINDOW_MS)
    }
}
/** "0:23" formatting for the lockout countdown line, shared by every surface
 *  that renders a rejection window (the phone lock overlay and its dialogs). */
fun formatLockoutSeconds(ms: Long): String {
    val total = ((ms + 999) / 1000).toInt()
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
