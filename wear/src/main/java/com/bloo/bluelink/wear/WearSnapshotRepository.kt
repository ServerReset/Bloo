package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.VehicleSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * The watch's one gateway to car state and car commands.
 *
 * It is a pure AUXILIARY surface: it does NOT read Drive, does NOT hit the network, and does
 * NOT execute commands itself. Vehicles come from [WearDataLayerSync] (pushed by the phone in
 * real time); commands are FORWARDED to the phone, which runs them through its normal command
 * path and reports back. The watch only ever renders what the phone sends and asks the phone to
 * act on its behalf.
 */
class WearSnapshotRepository(private val context: Context) {

    /** Every car the phone has pushed, live. */
    val vehicles: Flow<List<VehicleSnapshot>> = WearDataLayerSync.vehicles

    /** Ask the phone to lock/unlock. */
    fun lock(vin: String) = WearDataLayerSync.sendCommand(context, vin, CarAction.TOGGLE_LOCK)

    /** Ask the phone to toggle climate. */
    fun climate(vin: String) = WearDataLayerSync.sendCommand(context, vin, CarAction.TOGGLE_CLIMATE)

    /** The phone's last command result, so the UI can clear pending + show a failure. */
    val lastCommandResult = WearDataLayerSync.lastCommandResult
}
