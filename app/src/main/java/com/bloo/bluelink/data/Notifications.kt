package com.bloo.bluelink.data

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bloo.bluelink.R
import com.bloo.bluelink.widget.WidgetPhoto

/** Posts Bloo's local alerts (service due, door left open, car left running). */
object Notifications {
    private const val CHANNEL = "bloo_alerts"
    /** Bloo's accent, used to tint the small icon in the shade. */
    private const val ACCENT = BlooColors.brandAccent

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
    private fun ensureChannel(context: Context) = ensureNotificationChannel(
        context,
        id = CHANNEL,
        name = "Car alerts",
        importance = NotificationManager.IMPORTANCE_DEFAULT,
        description = "Service-due, door-open and car-running alerts",
    )

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
        val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ch = mgr.getNotificationChannel(channelId)
                ch == null || ch.importance != NotificationManager.IMPORTANCE_NONE
            } else true
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
    fun post(context: Context, id: Int, title: String, text: String, actions: List<Action> = emptyList()): Boolean {
        if (!hasPermission(context)) return false
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
        //
        // Returns whether the notification actually went out. Every caller but one ignores
        // it; UpdateCheckWorker must not, because it records "already told them about this
        // build" and had been doing so even when this returned early.
        return runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
            .isSuccess
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
    // Two earlier ids for this exact channel, from before it had this name -- each rename
    // was to change channel properties (importance, mainly), which Android only reads at
    // creation and never updates on an existing channel id. Neither rename deleted the old
    // channel, so a device that has had this feature installed since either earlier version
    // is carrying dead duplicates: the OS-level Notifications screen lists all three under
    // "Charging" with nothing to tell them apart, and the two orphans can never post again.
    // Deleted below, once, the first time [ensureChannel] runs after this fix ships.
    private val LEGACY_CHANNELS = listOf("bloo_charging", "bloo_charging_v2")
    private const val ACCENT = BlooColors.brandAccent
    // The one shared charge green (BlooColors.chargeGreen), same as the widget ring, the QS
    // and watch tiles, and the watch app. This used to be its own 0xFF34C759 -- a brighter,
    // Apple-style green that had drifted from the canonical token, so the live-charge bar (the
    // one charge surface that actively interrupts the user) showed a different green from every
    // glanceable surface. Consolidated so a future palette change moves all of them together.
    private const val CHARGE_GREEN = BlooColors.chargeGreen
    // The bar's "topped up" fill, once the pack is at (or past) its own configured
    // limit -- the same shared token every other surface that draws this bar now uses.
    private const val CHARGE_BLUE = BlooColors.chargeBlue
    private const val TRACK = 0x40FFFFFF
    // The "won't fill past here" segment, past either the limit or (once the charge
    // is already there) the current charge itself. Well under half TRACK's alpha,
    // not just half -- half turned out too close to TRACK to read as a second, dimmer
    // zone once actually rendered on a real device (see the phone's ChargeSegmentBar
    // for the same finding there); NotificationCompat.ProgressStyle has no explicit
    // inter-segment gap to fall back on the way the phone/widget bars do, so the
    // colour step here has to carry the whole distinction on its own.
    private const val TRACK_DIM = 0x14FFFFFF

    private fun idFor(vin: String) = ("live_charge_$vin").hashCode()

    private fun ensureChannel(context: Context) {
        ensureNotificationChannel(
            context,
            id = CHANNEL,
            name = "Charging",
            importance = NotificationManager.IMPORTANCE_LOW,
            description = "Live progress while your car is charging",
            // No launcher badge: a persistent live-progress bar shouldn't dot the app icon.
            showBadge = false,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            // Cheap once this device's orphans are gone: deleteNotificationChannel is a
            // no-op (not an error) for an id that doesn't exist, so this never needs its
            // own "already cleaned up" flag.
            LEGACY_CHANNELS.forEach { legacyId -> runCatching { mgr.deleteNotificationChannel(legacyId) } }
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
     * Deep-links to the OS Developer options screen -- `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS`.
     *
     * Exists for one specific, confirmed-on-a-real-device reason: at least through One UI 8.5,
     * Samsung gates whether a Live Update actually renders as a chip behind its OWN switch,
     * "Live notifications for all apps" -- found in Developer options, not the generic
     * per-app Live Updates permission [isPromotable] already checks. A device can pass every
     * row of the promotion checklist plus [isPromotable] returning true and still never show
     * a chip because this Samsung-only gate is off, with nothing in the standard Android
     * notification APIs able to see or report that state. There is no known intent extra to
     * jump straight to that one row -- Developer options is a flat, OEM-arranged list -- so
     * this can only land on the screen, not the exact toggle; [SettingsScreen]'s
     * troubleshooting steps tell the user what to look for once there.
     *
     * If Developer options themselves aren't enabled yet, this intent resolves to nothing on
     * most OEM builds rather than opening a blocked screen -- silently, same as the two
     * runCatching calls elsewhere in this file. There is no reliable settings action for "the
     * screen you enable Developer options from" across OEM skins (Samsung places the
     * build-number tap under About phone > Software information, not the stock location), so
     * the troubleshooting text spells out the manual path instead of guessing an intent that
     * might resolve to the wrong screen on a given OEM.
     */
    fun openDeveloperOptions(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * Convenience overload: derive the five charge fields from an [EvStatus] and delegate.
     *
     * The three callers named below -- AppViewModel's post-refresh hook, AlertWorker, and
     * LiveChargePollWorker -- each held an `ev: EvStatus?` and mapped it to these five fields
     * with the identical five lines. That mapping is exactly the kind of thing the KDoc on the
     * full-parameter [sync] below warns about: a rule ("charging means batteryCharge == true",
     * "the limit is targetForCurrentPlug") that has to agree across three sites. It now lives
     * here, next to the policy it belongs with.
     *
     * `charging = ev?.batteryCharge == true` -- verified identical at all three old call sites,
     * including LiveChargePollWorker whose local `charging` val was that exact expression. The
     * "only call this for a car you actually heard back from" contract on [sync] is unchanged:
     * these callers already guard on a non-null status before reaching here.
     */
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
        chargeLimit = ev?.targetForCurrentPlug(),
    )

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
        // The same photo the hero card shows, reused as the notification's large icon --
        // ties the bar back to the actual car instead of a generic icon. Always a local
        // file (see SettingsStore.imageUrl's own doc), so this is disk I/O, never a
        // network fetch, and WidgetPhoto.decodeCached already downsamples + LRU-caches it
        // for exactly this "decode a car photo outside Compose" job -- the widget's own
        // photo background uses the same call. runCatching because a missing/corrupt file
        // must never cost the bar itself; a null large icon is a graceful no-op.
        val carPhoto = runCatching { settings.imageUrl(vin)?.let { WidgetPhoto.decodeCached(it) } }.getOrNull()
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
        /** The charge limit for whichever plug is connected, if reported -- drawn as
         *  a split in the bar (and turns its fill blue once reached). */
        chargeLimit: Int? = null,
        /** The car's own photo, already decoded (see [sync]) -- shown as the notification's
         *  large icon so the bar reads as THIS car, the same way the hero card's photo does.
         *  Null falls back to the plain small icon with nothing extra, never an error. */
        carPhoto: Bitmap? = null,
    ) {
        val id = idFor(vin)
        // Check THIS feature's own channel, not the alerts channel: the charging bar posts to
        // bloo_live_charge (CHANNEL here is LiveCharge's own const), so a per-channel block on
        // the alerts channel must not cancel it, and a block on this one must stop it.
        if (!enabled || !charging || !Notifications.hasPermission(context, CHANNEL)) {
            runCatching { NotificationManagerCompat.from(context).cancel(id) }
            return
        }
        ensureChannel(context)

        val style = NotificationCompat.ProgressStyle()
        val limit = chargeLimit?.takeIf { it in 1..99 }
        if (percent != null) {
            val pct = percent.coerceIn(0, 100)
            // Independent of `charging` -- a car reported charged to its own limit
            // reads blue even hours later, unplugged, same as every other surface
            // that draws this bar (see the phone's ChargeReadout.stuckAtLimit).
            val stuck = limit != null && pct >= limit
            // Up to three segments -- filled to the current charge, track to the
            // limit, dim track past it -- or two once the charge is already at (or
            // past) its own limit, since there's no "still filling toward it" zone
            // left to show separately at that point. Replaces the old two-segment
            // fill/remainder split plus a Point marker at the limit: that pairing
            // read as a cluttered tracker glyph riding the bar on a real device, and
            // collapsing the split-not-marker case down to ONE segment whenever the
            // charge sits exactly at its limit sidesteps the reason a marker was
            // used there in the first place (two devices landing on the same pixel).
            // Zero-length segments are skipped throughout, same reasoning as
            // before: the API contract for one isn't documented, and an empty
            // segment says nothing a shorter list doesn't already say.
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
            // setStyledByProgress(FALSE), which is what the version confirmed working on
            // a real device used. True lets the platform style the bar from the progress
            // VALUE, which overrides the segment colours built right above -- so the
            // green/track split this code goes to the trouble of computing was being
            // thrown away. The rebuild flipped it to true with no reason recorded.
            style.setStyledByProgress(false)
                .setProgressSegments(segments)
                .setProgress(pct)
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
            // The confirmation must NOT land on `id`. That is this bar's own id, and
            // LiveCharge.sync posts, updates and cancels it on every 5-minute poll -- so a
            // confirmation posted there gets cancelled by the next poll, or replaced by a
            // reposted bar, and in the meantime LiveCharge believes its bar is showing when what
            // is actually showing is a "Stop charging sent" message.
            putExtra(AlertActionReceiver.EXTRA_CONFIRM_ID, ("live_charge_confirm_$vin").hashCode())
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
            // The hero card's own photo, when there is one -- see [sync]. Large icon, not a
            // background: a promoted notification can never have a custom background image,
            // because that means customContentView (RemoteViews), and RemoteViews is promotion
            // condition 6's explicit disqualifier. This is the closest a promoted notification
            // can get to "looks like the hero card" without giving up promotion to get there.
            .apply { carPhoto?.let { setLargeIcon(it) } }
            // VISIBILITY_PUBLIC, restored from the working version. Without it the
            // default is VISIBILITY_PRIVATE, and a secured lock screen hides a private
            // notification's content -- on the lock screen and the always-on display,
            // which are two of the three places a Live Update is supposed to appear.
            // The alert builder in this same file sets it; LiveCharge lost it in the
            // rebuild.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setRequestPromotedOngoing(true)
            .setStyle(style)
            .addAction(0, "Stop charging", stopPi)
            .setDeleteIntent(dismissPi)
            .apply { contentPi?.let { setContentIntent(it) } }
            // The STATUS BAR CHIP's text, and the piece the rebuild dropped.
            //
            // I previously left this out on the grounds that setShortCriticalText is
            // documented on the platform Notification.Builder and I couldn't confirm it
            // on NotificationCompat.Builder. That was wrong, and this repo's own history
            // is the proof: the version that was confirmed promoting on a real device
            // called exactly this, and that commit built. Reasoning from documentation I
            // couldn't fully fetch, when a working answer was sitting in git log, is the
            // mistake -- not the API.
            //
            // Deliberately paired with setShowWhen(false), matching that version. The
            // guide offers setShortCriticalText OR setWhen for chip state; the one known
            // to have worked here used the former and suppressed the timestamp. A
            // countdown chronometer is a nicer idea and I had added one, but it is an
            // unverified change to the exact surface that is broken, so it goes until the
            // chip is confirmed back.
            .setShowWhen(false)
            .apply { percent?.let { setShortCriticalText("${it.coerceIn(0, 100)}%") } }

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
     * away, so the alert is free to fire again next time the condition recurs --
     * and that reset happens regardless of [canDeliver], deliberately, so a flag
     * left set before delivery lapsed cannot outlive the condition it describes.
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
        /**
         * Whether the CALLER can actually get an alert in front of the user.
         *
         * The fire-once flag means "the user has been told about this", so recording it
         * when nobody was told is a lie that this function then believes forever. That was
         * live: [com.bloo.bluelink.work.AlertWorker]'s only delivery channel is a system
         * notification, and [Notifications.post] silently returns without posting when
         * POST_NOTIFICATIONS isn't granted -- so with notifications off, a door left open
         * marked itself alerted and stayed that way for the whole open episode. Worse for
         * the service-due alert, whose condition never clears on its own: marked once,
         * suppressed until the user records a service.
         *
         * False makes the fire sites below no-ops that touch NO flags -- so the condition
         * is simply re-evaluated next tick, and it fires for real the moment delivery
         * becomes possible. The RESET branches deliberately still run either way: a flag
         * left true because permission lapsed mid-episode would suppress the next genuine
         * alert once permission came back.
         *
         * Defaults true because most callers can always deliver --
         * [com.bloo.bluelink.ui.AppViewModel.checkAlerts] shows an in-app snackbar as well
         * as posting, and that needs no permission at all. Only a notification-only caller
         * should pass anything else.
         */
        canDeliver: Boolean = true,
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
            // due-ness at all (rather than treating it as "not due"). nextServiceMiles
            // is the shared formula -- this was a third inline `last + interval`, the
            // exact re-inlining that helper's KDoc warns against, and the one the phone
            // pebble and wear card both already route through.
            val due = if (last != null && interval != null) nextServiceMiles(last, interval) else null
            // serviceDue returns raw signed miles remaining ((last+interval) - odo),
            // or null if any input is unknown. `remaining <= 0` is exactly the
            // original `odo >= due` edge (fires the moment odo reaches the interval).
            val remaining = serviceDue(odo, last, interval)
            val key = "service_${v.vin}"
            if (remaining != null && remaining <= 0) {
                if (canDeliver && !settings.alertFired(key)) {
                    // formatDistance, not a bare "mi". This notification stated miles to a
                    // metric user while the phone service pebble and the wear service card
                    // (both formatDistance) showed the same figures in km -- so the one
                    // surface that interrupts you was the one in the wrong unit.
                    val metric = settings.unitSystem() == "metric"
                    // odo and due are both non-null in this branch (remaining != null requires
                    // all three inputs), but the compiler can't carry that through serviceDue's
                    // signature -- odo is Int? from parseOdometerMiles. `?.let ?: ""` keeps it
                    // total without a not-null assertion; the empty fallback is unreachable here.
                    val odoStr = odo?.let { formatDistance(it, metric) } ?: ""
                    val dueStr = due?.let { formatDistance(it, metric) } ?: ""
                    out += Alert(
                        serviceId(v),
                        "${v.name} is due for service",
                        "Odometer $odoStr is past the $dueStr service interval.",
                    )
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
                } else if (canDeliver && now - since > prefs.doorOpenMinutes * 60_000L && !settings.alertFired(key)) {
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
                } else if (canDeliver && now - since > prefs.unlockedMinutes * 60_000L && !settings.alertFired(key)) {
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
                } else if (canDeliver && now - since > prefs.runningMinutes * 60_000L && !settings.alertFired(key)) {
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
