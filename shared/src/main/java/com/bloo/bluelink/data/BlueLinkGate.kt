package com.bloo.bluelink.data

import kotlinx.coroutines.sync.Mutex

/**
 * Process-wide serialization for vehicle-status calls. Blue Link rejects
 * overlapping requests on the same account with "a previous request is pending",
 * so both the foreground ViewModel and the background [AlertWorker] funnel their
 * status/vehicle fetches through this single mutex.
 */
object BlueLinkGate {
    // A single shared Mutex instance: callers must `withLock { ... }` around any
    // vehicle-status/fetch call so that at most one such call is in flight at a
    // time for the whole process, regardless of which component (UI or worker)
    // initiated it. Kotlin's Mutex queues suspended coroutines fairly (FIFO),
    // so concurrent callers simply wait their turn instead of racing the API.
    val statusMutex = Mutex()
}
