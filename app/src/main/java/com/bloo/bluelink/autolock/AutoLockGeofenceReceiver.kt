package com.bloo.bluelink.autolock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.SettingsStore
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Battery-friendly "you left" trigger: the OS wakes this when the phone exits the car's
 * geofence, so AutoLock can confirm (or, if Bluetooth is disabled as a trigger, evaluate
 * outright) without a persistent service or polling. Ported from i5-AutoLock's
 * `GeofenceReceiver`.
 */
class AutoLockGeofenceReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT) return
        val vin = intent.getStringExtra(GeofenceManager.EXTRA_VIN) ?: return

        val pending = goAsync()
        val ctx = context.applicationContext
        scope.launch {
            try {
                val settings = SettingsStore(ctx).autoLockConfig(vin)
                if (settings.enabled && settings.useGeofence) {
                    // Logged per branch below, not unconditionally here -- with Bluetooth as
                    // the trigger (the common case), most geofence exits land in neither
                    // branch (this car isn't currently CONFIRMING), and "left the geofence"
                    // read as an event that did something even when it was a no-op.
                    if (AutoLockController.stateFor(vin).detection == DetectionState.CONFIRMING) {
                        AppLog.log("AutoLock: moved beyond the geofence for $vin — confirmed.")
                        AutoLockController.onMovedBeyondGeofence(vin)
                    } else if (!settings.useBluetoothTrigger) {
                        // Bluetooth trigger disabled: the geofence exit is the primary signal.
                        AppLog.log("AutoLock: left the geofence for $vin — starting evaluation.")
                        AutoLockService.start(ctx, vin)
                    }
                }
                // One-shot: re-registered on the next Bluetooth arrival at this car.
                GeofenceManager.remove(ctx, vin)
            } finally {
                pending.finish()
            }
        }
    }
}
