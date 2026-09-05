package com.bloo.bluelink.autolock

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-arms AutoLock's geofences after they've been silently dropped from under it: a device
 * reboot clears every app's registered geofences outright, and an app update can leave a
 * PendingIntent-backed registration pointing at a component the OS no longer resolves the
 * same way. Without this, a car parked (Bluetooth already connected) through either event
 * would have no geofence again until its NEXT connect -- normally the next drive, but for
 * someone who reboots their phone while sitting in the driveway with the car already
 * connected, that could be a long wait with the confirmation signal quietly not covering them.
 *
 * The Bluetooth disconnect TRIGGER itself needs none of this: it is the manifest-registered
 * [AutoLockBluetoothReceiver], which the OS re-wires automatically across both a reboot and
 * an app update -- this receiver exists purely to restore the one piece of state (the
 * geofence) that a reboot actively deletes rather than merely stops delivering events for.
 */
class AutoLockBootReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val ctx = context.applicationContext
        scope.launch {
            try {
                for ((vin, settings) in SettingsStore(ctx).allAutoLockConfigs()) {
                    if (!settings.enabled || !settings.useGeofence) continue
                    val address = settings.deviceAddress ?: continue
                    if (!isConnected(ctx, address)) continue
                    val loc = LocationHelper.currentLocation(ctx) ?: continue
                    GeofenceManager.register(ctx, vin, loc.latitude, loc.longitude, settings.geofenceRadiusMeters)
                    AppLog.log("AutoLock: re-armed the geofence for $vin after ${intent.action?.substringAfterLast('.')}.")
                }
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Whether [address] is currently connected on a profile a car's head unit is likely to
     * expose (hands-free calling and/or media audio). Uses only public API
     * ([BluetoothManager.getConnectionState], not the hidden `BluetoothDevice.isConnected()`
     * some apps reach for via reflection) -- fragile-but-common tricks like that have no place
     * in a receiver that runs unconditionally on every boot.
     */
    private fun isConnected(context: Context, address: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return false
        val adapter = manager.adapter ?: return false
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull() ?: return false
        return listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP).any { profile ->
            runCatching { manager.getConnectionState(device, profile) == BluetoothProfile.STATE_CONNECTED }.getOrDefault(false)
        }
    }
}
