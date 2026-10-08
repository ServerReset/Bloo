package com.bloo.bluelink.wear

import com.bloo.bluelink.ioScope
import android.content.Context
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.CarCommandRunner
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WatchCommandRequest
import com.bloo.bluelink.data.WatchCommandResult
import com.bloo.bluelink.data.WatchNotifyPrefs
import com.bloo.bluelink.data.WatchSyncPayload
import com.bloo.bluelink.data.notificationPrefs
import com.bloo.bluelink.data.setNotifyChargeComplete
import com.bloo.bluelink.data.setNotifyCharging
import com.bloo.bluelink.data.setNotifyWatchLowBattery
import com.bloo.bluelink.data.WatchSyncProtocol
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import com.bloo.bluelink.data.watchLockTiming

/**
 * Phone side of the watch sync. The watch is an AUXILIARY surface: it never talks to the
 * network or Drive -- everything it shows is pushed from here, and every command it wants run
 * is executed HERE through the normal [CarCommandRunner] and reported back. This service:
 *
 *  - PUSHES a [WatchSyncPayload] (vehicles + the watch lock timing + the stretched PIN record)
 *    whenever anything the watch shows changes, so the watch mirrors the phone in real time.
 *  - RECEIVES [WatchCommandRequest] items and runs them, then pushes [WatchCommandResult].
 *
 * A [WearableListenerService] so a push can be triggered even when the phone app is not in the
 * foreground (a command from the watch wakes this). [pushNow] is called from the phone app's
 * own snapshot writes to keep the watch live while it is open.
 */
class PhoneWatchSyncService : WearableListenerService() {

    private val scope = ioScope()
    private val json = Json { ignoreUnknownKeys = true }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val path = event.dataItem.uri.path ?: continue
            if (path != WatchSyncProtocol.PATH_COMMAND) continue
            val bytes = DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray(KEY_PAYLOAD) ?: continue
            val request = runCatching {
                json.decodeFromString(WatchCommandRequest.serializer(), bytes.decodeToString())
            }.getOrNull() ?: continue
            AppLog.log("WatchSync: command ${request.command.action} for ${request.command.vin}")
            scope.launch { runCommand(request) }
        }
    }

    override fun onMessageReceived(messageEvent: com.google.android.gms.wearable.MessageEvent) {
        super.onMessageReceived(messageEvent)
        when (messageEvent.path) {
            WatchSyncProtocol.PATH_CRED_KEY -> WatchSignIn.onKey(messageEvent.sourceNodeId, messageEvent.data)
            WatchSyncProtocol.PATH_CRED_ACK -> WatchSignIn.onAck()
            WatchSyncProtocol.PATH_NOTIF_PREFS -> scope.launch {
                runCatching { json.decodeFromString(WatchNotifyPrefs.serializer(), messageEvent.data.decodeToString()) }
                    .getOrNull()?.let { p ->
                        val store = SettingsStore(applicationContext)
                        store.setNotifyCharging(p.charging)
                        store.setNotifyChargeComplete(p.chargeComplete)
                        store.setNotifyWatchLowBattery(p.lowBattery)
                        if (!p.charging) {
                            com.bloo.bluelink.data.LiveCharge.cancelAll(applicationContext, SnapshotStore(applicationContext).current().vehicles.map { it.vin })
                        }
                        pushNow(applicationContext)
                    }
            }
        }
    }

    private suspend fun runCommand(request: WatchCommandRequest) {
        val result = runCatching { CarCommandRunner.execute(applicationContext, request.command) }
            .getOrElse {
                com.bloo.bluelink.data.CarCommandResult(request.command.vin, request.command.action, ok = false, message = it.message)
            }
        val out = WatchCommandResult(
            requestId = request.requestId,
            vin = result.vin,
            action = result.action,
            ok = result.ok,
            message = result.message,
        )
        pushBytes(WatchSyncProtocol.PATH_COMMAND_RESULT, json.encodeToString(WatchCommandResult.serializer(), out))
    }

    private fun pushBytes(path: String, jsonString: String) {
        val req = PutDataMapRequest.create(path).apply {
            dataMap.putByteArray(KEY_PAYLOAD, jsonString.encodeToByteArray())
            dataMap.putLong("_ts", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        runCatching { Wearable.getDataClient(applicationContext).putDataItem(req) }
    }

    companion object {
        private const val KEY_PAYLOAD = "payload"

        /**
         * Push the current snapshot + lock config to every paired watch, now. Called from the
         * phone app whenever it writes the snapshot so the watch stays live; a no-op (and
         * cheap) when no watch is paired. Runs off the caller's thread via the Data Layer's own
         * executor, so it never blocks a phone UI frame.
         *
         * [watchUpdateRunNumber]/[watchUpdateApkUrl]/[watchUpdateNotes] advertise a newer WATCH
         * build for the watch to offer installing; null (the default) means "nothing to
         * advertise" and the watch shows no update affordance.
         */
        /**
         * Advertise a newer WATCH build to any paired watch. A no-op when no watch is paired, so
         * the update check never pays for a push nobody will read. [run] is the phone's newest
         * [com.bloo.bluelink.data.WorkflowRun]; its watch asset URL is what the watch offers to
         * download.
         */
        fun pushWatchApk(context: Context, url: String) {
            val app = context.applicationContext
            ioScope().launch {
                runCatching {
                    val bytes = com.bloo.bluelink.data.ApiHttp.client.newCall(
                        okhttp3.Request.Builder().url(url).get().build(),
                    ).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        resp.body?.bytes() ?: error("empty body")
                    }
                    val asset = com.google.android.gms.wearable.Asset.createFromBytes(bytes)
                    val req = PutDataMapRequest.create(WatchSyncProtocol.PATH_WATCH_APK).apply {
                        dataMap.putAsset("apk", asset)
                        dataMap.putLong("_ts", System.currentTimeMillis())
                    }.asPutDataRequest().setUrgent()
                    Wearable.getDataClient(app).putDataItem(req).await()
                    AppLog.log("WatchSync: pushed watch APK (${bytes.size} bytes)")
                }.onFailure { AppLog.log("WatchSync: watch APK push failed (${it.javaClass.simpleName})") }
            }
        }

        fun pushNow(
            context: Context,
            watchUpdateRunNumber: Int? = null,
            watchUpdateApkUrl: String? = null,
            watchUpdateNotes: String? = null,
        ) {
            val app = context.applicationContext
            val job = ioScope().launch {
                val snapshot = SnapshotStore(app).current()
                val pinRecord = runCatching { CredentialStore(app).getPinRecord() }.getOrNull()
                val timing = SettingsStore(app).watchLockTiming()
                val payload = WatchSyncPayload(
                    vehicles = snapshot.vehicles,
                    selectedVin = snapshot.selectedVin,
                    lockTiming = timing.wireKey,
                    pinRecord = pinRecord,
                    sentAtMs = System.currentTimeMillis(),
                    watchUpdateRunNumber = watchUpdateRunNumber,
                    watchUpdateApkUrl = watchUpdateApkUrl,
                    watchUpdateNotes = watchUpdateNotes,
                    notify = SettingsStore(app).notificationPrefs().let { WatchNotifyPrefs(it.charging, it.chargeComplete, it.watchLowBattery) },
                )
                val jsonString = Json.encodeToString(WatchSyncPayload.serializer(), payload)
                val req = PutDataMapRequest.create(WatchSyncProtocol.PATH_SNAPSHOT).apply {
                    dataMap.putByteArray(KEY_PAYLOAD, jsonString.encodeToByteArray())
                    dataMap.putLong("_ts", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                runCatching { Wearable.getDataClient(app).putDataItem(req) }
                    .onFailure { AppLog.log("WatchSync: push failed (${it.javaClass.simpleName})") }
            }
            // Fire-and-forget: the caller is a snapshot write, not a place to await the network.
            job.invokeOnCompletion { }
        }
    }
}
