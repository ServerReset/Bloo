package com.bloo.bluelink.autolock

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The "left the car" trigger: fires when the phone disconnects from a car's paired
 * Bluetooth device (its head unit / hands-free profile). Also cancels a pending lock on
 * reconnect (the user got back in). Ported from i5-AutoLock's `BluetoothStateReceiver`,
 * generalized to Bloo's multiple garages: every car with AutoLock enabled is checked,
 * since more than one could plausibly share a phone.
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
                    if (!settings.enabled) continue
                    if (!deviceMac.equals(settings.deviceAddress, ignoreCase = true)) continue

                    when (action) {
                        BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                            AppLog.log("AutoLock: car Bluetooth disconnected for $vin — starting evaluation.")
                            val graceSeconds = settings.graceSeconds
                            val started = AutoLockService.start(ctx, vin)
                            // Always arm the deadline alarm, whichever path runs: with the
                            // service it is a safety net (the controller cancels it on any
                            // outcome, so it only ever fires if the process died mid-way);
                            // without it, the alarm IS the mechanism.
                            AutoLockAlarm.schedule(
                                ctx,
                                vin,
                                if (started) AutoLockAlarm.servicePathDeadlineMs(graceSeconds) else 0L,
                            )
                            if (!started) {
                                // No service, so this receiver owns the whole countdown. The
                                // record has to be persisted because the walk-away
                                // confirmation and the deadline can land in a later process.
                                val carName = SnapshotStore(ctx).current().vehicles
                                    .firstOrNull { it.vin == vin }?.name
                                AutoLockPending.begin(
                                    ctx,
                                    AutoLockPending.Record(
                                        vin = vin,
                                        carName = carName,
                                        deadlineMs = System.currentTimeMillis() + graceSeconds * 1000L,
                                        dryRun = settings.dryRun,
                                    ),
                                )
                                // Same mandatory walk-away confirmation the service path
                                // waits for; the transition is delivered to
                                // AutoLockActivityReceiver, which forwards it to the alarm
                                // receiver for an immediate lock.
                                ActivityRecognitionManager.start(ctx)
                                // The countdown the user can actually see and cancel. Its
                                // Cancel / Lock now actions are user interaction, which DOES
                                // permit the service start, so they keep working here.
                                runCatching {
                                    NotificationManagerCompat.from(ctx).notify(
                                        AutoLockNotification.notificationId(vin),
                                        AutoLockNotification.build(
                                            ctx, vin, carName ?: "your car",
                                            DetectionState.GRACE, graceSeconds,
                                        ),
                                    )
                                }
                            }
                        }
                        BluetoothDevice.ACTION_ACL_CONNECTED -> AutoLockController.cancel(ctx, vin)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
