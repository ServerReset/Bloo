package com.bloo.bluelink.autolock

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.ui.hasPermission

/**
 * The "left the car" trigger: fires when the phone disconnects from a car's paired Bluetooth device
 * (its head unit / hands-free profile). Also cancels a pending lock on reconnect (the user got back
 * in).
 *
 * Everything here is deliberately SYNCHRONOUS. Android exempts a background foreground-service start
 * that follows a Bluetooth broadcast from an app holding BLUETOOTH_CONNECT -- but the exemption is
 * tied to the app still processing that broadcast, and this receiver used to hand the work to a
 * coroutine behind `goAsync()` and read the settings file first. By the time
 * `startForegroundService()` ran, the broadcast was long finished and the exemption no longer applied,
 * which is one way the watcher notification never appeared. The address lookup is now a
 * SharedPreferences read (see [AutoLockAddresses]) and the trigger runs inside `onReceive`, so there is
 * no suspension point between the broadcast and the service start at all.
 */
class AutoLockBluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != BluetoothDevice.ACTION_ACL_DISCONNECTED && action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        if (com.bloo.bluelink.data.BootGate.isLocked(context)) {
            // Silent before, which made "AutoLock stopped working after a restart" impossible to tell
            // apart from "the Bluetooth event never arrived". See BootGate: after a reboot the app is
            // sealed until the user signs in again, and that seals this receiver with everything else.
            AppLog.log("AutoLock: ignoring $action - the app is sealed after a restart (BootGate), so no evaluation can run.")
            return
        }
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
            // AutoLock being broken, which is exactly how it gets reported.
            if (!context.hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT, android.os.Build.VERSION_CODES.S)) {
                AppLog.log("⚠ AutoLock: BLUETOOTH_CONNECT is not granted, so a car's Bluetooth disconnect cannot be matched to a car. Grant it from Settings, then AutoLock can see it.")
            }
            return
        }
        // ONE synchronous lookup, and a return for anything that is not a car. This is what stops a
        // pair of earbuds cold-starting the process into a coroutine and a settings read, and it is
        // also what makes the service start below land inside the broadcast.
        val vin = AutoLockAddresses.vinFor(context, deviceMac) ?: return
        val ctx = context.applicationContext
        if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            AutoLockTrigger.onCarDisconnected(ctx, vin)
        } else {
            AutoLockTrigger.onCarConnected(ctx, vin)
        }
    }
}
