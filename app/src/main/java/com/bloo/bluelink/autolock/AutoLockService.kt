package com.bloo.bluelink.autolock

import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.allAutoLockConfigs

/**
 * Short-lived foreground service that runs while one or more AutoLock evaluations are in
 * flight. Starts when a car's Bluetooth disconnects, drives [AutoLockController], reflects
 * progress in a per-car notification, and stops itself once every evaluation it's tracking
 * has reached a terminal state. Ported/simplified from i5-AutoLock's `AutoLockService` -- no persistent
 * "watching" mode, since Bloo's manifest-registered [AutoLockBluetoothReceiver] already
 * catches the disconnect without needing a service alive in between.
 *
 * Per-VIN state (`carNames`/`observeJobs`), not a single field of each: a Service is one
 * instance handling every onStartCommand call, so a second car's Bluetooth disconnecting
 * while the first is still mid-evaluation reaches the SAME instance. A single `carName`/
 * `observeJob` field here (this class's first version) meant the second car's live progress
 * was silently dropped by an "already observing" guard keyed on nothing car-specific, and
 * whichever car resolved its name last stomped the other's notification text -- exactly the
 * multi-car scenario this whole app is built around (one phone, more than one garage car).
 */
class AutoLockService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val observeJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
    private val carNames = java.util.concurrent.ConcurrentHashMap<String, String>()
    private var watcher: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    ?: return
                val address = runCatching { device.address }.getOrNull() ?: return
                scope.launch {
                    SettingsStore(applicationContext).allAutoLockConfigs().forEach { (vin, config) ->
                        if (!config.enabled || !address.equals(config.deviceAddress, ignoreCase = true)) return@forEach
                        AppLog.log("AutoLock: watcher received ${intent.action} for $vin")
                        if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                            AutoLockTrigger.onCarDisconnected(applicationContext, vin, config)
                        } else if (intent.action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                            AutoLockTrigger.onCarConnected(applicationContext, vin)
                        }
                    }
                }
            }
        }
        // System Bluetooth broadcasts require an exported dynamic receiver on modern Android.
        // The exact configured address is checked before any action is taken.
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            },
            androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )
        watcher = receiver
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_WATCH) {
            val vin = intent.getStringExtra(EXTRA_VIN)
            AppLog.log("AutoLock: background Bluetooth watcher started")
            if (vin != null) startForegroundCompat(vin, DetectionState.IDLE, 0)
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP_WATCH) {
            // Belt-and-suspenders alongside AutoLockController.cancel in setAutoLockConfig: if
            // the watcher is being torn down, make sure no evaluation it may have kicked off is
            // still counting down toward a lock. `forgetAll` needs the vins, which the service
            // doesn't hold here, so cancel every VIN the controller currently tracks.
            runCatching {
                AutoLockController.state.value.keys.forEach { AutoLockController.cancel(it) }
            }
            stopSelf()
            return START_NOT_STICKY
        }
        val vin = intent?.getStringExtra(EXTRA_VIN)
        if (vin == null) {
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_CANCEL -> {
                AutoLockController.cancel(vin)
                startForegroundCompat(vin, DetectionState.ABORTED, 0)
                scope.launch { delay(3000); finishTracking(vin) }
                return START_NOT_STICKY
            }
            ACTION_LOCK_NOW -> {
                // "Lock now" from the notification: skip the rest of the grace countdown. The
                // evaluation lives in the controller (started by the trigger), so this only
                // nudges it -- no separate fallback path to reconcile any more (see
                // AutoLockTrigger's own doc).
                AutoLockController.lockNow(this, vin)
                observe(vin)
                return START_NOT_STICKY
            }
        }

        // Default: a trigger fired (Bluetooth disconnect). The FIRST
        // startForegroundCompat call here is deliberately synchronous and unconditional --
        // Android requires startForeground() promptly and unconditionally after
        // startForegroundService(), and a coroutine dispatch (even on Main.immediate) is one
        // more thing that could theoretically be delayed under load. It posts with a
        // placeholder name; the coroutine below resolves the real one and reposts (posting
        // again to the SAME notification id updates it in place, not a new notification) as
        // soon as that fast local read completes, which is normally well before the
        // CONFIRMING phase's own 20s window is even half over.
        if (!startForegroundCompat(vin, DetectionState.CONFIRMING, 0)) {
            // The process could not become a foreground service. The evaluation is already
            // running in AutoLockController's own scope (the trigger started it before this
            // service was even asked for), so there is nothing to start here -- this service
            // is only the notification wrapper. Just stand down; the lock still happens.
            stopSelf()
            return START_NOT_STICKY
        }
        scope.launch {
            SnapshotStore(applicationContext).current().vehicles.firstOrNull { it.vin == vin }?.name?.let {
                carNames[vin] = it
            }
            startForegroundCompat(vin, DetectionState.CONFIRMING, 0)
        }
        // No onTriggerFired here any more: AutoLockTrigger already started the controller's
        // evaluation, and calling it again would cancel and restart that very job (see
        // onTriggerFired). This service just observes.
        observe(vin)
        return START_NOT_STICKY
    }

    private fun observe(vin: String) {
        // computeIfAbsent, not the plain get-then-put this class used before: two
        // onStartCommand calls for the SAME vin arriving close together (a real disconnect
        // racing a "Simulate leaving" tap, say) must still only ever start one collector.
        observeJobs.computeIfAbsent(vin) {
            scope.launch {
                // Whether this car has ever had a state in the map. Its own evaluation sets
                // that on its first step, so the very first emission arriving before that must
                // not be read as "forgotten" -- but a map entry that GOES AWAY (forgetAll on
                // sign-out) is exactly that, and this service must not sit in the foreground
                // watching a car nothing will ever evaluate again.
                var sawState = false
                AutoLockController.state.collect { all ->
                    val s = all[vin]
                    if (s == null) {
                        if (sawState) finishTracking(vin)
                        return@collect
                    }
                    sawState = true
                    startForegroundCompat(vin, s.detection, s.graceRemaining)
                    if (s.detection.isTerminal) {
                        // Let the user glance at the result, then tear this car's notification
                        // down -- but only stop the whole SERVICE once every car it's tracking
                        // has reached the same point.
                        delay(4000)
                        finishTracking(vin)
                    }
                }
            }
        }
    }

    /** Drops a finished (or cancelled) car's own notification and live-state collector, then
     *  stops the service entirely once nothing else is being tracked. */
    private fun finishTracking(vin: String) {
        observeJobs.remove(vin)?.cancel()
        carNames.remove(vin)
        runCatching { NotificationManagerCompat.from(this).cancel(AutoLockNotification.notificationId(vin)) }
        if (observeJobs.isEmpty()) stopSelf() else reanchorForeground()
    }

    /** Foreground promotion is tied to whichever notification id was posted LAST via
     *  startForeground() -- if that car's notification just got cancelled above and others
     *  are still being tracked, re-post one of the survivors so the service (and their own
     *  live updates) keeps running instead of losing its foreground standing. */
    private fun reanchorForeground() {
        val vin = observeJobs.keys.firstOrNull() ?: return
        val s = AutoLockController.stateFor(vin)
        startForegroundCompat(vin, s.detection, s.graceRemaining)
    }

    /** Returns false when the process could not be promoted -- see the guard's own comment. */
    private fun startForegroundCompat(vin: String, state: DetectionState, grace: Int): Boolean {
        val carName = carNames[vin] ?: "your car"
        val notification = AutoLockNotification.build(this, vin, carName, state, grace)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        // A connectedDevice foreground service needs one of the Bluetooth (or network)
        // permissions on top of FOREGROUND_SERVICE_CONNECTED_DEVICE. A device without
        // BLUETOOTH_CONNECT granted -- the permission AutoLock's own device picker asks for,
        // so a car that is actually configured has it, but a revoked or never-granted one does
        // not -- makes startForeground throw SecurityException. Uncaught, that killed the WHOLE
        // APP every time an evaluation started. A background convenience must never do that.
        return runCatching {
            ServiceCompat.startForeground(this, AutoLockNotification.notificationId(vin), notification, type)
        }.onFailure {
            AppLog.log("⚠ AutoLock: couldn't promote the service to foreground (${it.javaClass.simpleName}).")
        }.isSuccess
    }

    override fun onDestroy() {
        watcher?.let { runCatching { unregisterReceiver(it) } }
        watcher = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.bloo.bluelink.AUTOLOCK_CANCEL"
        const val ACTION_LOCK_NOW = "com.bloo.bluelink.AUTOLOCK_LOCK_NOW"
        const val ACTION_START_WATCH = "com.bloo.bluelink.AUTOLOCK_START_WATCH"
        const val ACTION_STOP_WATCH = "com.bloo.bluelink.AUTOLOCK_STOP_WATCH"
        const val EXTRA_VIN = "vin"

        /** Starts the notification wrapper for [vin] (Bluetooth disconnect / a manual
         *  "Simulate leaving" test from Settings). Best-effort: returns false when the OS
         *  refuses a background foreground-service start (the norm on Android 12+), which is
         *  fine now -- the evaluation itself runs in AutoLockController's own scope, started by
         *  the trigger, and does not depend on this. */
        fun start(context: Context, vin: String): Boolean {
            val intent = Intent(context, AutoLockService::class.java).putExtra(EXTRA_VIN, vin)
            return try {
                context.startForegroundService(intent)
                true
            } catch (t: Throwable) {
                AppLog.log("AutoLock: progress notification service not allowed in the background (${t.javaClass.simpleName}); the lock still runs.")
                false
            }
        }
    }
}
