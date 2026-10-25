package com.bloo.bluelink.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat

/**
 * Creates a notification channel only when it is missing. Channels are API 26+ and the app's minSdk
 * IS 26, so there is no version guard to make.
 */
fun ensureNotificationChannel(
    context: Context,
    id: String,
    name: String,
    importance: Int,
    description: String,
    showBadge: Boolean = true,
    sound: Uri? = null,
) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(id) != null) return
    manager.createNotificationChannel(
        NotificationChannel(id, name, importance).apply {
            this.description = description
            setShowBadge(showBadge)
            if (sound != null) {
                setSound(
                    sound,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        },
    )
}

/**
 * Posts the one notice allowed while a device is sealed after a restart (phone: sign in again; watch: enter the PIN), on its own
 * high-importance channel. [bigText] and [category] are optional extras. Failure to post is swallowed: a missing permission or
 * a revoked one must never crash the boot path.
 */
fun postSealedNotice(
    context: Context,
    channelId: String,
    channelName: String,
    id: Int,
    smallIcon: Int,
    title: String,
    text: String,
    contentIntent: PendingIntent,
    channelDescription: String? = null,
    bigText: String? = null,
    category: String? = null,
) {
    val app = context.applicationContext
    val nm = app.getSystemService(NotificationManager::class.java) ?: return
    nm.createNotificationChannel(
        NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
            channelDescription?.let { description = it }
        },
    )
    val n = NotificationCompat.Builder(app, channelId)
        .setSmallIcon(smallIcon)
        .setContentTitle(title)
        .setContentText(text)
        .apply {
            bigText?.let { setStyle(NotificationCompat.BigTextStyle().bigText(it)) }
            category?.let { setCategory(it) }
        }
        .setContentIntent(contentIntent)
        .setAutoCancel(true)
        .build()
    runCatching { nm.notify(id, n) }
}

/** Takes down the notice [id] (see [postSealedNotice]) once the device is unsealed. */
fun cancelNotice(context: Context, id: Int) {
    runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(id) }
}
