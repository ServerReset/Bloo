package com.bloo.bluelink.wear

import com.bloo.bluelink.data.WearAction
import com.bloo.bluelink.data.WearSync
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
                scope.launch {
                    val result = WearBridge.execute(applicationContext, command)
                    // Tell the watch how it went, then push the updated snapshots.
                    runCatching {
                        Tasks.await(
                            Wearable.getMessageClient(applicationContext).sendMessage(
                                event.sourceNodeId,
                                WearSync.PATH_COMMAND_RESULT,
                                WearSync.encodeResult(result).toByteArray(),
                            )
                        )
                    }
                    WearBridge.publishNow(applicationContext)
                }
            }

            WearSync.PATH_SYNC_REQUEST -> {
                val command = WearSync.decodeCommand(String(event.data))
                scope.launch {
                    if (command?.action == WearAction.REFRESH) {
                        WearBridge.refresh(applicationContext, command.vin)
                    }
                    WearBridge.publishNow(applicationContext)
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
