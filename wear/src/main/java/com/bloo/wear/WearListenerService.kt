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
 *
 * Mechanism: [WearableListenerService] is the system-managed counterpart to
 * the two Wearable Data Layer transports described in [WearComms]'s doc
 * comment. Android instantiates (or wakes) this service and calls one of the
 * two callbacks below whenever the phone-side app touches the Data Layer,
 * regardless of whether the watch's own UI process is running:
 *  - [onDataChanged] fires for DataItem changes (anything published via
 *    `DataClient.putDataItem`, e.g. vehicle state, settings, presets) --
 *    delivered as a batch of events since several DataItems can change
 *    together in one phone-side publish.
 *  - [onMessageReceived] fires for one-shot messages (anything sent via
 *    `MessageClient.sendMessage`) -- delivered one at a time, used here for
 *    command/sync/AI *results* coming back from the phone after the watch
 *    asked it to do something.
 * Both callbacks dispatch their path constant (from the shared [WearSync]
 * object) through a `when` to decide what the payload actually is.
 */
class WearListenerService : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /** Called by the system with a batch of DataItem change events. Each event
     *  is filtered to TYPE_CHANGED (ignoring TYPE_DELETED, which this app
     *  doesn't act on) and its raw payload string extracted via
     *  [DataMapItem.fromDataItem]; the (path, payload) pairs are collected
     *  first and only then processed on [serviceScope], keeping this
     *  synchronous callback itself fast (it runs on a binder thread). */
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
            // Each item wrapped individually -- one malformed/failing payload
            // (e.g. a corrupt PATH_STATE write) must not skip processing the
            // rest of the same batch (PATH_SETTINGS, PATH_EXTRAS, etc. changing
            // together is common on a single phone-side publish burst).
            updates.forEach { (path, raw) ->
                runCatching {
                    when (path) {
                        WearSync.PATH_STATE -> {
                            WearStateWriter.persistState(applicationContext, raw)
                            tileNeedsRefresh = true
                        }
                        WearSync.PATH_AUTH -> WearStateWriter.persistAuth(applicationContext, raw)
                        WearSync.PATH_SETTINGS -> {
                            // Settings carry the phone-synced theme colors the
                            // Tile reads (resolveRoles() in BlooTileService),
                            // so a theme change on the phone deserves the same
                            // immediate refresh push as a vehicle-state change
                            // -- without this, the Tile only picked it up on
                            // its next freshness-interval poll (up to 10
                            // minutes idle).
                            WearStateWriter.persistSettings(applicationContext, raw)
                            tileNeedsRefresh = true
                        }
                        WearSync.PATH_PRESETS -> WearStateWriter.persistPresets(applicationContext, raw)
                        WearSync.PATH_CLIMATE -> WearStateWriter.persistClimate(applicationContext, raw)
                        WearSync.PATH_EXTRAS -> WearStateWriter.persistExtras(applicationContext, raw)
                    }
                }
            }
            // Push a tile + complication refresh so the glanceable surfaces update
            // immediately when the phone publishes new vehicle state.
            if (tileNeedsRefresh) runCatching { refreshWearGlanceables(applicationContext) }
        }
    }

    /** Called by the system for each one-shot MessageClient message. Unlike
     *  [onDataChanged] there's no batching or filtering step here -- the raw
     *  byte payload is decoded straight to UTF-8 and dispatched by path. Every
     *  branch does two things: emits the decoded result on an in-process
     *  event bus ([WearCommandEvents]/[WearSyncEvents]/[WearAiEvents]) for a
     *  currently-running [com.bloo.wear.WearViewModel] to react to
     *  immediately, and posts a system notification as a backstop for the
     *  case where the watch app isn't running to see that event at all. */
    override fun onMessageReceived(event: MessageEvent) {
        val raw = String(event.data ?: ByteArray(0))
        when (event.path) {
            WearSync.PATH_COMMAND_RESULT -> serviceScope.launch {
                runCatching {
                    val result = WearSync.decodeResult(raw) ?: return@launch
                    // A live WearViewModel reverts its optimistic state and
                    // surfaces a message off this immediately (see
                    // WearCommandEvents' doc comment); the notification is a
                    // backstop in case the app was closed mid-request, same
                    // as PATH_SYNC_RESULT/PATH_AI_RESULT below.
                    WearCommandEvents.emit(result)
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
