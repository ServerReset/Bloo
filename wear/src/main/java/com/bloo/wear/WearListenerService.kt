package com.bloo.wear

import androidx.wear.tiles.TileService
import com.bloo.bluelink.data.WearSync
import com.bloo.wear.tile.BlooTile1
import com.bloo.wear.tile.BlooTile2
import com.bloo.wear.tile.BlooTile3
import com.bloo.wear.tile.BlooTile4
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the phone's published state + sessions on the watch, even when the
 * UI is closed, and persists them locally. The UI observes [com.bloo.bluelink.data.SnapshotStore]
 * so it updates automatically once this writes the new snapshot.
 *
 * Background work is dispatched to [serviceScope] to avoid blocking the binder
 * thread with DataStore disk I/O.
 */
class WearListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.coroutineContext[SupervisorJob]?.cancel()
    }

    override fun onDataChanged(events: com.google.android.gms.wearable.DataEventBuffer) {
        val updates = events.mapNotNull { event ->
            if (event.type != com.google.android.gms.wearable.DataEvent.TYPE_CHANGED) return@mapNotNull null
            val item = event.dataItem
            val raw = DataMapItem.fromDataItem(item).dataMap.getString(WearSync.KEY_PAYLOAD)
                ?: return@mapNotNull null
            item.uri.path to raw
        }
        if (updates.isEmpty()) return

        serviceScope.launch {
            var tileNeedsRefresh = false
            updates.forEach { (path, raw) ->
                when (path) {
                    WearSync.PATH_STATE -> {
                        WearStateWriter.persistState(applicationContext, raw)
                        tileNeedsRefresh = true
                    }
                    WearSync.PATH_AUTH -> WearStateWriter.persistAuth(applicationContext, raw)
                    WearSync.PATH_SETTINGS -> WearStateWriter.persistSettings(applicationContext, raw)
                    WearSync.PATH_PRESETS -> WearStateWriter.persistPresets(applicationContext, raw)
                    WearSync.PATH_CLIMATE -> WearStateWriter.persistClimate(applicationContext, raw)
                    WearSync.PATH_EXTRAS -> WearStateWriter.persistExtras(applicationContext, raw)
                }
            }
            // Push a tile + complication refresh so the glanceable surfaces update
            // immediately when the phone publishes new vehicle state.
            if (tileNeedsRefresh) {
                val updater = runCatching { TileService.getUpdater(applicationContext) }.getOrNull() ?: return@launch
                listOf(
                    BlooTile1::class.java,
                    BlooTile2::class.java,
                    BlooTile3::class.java,
                    BlooTile4::class.java,
                ).forEach { cls -> runCatching { updater.requestUpdate(cls) } }
                com.bloo.wear.complication.ComplicationLink.requestUpdate(applicationContext)
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearSync.PATH_COMMAND_RESULT) return
        val raw = String(event.data ?: ByteArray(0))
        serviceScope.launch {
            runCatching {
                val result = WearSync.decodeCommandResult(raw) ?: return@launch
                WearNotifications.post(
                    applicationContext,
                    ("result" + result.vin + result.action).hashCode(),
                    if (result.ok) "Command succeeded" else "Command failed",
                    result.message ?: "Done",
                )
            }
        }
    }
}
