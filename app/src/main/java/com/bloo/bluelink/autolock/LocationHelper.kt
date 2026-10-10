package com.bloo.bluelink.autolock

import android.content.Context
import com.bloo.bluelink.ui.hasLocationPermission
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A single best-effort current-location read (the phone's own last-known position), and a
 * continuous stream of the same for surfaces that want it live.
 */
object LocationHelper {
    /** How often the live stream asks for a fix, and the fastest it will accept one. */
    const val LIVE_INTERVAL_MS = 10_000L
    const val LIVE_MIN_INTERVAL_MS = 5_000L

    fun hasPermission(context: Context): Boolean = context.hasLocationPermission()

    /**
     * One best-effort current-location read: a fresh high-accuracy fix first, falling back to the
     * provider's last known fix. Without the fallback a caller got null every time the fresh read
     * timed out (which it does routinely indoors), so the map's "you are here" dot could just never
     * appear even though a perfectly good recent fix was sitting there.
     */
    suspend fun currentLocation(context: Context): android.location.Location? {
        if (!hasPermission(context)) return null
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val fresh = try {
            suspendCancellableCoroutine<android.location.Location?> { cont ->
                fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        } catch (_: SecurityException) {
            null
        }
        if (fresh != null) return fresh
        return try {
            suspendCancellableCoroutine<android.location.Location?> { cont ->
                fused.lastLocation
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        } catch (_: SecurityException) {
            null
        }
    }

    /**
     * A continuous stream of fused location fixes, for surfaces that want the device's own position
     * to track in real time (the car map's "you are here" dot, weather's distance-to-car).
     *
     * IMPORTANT: with no location permission the flow CLOSES immediately (rather than idling open
     * behind an `awaitClose`), so the collector's job COMPLETES. An idling-but-"active" job could
     * never be replaced, which is exactly how the dot stayed dead forever after a permission that
     * was granted outside the app's own dialog (see AppViewModel.ensureLiveDeviceLocation).
     */
    fun liveUpdates(
        context: Context,
        intervalMs: Long = LIVE_INTERVAL_MS,
        minUpdateMs: Long = LIVE_MIN_INTERVAL_MS,
        priority: Int = Priority.PRIORITY_HIGH_ACCURACY,
    ): Flow<android.location.Location> = callbackFlow {
        if (!hasPermission(context)) {
            close()
            return@callbackFlow
        }
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateIntervalMillis(minUpdateMs)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }
        try {
            fused.requestLocationUpdates(request, callback, null)
        } catch (_: SecurityException) {
            close()
            return@callbackFlow
        }
        awaitClose { fused.removeLocationUpdates(callback) }
    }
}
