package com.bloo.bluelink.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat

/** How long a notice about something that happened stays in the shade untouched before the system takes it down. */
const val NOTICE_CLEAR_AFTER_MS = 30 * 60 * 1000L

/**
 * Has the system clear this notification on its own if the user has not swiped it away: a "your car locked" from this morning is
 * not news by lunch, and left in the shade it reads as the most recent one. The system does it, so it happens with the app closed.
 */
fun NotificationCompat.Builder.clearsAfterAWhile(): NotificationCompat.Builder = setTimeoutAfter(NOTICE_CLEAR_AFTER_MS)

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
 * Posts the one notice allowed while the phone is sealed after a restart (sign in again), on its own
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

/**
 * A notification channel created at most once per process. Channels persist across runs, so after the first call there is nothing left to
 * do: [ensure] runs its [create] the first time, and returns straight away every time after that.
 */
class ChannelOnce {
    @Volatile private var done = false

    fun ensure(create: () -> Unit) {
        if (done) return
        synchronized(this) {
            if (!done) {
                create()
                done = true
            }
        }
    }
}
