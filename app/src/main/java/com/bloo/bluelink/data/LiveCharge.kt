package com.bloo.bluelink.data

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import com.bloo.bluelink.R

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
    private const val ACCENT = BlooColors.brandAccent
    // The one shared charge green (BlooColors.chargeGreen), used by every charge readout on the
    // phone. This used to be its own 0xFF34C759 -- a brighter,
    // Apple-style green that had drifted from the canonical token, so the live-charge bar (the
    // one charge surface that actively interrupts the user) showed a different green from every
    // other charge surface. Consolidated so a future palette change moves all of them together.
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
    // inter-segment gap to fall back on the way the phone bars do, so the
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
     * Whether the OS will currently let Bloo run its background work (AlertWorker's
     * 30-minute poll, and the 5-minute chain this poll kicks off once it finds a car
     * charging) on schedule -- the standard Android "battery optimization" exemption,
     * `PowerManager.isIgnoringBatteryOptimizations`.
     *
     * This is a DIFFERENT question from everything else in this file. The nine-row
     * promotion checklist and [isPromotable] are about whether an ALREADY-POSTED
     * notification can become a status-bar chip; this is about whether the periodic
     * work that would post or update it in the first place gets to run at all while
     * the app isn't open. A car that starts charging with Bloo backgrounded and no
     * exemption can sit for up to the full 30-minute AlertWorker interval -- or
     * longer, since a non-exempt app's periodic work is exactly what Doze defers --
     * before anything notices, which reads as "the live notification isn't
     * triggering," not as a promotion problem. Reported from a real device.
     */
    fun isBackgroundUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(android.os.PowerManager::class.java) ?: return true
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Requests the standard Android battery-optimization exemption directly --
     * `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which prompts the user
     * with a system "Allow" dialog rather than merely opening a settings page they'd
     * have to find the right toggle on themselves. Requires
     * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in the manifest (a normal, non-runtime
     * permission) to resolve at all.
     *
     * Falls back to the app's own battery-settings page if the direct-request intent
     * isn't resolvable (an OEM build without it), same "always land somewhere useful"
     * contract as [openLiveUpdateSettings].
     *
     * This is ONLY the standard Android mechanism. At least Samsung layers its OWN,
     * separate "put unused apps to sleep" restriction on top, which this exemption
     * does not touch and which has no known intent to jump straight to -- that is
     * exactly the kind of second, invisible-to-this-app OEM gate [isPromotable]'s own
     * doc already describes for a different feature, so the troubleshooting text
     * spells out the manual Samsung path too rather than claiming this one request
     * covers everything.
     */
    // @SuppressLint("BatteryLife"): lint flags ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS as
    // a Play Content Policy risk. That policy governs Play-distributed apps; Bloo is
    // sideloaded from a GitHub Release, and AutoLock (the only caller) genuinely cannot work
    // from a Doze-suspended process -- its whole job is reacting to leaving the car. The
    // documented flow still falls back to the general battery-optimization settings screen
    // when the direct request is refused (see below), so a user who declines is never stuck.
    @android.annotation.SuppressLint("BatteryLife")
    fun requestBackgroundUnrestricted(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData("package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) return
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(fallback) }
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
        // displayChargeLimit, not targetForCurrentPlug directly, for the same reason
        // every other display of this value now does -- see that function's own doc.
        // This bar only ever exists while charging = true, which normally implies a
        // plug is connected, but the fallback costs nothing and covers the rare case
        // of a status inconsistency between the two fields.
        chargeLimit = ev?.displayChargeLimit(),
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
            data = "bloo://live_charge/$vin".toUri()
            putExtra(AlertActionReceiver.EXTRA_VIN, vin)
            putExtra(AlertActionReceiver.EXTRA_ACTION, CarAction.CHARGE_OFF)
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
            data = "bloo://live_charge_dismissed/$vin".toUri()
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
        // The local check is explicit so lint's MissingPermission analysis
        // sees the grant right next to the notify and stays honest.
        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }
}
