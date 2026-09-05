package com.bloo.bluelink.autolock

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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
}
