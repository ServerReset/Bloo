package com.bloo.bluelink.wear

import com.bloo.bluelink.data.Ai
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.ClimateSyncStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WearAction
import com.bloo.bluelink.data.WearExtras
import com.bloo.bluelink.data.WearSync
import androidx.glance.appwidget.updateAll
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receives the watch's messages on the phone. Bound by the system whenever a
 * Data Layer message arrives on a `/bloo` path, even if the phone app's UI
 * isn't running — so "lock from my watch" works with the phone in your pocket.
 */
class WearPhoneService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearSync.PATH_COMMAND -> {
                val command = WearSync.decodeCommand(String(event.data)) ?: return
                if (command.action == WearAction.AI_SUMMARY) {
                    scope.launch {
                        val ctx = applicationContext
                        val snap = SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == command.vin }
                        if (snap != null) {
                            val summary = runCatching { Ai(ctx).summarize("${snap.name} vehicle status: The doors are ${if (snap.locked == true) "locked" else "unlocked"}. Climate is ${if (snap.climateOn == true) "on" else "off"}.${snap.percent?.let { " Battery is at $it%." } ?: ""}${snap.rangeMi?.let { " Range is $it miles." } ?: ""}") }.getOrNull()
                            if (summary != null) {
                                // Read the current extras item from the Data Layer, patch the ai map, republish.
                                val dataClient = Wearable.getDataClient(ctx)
                                val items = runCatching { Tasks.await(dataClient.getDataItems(android.net.Uri.parse("wear://*${WearSync.PATH_EXTRAS}"))) }.getOrNull()
                                val existing = items?.map { WearSync.decodeExtras(DataMapItem.fromDataItem(it).dataMap.getString(WearSync.KEY_PAYLOAD)) }?.firstOrNull() ?: WearExtras()
                                items?.release()
                                val updated = existing.copy(ai = existing.ai + (command.vin to summary))
                                WearBridge.publishExtras(ctx, updated)
                                AppLog.log("AI summary generated for ${snap.name}")
                            }
                        }
                    }
                    return
                }
                scope.launch {
                    val result = WearBridge.execute(applicationContext, command)
                    // Tell the watch how it went, then fan out to all surfaces.
                    runCatching {
                        Tasks.await(
                            Wearable.getMessageClient(applicationContext).sendMessage(
                                event.sourceNodeId,
                                WearSync.PATH_COMMAND_RESULT,
                                WearSync.encodeResult(result).toByteArray(),
                            )
                        )
                    }
                    val ctx = applicationContext
                    WearBridge.refreshAllSurfaces(ctx)
                }
            }

            WearSync.PATH_SYNC_REQUEST -> {
                val command = WearSync.decodeCommand(String(event.data))
                scope.launch {
                    val ctx = applicationContext
                    if (command?.action == WearAction.REFRESH) {
                        WearBridge.refresh(ctx, command.vin)
                    }
                    WearBridge.refreshAllSurfaces(ctx)
                }
            }
        }
    }

    /**
     * The watch writes data items too: its live climate draft on
     * [WearSync.PATH_CLIMATE] and presets it created/edited on
     * [WearSync.PATH_PRESETS]. Persist both so the phone reflects them.
     */
    override fun onDataChanged(events: DataEventBuffer) {
        val updates = events.mapNotNull { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@mapNotNull null
            val item = event.dataItem
            val path = item.uri.path
            if (path != WearSync.PATH_CLIMATE && path != WearSync.PATH_PRESETS &&
                path != WearSync.PATH_PEBBLE_ORDER && path != WearSync.PATH_LOCAL
            ) return@mapNotNull null
            val raw = DataMapItem.fromDataItem(item).dataMap.getString(WearSync.KEY_PAYLOAD)
                ?: return@mapNotNull null
            path to raw
        }
        if (updates.isEmpty()) return
        scope.launch {
            updates.forEach { (path, raw) ->
                when (path) {
                    WearSync.PATH_CLIMATE -> ClimateSyncStore(applicationContext).save(raw)
                    WearSync.PATH_PRESETS -> {
                        val store = SettingsStore(applicationContext)
                        WearSync.decodePresets(raw).byVin.forEach { (vin, list) ->
                            store.setClimatePresets(vin, list)
                        }
                    }
                    WearSync.PATH_PEBBLE_ORDER -> {
                        val po = WearSync.decodePebbleOrder(raw) ?: return@forEach
                        if (po.vin.isNotBlank() && po.order.isNotEmpty()) {
                            SettingsStore(applicationContext).setSectionOrder(po.vin, po.order)
                            // Mirror the saved order straight back so every device
                            // (and the watch's optimistic override) lines up.
                            runCatching {
                                val appearance = SettingsStore(applicationContext).appearance.first()
                                WearBridge.publishSettingsNow(applicationContext, appearance)
                            }
                        }
                    }
                    WearSync.PATH_LOCAL -> {
                        val payload = WearSync.decodeLocal(raw) ?: return@forEach
                        SettingsStore(applicationContext).setUiScale(payload.uiScale.coerceIn(0.8f, 1.4f))
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
