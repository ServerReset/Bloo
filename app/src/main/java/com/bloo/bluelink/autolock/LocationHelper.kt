package com.bloo.bluelink.autolock

import android.Manifest
import android.content.Context
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import com.bloo.bluelink.ui.hasPermission

/**
 * A single best-effort current-location read (the phone's own last-known position), and a
 * continuous stream of the same for surfaces that want it live.
 */
object LocationHelper {
    private fun hasPermission(context: Context): Boolean =
        context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)

    suspend fun currentLocation(context: Context): android.location.Location? {
        if (!hasPermission(context)) return null
        val fused = LocationServices.getFusedLocationProviderClient(context)
        return try {
            suspendCancellableCoroutine { cont ->
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * A continuous stream of fused location fixes, for surfaces that want the device's own position
     * to track in real time while they're on screen (the car map's "you are here" dot, weather's
     * distance-to-car) instead of only the single point-in-time reads [currentLocation] gives on
     * refresh/open. [intervalMs] is a balance, not a hard real-time guarantee -- fused location
     * coalesces/batches updates on its own schedule regardless of what is requested.
     */
    fun liveUpdates(context: Context, intervalMs: Long = 90_000L): Flow<android.location.Location> = callbackFlow {
        if (!hasPermission(context)) {
            awaitClose {}
            return@callbackFlow
        }
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, intervalMs).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }
        try {
            fused.requestLocationUpdates(request, callback, null)
        } catch (_: SecurityException) {
            close()
        }
        awaitClose { fused.removeLocationUpdates(callback) }
    }
}
