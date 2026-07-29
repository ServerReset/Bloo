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
        // Defaulted so existing callers are unchanged, but AlertWorker already
        // loads this once per tick and now passes it in — it was being re-read
        // from DataStore once per VEHICLE, per tick, for an identical value.
        prefs: SettingsStore.NotificationPrefs = settings.notificationPrefs(),
    ): List<Alert> {
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
}
