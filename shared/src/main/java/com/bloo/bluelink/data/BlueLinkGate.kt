package com.bloo.bluelink.data

import kotlinx.coroutines.sync.Mutex

/** Process-wide serialization for vehicle-status calls. */
object BlueLinkGate {
    // A single shared Mutex instance: callers must `withLock { ... }` around any
    // vehicle-status/fetch call so that at most one such call is in flight at a time for the whole
    // process, regardless of which component (UI or worker) initiated it.
    val statusMutex = Mutex()
}
