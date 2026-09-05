package com.bloo.bluelink.autolock

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.bloo.bluelink.R
import com.bloo.bluelink.data.ensureNotificationChannel

/** Builds the short-lived foreground notification AutoLock shows while an evaluation is in
 *  flight -- countdown, a "Lock now" shortcut, and a Cancel that aborts (the car reconnect
 *  path cancels the same way). Ported/condensed from i5-AutoLock's `AutoLockNotification`. */
object AutoLockNotification {
    private const val CHANNEL_ID = "bloo_autolock"

    private fun ensureChannel(context: Context) = ensureNotificationChannel(
        context,
        id = CHANNEL_ID,
        name = "AutoLock",
        importance = NotificationManager.IMPORTANCE_LOW,
        description = "Shows while AutoLock is deciding whether to lock your car",
        showBadge = false,
    )

    fun notificationId(vin: String): Int = 0x41_0000 or (vin.hashCode() and 0xFFFF)

    fun build(context: Context, vin: String, carName: String, state: DetectionState, graceRemaining: Int): Notification {
        ensureChannel(context)
        val (title, text) = when (state) {
            DetectionState.CONFIRMING -> "Watching $carName" to "Confirming you've left before locking…"
            DetectionState.GRACE -> "Locking $carName in ${graceRemaining}s" to "Tap Cancel if you're coming right back."
            DetectionState.VERIFYING -> "Checking $carName" to "Reading the car's status…"
            DetectionState.LOCKING -> "Locking $carName" to "Sending the lock command…"
            DetectionState.LOCKED -> "$carName locked" to "AutoLock locked it for you."
            DetectionState.SKIPPED -> "AutoLock skipped $carName" to "Nothing to do — see the activity log for why."
            DetectionState.ABORTED -> "AutoLock cancelled" to "$carName wasn't touched."
            DetectionState.ERROR -> "AutoLock error" to "Couldn't finish evaluating $carName."
            else -> "AutoLock" to carName
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(!state.isTerminal)
            .setAutoCancel(state.isTerminal)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(context))
        if (state == DetectionState.GRACE) {
            builder.addAction(0, "Lock now", actionIntent(context, vin, AutoLockService.ACTION_LOCK_NOW))
        }
        if (!state.isTerminal) {
            builder.addAction(0, "Cancel", actionIntent(context, vin, AutoLockService.ACTION_CANCEL))
        }
        return builder.build()
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    private fun actionIntent(context: Context, vin: String, action: String): PendingIntent {
        val intent = Intent(context, AutoLockService::class.java).apply {
            this.action = action
            putExtra(AutoLockService.EXTRA_VIN, vin)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getService(context, notificationId(vin) + action.hashCode(), intent, flags)
    }
}
