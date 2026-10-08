package com.bloo.bluelink.wear

import com.bloo.bluelink.ioScope
import android.content.Context
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.VehicleSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * The watch's one gateway to car state and car commands.
 *
 * Commands run ON the watch whenever it has been signed in by the phone
 * ([WearCredentialSync]), so it works on its own -- no phone nearby, no Bluetooth link. A watch
 * that hasn't been signed in yet hands the command to the phone instead. Vehicles arrive from
 * [WearDataLayerSync], pushed by the phone and refreshed by the watch's own commands.
 */
class WearSnapshotRepository(private val context: Context) {
    private val scope = ioScope()

    /** Every car the phone has pushed, live. */
    val vehicles: Flow<List<VehicleSnapshot>> = WearDataLayerSync.vehicles

    /** Lock/unlock. */
    fun lock(vin: String) = run(vin, CarAction.TOGGLE_LOCK)

    /** Toggle climate. */
    fun climate(vin: String) = run(vin, CarAction.TOGGLE_CLIMATE)

    /** Start or stop charging. */
    fun charge(vin: String) = run(vin, CarAction.TOGGLE_CHARGE)

    /** Flash the hazards (Hyundai/Genesis). */
    fun flashLights(vin: String) = run(vin, CarAction.FLASH_LIGHTS)

    /** Pull fresh status when the watch has its own session. */
    fun refresh() {
        scope.launch { WearCredentialSync.refresh(context) }
    }

    private fun run(vin: String, action: String) {
        scope.launch { runWatchCommand(context, vin, action) }
    }

    /** The last command result, so the UI can clear pending + show a failure. */
    val lastCommandResult = WearDataLayerSync.lastCommandResult

    /** Whether a snapshot has been received from the phone at least once this session. */
    val connected = WearDataLayerSync.connected
}

/**
 * Runs [action] for [vin] on the watch itself when it has its own session, else hands it to the
 * phone. The watch's one command path, shared by [WearSnapshotRepository] and [WearActionReceiver]
 * (a notification's Stop action).
 */
internal suspend fun runWatchCommand(context: Context, vin: String, action: String) {
    if (WearCredentialSync.runLocally(context, vin, action) == null) {
        WearDataLayerSync.sendCommand(context, vin, action)
    }
}
