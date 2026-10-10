package com.bloo.bluelink.autolock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.autoLockConfig
import com.bloo.bluelink.data.setAutoLockConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * DEBUG-ONLY AutoLock trigger (lives in the debug source set, so it is absent from a release
 * APK). Exists so the whole background flow can be exercised from adb without physically
 * walking away from a car -- which is otherwise the only way to test any of it, and made the
 * "AutoLock does nothing" bug (a blocked background foreground-service start) invisible in
 * testing: with the phone in hand and the app open, the service start is ALLOWED, so the
 * fallback path never ran.
 *
 * Usage (debug build, notification permission granted). Target the component explicitly
 * (`-n`) -- a bare `-a` broadcast is an implicit one and does NOT reach a manifest receiver
 * in a backgrounded app on modern Android, which is exactly the mistake that makes this look
 * broken when it isn't:
 *   adb shell am broadcast -n com.bloo.bluelink/com.bloo.bluelink.autolock.AutoLockDebugReceiver --es cmd trigger
 *   adb shell am broadcast -n com.bloo.bluelink/com.bloo.bluelink.autolock.AutoLockDebugReceiver --es cmd walk
 *
 * `trigger` enables AutoLock for the first car in the snapshot with DRY RUN ON (so no real
 * command is ever sent) and a 5-second grace, then runs [AutoLockTrigger.onCarDisconnected] --
 * the SAME code the manifest Bluetooth receiver runs. `walk` confirms walking to every
 * in-flight evaluation, exactly as the activity-recognition receiver does.
 */
class AutoLockDebugReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: "trigger"
        Log.i("AutoLockDebug", "received cmd=$cmd")
        val ctx = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val vin = intent.getStringExtra("vin")
                    ?: SnapshotStore(ctx).current().vehicles.firstOrNull()?.vin
                if (vin == null) {
                    Log.w("AutoLockDebug", "no vehicle in the snapshot — sign in first")
                    AppLog.log("AutoLock debug: no vehicle in the snapshot — sign in first.")
                    return@launch
                }
                Log.i("AutoLockDebug", "cmd=$cmd vin=$vin")
                when (cmd) {
                    "trigger" -> {
                        // Dry run + a short grace, so this can never touch a real car.
                        SettingsStore(ctx).setAutoLockConfig(
                            vin,
                            AutoLockConfig(
                                enabled = true,
                                deviceAddress = "00:00:00:00:00:00",
                                deviceName = "debug",
                                graceSeconds = 5,
                                dryRun = true,
                            ),
                        )
                        AppLog.log("AutoLock debug: triggering an evaluation for $vin (dry run).")
                        Log.i("AutoLockDebug", "trigger: running the REAL trigger sequence for $vin")
                        // The real path, unchanged: the same AutoLockTrigger the manifest
                        // receiver calls, so the debug run exercises exactly what a real
                        // Bluetooth disconnect does.
                        AutoLockTrigger.onCarDisconnected(
                            ctx, vin, SettingsStore(ctx).autoLockConfig(vin), log = false,
                        )
                    }
                    "walk" -> AutoLockController.onWalkingConfirmedAny()
                    // Posts the outcome notification directly, so the notification's channel,
                    // sound and wording can be verified without a car that happens to be
                    // unlocked (the emulator's test car reports "already locked", which the
                    // policy correctly skips).
                    "notify-dry" -> {
                        val carName = SnapshotStore(ctx).current().vehicles
                            .firstOrNull { it.vin == vin }?.name ?: "your car"
                        Log.i("AutoLockDebug", "posting the dry-run notification for $carName")
                        AutoLockEventNotifier.notifyLocked(ctx, vin, carName, dryRun = true)
                    }
                    "notify-live" -> {
                        val carName = SnapshotStore(ctx).current().vehicles
                            .firstOrNull { it.vin == vin }?.name ?: "your car"
                        Log.i("AutoLockDebug", "posting the locked notification for $carName")
                        AutoLockEventNotifier.notifyLocked(ctx, vin, carName, dryRun = false)
                    }
                    else -> AppLog.log("AutoLock debug: unknown cmd '$cmd'")
                }
            } finally {
                pending.finish()
            }
        }
    }
}
