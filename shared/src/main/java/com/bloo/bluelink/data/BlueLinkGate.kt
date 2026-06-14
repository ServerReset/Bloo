package com.bloo.bluelink.data

import kotlinx.coroutines.sync.Mutex

/**
 * Process-wide serialization for vehicle-status calls. Blue Link rejects
 * overlapping requests on the same account with "a previous request is pending",
 * so both the foreground ViewModel and the background [AlertWorker] funnel their
 * status/vehicle fetches through this single mutex.
 */
object BlueLinkGate {
    val statusMutex = Mutex()
}
