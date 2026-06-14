package com.bloo.wear

import com.bloo.bluelink.data.WearSync
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
        runBlocking {
            updates.forEach { (path, raw) ->
                when (path) {
                    WearSync.PATH_STATE -> WearStateWriter.persistState(applicationContext, raw)
                    WearSync.PATH_AUTH -> WearStateWriter.persistAuth(applicationContext, raw)
                }
            }
        }
    }
}
