package com.bloo.bluelink.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * DEBUG-ONLY sample cars for the watch, so its screens can be photographed without a paired phone or an account (lives in the debug
 * source set, so it is absent from a release build). Two cars that belong to nobody, one locked and one charging, with no location:
 *
 *   adb shell am broadcast -n com.bloo.bluelink/com.bloo.bluelink.wear.WearDebugFixturesReceiver
 *
 * The cars are written to the watch's own store and the screens reload from it, exactly as after a command the watch ran itself.
 */
class WearDebugFixturesReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SnapshotStore(app).saveVehicles(
                    listOf(
                        VehicleSnapshot(
                            vin = "SAMPLEVIN00000001", name = "My Car", model = "IONIQ 5", isEv = true,
                            percent = 72, rangeMi = 214, locked = true, charging = false, climateOn = false, engineOn = false,
                        ),
                        VehicleSnapshot(
                            vin = "SAMPLEVIN00000002", name = "Weekend Car", model = "KONA Electric", isEv = true,
                            percent = 45, rangeMi = 118, locked = false, charging = true, climateOn = false, engineOn = false,
                        ),
                    ),
                )
                WearDataLayerSync.reloadFromStore(app, null)
            } finally {
                pending.finish()
            }
        }
    }
}
