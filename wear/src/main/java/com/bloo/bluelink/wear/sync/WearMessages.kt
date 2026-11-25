package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.ioScope
import com.bloo.bluelink.suspendRunCatching
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** The watch's one way to message the phone: [bytes] on [path] to every connected node. Five files each wrote this loop out. */
internal object WearMessages {
    /** Sends now and waits; false when nothing could be sent (no phone in reach, or the send failed). */
    suspend fun send(context: Context, path: String, bytes: ByteArray): Boolean = suspendRunCatching {
        val app = context.applicationContext
        val nodes = Wearable.getNodeClient(app).connectedNodes.await()
        nodes.forEach { Wearable.getMessageClient(app).sendMessage(it.id, path, bytes).await() }
        nodes.isNotEmpty()
    }.getOrDefault(false)

    /** Sends in the background, for a caller that does not wait on it. */
    fun sendLater(context: Context, path: String, bytes: ByteArray) {
        val app = context.applicationContext
        ioScope().launch { send(app, path, bytes) }
    }
}
