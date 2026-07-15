package com.bloo.wear

import com.bloo.bluelink.data.WearSync
import com.bloo.wear.tile.refreshWearGlanceables
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
        serviceScope.cancel()
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
          runCatching {
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
            if (tileNeedsRefresh) refreshWearGlanceables(applicationContext)
          }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        val raw = String(event.data ?: ByteArray(0))
        when (event.path) {
            WearSync.PATH_COMMAND_RESULT -> serviceScope.launch {
                runCatching {
                    val result = WearSync.decodeResult(raw) ?: return@launch
                    WearNotifications.post(
                        applicationContext,
                        ("result" + result.vin + result.action).hashCode(),
                        if (result.ok) "Command succeeded" else "Command failed",
                        result.message ?: "Done",
                    )
                }
            }
            WearSync.PATH_SYNC_RESULT -> serviceScope.launch {
                runCatching {
                    val result = WearSync.decodeSyncResult(raw) ?: return@launch
                    // A live WearViewModel (Settings screen, where "Sync now" lives)
                    // clears its busy spinner off this immediately; the notification
                    // is a backstop in case the app was closed mid-request.
                    WearSyncEvents.emit(result)
                    WearNotifications.post(
                        applicationContext,
                        "drivesync".hashCode(),
                        if (result.ok) "Drive sync complete" else "Drive sync failed",
                        result.message ?: if (result.ok) "Settings synced" else "Couldn't sync settings",
                    )
                }
            }
            WearSync.PATH_AI_RESULT -> serviceScope.launch {
                runCatching {
                    val result = WearSync.decodeAiResult(raw) ?: return@launch
                    // A live WearViewModel clears its aiBusy spinner off this
                    // immediately; only notify on failure -- a success is already
                    // visible the moment the extras push lands on the AI card, so
                    // a success notification here would just be noise.
                    WearAiEvents.emit(result)
                    if (!result.ok) {
                        WearNotifications.post(
                            applicationContext,
                            ("ai" + result.vin).hashCode(),
                            "Summary failed",
                            result.message ?: "Couldn't generate a summary",
                        )
                    }
                }
            }
        }
    }
}
