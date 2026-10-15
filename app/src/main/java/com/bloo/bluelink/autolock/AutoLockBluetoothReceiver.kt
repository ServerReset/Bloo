package com.bloo.bluelink.autolock

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.allAutoLockConfigs
import com.bloo.bluelink.ui.hasPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The "left the car" trigger: fires when the phone disconnects from a car's paired Bluetooth device
 * (its head unit / hands-free profile). Also cancels a pending lock on reconnect (the user got back
 * in).
 */
class AutoLockBluetoothReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (com.bloo.bluelink.data.BootGate.isLocked(context)) {
            // Silent before, which made "AutoLock stopped working after a restart" impossible to tell
            // apart from "the Bluetooth event never arrived". See BootGate: after a reboot the app is
            // sealed until the user signs in again, and that seals this receiver with everything else.
            AppLog.log("AutoLock: ignoring $action — the app is sealed after a restart (BootGate), so no evaluation can run.")
            return
        }
        if (action != BluetoothDevice.ACTION_ACL_DISCONNECTED && action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        @Suppress("DEPRECATION")
        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        // BluetoothDevice.getAddress() is @RequiresPermission(BLUETOOTH_CONNECT) from API 31 -- and
        // this receiver fires for EVERY Bluetooth device this phone ever sees (headphones, earbuds,
        // anything), not just a configured car, since the manifest intent-filter has no way to
        // scope by device.
        val deviceMac = device?.let { runCatching { it.address }.getOrNull() }
        if (deviceMac == null) {
            // The address is the ONLY thing that can match this event to a car, and without
            // BLUETOOTH_CONNECT it reads as null -- so the app did nothing at all: no trigger, no
            // notification, no command, and no log line saying why. That is indistinguishable from
            // AutoLock being broken, which is exactly how it gets reported. Say it out loud, once per
            // event, and only when the permission is the reason.
            if (!context.hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT, android.os.Build.VERSION_CODES.S)) {
                AppLog.log("⚠ AutoLock: BLUETOOTH_CONNECT is not granted, so a car's Bluetooth disconnect cannot be matched to a car. Grant it from Settings, then AutoLock can see it.")
            }
            return
        }

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
