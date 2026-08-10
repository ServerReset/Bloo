package com.bloo.bluelink.data

import kotlinx.coroutines.delay

/**
 * Retry [block] with exponential backoff, returning whether it eventually succeeded.
 *
 * Extracted from three byte-for-byte copies of the same loop -- WearBridge.putItem and
 * WearPhoneService.sendResult on the phone, WearComms.publish on the watch -- each guarding a
 * Wearable Data Layer call (`Tasks.await(putDataItem / sendMessage)`) that can fail transiently
 * while the peer app is being installed, updated, or momentarily unreachable, and whose failure
 * is otherwise SILENT: a swallowed write returns exactly what a successful one does, so a caller
 * with an optimistic override waits forever for an echo that never comes. The three copies had
 * already drifted only in their constants and log text, never their control flow, which is
 * exactly the kind of duplication that eventually drifts in behaviour too.
 *
 * The Play Services call stays in [block] at each site -- only the loop, the backoff and the
 * "gave up" handling move here.
 *
 * Runs [block] in the CALLER's coroutine context, deliberately: `Tasks.await` calls
 * `Preconditions.checkNotMainThread()` unconditionally, so wrapping in `withContext(IO)` here
 * would be redundant for the callers (all already on IO) and would hide that requirement from a
 * future caller who is not. The contract is "call me off the main thread", same as the raw loop.
 *
 * @param attempts total tries, including the first (so 3 = one try plus two retries).
 * @param firstDelayMs backoff before the FIRST retry; each subsequent retry doubles it
 *        (`firstDelayMs shl attemptIndex`), matching the original `RETRY_MS shl attempt`.
 * @param onExhausted invoked once with the last failure if every attempt failed -- the site's
 *        own AppLog line, kept per-caller so each keeps its distinct message.
 * @param block the operation; throwing OR the timeout inside it counts as a failed attempt.
 * @return true as soon as [block] returns without throwing, false if [attempts] are exhausted.
 */
suspend fun retryWithBackoff(
    attempts: Int,
    firstDelayMs: Long,
    onExhausted: (Throwable?) -> Unit = {},
    block: suspend () -> Unit,
): Boolean {
    var lastError: Throwable? = null
    repeat(attempts) { attempt ->
        val outcome = runCatching { block() }
        if (outcome.isSuccess) return true
        lastError = outcome.exceptionOrNull()
        if (attempt < attempts - 1) delay(firstDelayMs shl attempt)
    }
    onExhausted(lastError)
    return false
}
