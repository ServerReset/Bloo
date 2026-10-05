package com.bloo.bluelink.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.bloo.bluelink.R

/**
 * Android 16's "Live Update" notification for an actively-charging car -- the ongoing progress bar
 * Google documents at developer.android.com under "Create live update notifications" and
 * "Progress-centric notifications" (the Android 16 feature page).
 */
object LiveCharge {
    // Own channel, never shared with the alert channel above: this reposts on every poll (as often
    // as every 5 minutes while charging), which would be intolerable noise mixed into a channel
    // meant for occasional door/service alerts.
    private const val CHANNEL = "bloo_live_charge"
    private const val ACCENT = BlooColors.brandAccent

    // The one shared charge green (BlooColors.chargeGreen), used by every charge readout on the
    // phone. Consolidated so a future palette change moves all of them together.
    private const val CHARGE_GREEN = BlooColors.chargeGreen

    private const val CHARGE_BLUE = BlooColors.chargeBlue

    private const val TRACK = 0x40FFFFFF

    // The "won't fill past here" segment, past either the limit or (once the charge is already
    // there) the current charge itself.
    private const val TRACK_DIM = 0x14FFFFFF

    internal fun idFor(vin: String) = ("live_charge_$vin").hashCode()

    internal fun ensureChannel(context: Context) {
        ensureNotificationChannel(
            context,
            id = CHANNEL,
            name = "Charging",
            importance = NotificationManager.IMPORTANCE_LOW,
            description = "Live progress while your car is charging",
            // No launcher badge: a persistent live-progress bar shouldn't dot the app icon.
            showBadge = false,
        )
    }

    /**
     * Clears every car's live-charge notification at once -- used when the user turns the feature
     * off, so nothing is left pinned in the shade until the next poll happens to notice.
     */
    fun cancelAll(context: Context, vins: List<String>) {
        val mgr = NotificationManagerCompat.from(context)
        vins.forEach { vin -> runCatching { mgr.cancel(idFor(vin)) } }
    }

    /**
     * Whether the SYSTEM will currently let this app show a promoted chip -- the per-app Live
     * Updates toggle, live-queried rather than guessed. `false` below API 36 unconditionally:
     * `canPostPromotedNotifications()` itself doesn't exist on the platform there, so there's
     * nothing to ask.
     */
    fun isPromotable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 36 &&
            NotificationManagerCompat.from(context).canPostPromotedNotifications()

    /** Convenience overload: derive the five charge fields from an [EvStatus] and delegate. */
    suspend fun sync(
        context: Context,
        settings: SettingsStore,
        vin: String,
        carName: String,
        ev: EvStatus?,
    ) = sync(
        context = context,
        settings = settings,
        vin = vin,
        carName = carName,
        charging = ev?.batteryCharge == true,
        percent = ev?.batteryStatus,
        minutesToFull = ev?.minutesToFull,
        pluggedInLabel = ev?.pluggedInLabel,
        // This bar only ever exists while charging = true, which normally implies a plug is
        // connected, but the fallback costs nothing and covers the rare case of a status
        // inconsistency between the two fields.
        chargeLimit = ev?.displayChargeLimit(),
    )

    /**
     * [sync] for a caller holding a [Vehicle] and its current [EvStatus], which is how both
     * background workers reach it: each wrote out `sync(context = applicationContext, settings =
     * settings, vin = v.vin, carName = v.name, ev = ev)` verbatim.
     */
    suspend fun sync(
        context: Context,
        settings: SettingsStore,
        v: Vehicle,
        ev: EvStatus?,
    ) = sync(
        context = context,
        settings = settings,
        vin = v.vin,
        carName = v.name,
        ev = ev,
    )

    /**
     * The one entry point callers should use: applies the dismissal rule, then delegates to
     * [update].
     */
    suspend fun sync(
        context: Context,
        settings: SettingsStore,
        vin: String,
        carName: String,
        charging: Boolean,
        percent: Int? = null,
        minutesToFull: Int? = null,
        pluggedInLabel: String? = null,
        chargeLimit: Int? = null,
    ) {
        if (!charging) {
            // Guarded, like the four identical resets in CarAlerts below -- one of which
            // spells out the reason: an unconditional write costs editTracked a full
            // copy+diff of every preference plus a whole-file serialize and fsync (edit {}
            // does not return until the data is durable), even when the value is already
            // false. This one call site was the exception.
            //
            // It is the hot path, not a corner: prefs.charging defaults on, so for V cars
            // with none charging this fired V full-file writes per 30-minute AlertWorker
            // tick, plus V more per persistSnapshots() -- which runs up to three times per
            // command and is also the debounced target for text-field edits, so typing a
            // license plate cost V durable writes.
            runCatching { if (settings.liveChargeDismissed(vin)) settings.setLiveChargeDismissed(vin, false) }
            update(context, vin, carName, charging = false)
            return
        }
        if (runCatching { settings.liveChargeDismissed(vin) }.getOrDefault(false)) return
        // Large icon removed - widget system deleted
        val carPhoto = null
        update(
            context = context,
            vin = vin,
            carName = carName,
            charging = true,
            percent = percent,
            minutesToFull = minutesToFull,
            pluggedInLabel = pluggedInLabel,
            enabled = true,
            chargeLimit = chargeLimit,
            carPhoto = carPhoto,
        )
    }

    /**
     * Shows, updates, or cancels [vin]'s live-charge notification to match its current charge
     * state.
     */
    fun update(
        context: Context,
        vin: String,
        carName: String,
        charging: Boolean,
        percent: Int? = null,
        minutesToFull: Int? = null,
        pluggedInLabel: String? = null,
        enabled: Boolean = true,
        chargeLimit: Int? = null,
        /**
         * The car's own photo, already decoded (see [sync]) -- shown as the notification's large
         * icon so the bar reads as THIS car, the same way the hero card's photo does.
         */
        carPhoto: Bitmap? = null,
    ) {
        val id = idFor(vin)
        // Check THIS feature's own channel, not the alerts channel: the charging bar posts to
        // bloo_live_charge (CHANNEL here is LiveCharge's own const), so a per-channel block on the
        // alerts channel must not cancel it, and a block on this one must stop it.
        if (!enabled || !charging || !Notifications.hasPermission(context, CHANNEL)) {
            runCatching { NotificationManagerCompat.from(context).cancel(id) }
            return
        }
        ensureChannel(context)

        val style = NotificationCompat.ProgressStyle()
        val limit = chargeLimit?.takeIf { it in 1..99 }
        if (percent != null) {
            val pct = percent.coerceIn(0, 100)
            val stuck = limit != null && pct >= limit
            // Up to three segments -- filled to the current charge, track to the limit, dim track
            // past it -- or two once the charge is already at (or past) its own limit, since
            // there's no "still filling toward it" zone left to show separately at that point.
            val segments = buildList {
                if (pct > 0) add(NotificationCompat.ProgressStyle.Segment(pct).setColor(if (stuck) CHARGE_BLUE else CHARGE_GREEN))
                when {
                    limit == null -> if (pct < 100) add(NotificationCompat.ProgressStyle.Segment(100 - pct).setColor(TRACK))
                    stuck -> if (pct < 100) add(NotificationCompat.ProgressStyle.Segment(100 - pct).setColor(TRACK_DIM))
                    else -> {
                        if (limit > pct) add(NotificationCompat.ProgressStyle.Segment(limit - pct).setColor(TRACK))
                        if (limit < 100) add(NotificationCompat.ProgressStyle.Segment(100 - limit).setColor(TRACK_DIM))
                    }
                }
            }
            // setStyledByProgress(FALSE), which is what the version confirmed working on a real
            // device used. The rebuild flipped it to true with no reason recorded.
            style.setStyledByProgress(false)
                .setProgressSegments(segments)
                .setProgress(pct)
        } else {
            style.setProgressIndeterminate(true)
        }

        val detail = listOfNotNull(
            percent?.let { "$it%" },
            limit?.takeIf { percent == null || percent < it }?.let { "to $it%" },
            minutesToFull?.takeIf { it > 0 }?.let { "${fmtMinutes(it)} left" },
            pluggedInLabel?.takeIf { it.isNotBlank() && !it.startsWith("Not ") },
        ).joinToString(" · ")

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentPi = launch?.let {
            PendingIntent.getActivity(
                context, id, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val stopIntent = Intent(context, AlertActionReceiver::class.java).apply {
            action = AlertActionReceiver.ACTION_RUN
            data = "bloo://live_charge/$vin".toUri()
            putExtra(AlertActionReceiver.EXTRA_VIN, vin)
            putExtra(AlertActionReceiver.EXTRA_ACTION, CarAction.CHARGE_OFF)
            putExtra(AlertActionReceiver.EXTRA_NOTIF_ID, id)
            // The confirmation must NOT land on `id`.
            putExtra(AlertActionReceiver.EXTRA_CONFIRM_ID, ("live_charge_confirm_$vin").hashCode())
            putExtra(AlertActionReceiver.EXTRA_LABEL, "Stop charging")
        }
        val stopPi = PendingIntent.getBroadcast(
            context, id, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // deleteIntent: the only way to learn the user swiped this away. Without it the next poll
        // silently reposted a bar they had just dismissed, every five minutes for the length of the
        // charge -- which the Live Updates guidance calls out specifically.
        val dismissIntent = Intent(context, AlertActionReceiver::class.java).apply {
            action = AlertActionReceiver.ACTION_LIVE_CHARGE_DISMISSED
            data = "bloo://live_charge_dismissed/$vin".toUri()
            putExtra(AlertActionReceiver.EXTRA_VIN, vin)
        }
        val dismissPi = PendingIntent.getBroadcast(
            context, id + 1, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL)
            // The watch shows its own charging card, so the phone's isn't copied onto it.
            .setLocalOnly(com.bloo.bluelink.wear.WatchPresence.appInstalled(context))
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setColor(ACCENT)
            .setContentTitle("$carName is charging")
            .setContentText(detail.ifBlank { "Charging" })
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            // The hero card's own photo, when there is one -- see [sync]. Large icon, not a
            // background: a promoted notification can never have a custom background image, because
            // that means customContentView (RemoteViews), and RemoteViews is promotion condition
            // 6's explicit disqualifier.
            .apply { carPhoto?.let { setLargeIcon(it) } }
            // VISIBILITY_PUBLIC, restored from the working version. Without it the default is
            // VISIBILITY_PRIVATE, and a secured lock screen hides a private notification's content
            // -- on the lock screen and the always-on display, which are two of the three places a
            // Live Update is supposed to appear.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setRequestPromotedOngoing(true)
            .setStyle(style)
            .addAction(0, "Stop charging", stopPi)
            .setDeleteIntent(dismissPi)
            .apply { contentPi?.let { setContentIntent(it) } }
            // The STATUS BAR CHIP's text, and the piece the rebuild dropped. Reasoning from
            // documentation I couldn't fully fetch, when a working answer was sitting in git log,
            // is the mistake -- not the API. Deliberately paired with setShowWhen(false), matching
            // that version.
        .setShowWhen(false)
        .apply { percent?.let { setShortCriticalText("${it.coerceIn(0, 100)}%") } }

        postBuilt(context, id, builder)
    }
}
