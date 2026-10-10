package com.bloo.bluelink.data

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.bloo.bluelink.R
import com.bloo.bluelink.ui.hasPermission

/** Posts Bloo's local alerts (service due, door left open, car left running). */
object Notifications {
    // One channel per KIND of notification, so the user can tune (or silence) each independently in
    // system settings -- a car that keeps nagging about a door is a very different thing from "your
    // car was started", and Android gives no way to split them once they share a channel. Every
    // notification this app posts names one of these.
    /** State alerts: service due, door left open, unlocked/running too long. */
    const val CHANNEL_ALERTS = "bloo_alerts"
    /** Events: the car was started, a charge session completed. */
    const val CHANNEL_EVENTS = "bloo_events"
    /** Bloo's own app updates: a newer build is available / was downloaded. */
    const val CHANNEL_UPDATES = "bloo_updates"

    /** Kept as the alerts channel for the many existing call sites that mean "an alert". */
    private const val CHANNEL = CHANNEL_ALERTS
    /** Bloo's accent, used to tint the small icon in the shade. */
    private const val ACCENT = BlooColors.brandAccent

    /** A tappable action on an alert that issues a remote command for [vin]. */
    data class Action(val label: String, val vin: String, val carAction: String)

    /**
     * Makes sure the "bloo_alerts" notification channel exists before we ever try
     * to post to it. Notification channels only exist on API 26+ (Oreo), so this
     * is a no-op on older devices where channels aren't a concept. It first reads
     * back the channel by id and only calls [NotificationManager.createNotificationChannel]
     * if it's missing -- creating a channel that already exists is harmless in
     * Android's API but doing the existence check avoids clobbering the user's own
     * per-channel settings (importance, sound, etc.) that they may have changed
     * from system settings, since re-creating a channel resets none of that but
     * needlessly re-declaring it is still wasted work best avoided.
     */
    private fun ensureChannel(context: Context) = ensureNotificationChannel(
        context,
        id = CHANNEL_ALERTS,
        name = "Car alerts",
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        description = "Service-due, door-open and car-running alerts",
    )

    /**
     * Idempotently creates the channels this object can post to. Called from [post] with
     * the channel actually being used, so a caller that only ever posts updates never
     * creates the alerts channel and vice versa.
     */
    private fun ensureChannel(context: Context, channelId: String) {
        when (channelId) {
            CHANNEL_EVENTS -> ensureNotificationChannel(
                context,
                id = CHANNEL_EVENTS,
                name = "Car events",
                importance = NotificationManager.IMPORTANCE_DEFAULT,
                description = "Your car was started, or a charge session finished",
            )
            CHANNEL_UPDATES -> ensureNotificationChannel(
                context,
                id = CHANNEL_UPDATES,
                name = "App updates",
                importance = NotificationManager.IMPORTANCE_DEFAULT,
                description = "A newer Bloo build is available or was downloaded",
            )
            else -> ensureChannel(context)
        }
    }

    /**
     * Whether Bloo can actually get a notification in front of the user.
     *
     * THREE things have to be true, and this used to test only the first:
     *  1. the API 33+ runtime POST_NOTIFICATIONS grant (older versions have no such
     *     permission, hence the short-circuit);
     *  2. notifications not blocked for the whole app -- possible on EVERY API level,
     *     including the ones where step 1 short-circuits to true;
     *  3. the alerts channel not blocked individually (API 26+), which a user can do
     *     from the notification's own long-press menu without touching app settings.
     *
     * Why it matters more than a normal capability check: `notify()` does NOT throw for
     * 2 or 3. It succeeds, the notification never appears, and [post] therefore returned
     * true -- so every caller that persists "the user has been told" recorded a delivery
     * that never happened. CarAlerts' fired-flags only clear when the condition clears,
     * so a door-left-open could mark itself alerted and go unmentioned for the whole
     * episode; service-due is worse, since its condition never clears on its own.
     *
     * That failure mode is already in this file's history -- `canDeliver` and post()'s
     * Boolean return exist precisely to stop flags being written for undelivered
     * notifications. They were just resting on a permission check that answered a
     * narrower question than the one being asked.
     */
    fun hasPermission(context: Context, channelId: String = CHANNEL): Boolean {
        @Suppress("InlinedApi") // gated by the minSdk arg below
        val runtimeGranted = context.hasPermission(
            Manifest.permission.POST_NOTIFICATIONS, Build.VERSION_CODES.TIRAMISU,
        )
        if (!runtimeGranted) return false
        val mgr = NotificationManagerCompat.from(context)
        if (!mgr.areNotificationsEnabled()) return false
        // Channel-level block, tested against the channel actually being posted to
        // ([channelId], defaulting to the alerts CHANNEL). LiveCharge has its OWN channel and
        // must pass it -- checking the alerts channel's block state told LiveCharge the wrong
        // answer in BOTH directions (block alerts -> it cancelled the still-enabled charging
        // bar every poll; block only the charging channel -> it kept posting to a blocked one).
        // runCatching because this reads a system service and the channel may not exist yet --
        // an absent channel is NOT a block (ensureChannel creates it on the way to posting), so
        // absence must answer true.
        return runCatching {
            // No SDK guard: channels exist since O and the app minSdk is 26.
            val ch = mgr.getNotificationChannel(channelId)
            ch == null || ch.importance != NotificationManager.IMPORTANCE_NONE
        }.getOrDefault(true)
    }

    /**
     * Builds and posts a single alert notification under the shared "bloo_alerts"
     * channel, with an optional row of action buttons.
     *
     * Mechanism, in order:
     * 1. Bails immediately if notification permission isn't granted -- nothing
     *    below runs, so a denied permission is a cheap no-op rather than a crash.
     *    Returns false when it does, and false on a throw from `notify` too, so a
     *    caller that persists "the user has been told" can only do so truthfully.
     *    Most callers rightly ignore the result; the ones that record something
     *    must not (see [com.bloo.bluelink.work.UpdateCheckWorker]).
     * 2. Lazily ensures the channel exists (see [ensureChannel]).
     * 3. Builds a content [PendingIntent] that reopens the app when the
     *    notification body itself is tapped (not the action buttons), using the
     *    package's own launch intent so it lands on whatever the app's entry
     *    activity is.
     * 4. Configures the notification (icon, accent color, big-text style so long
     *    alert text isn't truncated, auto-cancel so tapping dismisses it).
     * 5. For each [Action], builds a *separate* broadcast [PendingIntent] that
     *    targets [AlertActionReceiver]. Each carries the VIN, the wear-style
     *    action id, this notification's id (so the receiver can cancel/replace
     *    this exact notification later) and the button label. The intent's data
     *    URI is made unique per (notification id, action) pair specifically so
     *    Android doesn't collapse/reuse PendingIntents across different alerts or
     *    different buttons on the same alert -- extras alone aren't part of
     *    PendingIntent identity, only the underlying Intent's action/data/component
     *    are, so without a unique URI two actions could silently share one intent.
     *    The request code (`id * 16 + i`) similarly keeps each button's
     *    PendingIntent distinct even if data collisions ever occurred.
     * 6. Finally posts the notification, wrapped in [runCatching] because the
     *    permission check above is a TOCTOU race -- the user could revoke the
     *    permission between the check and this call, and notify() would then
     *    throw a SecurityException that we don't want to crash the caller for.
     */
    fun post(
        context: Context,
        id: Int,
        title: String,
        text: String,
        actions: List<Action> = emptyList(),
        /** Which of the app's channels this belongs on -- see the CHANNEL_* consts. */
        channelId: String = CHANNEL_ALERTS,
        /** Don't bridge this to a paired watch (it raises its own version). */
        localOnly: Boolean = false,
    ): Boolean {
        if (!hasPermission(context)) return false
        ensureChannel(context, channelId)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = launch?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setLocalOnly(localOnly)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply { pi?.let { setContentIntent(it) } }

        actions.forEachIndexed { i, a ->
            val actionIntent = Intent(context, AlertActionReceiver::class.java).apply {
                action = AlertActionReceiver.ACTION_RUN
                // Unique data per (notification, action) so PendingIntents don't collapse.
                data = "bloo://alert/$id/${a.carAction}".toUri()
                putExtra(AlertActionReceiver.EXTRA_VIN, a.vin)
                putExtra(AlertActionReceiver.EXTRA_ACTION, a.carAction)
                putExtra(AlertActionReceiver.EXTRA_NOTIF_ID, id)
                putExtra(AlertActionReceiver.EXTRA_LABEL, a.label)
            }
            val actionPi = PendingIntent.getBroadcast(
                context, id * 16 + i, actionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, a.label, actionPi)
        }
        // NotificationManagerCompat.notify can throw if permission was revoked
        // between the hasPermission() check above and here; swallow rather than crash.
        //
        // Returns whether the notification actually went out. Every caller but one ignores
        // it; UpdateCheckWorker must not, because it records "already told them about this
        // build" and had been doing so even when this returned early.
        // Re-check explicitly right before the call (not just through
        // [hasPermission], which lint can't reason through): the check above is
        // still the TOCTOU race the runCatching guards, but this local read makes
        // the permission contract explicit at the call site and keeps lint's
        // MissingPermission analysis honest.
        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return postBuilt(context, id, builder)
    }
}

/**
 * The one place a BUILT notification is handed to the system.
 *
 * Every caller used to re-implement the same three-step guard — a runtime `POST_NOTIFICATIONS`
 * re-read (lint's contract at the call site), a [runCatching] around `notify()` because that
 * check is a TOCTOU race, and a boolean/nothing return so callers can record whether it went
 * out. Three copies had already drifted (one returned `Boolean`, two returned `Unit` on the
 * same failure); one function keeps them honest, and every new notification surface starts
 * from this instead of another copy.
 */
internal fun postBuilt(context: Context, id: Int, builder: androidx.core.app.NotificationCompat.Builder): Boolean {
    if (androidx.core.app.ActivityCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS,
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    return runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }.isSuccess
}
