package com.bloo.bluelink.autolock

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Registers a battery-friendly, system-managed geofence around the parked car so the OS wakes
 * [AutoLockGeofenceReceiver] when the phone exits the radius, with no polling or persistent
 * service needed. Ported from i5-AutoLock's `GeofenceManager`. One geofence per VIN (the
 * request id encodes it), since more than one car can have AutoLock + geofence enabled.
 */
object GeofenceManager {
    private fun pendingIntent(context: Context, vin: String): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val intent = Intent(context, AutoLockGeofenceReceiver::class.java).apply {
            action = ACTION_GEOFENCE
            putExtra(EXTRA_VIN, vin)
        }
        return PendingIntent.getBroadcast(context, vin.hashCode(), intent, flags)
    }

    private fun hasBackgroundLocation(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine) return false
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission") // Guarded by hasBackgroundLocation() + runCatching.
    fun register(context: Context, vin: String, lat: Double, lng: Double, radiusMeters: Int) {
        if (!hasBackgroundLocation(context)) return
        val geofence = Geofence.Builder()
            .setRequestId(requestId(vin))
            .setCircularRegion(lat, lng, radiusMeters.toFloat().coerceAtLeast(50f))
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .setLoiteringDelay(5_000)
            .build()
        val request = GeofencingRequest.Builder()
            // Don't fire on registration even if already outside -- wait for a real exit.
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()
        runCatching { LocationServices.getGeofencingClient(context).addGeofences(request, pendingIntent(context, vin)) }
    }

    fun remove(context: Context, vin: String) {
        runCatching { LocationServices.getGeofencingClient(context).removeGeofences(pendingIntent(context, vin)) }
    }

    private fun requestId(vin: String) = "autolock_geofence_$vin"

    const val ACTION_GEOFENCE = "com.bloo.bluelink.AUTOLOCK_GEOFENCE"
    const val EXTRA_VIN = "vin"
}
