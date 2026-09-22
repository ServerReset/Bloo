package com.bloo.bluelink.autolock

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.bloo.bluelink.R
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.Notifications
import com.bloo.bluelink.data.ensureNotificationChannel

/**
 * The lasting notification AutoLock posts when it ACTS: "Locked <car>" after a real lock, or
 * "Would have locked <car>" in dry-run. Distinct from [AutoLockNotification]'s in-flight
 * progress notification, which is low-importance, silent, and torn down a few seconds after
 * the evaluation ends -- that one is a live status readout, this one is the record of what
 * happened, and it is the only feedback a dry-run user gets at all.
 *
 * Its own channel, with its own sound: the car-door-lock clip in `res/raw`, so an AutoLock
 * event is recognisable by ear without looking at the phone. That channel is deliberately
 * separate from the in-flight one so the user can silence the running commentary without
 * silencing the outcome (and vice versa), and separate from car alerts/events so "my car
 * locked itself" never rides the same channel as "your door is open".
 */
object AutoLockEventNotifier {

    /**
     * Versioned: a notification channel's sound and importance are fixed when the channel is
     * first created -- Android silently ignores any later change to an existing channel -- so
     * changing the clip or the importance for EXISTING installs requires a new id. The suffix
     * is what makes that possible without asking the user to clear app data.
     */
    private const val CHANNEL_ID = "bloo_autolock_events_v1"

    @Volatile private var channelEnsured = false

    /** The bundled car-door-lock clip as a notification-sound URI. */
    fun carLockSound(context: Context): Uri =
        "android.resource://${context.packageName}/${R.raw.car_door_lock}".toUri()

    private fun ensureChannel(context: Context) {
        if (channelEnsured) return
        ensureNotificationChannel(
            context,
            id = CHANNEL_ID,
            name = "AutoLock events",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            description = "Your car was locked automatically (or would have been, in dry run)",
            showBadge = true,
            sound = carLockSound(context),
        )
        channelEnsured = true
    }

    /** A notification id per car, in a range of its own (0x42...) so it can never collide
     *  with the in-flight notification (0x41...) or with the per-alert ids. */
    fun notificationId(vin: String): Int = 0x42_0000 or (vin.hashCode() and 0xFFFF)

    /**
     * Posts the outcome for [vin]. [dryRun] changes the wording AND the promise being made:
     * a dry-run notification must not read like something happened to the car, because
     * nothing did.
     */
    fun notifyLocked(context: Context, vin: String, carName: String, dryRun: Boolean) {
        // Same three-way permission gate every other notification path uses (runtime grant,
        // notifications enabled, channel not blocked). Without it a user who has blocked this
        // channel would still see the in-flight notification's own teardown happen, which
        // reads as a glitch.
        if (!Notifications.hasPermission(context, CHANNEL_ID)) return
        ensureChannel(context)

        val title = if (dryRun) "Would have locked $carName" else "Locked $carName"
        val text = if (dryRun) {
            "Dry run: no command was sent. Turn dry run off to let AutoLock lock it for you."
        } else {
            "AutoLock locked it for you after you walked away."
        }

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launch?.let {
            PendingIntent.getActivity(
                context,
                notificationId(vin),
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setColor(BlooColors.brandAccent)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        contentIntent?.let { builder.setContentIntent(it) }

        // Explicit grant read at the call site: lint cannot see through
        // [Notifications.hasPermission]'s own check, and the permission contract belongs
        // next to the notify() it protects anyway (the check above is still the TOCTOU
        // guard the runCatching covers).
        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(vin), builder.build())
        }
    }

    fun cancel(context: Context, vin: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(notificationId(vin)) }
    }
}
