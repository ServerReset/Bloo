package com.bloo.bluelink.data

import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * The one notification that is allowed to appear while the app is sealed after a restart (see
 * [BootGate]): it asks the user to open the app and sign in, so alerts and AutoLock can resume.
 */
object BootReauthNotice {
    private const val ID = 4107

    fun post(context: Context) = postSealedNotice(
        context,
        channelId = "bloo_reauth",
        channelName = "Sign in after restart",
        channelDescription = "Asks you to sign in to Bloo after your phone restarts.",
        id = ID,
        smallIcon = com.bloo.bluelink.R.drawable.ic_stat_bloo,
        title = "Sign in to Bloo",
        text = "Your phone restarted. Open Bloo to sign in.",
        bigText = "Your phone restarted. Open Bloo to sign in.",
        category = NotificationCompat.CATEGORY_STATUS,
        contentIntent = com.bloo.bluelink.Shortcuts.openCardPendingIntent(context.applicationContext, vin = null),
    )

    fun cancel(context: Context) = cancelNotice(context, ID)
}
