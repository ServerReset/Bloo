package com.bloo.bluelink.autolock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
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

/** A single best-effort current-location read, used to register (arrival) and confirm
 *  (walked-beyond-radius) AutoLock's geofence. Ported down from i5-AutoLock's
 *  `LocationHelper` -- Bloo's own weather "My location" feature only needs a one-shot coarse
 *  fix via the platform `LocationManager`, so this is new: AutoLock's geofence needs the
 *  fine, fused location fix that comes with play-services-location (already a dependency
 *  for the geofencing/activity-recognition APIs it also uses). */
object LocationHelper {
    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

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
     * A continuous stream of fused location fixes, for surfaces that want the
     * device's own position to track in real time while they're on screen (the
     * car map's "you are here" dot, weather's distance-to-car) instead of only
     * the single point-in-time reads [currentLocation] gives on refresh/open.
     *
     * [intervalMs] is a balance, not a hard real-time guarantee -- fused
     * location coalesces/batches updates on its own schedule regardless of what
     * is requested. PRIORITY_BALANCED_POWER_ACCURACY (network+GPS, city-block
     * accuracy) rather than HIGH_ACCURACY: this drives a map dot and a "how far
     * is the car" readout, not turn-by-turn navigation, so the battery cost of
     * raw GPS is not worth paying here.
     *
     * Emits nothing (an empty flow that never completes on its own) without
     * fine-location permission -- collectors should stop collecting when the
     * screen that wanted this goes away, same as any other flow; there's
     * nothing here for a caller to poll for permission having been granted
     * mid-collection since Android doesn't restart an in-flight collector for
     * that anyway.
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
