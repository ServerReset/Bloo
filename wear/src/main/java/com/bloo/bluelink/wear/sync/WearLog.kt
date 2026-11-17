package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.WatchSyncProtocol
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The watch's own log, kept only while advanced logging is on at the phone (see DiagSettings). Lines are gathered for a few seconds and
 * then sent to the phone, which writes them into its activity log marked as the watch's. While it is off, [log] does nothing at all.
 *
 * A batch is dropped if the phone is out of reach when it is due: the log is for a diagnosis made while both are together.
 */
internal object WearLog {
    /** Set from the phone's sync: advanced logging is on at the phone. */
    @Volatile var enabled = false

    private var app: Context? = null
    private val pending = ArrayList<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var flushJob: Job? = null
    private val stamp = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    private const val MAX_PENDING = 400
    private const val FLUSH_MS = 4_000L

    fun attach(context: Context) {
        app = context.applicationContext
    }

    fun log(message: String) {
        if (!enabled) return
        val line = "${stamp.get()!!.format(Date())} $message"
        synchronized(pending) {
            if (pending.size >= MAX_PENDING) pending.removeAt(0)
            pending += line
            if (flushJob?.isActive != true) flushJob = scope.launch { delay(FLUSH_MS); flush() }
        }
    }

    private suspend fun flush() {
        val batch = synchronized(pending) { pending.toList().also { pending.clear() } }
        val context = app ?: return
        if (batch.isEmpty()) return
        val bytes = batch.joinToString("\n").encodeToByteArray()
        runCatching {
            Wearable.getNodeClient(context).connectedNodes.await().forEach {
                Wearable.getMessageClient(context).sendMessage(it.id, WatchSyncProtocol.PATH_WATCH_LOG, bytes).await()
            }
        }
    }
}
