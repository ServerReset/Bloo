package com.bloo.bluelink.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bloo.bluelink.R

/** Posts Bloo's local alerts (service due, door left open, car left running). */
object Notifications {
    private const val CHANNEL = "bloo_alerts"
    /** Bloo's accent, used to tint the small icon in the shade. */
    private const val ACCENT = 0xFF7B83EB.toInt()

    /** A tappable action on an alert that issues a remote command for [vin]. */
    data class Action(val label: String, val vin: String, val wearAction: String)

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
    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Car alerts", NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { description = "Service-due, door-open and car-running alerts" },
                )
            }
        }
    }

    /**
     * Whether Bloo is currently allowed to post notifications. Runtime notification
     * permission (POST_NOTIFICATIONS) was only introduced in Android 13 (Tiramisu);
     * on older versions notifications are always allowed at the OS level, so this
     * short-circuits to `true` there without ever touching the permission API. On
     * 13+ it defers to [ContextCompat.checkSelfPermission] to read the current
     * grant state.
     */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Builds and posts a single alert notification under the shared "bloo_alerts"
     * channel, with an optional row of action buttons.
     *
     * Mechanism, in order:
     * 1. Bails immediately if notification permission isn't granted -- nothing
     *    below runs, so a denied permission is a cheap no-op rather than a crash.
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
    fun post(context: Context, id: Int, title: String, text: String, actions: List<Action> = emptyList()) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = launch?.let {
            PendingIntent.getActivity(
                context, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply { pi?.let { setContentIntent(it) } }

        actions.forEachIndexed { i, a ->
            val actionIntent = Intent(context, AlertActionReceiver::class.java).apply {
                action = AlertActionReceiver.ACTION_RUN
                // Unique data per (notification, action) so PendingIntents don't collapse.
                data = Uri.parse("bloo://alert/$id/${a.wearAction}")
                putExtra(AlertActionReceiver.EXTRA_VIN, a.vin)
                putExtra(AlertActionReceiver.EXTRA_ACTION, a.wearAction)
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
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }
}

/**
 * Android 16's "Live Update" notification for an actively-charging car -- the
 * ongoing progress bar Google documents at developer.android.com under
 * "Create live update notifications" and "Progress-centric notifications"
 * (the Android 16 feature page). Every API call below is checked against
 * those two pages plus the androidx.core 1.17.0-alpha01 release notes, which
 * is the release that added [NotificationCompat.ProgressStyle],
 * `setRequestPromotedOngoing`, and `canPostPromotedNotifications` together --
 * this app depends on core-ktx 1.19.0, well past that floor.
 *
 * ## Two different things, one notification
 *
 * 1. **An ordinary ongoing notification with a progress bar.** Works on
 *    every OS version this app supports (minSdk 26): [update] reposts under
 *    the same id as the percentage climbs, and Android always treats a
 *    repost with the same id as "replace", not "post a new one".
 * 2. **The same notification promoted into a status-bar / lock-screen
 *    chip.** Android 16 (API 36) and up only, and made ENTIRELY by the
 *    system at post time -- this code can ask, never force it. Below API 36
 *    the exact same builder just renders as (1), which is correct behaviour,
 *    not a failure.
 *
 * ## The promotion checklist
 *
 * `Notification#hasPromotableCharacteristics()` is Google's own gate for
 * (2), and its documented requirements are ALL of:
 *
 *  1. A promotable style (Standard, `BigTextStyle`, `CallStyle`,
 *     `ProgressStyle`, or `MetricStyle`) -- [update] always builds a
 *     `ProgressStyle`.
 *  2. The `POST_PROMOTED_NOTIFICATIONS` manifest permission -- install-time,
 *     never a runtime prompt, nothing to request from this code.
 *  3. `setRequestPromotedOngoing(true)` -- [update].
 *  4. `setOngoing(true)` -- [update].
 *  5. A non-blank `contentTitle` -- [update], always `"$carName is charging"`.
 *  6. No custom `RemoteViews` -- never set here.
 *  7. Not a group summary -- never set here.
 *  8. Not `setColorized(true)` -- never set here.
 *  9. Channel importance above `IMPORTANCE_MIN` -- [ensureChannel] uses
 *     `IMPORTANCE_LOW`, one full step above the floor.
 *
 * ## The 10th condition -- and the part that's new
 *
 * All nine rows above are checkable from code. There is also a per-app OS
 * "Live Updates" toggle that `hasPromotableCharacteristics()` does NOT
 * cover -- a notification satisfying every row above can still render as an
 * ordinary notification if the user has that switch off, with nothing
 * queryable to explain why. Earlier androidx.core releases had no API for
 * this at all. As of 1.17 there is one:
 * `NotificationManagerCompat.canPostPromotedNotifications()` (API 36+ only;
 * unconditionally `false` below that, since the underlying platform method
 * doesn't exist there either) -- wrapped here as [isPromotable]. When it's
 * false, [openLiveUpdateSettings] sends the user straight to the OS page for
 * it via `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS`.
 *
 * ## How to tell it's actually promoting, on a real Android 16+ device
 *
 * Start a charge and watch for a CHIP in the status bar / lock screen, not
 * just a notification in the shade. If the shade notification looks right
 * (title, moving bar, Stop button) but no chip appears: check, in order,
 * (a) `Build.VERSION.SDK_INT >= 36` on the device, (b) [isPromotable] --
 * if false, [openLiveUpdateSettings] is the fix, not a code change --
 * (c) only then re-check the nine-row table above against whatever changed.
 *
 * Sources: https://developer.android.com/develop/ui/views/notifications/live-update ,
 * https://developer.android.com/about/versions/16/features/progress-centric-notifications ,
 * androidx.core 1.17.0-alpha01 release notes.
 */
object LiveCharge {
    // Own channel, never shared with the alert channel above: this reposts
    // on every poll (as often as every 5 minutes while charging), which
    // would be intolerable noise mixed into a channel meant for occasional
    // door/service alerts. IMPORTANCE_LOW keeps it silent (no sound, no
    // heads-up peek) while still clearing promotion condition 9 above with a
    // full step to spare.
    private const val CHANNEL = "bloo_live_charge"
    private const val ACCENT = 0xFF7B83EB.toInt()
    private const val CHARGE_GREEN = 0xFF34C759.toInt()
    private const val TRACK = 0x40FFFFFF
    private const val LIMIT_POINT = 0xFFFFFFFF.toInt()

    private fun idFor(vin: String) = ("live_charge_$vin").hashCode()

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Charging", NotificationManager.IMPORTANCE_LOW)
                        .apply {
                            description = "Live progress while your car is charging"
                            setShowBadge(false)
                        },
                )
            }
        }
    }

    /** Clears every car's live-charge notification at once -- used when the
     *  user turns the feature off, so nothing is left pinned in the shade
     *  until the next poll happens to notice. */
    fun cancelAll(context: Context, vins: List<String>) {
        val mgr = NotificationManagerCompat.from(context)
        vins.forEach { vin -> runCatching { mgr.cancel(idFor(vin)) } }
    }

    /**
     * Whether the SYSTEM will currently let this app show a promoted chip --
     * the per-app Live Updates toggle, live-queried rather than guessed.
     * `false` below API 36 unconditionally: `canPostPromotedNotifications()`
     * itself doesn't exist on the platform there, so there's nothing to ask.
     */
    fun isPromotable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 36 &&
            NotificationManagerCompat.from(context).canPostPromotedNotifications()

    /**
     * Deep-links to the OS page for this app's Live Updates permission --
     * `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`
     * (`"android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"`), which
     * takes the target package via `EXTRA_APP_PACKAGE` (an extra, NOT a
     * `package:` URI -- that's the older per-app-settings convention this
     * action doesn't use). Confirmed against the AOSP `Settings.java`
     * source directly rather than trusting a doc summary, since the wrong
     * extra means the intent resolves to nothing useful.
     *
     * Falls back to the app's general notification settings if the specific
     * page isn't resolvable (an OEM build without it, or a device below the
     * version this action shipped on), so the tap always lands somewhere
     * useful instead of silently doing nothing.
     */
    fun openLiveUpdateSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 36) {
            val intent = Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS").apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) return
        }
        // Fallback: the app's general notification settings page (works on any OS version).
        val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(fallback) }
    }

    /**
     * The one entry point callers should use: applies the dismissal rule, then delegates
     * to [update].
     *
     * There are three callers -- the 5-minute poll worker, the 30-minute alert worker, and
     * the app's own post-refresh hook -- and every rule about WHEN this notification should
     * exist has to hold in all three. It previously didn't: each called [update] directly
     * and each independently got the "I couldn't fetch a status" case wrong, cancelling the
     * bar because the network blipped. Putting the policy here means the next rule added
     * lands once.
     *
     * Two rules live here:
     *
     * Charging ended -> clear the bar and FORGET any dismissal, so the next charging
     * session shows it again instead of being permanently suppressed by one old swipe.
     *
     * Still charging but dismissed -> do nothing at all. Not a cancel: the notification is
     * already gone, the user removed it, and re-cancelling would be a pointless call.
     *
     * Callers must still only call this for a car they actually have a status for --
     * `charging = false` here genuinely means "the car told us it stopped", never "we
     * don't know".
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
            runCatching { settings.setLiveChargeDismissed(vin, false) }
            update(context, vin, carName, charging = false)
            return
        }
        if (runCatching { settings.liveChargeDismissed(vin) }.getOrDefault(false)) return
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
        )
    }

    /**
     * Shows, updates, or cancels [vin]'s live-charge notification to
     * match its current charge state. Reposting under the same [idFor] id is
     * exactly what makes this "live" below API 36 -- see the class doc.
     *
     * [percent] absent means the car hasn't reported a state of charge yet;
     * an indeterminate `ProgressStyle` is used rather than skipping the
     * style entirely, since a promotable style is promotion condition 1 and
     * skipping it on the very first poll would silently cost promotion for
     * however long the percent stays unknown.
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
        /** The charge limit for whichever plug is connected, if reported --
         *  drawn as a point marker on the bar. */
        chargeLimit: Int? = null,
    ) {
        val id = idFor(vin)
        if (!enabled || !charging || !Notifications.hasPermission(context)) {
            runCatching { NotificationManagerCompat.from(context).cancel(id) }
            return
        }
        ensureChannel(context)

        val style = NotificationCompat.ProgressStyle()
        val limit = chargeLimit?.takeIf { it in 1..99 }
        if (percent != null) {
            val pct = percent.coerceIn(0, 100)
            // Two segments: charged so far, and the remainder -- a zero-length
            // segment at either 0% or 100% is skipped rather than passed
            // through, since the API contract for a zero-length segment isn't
            // documented and there's no reason to rely on it when a single
            // full-length segment says the same thing unambiguously.
            val segments = buildList {
                if (pct > 0) add(NotificationCompat.ProgressStyle.Segment(pct).setColor(CHARGE_GREEN))
                if (pct < 100) add(NotificationCompat.ProgressStyle.Segment(100 - pct).setColor(TRACK))
            }
            style.setStyledByProgress(true)
                .setProgressSegments(segments)
                .setProgress(pct)
            if (limit != null) {
                style.setProgressPoints(
                    listOf(NotificationCompat.ProgressStyle.Point(limit).setColor(LIMIT_POINT)),
                )
            }
        } else {
            style.setProgressIndeterminate(true)
        }

        // "82% · to 80% · 1h 20m left · Plugged in (AC)" -- only the pieces
        // the car actually reported, joined with no stray separator for a
        // missing one.
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
            data = Uri.parse("bloo://live_charge/$vin")
            putExtra(AlertActionReceiver.EXTRA_VIN, vin)
            putExtra(AlertActionReceiver.EXTRA_ACTION, WearAction.CHARGE_OFF)
            putExtra(AlertActionReceiver.EXTRA_NOTIF_ID, id)
            putExtra(AlertActionReceiver.EXTRA_LABEL, "Stop charging")
        }
        val stopPi = PendingIntent.getBroadcast(
            context, id, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // deleteIntent: the only way to learn the user swiped this away. Without it the
        // next poll silently reposted a bar they had just dismissed, every five minutes
        // for the length of the charge -- which the Live Updates guidance calls out
        // specifically. Distinct request code from stopPi so the two PendingIntents don't
        // collide (same id, same class, different action -> FLAG_UPDATE_CURRENT would
        // otherwise have one overwrite the other's extras).
        val dismissIntent = Intent(context, AlertActionReceiver::class.java).apply {
            action = AlertActionReceiver.ACTION_LIVE_CHARGE_DISMISSED
            data = Uri.parse("bloo://live_charge_dismissed/$vin")
            putExtra(AlertActionReceiver.EXTRA_VIN, vin)
        }
        val dismissPi = PendingIntent.getBroadcast(
            context, id + 1, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setColor(ACCENT)
            .setContentTitle("$carName is charging")
            .setContentText(detail.ifBlank { "Charging" })
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setRequestPromotedOngoing(true)
            .setStyle(style)
            .addAction(0, "Stop charging", stopPi)
            .setDeleteIntent(dismissPi)
            .apply { contentPi?.let { setContentIntent(it) } }
            .apply {
                // A COUNTDOWN CHRONOMETER to the time the car says it will be full.
                //
                // This is what makes the thing actually live, and it is the piece that
                // was missing. Everything else here only changes when a poll lands, and
                // the poll is every five minutes -- a progress bar that moves once every
                // five minutes does not read as live, and the status-bar chip had no text
                // at all between polls. A chronometer is rendered and ticked by the
                // system, so it counts down every second at zero cost: no wake-ups, no
                // network, no reposts.
                //
                // Per the Live Updates guide, setWhen is what drives the chip's countdown
                // and the chronometer shows "as long as it is positive", so this is the
                // documented route to useful chip text.
                //
                // NOT setShortCriticalText, which the guide also suggests. That method is
                // documented on the platform Notification.Builder, and I could not confirm
                // it exists on NotificationCompat.Builder in the version this project
                // pins -- the compat release notes name setRequestPromotedOngoing and
                // ProgressStyle but not it. Guessing at a method name I cannot verify and
                // cannot compile locally is how you get a red build or, worse, a silent
                // no-op. The chronometer is better here anyway: it self-updates, where
                // short critical text would be frozen between polls exactly like the rest.
                val minsLeft = minutesToFull?.takeIf { it > 0 }
                if (minsLeft != null) {
                    setWhen(System.currentTimeMillis() + minsLeft * 60_000L)
                    setUsesChronometer(true)
                    setChronometerCountDown(true)
                    setShowWhen(true)
                } else {
                    // No estimate yet (or already full): suppress the timestamp entirely
                    // rather than letting the shade render this notification's post time
                    // as if it meant something about the charge.
                    setShowWhen(false)
                }
            }

        // Same TOCTOU reasoning as Notifications.post: permission could be
        // revoked between the hasPermission() check above and this call.
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }
}

/**
 * Evaluates per-car alert conditions, persisting bookkeeping so each fires once.
 * Returns the alerts that should be shown now (toast + notification).
 */
object CarAlerts {
    data class Alert(
        val id: Int,
        val title: String,
        val text: String,
        val actions: List<Notifications.Action> = emptyList(),
    )

    /**
     * Runs all enabled alert checks for one car against its latest polled
     * [status] and returns only the alerts that should fire *this call*.
     *
     * Each check follows the same "fire once" pattern using a per-check,
     * per-VIN boolean flag persisted in [settings] ([SettingsStore.alertFired] /
     * [SettingsStore.setAlertFired]): the condition is evaluated fresh every
     * call (this function is expected to run on a timer, e.g. from
     * [com.bloo.bluelink.work.AlertWorker]), but the flag suppresses posting the
     * same alert again on every subsequent tick while the condition remains true.
     * The flag is cleared back to false as soon as the underlying condition goes
     * away, so the alert is free to fire again next time the condition recurs.
     * `out` accumulates whichever alerts actually fired on this pass and is
     * returned to the caller for posting/toasting.
     */
    suspend fun evaluate(
        settings: SettingsStore,
        v: Vehicle,
        status: VehicleStatus?,
        // Kotlin doesn't allow a suspend call as a default parameter value, so
        // the "reuse an already-loaded prefs" default has to be null + a
        // fallback load inside the body instead of in the signature. AlertWorker
        // already loads this once per tick and passes it in — it was being
        // re-read from DataStore once per VEHICLE, per tick, for an identical
        // value; other callers (AppViewModel) are unchanged, they just don't
        // pass one and this loads it itself, same as before.
        prefs: SettingsStore.NotificationPrefs? = null,
    ): List<Alert> {
        val prefs = prefs ?: settings.notificationPrefs()
        val out = mutableListOf<Alert>()

        if (prefs.service) {
            // Odometer strings can arrive with thousands separators (e.g. "12,345")
            // and fractional miles; parseOdometerMiles strips/floors them to an Int.
            val odo = parseOdometerMiles(v.odometer)
            val last = settings.lastServiceMiles(v.vin)
            val interval = settings.serviceIntervalMiles(v.vin)
            // "Due" mileage only exists once both the last-serviced mileage and
            // the chosen interval are known; either missing means we can't judge
            // due-ness at all (rather than treating it as "not due").
            val due = if (last != null && interval != null) last + interval else null
            // serviceDue returns raw signed miles remaining ((last+interval) - odo),
            // or null if any input is unknown. `remaining <= 0` is exactly the
            // original `odo >= due` edge (fires the moment odo reaches the interval).
            val remaining = serviceDue(odo, last, interval)
            val key = "service_${v.vin}"
            if (remaining != null && remaining <= 0) {
                if (!settings.alertFired(key)) {
                    out += Alert(serviceId(v), "${v.name} is due for service", "Odometer $odo mi is past the $due mi service interval.")
                    settings.setAlertFired(key, true)
                }
            } else {
                // Not due (or not knowable) -- reset the flag so a future crossing
                // of the threshold is free to alert again. Guarded: an unconditional
                // write still costs editTracked a full copy+diff of every preference
                // and the store's write mutex, even when the value is already false.
                if (settings.alertFired(key)) settings.setAlertFired(key, false)
            }
        }

        // A null status means this poll's fetch failed, not that doors/engine
        // are actually closed/off -- treating it as "closed" reset the open/running
        // timers on every transient failure, so a genuinely open door across a run
        // of flaky polls could indefinitely delay the alert it was meant to fire.
        // Skip evaluation entirely rather than guess.
        if (prefs.doorOpen && status != null) {
            val open = status.doorOpen?.anyOpen == true || status.trunkOpen == true || status.hoodOpen == true
            val key = "door_${v.vin}"
            val now = System.currentTimeMillis()
            if (open) {
                // Timestamp is set the first time we observe "open" and left alone
                // on every subsequent open observation, so it always reflects when
                // the open state *began*, not when we last checked.
                val since = settings.doorOpenSince(v.vin)
                if (since == null) {
                    settings.setDoorOpenSince(v.vin, now)
                } else if (now - since > prefs.doorOpenMinutes * 60_000L && !settings.alertFired(key)) {
                    // Been open long enough and haven't already alerted for this
                    // open episode -- fire, offering a one-tap Lock action.
                    out += Alert(
                        doorId(v),
                        "${v.name} door is open",
                        "A door/trunk/hood has been open for over ${prefs.doorOpenMinutes} min.",
                        actions = listOf(Notifications.Action("Lock", v.vin, WearAction.LOCK)),
                    )
                    settings.setAlertFired(key, true)
                }
            } else {
                // Door closed (and status was actually fetched, per the note above)
                // -- reset both the open-since clock and the fired flag so the next
                // open episode starts its own fresh timer/alert.
                if (settings.doorOpenSince(v.vin) != null) settings.setDoorOpenSince(v.vin, null)
                if (settings.alertFired(key)) settings.setAlertFired(key, false)
            }
        }

        // Distinct from the door-open check above: a car can be fully closed up
        // and still sitting unlocked (doorLock == false is the car's own lock
        // state, independent of whether any door/trunk/hood happens to be
        // open right now) -- same null-status skip reasoning as doorOpen.
        if (prefs.unlocked && status != null) {
            val unlocked = status.doorLock == false
            val key = "unlocked_${v.vin}"
            val now = System.currentTimeMillis()
            if (unlocked) {
                // Same "start the clock on first observation, don't reset it
                // while still true" pattern as the door-open/running checks.
                val since = settings.unlockedSince(v.vin)
                if (since == null) {
                    settings.setUnlockedSince(v.vin, now)
                } else if (now - since > prefs.unlockedMinutes * 60_000L && !settings.alertFired(key)) {
                    out += Alert(
                        unlockedId(v),
                        "${v.name} is unlocked",
                        "It's been left unlocked for over ${prefs.unlockedMinutes} min.",
                        actions = listOf(Notifications.Action("Lock", v.vin, WearAction.LOCK)),
                    )
                    settings.setAlertFired(key, true)
                }
            } else {
                // Locked again (and status was actually fetched) -- reset both
                // the unlocked-since clock and the fired flag so the next
                // unlocked spell starts its own fresh timer/alert.
                if (settings.unlockedSince(v.vin) != null) settings.setUnlockedSince(v.vin, null)
                if (settings.alertFired(key)) settings.setAlertFired(key, false)
            }
        }

        if (prefs.running && status != null) {
            // Remote start / climate (and on supported cars, the engine) report as "on".
            val on = status.engine == true || status.airCtrlOn == true
            val key = "running_${v.vin}"
            val now = System.currentTimeMillis()
            if (on) {
                // Same "start the clock on first observation, don't reset it while
                // still true" pattern as the door-open check above.
                val since = settings.engineOnSince(v.vin)
                if (since == null) {
                    settings.setEngineOnSince(v.vin, now)
                } else if (now - since > prefs.runningMinutes * 60_000L && !settings.alertFired(key)) {
                    out += Alert(
                        runningId(v),
                        "${v.name} is running",
                        "The engine/climate has been running for over ${prefs.runningMinutes} min.",
                        actions = listOf(Notifications.Action("Turn off", v.vin, WearAction.CLIMATE_OFF)),
                    )
                    settings.setAlertFired(key, true)
                }
            } else {
                // Engine/climate off -- reset the clock and the fired flag so the
                // next running episode gets its own fresh timer/alert.
                if (settings.engineOnSince(v.vin) != null) settings.setEngineOnSince(v.vin, null)
                if (settings.alertFired(key)) settings.setAlertFired(key, false)
            }
        }
        return out
    }

    // Stable per-VIN notification ids, one distinct id per alert *kind* so the
    // three alert types for the same car never overwrite each other's
    // notification (each hashes a kind-prefixed string unique to that VIN).
    private fun serviceId(v: Vehicle) = ("svc" + v.vin).hashCode()
    private fun doorId(v: Vehicle) = ("door" + v.vin).hashCode()
    private fun runningId(v: Vehicle) = ("run" + v.vin).hashCode()
    private fun unlockedId(v: Vehicle) = ("unlocked" + v.vin).hashCode()
}
