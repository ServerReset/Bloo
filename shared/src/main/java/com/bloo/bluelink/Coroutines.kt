package com.bloo.bluelink

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * A detached scope for work that must outlive its caller: an app-scoped object's own coroutines, or
 * a service/receiver callback that returns immediately. SupervisorJob so one failed child never
 * cancels its siblings, Dispatchers.IO so blocking work never runs on the main thread.
 */
fun ioScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * The one line every `catch (e: Exception)` around suspending work must start with: cancellation is
 * control flow, not a failure to report, so it is rethrown before the catch turns it into an error
 * message. Keeps the idiom in one place instead of retyped at every call site.
 */
fun rethrowIfCancellation(e: Throwable) {
    if (e is CancellationException) throw e
}

/**
 * [runCatching] for suspending work: a failure becomes a [Result], but the caller being cancelled is rethrown, so a stopped worker or a
 * left screen stops instead of carrying on with each later call failing. A timeout of the block's own (an inner withTimeout) is an
 * ordinary failure, as it was with runCatching: code around these calls treats "took too long" as a result.
 */
inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: Throwable) {
    if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
    Result.failure(e)
}
