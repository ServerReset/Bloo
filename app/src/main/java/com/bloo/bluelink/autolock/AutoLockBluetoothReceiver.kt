package com.bloo.bluelink.autolock

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Primary "left the car" trigger: fires when the phone disconnects from a car's paired
 * Bluetooth device (its head unit / hands-free profile). Also cancels a pending lock and
 * re-arms the geofence on reconnect (the user got back in). Ported from i5-AutoLock's
 * `BluetoothStateReceiver`, generalized to Bloo's multiple garages: every car with AutoLock
 * enabled is checked, since more than one could plausibly share a phone.
 *
 * Registered in the manifest (not `registerReceiver` at runtime) -- ACL connect/disconnect
 * is one of the implicit broadcasts still delivered to manifest-declared receivers even
 * with the app process dead, so no persistent "watching" service is needed to catch it.
 */
class AutoLockBluetoothReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothDevice.ACTION_ACL_DISCONNECTED && action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        @Suppress("DEPRECATION")
        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        // BluetoothDevice.getAddress() is @RequiresPermission(BLUETOOTH_CONNECT) from API 31 --
        // and this receiver fires for EVERY Bluetooth device this phone ever sees (headphones,
        // earbuds, anything), not just a configured car, since the manifest intent-filter has
        // no way to scope by device. Without runCatching here, a user who has Bluetooth
        // headphones and has never so much as opened AutoLock's Settings section -- so never
        // had a reason to grant BLUETOOTH_CONNECT -- would crash the whole app the very first
        // time those headphones connected or disconnected.
        val deviceMac = device?.let { runCatching { it.address }.getOrNull() } ?: return

        val pending = goAsync()
        val ctx = context.applicationContext
        scope.launch {
            try {
                // ONE DataStore read for every registered car, not one round trip per car --
                // this fires on every Bluetooth connect/disconnect this phone sees (any
                // device, not just a car's), so it wants to be cheap when there's nothing to
                // do, which is almost always.
                for ((vin, settings) in SettingsStore(ctx).allAutoLockConfigs()) {
                    if (!settings.enabled || !settings.useBluetoothTrigger) continue
                    if (!deviceMac.equals(settings.deviceAddress, ignoreCase = true)) continue

                    when (action) {
                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                            AppLog.log("AutoLock: car Bluetooth disconnected for $vin — starting evaluation.")
                            AutoLockService.start(ctx, vin)
                        }
                        BluetoothDevice.ACTION_ACL_CONNECTED -> {
                            AutoLockController.cancel(vin)
                            if (settings.useGeofence) {
                                LocationHelper.currentLocation(ctx)?.let { loc ->
                                    GeofenceManager.register(ctx, vin, loc.latitude, loc.longitude, settings.geofenceRadiusMeters)
                                    AppLog.log("AutoLock: arrived at $vin — geofence armed.")
                                }
                            }
                        }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
