package com.bloo.wear

import android.Manifest
import android.app.NotificationChannel
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
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    /** Create the alerts channel once (idempotent); no-op below Android O. */
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Car alerts",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Command results and alerts from the watch"
                },
            )
        }
    }
}
