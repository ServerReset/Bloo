package com.bloo.bluelink.wear

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
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
import kotlinx.coroutines.tasks.await

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
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            val path = event.dataItem.uri.path ?: continue
            when (path) {
                WatchSyncProtocol.PATH_SNAPSHOT -> {
                    val bytes = map.getByteArray("payload")
                    scope.launch { WearDataLayerSync.mirrorSnapshot(applicationContext, bytes) }
                }
                WatchSyncProtocol.PATH_COMMAND_RESULT ->
                    WearDataLayerSync.applyCommandResult(applicationContext, map.getByteArray("payload"))
                WatchSyncProtocol.PATH_WATCH_APK -> {
                    val asset = map.getAsset("apk") ?: continue
                    scope.launch { installPushedApk(asset) }
                }
            }
        }
    }

    /**
     * Pull the APK asset off the Data Layer, write it, and launch the system installer. This is
     * the seamless path: the phone did the network work, and the user stays on the watch -- a
     * single tap on the system installer is the only interaction.
     */
    @android.annotation.SuppressLint("WearRecents") // starting the installer from a Service
    private suspend fun installPushedApk(asset: com.google.android.gms.wearable.Asset) {
        val bytes = runCatching {
            com.google.android.gms.wearable.Wearable.getDataClient(applicationContext)
                .getFdForAsset(asset).await().inputStream?.use { it.readBytes() }
        }.getOrNull() ?: return
        val file = WearDataLayerSync.writePushedApk(applicationContext, bytes) ?: return
        runCatching {
            val uri: Uri = FileProvider.getUriForFile(
                applicationContext,
                "${packageName}.fileprovider",
                file,
            )
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        val phone = messageEvent.sourceNodeId
        when (messageEvent.path) {
            WatchSyncProtocol.PATH_CRED_OFFER -> scope.launch { WearCredentialSync.onOffer(applicationContext, phone) }
            WatchSyncProtocol.PATH_CRED_PAYLOAD ->
                scope.launch { WearCredentialSync.onPayload(applicationContext, phone, messageEvent.data) }
        }
    }
}
