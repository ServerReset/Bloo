package com.bloo.bluelink.wear

import com.bloo.bluelink.data.WatchSyncProtocol
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the phone's pushes on the watch, even when the watch app is not open, and mirrors
 * them. All the actual work lives in [WearDataLayerSync]; this is just the manifest-declared
 * endpoint the Data Layer delivers to.
 */
class WatchDataListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val path = event.dataItem.uri.path ?: continue
            val bytes = DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray("payload")
            when (path) {
                WatchSyncProtocol.PATH_SNAPSHOT -> scope.launch {
                    WearDataLayerSync.mirrorSnapshot(applicationContext, bytes)
                }
                WatchSyncProtocol.PATH_COMMAND_RESULT ->
                    WearDataLayerSync.applyCommandResult(applicationContext, bytes)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
    }
}
