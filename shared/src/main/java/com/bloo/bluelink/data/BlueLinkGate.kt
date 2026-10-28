package com.bloo.bluelink.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-wide serialization for vehicle-status calls. */
object BlueLinkGate {
    // A single shared Mutex instance: callers must `withLock { ... }` around any
    // vehicle-status/fetch call so that at most one such call is in flight at a time for the whole
    // process, regardless of which component (UI or worker) initiated it.
    val statusMutex = Mutex()
}

/**
 * A background read of one car's status: taken under the account's lock, without waking the car, and null when it fails. The workers that
 * keep the stored snapshots fresh read this way; a failed read has nothing to contribute.
 */
suspend fun VehicleRepository.lastKnownStatus(v: Vehicle): VehicleStatus? =
    runCatching { BlueLinkGate.statusMutex.withLock { status(v, refresh = false) } }.getOrNull()
