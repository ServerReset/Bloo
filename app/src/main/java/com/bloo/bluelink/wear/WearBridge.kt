package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WearAuthBundle
import com.bloo.bluelink.data.WearCommand
import com.bloo.bluelink.data.WearCommandResult
import com.bloo.bluelink.data.WearCommandRunner
import com.bloo.bluelink.data.WearSessionDto
import com.bloo.bluelink.data.WearStatePayload
import com.bloo.bluelink.data.WearSync
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The phone half of the watch sync. It mirrors car snapshots + sessions to the
 * Wearable Data Layer and runs the commands the watch forwards (delegating to the
 * shared [WearCommandRunner], the same stored-session pattern the Quick-Settings
 * tiles use) — so the watch never needs the credentials to lock a door.
 */
object WearBridge {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fire-and-forget publish of the current snapshots (and auth) to a paired watch. */
    fun publish(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching { publishNow(app) }
            runCatching { publishAuth(app) }
        }
    }

    /** Publish the on-disk snapshots as a Data Layer item (phone → watch). */
    suspend fun publishNow(context: Context) {
        val data = SnapshotStore(context).current()
        val payload = WearStatePayload(
            vehicles = data.vehicles,
            selectedVin = data.selectedVin,
            producedAt = System.currentTimeMillis(),
        )
        val request = PutDataMapRequest.create(WearSync.PATH_STATE).apply {
            dataMap.putString(WearSync.KEY_PAYLOAD, WearSync.encodeState(payload))
            // A changing timestamp guarantees the item is treated as updated even
            // when the car states are byte-identical to the previous push.
            dataMap.putLong(WearSync.KEY_TIMESTAMP, payload.producedAt)
        }.asPutDataRequest().setUrgent()
        runCatching { Tasks.await(Wearable.getDataClient(context).putDataItem(request)) }
    }

    /**
     * Publish the signed-in sessions so the watch can operate standalone on its
     * own Wi-Fi/cell. Sent as a separate item with no timestamp, so the Data
     * Layer only re-transmits it when the tokens actually change.
     */
    suspend fun publishAuth(context: Context) {
        val sessionStore = SessionStore(context)
        val sessions = sessionStore.loggedInBrands().mapNotNull { brand ->
            sessionStore.load(brand)?.let { s ->
                WearSessionDto(
                    brand = s.brand.name,
                    accessToken = s.accessToken,
                    refreshToken = s.refreshToken,
                    username = s.username,
                    pin = s.pin,
                    deviceId = s.deviceId,
                )
            }
        }
        val request = PutDataMapRequest.create(WearSync.PATH_AUTH).apply {
            dataMap.putString(WearSync.KEY_PAYLOAD, WearSync.encodeAuth(WearAuthBundle(sessions)))
        }.asPutDataRequest().setUrgent()
        runCatching { Tasks.await(Wearable.getDataClient(context).putDataItem(request)) }
    }

    suspend fun execute(context: Context, command: WearCommand): WearCommandResult =
        WearCommandRunner.execute(context, command)

    suspend fun refresh(context: Context, vin: String) =
        WearCommandRunner.refresh(context, vin)
}
