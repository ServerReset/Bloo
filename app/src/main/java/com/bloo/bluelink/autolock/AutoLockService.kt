package com.bloo.bluelink.autolock

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
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
 * Short-lived foreground service that runs while one AutoLock evaluation is in flight for a
 * given car. Starts when that car's Bluetooth disconnects (or its geofence fires, if
 * Bluetooth isn't the configured trigger), drives [AutoLockController], reflects progress in
 * the notification, and stops itself once the evaluation reaches a terminal state. Ported/
 * simplified from i5-AutoLock's `AutoLockService` -- no persistent "watching" mode, since
 * Bloo's manifest-registered [AutoLockBluetoothReceiver] already catches the disconnect
 * without needing a service alive in between.
 */
class AutoLockService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var carName: String = "your car"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val vin = intent?.getStringExtra(EXTRA_VIN)
        if (vin == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_CANCEL -> {
                AutoLockController.cancel(vin)
                startForegroundCompat(vin, DetectionState.ABORTED, 0)
                scope.launch { delay(3000); stopSelf() }
                return START_NOT_STICKY
            }
            ACTION_LOCK_NOW -> {
                AutoLockController.lockNow(this, vin)
                observe(vin)
                return START_NOT_STICKY
            }
        }

        // Default: a trigger fired (Bluetooth disconnect / geofence exit).
        scope.launch {
            carName = SnapshotStore(applicationContext).current().vehicles.firstOrNull { it.vin == vin }?.name ?: carName
            startForegroundCompat(vin, DetectionState.CONFIRMING, 0)
        }
        startForegroundCompat(vin, DetectionState.CONFIRMING, 0)
        AutoLockController.onTriggerFired(this, vin)
        observe(vin)
        return START_NOT_STICKY
    }

    private fun observe(vin: String) {
        if (observeJob != null) return
        observeJob = scope.launch {
            AutoLockController.state.collect { all ->
                val s = all[vin] ?: return@collect
                startForegroundCompat(vin, s.detection, s.graceRemaining)
                if (s.detection.isTerminal) {
                    // Let the user glance at the result, then tear down.
                    delay(4000)
                    stopSelf()
                }
            }
        }
    }

    private fun startForegroundCompat(vin: String, state: DetectionState, grace: Int) {
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
