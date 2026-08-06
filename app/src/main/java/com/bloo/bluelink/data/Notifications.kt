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

/**
 * The live, self-updating charging notification.
 *
 * Unlike [CarAlerts], which fires one-shot alerts when a condition first
 * becomes true, this is an ONGOING notification that exists for exactly as
 * long as the car is charging and rewrites itself in place as the percentage
 * climbs -- a progress bar in the shade rather than a stream of alerts. See
 * [update] for the write path and [ChargingPollWorker] for what keeps it
 * moving while the app isn't in the foreground.
 *
 * ## What "live" means here, precisely
 *
 * Two DIFFERENT things both get called "live" and are easy to conflate:
 *
 * 1. **Self-updating.** The notification rewrites itself in place as the
 *    number changes, rather than posting a new one each time. This works on
 *    every supported OS version (minSdk 26) and needs nothing from the
 *    system beyond an ordinary ongoing notification -- [update] is called
 *    on a timer ([ChargingPollWorker]) and just calls `notify()` again with
 *    the same [idFor] id, which Android always treats as "replace the
 *    existing one" rather than "post a new one".
 * 2. **Promoted to a Live Update chip.** Android 16 (API 36)+ can lift an
 *    ongoing notification with the right shape into the status bar and
 *    lock screen as its own chip (Google's "Now Bar" on some OEM skins),
 *    the same visual language as a running timer or a rideshare tracker.
 *    This is what most people mean by "live update" and what the in-app
 *    copy promises ("A progress bar in the shade **and on the lock
 *    screen**"). It is entirely a system decision made at post time, is
 *    all-or-nothing (there is no partial credit, no API to ask "why
 *    wasn't I promoted"), and below API 36 it simply never happens -- the
 *    exact same builder just renders as an ordinary progress notification
 *    with no chip, which is (1) working correctly, not (2) failing.
 *
 * If you're checking whether this feature "works", (1) is verifiable from
 * this code alone (post it, watch the percentage change). (2) additionally
 * needs an Android 16+ device and is where the checklist below matters.
 *
 * ## The promotion checklist (Android 16+ only)
 *
 * Google documents nine conditions for `hasPromotableCharacteristics()` to
 * return true (developer.android.com, "Create live update notifications").
 * Each one this file controls is listed with exactly where it's satisfied,
 * so a future change that accidentally breaks one is traceable back here:
 *
 *  | # | Condition                              | Where                                          |
 *  |---|-----------------------------------------|-------------------------------------------------|
 *  | 1 | A promotable style (`ProgressStyle`)    | [update], both the `percent != null` branch and the no-percent fallback -- see that method's own doc for why BOTH branches need it |
 *  | 2 | `setOngoing(true)`                      | [update]                                       |
 *  | 3 | A non-blank `contentTitle`               | [update] -- always `"$carName is charging"`    |
 *  | 4 | `setRequestPromotedOngoing(true)`        | [update]                                       |
 *  | 5 | `POST_PROMOTED_NOTIFICATIONS` manifest permission | the app manifest -- install-time, not a runtime prompt, nothing to request in code |
 *  | 6 | No custom `RemoteViews`                  | never set here                                 |
 *  | 7 | Not a group summary                      | never set here                                 |
 *  | 8 | Not `setColorized(true)`                 | never set here                                 |
 *  | 9 | Channel importance above `IMPORTANCE_MIN` | [ensureChannel] -- `IMPORTANCE_LOW`, which is one step above the floor |
 *
 * A 10th condition exists but is **not controllable from code at all**:
 * whether the user has the per-app system "Live Updates" toggle switched
 * on. The docs state outright that `hasPromotableCharacteristics()` does
 * NOT factor this in -- a notification can satisfy every row above and
 * still render as an ordinary notification, silently, with nothing in
 * Logcat and nothing this app can query. [openLiveUpdateSettings] exists
 * because this is the one lever actually left once the table above is
 * green, and it is the user's lever, not this code's.
 *
 * ## How to tell it's actually promoting, on a real Android 16+ device
 *
 * Start a charge (or fake `charging = true` in a debug build) and watch
 * for a chip in the status bar / lock screen, not just a notification in
 * the shade. If the shade notification appears correctly (title, bar,
 * Stop button, updates as the percentage climbs) but no chip ever shows:
 * the *notification* is working (requirement 1 above, "self-updating") and
 * the fault is specifically in *promotion* (requirement 2). Check, in
 * order: (a) the device is genuinely running API 36+ (`Build.VERSION.
 * SDK_INT`, logged or breakpointed -- promotion is silently skipped below
 * it, by design, not a bug); (b) the per-app Live Updates system toggle,
 * via [openLiveUpdateSettings] from Settings > Notifications in-app; (c)
 * only then suspect the builder itself, and re-check the table above
 * against whatever changed.
 *
 * ## Why it lives on its own channel
 *
 * IMPORTANCE_LOW so it never makes a sound or peeks -- a notification that
 * reposts on every poll would be intolerable on the default channel. LOW
 * also satisfies condition 9 above (it has to clear IMPORTANCE_MIN, which
 * LOW does with one full step to spare) while staying quiet.
 *
 * Below API 36 the exact same builder degrades by itself: `ProgressStyle`
 * renders as an ordinary determinate progress bar, and
 * `setRequestPromotedOngoing` is silently ignored. One code path, not two.
 */
object ChargingLive {
    // Bumped from "bloo_charging" to "bloo_charging_v2". Channel PROPERTIES
    // are immutable from the app's side once created -- ensureChannel below
    // only ever calls createNotificationChannel when no channel with this id
    // exists yet, and Android does not let an app change an existing
    // channel's importance afterwards, only the user can, in system
    // settings. This channel has existed under the old id since the very
    // first version of this feature, through many rebuilds in one long
    // session; if it was ever created at the wrong importance by an earlier
    // iteration, or the user silenced/downgraded it while testing any of
    // those iterations, every later fix to the CODE would have been talking
    // to a channel the system had already locked in. A fresh id guarantees
    // today's code is what actually creates it, with nothing earlier able to
    // have left it in a state this file can't detect or repair. If this was
    // never actually the problem, the new id costs nothing -- it just means
    // charging notifications land on a differently-named channel from here on.
    private const val CHANNEL = "bloo_charging_v2"
    private const val ACCENT = 0xFF7B83EB.toInt()
    /** Filled portion of the Live Update bar: the same green the app and
     *  widget already use for charging, so the chip matches the car card. */
    private const val CHARGE_GREEN = BlooColors.chargeGreen
    /** The not-yet-charged remainder. Deliberately dim rather than empty --
     *  a zero-width remaining segment at 100% is filtered out below. */
    private const val TRACK = 0x40FFFFFF
    /** The limit marker on the bar. Light, so it reads against both the green
     *  fill it sits on below the limit and the dim track above it. */
    private const val LIMIT_POINT = 0xFFFFFFFF.toInt()

    /** One stable notification id per car, distinct from the alert ids so a
     *  charging notification never overwrites a door/service alert. */
    private fun idFor(vin: String) = ("charging" + vin).hashCode()

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Charging", NotificationManager.IMPORTANCE_LOW)
                        .apply {
                            description = "A live progress notification while your car is charging"
                            setShowBadge(false)
                        },
                )
            }
        }
    }

    /**
     * Posts, updates or clears the charging notification for one car.
     *
     * Safe and cheap to call on every poll: when the car isn't charging (or
     * the feature is off, or permission is missing) it just cancels any
     * notification already showing, so charging ending always tidies up
     * rather than leaving a stale bar pinned in the shade.
     *
     * [percent] and [minutesToFull] are both optional because a car can
     * report that it is charging before it reports either -- see the
     * `percent == null` branch below for how that's handled without giving
     * up promotion (condition 1 of [ChargingLive]'s own class-level
     * checklist), which is exactly the branch that used to give it up.
     */
    fun update(
        context: Context,
        vin: String,
        carName: String,
        charging: Boolean,
        percent: Int?,
        minutesToFull: Int?,
        pluggedInLabel: String?,
        enabled: Boolean,
        /** The car's charge limit for the plug it's on, if it reported one.
         *  Drawn as the seam between "will charge" and "won't", matching the
         *  app hero's own segmented bar. */
        chargeLimit: Int? = null,
    ) {
        val id = idFor(vin)
        if (!enabled || !charging || !Notifications.hasPermission(context)) {
            runCatching { NotificationManagerCompat.from(context).cancel(id) }
            return
        }
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = launch?.let {
            PendingIntent.getActivity(
                context, id, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        // "82% · to 80% · 1h 20m left · Plugged in (AC)" -- built from
        // whichever pieces the car actually reported, joined so a missing one
        // leaves no stray separator behind.
        //
        // On the history here, because it matters for the next person who
        // touches this builder: setProgressPoints (the limit marker, below)
        // was once removed from it on the theory that it was costing the
        // promotion, since it was the only change between a build confirmed
        // working on a real device and one confirmed demoted. Removing it did
        // NOT restore the promotion, so the theory was wrong and the point is
        // back -- see ChargingLive's own class doc for the checklist this
        // builder is actually held to, and what the real fault turned out to
        // be (the no-percent branch below, at the time).
        val limit = chargeLimit?.takeIf { it in 1..99 }
        val detail = listOfNotNull(
            percent?.let { "$it%" },
            // Only worth saying while it's still ahead of the car: once
            // charging has reached the limit the number is the same number.
            limit?.takeIf { percent == null || percent < it }?.let { "to $it%" },
            minutesToFull?.takeIf { it > 0 }?.let { "${fmtMinutes(it)} left" },
            pluggedInLabel?.takeIf { it.isNotBlank() && !it.startsWith("Not ") },
        ).joinToString(" · ")

        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setColor(ACCENT)
            .setContentTitle("$carName is charging")
            .setContentText(detail.ifBlank { "Charging" })
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Ongoing so it can't be swiped away while charging continues, and
            // alert-once so rewriting it every poll stays silent.
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .apply { pi?.let { setContentIntent(it) } }

        if (percent != null) {
            // ProgressStyle is what makes this a promotable Live Update. The
            // bar is drawn as one filled segment plus one remaining segment,
            // rather than setProgress's plain track, so the charged portion
            // carries the accent and the rest reads as headroom.
            //
            // TWO segments, split at the CHARGE: green is what's in the pack,
            // the track is what isn't. The limit is a POINT on that bar, set
            // below -- not a third segment and not a second division. Every
            // other surface draws this value the same way now (the phone hero,
            // the widget's bar and ring, the watch ring): fill to the charge,
            // track for the rest, a dot at the limit.
            val style = NotificationCompat.ProgressStyle()
                .setStyledByProgress(false)
                .setProgress(percent)
                .setProgressSegments(
                    // Built conditionally rather than filtered afterwards:
                    // a zero-length segment is meaningless, and at 0% or
                    // 100% one of these is exactly that.
                    buildList {
                        if (percent > 0) {
                            add(NotificationCompat.ProgressStyle.Segment(percent).setColor(CHARGE_GREEN))
                        }
                        if (percent < 100) {
                            add(NotificationCompat.ProgressStyle.Segment(100 - percent).setColor(TRACK))
                        }
                    },
                )
            // The limit as a point ON the bar, which is exactly what it is on
            // the phone hero, the widget's bar and ring, and the watch: the
            // fill is the charge, the point is the target.
            if (limit != null) {
                style.setProgressPoints(
                    listOf(NotificationCompat.ProgressStyle.Point(limit).setColor(LIMIT_POINT)),
                )
            }
            builder.setStyle(style)
            // The compact text on the status-bar chip, where there is room for
            // a couple of glyphs and nothing more.
            builder.setShortCriticalText("$percent%")
        } else {
            // Charging confirmed but no percentage reported yet.
            //
            // This used to fall back to the legacy setProgress(0, 0, true)
            // indeterminate bar -- honest about not having a number, but it
            // meant the notification posted with NO ProgressStyle at all
            // for however long the car takes to first report a percentage
            // (sometimes indefinitely, on a status response missing the
            // field). "A promotable style" is one of the documented,
            // non-negotiable conditions for promotion -- setRequestPromoted
            // Ongoing below was being called on a builder that had already
            // failed that condition, so the notification could sit un-
            // promoted through its entire first stretch of updates even
            // with everything else about the builder correct. A single
            // all-track segment (no green fill, since there's nothing
            // charged to report yet) keeps ProgressStyle in play from the
            // very first post instead of only once a percentage shows up.
            builder.setStyle(
                NotificationCompat.ProgressStyle()
                    .setStyledByProgress(false)
                    .setProgress(0)
                    .setProgressSegments(listOf(NotificationCompat.ProgressStyle.Segment(100).setColor(TRACK))),
            )
        }
        // Asks the system to promote this to a Live Update. Ignored below API
        // 36 and whenever any of the documented conditions isn't met, so it is
        // safe to request unconditionally.
        builder.setRequestPromotedOngoing(true)

        // Stop-charging is offered inline, since the whole point of a live
        // notification is acting without opening the app.
        val stopIntent = Intent(context, AlertActionReceiver::class.java).apply {
            action = AlertActionReceiver.ACTION_RUN
            data = Uri.parse("bloo://charging/$vin/stop")
            putExtra(AlertActionReceiver.EXTRA_VIN, vin)
            // CHARGE_OFF, not TOGGLE_CHARGE: if the car finished or was
            // unplugged between the poll that drew this notification and
            // the tap, a toggle would START charging again.
            putExtra(AlertActionReceiver.EXTRA_ACTION, WearAction.CHARGE_OFF)
            putExtra(AlertActionReceiver.EXTRA_NOTIF_ID, id)
            putExtra(AlertActionReceiver.EXTRA_LABEL, "Stop")
        }
        builder.addAction(
            0, "Stop charging",
            PendingIntent.getBroadcast(
                context, id, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }

    /**
     * Opens this app's system notification settings.
     *
     * Android decides at post time whether an ongoing notification is promoted
     * to a Live Update, reports nothing back that an app can read, and -- per
     * the documentation -- ignores its own promotability check entirely if the
     * user has Live Updates switched off for the app. So when the bar posts as
     * an ordinary notification despite the builder being correct, the only
     * remaining lever is the user's, and the app's job is to hand them the
     * door rather than to keep guessing at the builder.
     */
    fun openLiveUpdateSettings(context: Context) {
        // The CHANNEL's own settings page, not the app's general notification
        // list. The "Charging" channel is one row among however many this app
        // has, and a Live Updates toggle (where the OS exposes one) lives on
        // the CHANNEL's page, not the app's -- landing the user on the app's
        // whole list makes them go find and tap the right row themselves.
        // Falls back to the app-level page (previous behaviour) if the
        // channel-specific action isn't available, and from there to bare
        // app-details, same as before.
        val channelIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CHANNEL)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val appIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(channelIntent) }
            .recoverCatching { context.startActivity(appIntent) }
            .onFailure {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }
    }

    /** Clears every car's charging notification -- used when the feature is
     *  switched off in settings, so an already-posted bar disappears at once
     *  instead of lingering until the next poll. */
    fun cancelAll(context: Context, vins: List<String>) {
        val mgr = NotificationManagerCompat.from(context)
        vins.forEach { vin -> runCatching { mgr.cancel(idFor(vin)) } }
    }
}
