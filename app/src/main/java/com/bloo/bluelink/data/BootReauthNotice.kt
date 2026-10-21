package com.bloo.bluelink.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * The one notification that is allowed to appear while the app is sealed after a restart (see
 * [BootGate]): it asks the user to open the app and sign in, so alerts and AutoLock can resume.
 */
object BootReauthNotice {
    private const val CHANNEL = "bloo_reauth"
    private const val ID = 4107

    fun post(context: Context) {
        val app = context.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sign in after restart", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Asks you to sign in to Bloo after your phone restarts."
            },
        )
        val open = com.bloo.bluelink.Shortcuts.openCardPendingIntent(app, vin = null)
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(com.bloo.bluelink.R.drawable.ic_stat_bloo)
            .setContentTitle("Sign in to Bloo")
            .setContentText("Your phone restarted. Open Bloo and sign in so alerts and AutoLock can run.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Your phone restarted. Open Bloo and sign in with your account and your fingerprint or PIN so alerts and AutoLock can run again."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { nm.notify(ID, n) }
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(ID) }
    }
}
