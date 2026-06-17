package com.bloo.wear

import androidx.wear.tiles.TileService
import com.bloo.bluelink.data.WearSync
import com.bloo.wear.tile.BlooTileService
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking

/**
 * Receives the phone's published state + sessions on the watch, even when the
 * UI is closed, and persists them locally. The UI observes [com.bloo.bluelink.data.SnapshotStore]
 * so it updates automatically once this writes the new snapshot.
 *
 * Listener callbacks run on a background binder thread, so the short suspend
 * writes are run with [runBlocking].
 */
class WearListenerService : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        val updates = events.mapNotNull { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@mapNotNull null
            val item = event.dataItem
            val raw = DataMapItem.fromDataItem(item).dataMap.getString(WearSync.KEY_PAYLOAD)
                ?: return@mapNotNull null
            item.uri.path to raw
        }
        if (updates.isEmpty()) return
        var tileNeedsRefresh = false
        runBlocking {
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
        }
        // Push a tile refresh so the glanceable tile updates immediately when the
        // phone publishes new vehicle state.
        if (tileNeedsRefresh) {
            TileService.getUpdater(applicationContext).requestUpdate(BlooTileService::class.java)
        }
    }
}
