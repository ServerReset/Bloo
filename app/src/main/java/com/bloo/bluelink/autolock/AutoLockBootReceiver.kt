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
import com.bloo.bluelink.MainActivity
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
 *
 * ACTION_MY_PACKAGE_REPLACED also carries a second, unrelated job now: relaunching
 * MainActivity. Bloo self-updates from GitHub releases (it isn't on the Play Store), and
 * BOTH install paths (the Shizuku silent-install session and the tap-through system
 * installer) end with this process gone -- a replace-install force-stops the process the
 * instant the swap lands, and there was previously nothing to bring the app back afterward,
 * so the user was simply dropped on whatever the OS fell through to (home screen, or the
 * keyguard if the screen had locked mid-install). This broadcast is the one signal that
 * fires reliably exactly once the swap actually completes, regardless of which install path
 * did it or whether this process survived to see it -- and a manifest-registered receiver
 * responding to it is one of the platform's background-activity-start exemptions, so
 * starting MainActivity from here (unlike a plain background startActivity call) is allowed.
 * Deliberately NOT done on ACTION_BOOT_COMPLETED -- auto-launching on every device boot
 * would be a surprising, unwanted app launch with nothing to do with an update at all.
 */
class AutoLockBootReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            runCatching {
                val relaunch = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(relaunch)
            }
        }
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
