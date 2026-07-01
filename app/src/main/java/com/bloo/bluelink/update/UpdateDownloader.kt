package com.bloo.bluelink.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Downloads the update APK via the system DownloadManager rather than a manual
 * OkHttp stream: APKs are tens of MB, DownloadManager survives process death/
 * backgrounding, shows a system progress notification for free, and handles
 * retries/network changes without a custom foreground service.
 */
object UpdateDownloader {

    private const val FILE_NAME = "bloo-update.apk"

    /** Enqueue the download and suspend until it completes; null on failure or
     *  cancellation. The file lands in getExternalFilesDir(DIRECTORY_DOWNLOADS),
     *  matching res/xml/file_paths.xml's FileProvider mapping. */
    suspend fun download(context: Context, url: String): File? = suspendCancellableCoroutine { cont ->
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Bloo update")
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, FILE_NAME)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val downloadId = manager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                runCatching { context.unregisterReceiver(this) }
                val ok = manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { c ->
                    val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    c.moveToFirst() && statusIdx >= 0 && c.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL
                } ?: false
                val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), FILE_NAME)
                if (cont.isActive) cont.resume(if (ok && file.exists()) file else null)
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
    }
}
