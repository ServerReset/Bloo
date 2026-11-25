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
    internal const val CHANNEL_ID = "bloo_autolock"

    /**
     * The countdown before a lock, on a channel that is heard. It is the person's one chance to stop the car locking with someone
     * still beside it, and on the quiet channel it made no sound at all. It sounds once, as the countdown starts, not every second.
     */
    internal const val COUNTDOWN_CHANNEL_ID = "bloo_autolock_countdown"

    private fun ensureChannel(context: Context) = ensureNotificationChannel(
        context,
        id = CHANNEL_ID,
        name = "AutoLock",
        importance = NotificationManager.IMPORTANCE_LOW,
        description = "Shows while AutoLock is deciding whether to lock your car",
        showBadge = false,
    )

    private fun ensureCountdownChannel(context: Context) = ensureNotificationChannel(
        context,
        id = COUNTDOWN_CHANNEL_ID,
        name = "AutoLock countdown",
        importance = NotificationManager.IMPORTANCE_HIGH,
        description = "The few seconds before AutoLock locks your car, so you can cancel",
        showBadge = false,
    )

    fun notificationId(vin: String): Int = 0x41_0000 or (vin.hashCode() and 0xFFFF)

    /** [countdownStarted] is true for the first countdown update only: that one is heard, the per-second ticks after it are not. */
    fun build(
        context: Context,
        vin: String,
        carName: String,
        state: DetectionState,
        graceRemaining: Int,
        countdownStarted: Boolean = false,
    ): Notification {
        ensureChannel(context)
        val countdown = state == DetectionState.GRACE
        if (countdown) ensureCountdownChannel(context)
        val (title, text) = when (state) {
            DetectionState.CONFIRMING -> "Watching $carName" to "Confirming you've left before locking…"
            DetectionState.GRACE -> if (graceRemaining > 0) {
                "Locking $carName in ${graceRemaining}s" to "Tap Cancel if you're coming right back."
            } else {
                "Waiting to lock $carName" to "You're still beside it. It locks once you step away."
            }
            DetectionState.VERIFYING -> "Checking $carName" to "Reading the car's status…"
            DetectionState.LOCKING -> "Locking $carName" to "Sending the lock command…"
            DetectionState.LOCKED -> "$carName locked" to "AutoLock locked it for you."
            DetectionState.SKIPPED -> "AutoLock skipped $carName" to "Nothing to do — see the activity log for why."
            DetectionState.ABORTED -> "AutoLock cancelled" to "$carName wasn't touched."
            DetectionState.ERROR -> "AutoLock error" to "Couldn't finish evaluating $carName."
            else -> "AutoLock" to carName
        }
        val builder = NotificationCompat.Builder(context, if (countdown) COUNTDOWN_CHANNEL_ID else CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setContentTitle(title)
            .setContentText(text)
            // The notification is already showing (it was "Watching"), so "alert once" would swallow the countdown's sound: the
            // first countdown update is let through, every later one stays silent.
            .setOnlyAlertOnce(!(countdown && countdownStarted))
            .setOngoing(!state.isTerminal)
            .setAutoCancel(state.isTerminal)
            .setPriority(if (countdown) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
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
        // FLAG_IMMUTABLE exists from API 23, under the app's minimum, so it needs no version check.
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
