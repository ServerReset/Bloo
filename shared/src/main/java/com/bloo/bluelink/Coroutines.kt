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
