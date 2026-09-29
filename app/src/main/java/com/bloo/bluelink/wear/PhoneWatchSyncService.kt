package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.CarCommandRunner
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WatchCommandRequest
import com.bloo.bluelink.data.WatchCommandResult
import com.bloo.bluelink.data.WatchSyncPayload
import com.bloo.bluelink.data.WatchSyncProtocol
import com.bloo.bluelink.data.WatchLockTiming
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
        fun pushNow(
            context: Context,
            watchUpdateRunNumber: Int? = null,
            watchUpdateApkUrl: String? = null,
            watchUpdateNotes: String? = null,
        ) {
            val app = context.applicationContext
            val job = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
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
