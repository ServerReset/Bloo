package com.bloo.bluelink.autolock

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.allAutoLockConfigs

/**
 * The "left the car" trigger: fires when the phone disconnects from a car's paired Bluetooth device
 * (its head unit / hands-free profile). Also cancels a pending lock on reconnect (the user got back
 * in).
 */
class AutoLockBluetoothReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothDevice.ACTION_ACL_DISCONNECTED && action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        @Suppress("DEPRECATION")
        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        // BluetoothDevice.getAddress() is @RequiresPermission(BLUETOOTH_CONNECT) from API 31 -- and
        // this receiver fires for EVERY Bluetooth device this phone ever sees (headphones, earbuds,
        // anything), not just a configured car, since the manifest intent-filter has no way to
        // scope by device.
        val deviceMac = device?.let { runCatching { it.address }.getOrNull() } ?: return

        val pending = goAsync()
        val ctx = context.applicationContext
        scope.launch {
            try {
                // ONE DataStore read for every registered car, not one round trip per car -- this
                // fires on every Bluetooth connect/disconnect this phone sees (any device, not just
                // a car's), so it wants to be cheap when there's nothing to do, which is almost
                // always.
                for ((vin, settings) in SettingsStore(ctx).allAutoLockConfigs()) {
                    if (!settings.enabled) continue
                    if (!deviceMac.equals(settings.deviceAddress, ignoreCase = true)) continue

                    when (action) {
                        BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                            AutoLockTrigger.onCarDisconnected(ctx, vin, settings)
                        BluetoothDevice.ACTION_ACL_CONNECTED ->
                            AutoLockTrigger.onCarConnected(ctx, vin)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
