package com.bloo.wear

import android.content.Context
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WearAction
import com.bloo.bluelink.data.WearCommand
import com.bloo.bluelink.data.WearClimateState
import com.bloo.bluelink.data.WearPresets
import com.bloo.bluelink.data.WearCommandRunner
import com.bloo.bluelink.data.WearSync
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The watch's link to the car. Prefers relaying through a connected phone (which
 * holds the tested, authenticated session); falls back to running the command
 * itself with the synced session when no phone is reachable — that's the
 * standalone-on-Wi-Fi/cell path.
 */
object WearComms {

    /** The id of a connected phone node, or null when none is reachable. */
    suspend fun phoneNodeId(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                .firstOrNull { it.isNearby }?.id
                ?: Tasks.await(Wearable.getNodeClient(context).connectedNodes).firstOrNull()?.id
        }.getOrNull()
    }

    /** Run a command: optimistic local flip, then relay to the phone (or execute
     *  it standalone). */
    suspend fun send(context: Context, command: WearCommand) {
        withContext(Dispatchers.IO) {
            // Optimistic update so the tile reacts the instant it's tapped.
            runCatching {
                val store = SnapshotStore(context)
                store.current().vehicles.firstOrNull { it.vin == command.vin }?.let {
                    store.updateVehicle(WearCommandRunner.optimistic(it, command.action))
                }
            }
            val node = phoneNodeId(context)
            if (node != null) {
                runCatching {
                    Tasks.await(
                        Wearable.getMessageClient(context).sendMessage(
                            node, WearSync.PATH_COMMAND, WearSync.encodeCommand(command).toByteArray(),
                        )
                    )
                }.onFailure {
                    // Phone dropped mid-send — fall back to standalone.
                    WearCommandRunner.execute(context, command)
                }
            } else {
                WearCommandRunner.execute(context, command)
            }
        }
    }

    /** Ask for fresh data: relay a refresh to the phone, or refresh standalone. */
    suspend fun requestSync(context: Context, vin: String, refresh: Boolean) {
        withContext(Dispatchers.IO) {
            val node = phoneNodeId(context)
            val command = WearCommand(vin = vin, action = if (refresh) WearAction.REFRESH else "")
            if (node != null) {
                runCatching {
                    Tasks.await(
                        Wearable.getMessageClient(context).sendMessage(
                            node, WearSync.PATH_SYNC_REQUEST, WearSync.encodeCommand(command).toByteArray(),
                        )
                    )
                }.onFailure { if (refresh) WearCommandRunner.refresh(context, vin) }
            } else if (refresh) {
                WearCommandRunner.refresh(context, vin)
            }
        }
    }

    /** Publish the watch's live climate draft so the phone mirrors it. Written as
     *  a DataItem on the shared [WearSync.PATH_CLIMATE] channel. */
    suspend fun publishClimate(context: Context, state: WearClimateState) {
        withContext(Dispatchers.IO) {
            runCatching {
                val request = PutDataMapRequest.create(WearSync.PATH_CLIMATE).apply {
                    dataMap.putString(WearSync.KEY_PAYLOAD, WearSync.encodeClimate(state))
                    dataMap.putLong(WearSync.KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                Tasks.await(Wearable.getDataClient(context).putDataItem(request))
            }
        }
    }

    /** Publish presets the watch created/edited so the phone saves + mirrors them. */
    suspend fun publishPresets(context: Context, presets: WearPresets) {
        withContext(Dispatchers.IO) {
            runCatching {
                val request = PutDataMapRequest.create(WearSync.PATH_PRESETS).apply {
                    dataMap.putString(WearSync.KEY_PAYLOAD, WearSync.encodePresets(presets))
                    dataMap.putLong(WearSync.KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                Tasks.await(Wearable.getDataClient(context).putDataItem(request))
            }
        }
    }

    /** Publish a car's reordered pebble order so the phone saves it as that car's
     *  section order and mirrors it back to every device. */
    suspend fun publishPebbleOrder(context: Context, vin: String, order: List<String>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val payload = com.bloo.bluelink.data.WearPebbleOrder(vin, order)
                val request = PutDataMapRequest.create(WearSync.PATH_PEBBLE_ORDER).apply {
                    dataMap.putString(WearSync.KEY_PAYLOAD, WearSync.encodePebbleOrder(payload))
                    dataMap.putLong(WearSync.KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                Tasks.await(Wearable.getDataClient(context).putDataItem(request))
            }
        }
    }

    /** On launch, pull whatever the phone already published so the UI isn't empty
     *  while waiting for the next DataChanged callback. */
    suspend fun pullLatest(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching {
                val items = Tasks.await(Wearable.getDataClient(context).dataItems)
                try {
                    items.forEach { item ->
                        val raw = DataMapItem.fromDataItem(item).dataMap.getString(WearSync.KEY_PAYLOAD)
                            ?: return@forEach
                        when (item.uri.path) {
                            WearSync.PATH_STATE -> WearStateWriter.persistState(context, raw)
                            WearSync.PATH_AUTH -> WearStateWriter.persistAuth(context, raw)
                            WearSync.PATH_SETTINGS -> WearStateWriter.persistSettings(context, raw)
                            WearSync.PATH_PRESETS -> WearStateWriter.persistPresets(context, raw)
                            WearSync.PATH_CLIMATE -> WearStateWriter.persistClimate(context, raw)
                            WearSync.PATH_EXTRAS -> WearStateWriter.persistExtras(context, raw)
                        }
                    }
                } finally {
                    items.release()
                }
            }
        }
    }
}
