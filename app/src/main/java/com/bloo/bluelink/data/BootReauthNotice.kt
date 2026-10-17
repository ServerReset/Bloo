package com.bloo.bluelink.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bloo.bluelink.MainActivity

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
        val open = PendingIntent.getActivity(
            app, 0,
            Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
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
