package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.CarCommand
import com.bloo.bluelink.data.CarCommandRunner
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The watch's one gateway to car state and car commands.
 *
 * "Live syncs to the phone" today means: both processes read the SAME on-disk
 * [SnapshotStore] that the phone's own background workers already mirror into, so a
 * status the phone fetches shows up on the watch without a bespoke wire protocol. The
 * next step -- pushing that snapshot across the Wearable Data Layer so the two devices
 * stay in step when they are NOT the same device -- layers on top of this class without
 * its callers changing; see [WearDataLayerSync].
 *
 * Commands run through the phone's own [CarCommandRunner] (the exact path the phone's
 * notification buttons and AutoLock use), so the watch is not a second, drifting command
 * implementation. On a standalone watch the runner needs a session the phone created, so
 * it simply reports the failure back rather than pretending to have acted.
 */
class WearSnapshotRepository(private val context: Context) {

    private val store = SnapshotStore(context.applicationContext)

    /** Every car the phone knows about, re-emitting on each snapshot write. */
    val vehicles: Flow<List<VehicleSnapshot>> = store.payload.map { it.vehicles }

    suspend fun send(command: CarCommand) = CarCommandRunner.execute(context, command)

    fun lock(vin: String) = CarCommand(vin, CarAction.TOGGLE_LOCK)
    fun climate(vin: String) = CarCommand(vin, CarAction.TOGGLE_CLIMATE)
}
