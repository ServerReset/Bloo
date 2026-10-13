package com.bloo.bluelink.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri

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
