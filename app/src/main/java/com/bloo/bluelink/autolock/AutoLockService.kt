package com.bloo.bluelink.autolock

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.bloo.bluelink.data.SnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Short-lived foreground service that runs while one or more AutoLock evaluations are in
 * flight. Starts when a car's Bluetooth disconnects (or its geofence fires, if Bluetooth
 * isn't the configured trigger), drives [AutoLockController], reflects progress in a
 * per-car notification, and stops itself once every evaluation it's tracking has reached a
 * terminal state. Ported/simplified from i5-AutoLock's `AutoLockService` -- no persistent
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val vin = intent?.getStringExtra(EXTRA_VIN)
        if (vin == null) {
            if (observeJobs.isEmpty()) stopSelf()
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
                AutoLockController.lockNow(this, vin)
                observe(vin)
                return START_NOT_STICKY
            }
        }

        // Default: a trigger fired (Bluetooth disconnect / geofence exit). The FIRST
        // startForegroundCompat call here is deliberately synchronous and unconditional --
        // Android requires startForeground() promptly and unconditionally after
        // startForegroundService(), and a coroutine dispatch (even on Main.immediate) is one
        // more thing that could theoretically be delayed under load. It posts with a
        // placeholder name; the coroutine below resolves the real one and reposts (posting
        // again to the SAME notification id updates it in place, not a new notification) as
        // soon as that fast local read completes, which is normally well before the
        // CONFIRMING phase's own 20s window is even half over.
        startForegroundCompat(vin, DetectionState.CONFIRMING, 0)
        scope.launch {
            SnapshotStore(applicationContext).current().vehicles.firstOrNull { it.vin == vin }?.name?.let {
                carNames[vin] = it
            }
            startForegroundCompat(vin, DetectionState.CONFIRMING, 0)
        }
        AutoLockController.onTriggerFired(this, vin)
        observe(vin)
        return START_NOT_STICKY
    }

    private fun observe(vin: String) {
        // computeIfAbsent, not the plain get-then-put this class used before: two
        // onStartCommand calls for the SAME vin arriving close together (a real disconnect
        // racing a "Simulate leaving" tap, say) must still only ever start one collector.
        observeJobs.computeIfAbsent(vin) {
            scope.launch {
                AutoLockController.state.collect { all ->
                    val s = all[vin] ?: return@collect
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

    private fun startForegroundCompat(vin: String, state: DetectionState, grace: Int) {
        val carName = carNames[vin] ?: "your car"
        val notification = AutoLockNotification.build(this, vin, carName, state, grace)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        } else {
            0
        }
        ServiceCompat.startForeground(this, AutoLockNotification.notificationId(vin), notification, type)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.bloo.bluelink.AUTOLOCK_CANCEL"
        const val ACTION_LOCK_NOW = "com.bloo.bluelink.AUTOLOCK_LOCK_NOW"
        const val EXTRA_VIN = "vin"

        /** Fires a one-off evaluation for [vin] (Bluetooth disconnect / geofence exit / a
         *  manual "Simulate leaving" test from Settings). */
        fun start(context: Context, vin: String) {
            val intent = Intent(context, AutoLockService::class.java).putExtra(EXTRA_VIN, vin)
            try {
                context.startForegroundService(intent)
            } catch (t: Throwable) {
                // e.g. ForegroundServiceStartNotAllowedException when the OS blocks a background
                // start (rare for a Bluetooth-disconnect-triggered start, which the system treats
                // as a qualifying event, but harmless to guard regardless).
                android.util.Log.w("AutoLockService", "startForegroundService blocked: ${t.message}")
            }
        }
    }
}
