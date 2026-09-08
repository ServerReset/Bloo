package com.bloo.wear

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.ensureNotificationChannel

/**
 * Watch-side notifications for command outcomes.
 *
 * Normally the phone's bridged alerts surface on the watch automatically. But
 * when the watch runs a command *standalone* — no phone reachable, e.g. a Tile
 * or complication tap over Wi-Fi/cell — there is no phone to report the result,
 * so the watch posts its own. It's also the backstop when the app was closed
 * mid-request: the live WearViewModel would normally consume the result off its
 * event bus, but if nothing is listening, this is what tells the user what
 * happened. Tapping a notification opens the app ([MainActivity]).
 *
 * Everything routes through [post]; the channel is created lazily and the
 * POST_NOTIFICATIONS runtime permission (Android 13+) is checked before we ever
 * try to notify.
 */
object WearNotifications {

    private const val CHANNEL_ID = "bloo_wear_alerts"

    // Its own channel, never the alerts one. This posts and retracts around every
    // standalone command, which would be intolerable noise on a channel meant for
    // occasional results -- and IMPORTANCE_LOW keeps it silent (no sound, no
    // heads-up) while still clearing the "above IMPORTANCE_MIN" promotion condition
    // with a full step to spare.
    private const val LIVE_CHANNEL_ID = "bloo_wear_live"

    // Bloo accent, applied to the notification's icon/title tint.
    private const val ACCENT_COLOR = BlooColors.brandAccent

    /**
     * True when we're allowed to post notifications: always on pre-Tiramisu,
     * and gated on the runtime POST_NOTIFICATIONS grant from Android 13 on.
     * [post] short-circuits on this so callers never have to check first.
     */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * Post (or update, for a repeated [id]) a command-result notification.
     *
     * No-ops silently when the POST_NOTIFICATIONS permission is missing, so it's
     * always safe to call. [id] doubles as the update key and the PendingIntent
     * request code — callers derive it from the command so a retry replaces the
     * earlier notification rather than stacking a new one.
     */
    fun post(context: Context, id: Int, title: String, text: String) {
        if (!hasPermission(context)) return
        ensureChannel(context)

        // Tapping opens the app, bringing any existing task to the front rather
        // than spawning a duplicate.
        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            id,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setColor(ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            // BigText so a longer failure message ("Bring your phone nearby…")
            // isn't clipped when the notification is expanded.
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(contentIntent)
            .build()

        // notify() can throw if the permission was revoked between the check
        // above and here (a race on the OS side); swallow it rather than crash.
        // The local check below mirrors the one above so lint's MissingPermission
        // analysis sees the grant reading right next to the notify.
        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    /**
     * A watch-local **Live Update** for an operation that is running right now --
     * currently a car command issued standalone, which is unbounded network work that
     * routinely takes tens of seconds while the watch shows nothing outside the app.
     *
     * Two things at once, exactly as the phone's LiveCharge documents:
     *  1. An ordinary ongoing notification carrying an indeterminate progress bar.
     *     Works on every watch this app supports.
     *  2. That same notification promoted to a status chip. Wear OS 7 (API 36) and up
     *     only, decided entirely by the system at post time -- this can ask, never
     *     force. Below that the identical builder renders as (1), which is correct,
     *     not a failure. Google's own note is that Live Updates are the recommended
     *     replacement for the legacy Ongoing Activities API from Wear OS 7 on.
     *
     * The promotion conditions met here are the same checklist the phone's LiveCharge
     * spells out: a promotable style (ProgressStyle), the POST_PROMOTED_NOTIFICATIONS
     * manifest permission, setRequestPromotedOngoing, setOngoing, a non-blank title,
     * no custom RemoteViews, not a group summary, not colorized, and a channel above
     * IMPORTANCE_MIN. [shortCriticalText] is what a status chip actually shows when it
     * has room for only a few characters, so it is kept to one short word.
     *
     * Indeterminate on purpose: a car command reports no percentage. It reports
     * "sent" and then, eventually, an outcome.
     *
     * Sources: developer.android.com/training/wearables/notifications/live-updates
     * (Wear OS 7+, ProgressStyle supported, MetricStyle not) and the phone-side
     * live-update guidance the LiveCharge doc in :app cites in full.
     */
    fun postProgress(
        context: Context,
        id: Int,
        title: String,
        text: String,
        shortCriticalText: String,
    ) {
        if (!hasPermission(context)) return
        ensureLiveChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            id,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // Built as a statement rather than chained into setStyle: the phone's
        // LiveCharge uses ProgressStyle's setters the same way, and not depending on
        // what they return keeps this correct either way.
        val progressStyle = NotificationCompat.ProgressStyle()
        progressStyle.setProgressIndeterminate(true)

        val notification = NotificationCompat.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setColor(ACCENT_COLOR)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(progressStyle)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            // Ongoing + promoted-ongoing are two of the promotion conditions, and
            // ongoing is independently correct: this describes work in flight, so it
            // should not be swipeable away as though it were a finished message.
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(shortCriticalText)
            // Deliberately NOT setAutoCancel: the command's own completion cancels
            // this, so a tap that opens the app must not also dismiss the thing
            // telling the user their car is still being talked to.
            .setContentIntent(contentIntent)
            .build()

        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    /** Dismiss a notification by id -- how an in-flight [postProgress] is retired once
     *  the work it describes is over. Safe to call for an id that was never posted. */
    fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }

    /** Create the alerts channel once (idempotent); no-op below Android O. */
    private fun ensureChannel(context: Context) = ensureNotificationChannel(
        context,
        id = CHANNEL_ID,
        name = "Car alerts",
        importance = NotificationManager.IMPORTANCE_HIGH,
        description = "Command results and alerts from the watch",
    )

    /** Create the live-progress channel once (idempotent). */
    private fun ensureLiveChannel(context: Context) = ensureNotificationChannel(
        context,
        id = LIVE_CHANNEL_ID,
        name = "In progress",
        importance = NotificationManager.IMPORTANCE_LOW,
        description = "Live progress while a command is running on your car",
        // No launcher badge: a transient progress bar shouldn't dot the app icon.
        showBadge = false,
    )
}
