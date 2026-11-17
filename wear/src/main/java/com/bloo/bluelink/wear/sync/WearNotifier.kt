package com.bloo.bluelink.wear

import com.bloo.bluelink.data.clearsAfterAWhile
import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.ensureNotificationChannel
import com.bloo.bluelink.data.privatePrefs
import com.bloo.bluelink.ioScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/** Which watch notifications the user wants. Plain prefs: nothing here is secret. */
class WearNotificationPrefs(context: Context) {
    private val prefs = context.privatePrefs("bloo_watch_notifications")

    /** Take the phone's choices (it is the source of truth; see [WatchNotifyPrefs]). */
    fun apply(p: com.bloo.bluelink.data.WatchNotifyPrefs) {
        prefs.edit {
            putBoolean("charging", p.charging); putBoolean("charge_complete", p.chargeComplete)
            putBoolean("low_battery", p.lowBattery); putBoolean("auto_lock", p.autoLock)
        }
    }

    fun current() = com.bloo.bluelink.data.WatchNotifyPrefs(charging, chargeComplete, lowBattery, autoLock)

    /** Tell the phone about a change made here, so both sides stay the same. */
    fun sendToPhone(context: Context) {
        val bytes = Json.encodeToString(com.bloo.bluelink.data.WatchNotifyPrefs.serializer(), current()).encodeToByteArray()
        val app = context.applicationContext
        ioScope().launch {
            runCatching {
                com.google.android.gms.wearable.Wearable.getNodeClient(app).connectedNodes.await().forEach {
                    com.google.android.gms.wearable.Wearable.getMessageClient(app)
                        .sendMessage(it.id, com.bloo.bluelink.data.WatchSyncProtocol.PATH_NOTIF_PREFS, bytes).await()
                }
            }
        }
    }

    var charging: Boolean
        get() = prefs.getBoolean("charging", true)
        set(v) = prefs.edit { putBoolean("charging", v) }
    var chargeComplete: Boolean
        get() = prefs.getBoolean("charge_complete", true)
        set(v) = prefs.edit { putBoolean("charge_complete", v) }
    var lowBattery: Boolean
        get() = prefs.getBoolean("low_battery", true)
        set(v) = prefs.edit { putBoolean("low_battery", v) }

    /** Alerts for what Auto lock did (on by default, like the phone's, and one switch with it). */
    var autoLock: Boolean
        get() = prefs.getBoolean("auto_lock", true)
        set(v) = prefs.edit { putBoolean("auto_lock", v) }
}

/**
 * The watch's own notifications, raised whenever a car's state changes on the watch -- whether the
 * change came from the phone's push or from the watch's own refresh -- so they appear even with the
 * phone out of range. - an ongoing "Charging" card with a progress bar and a Stop action, while a
 * car charges; - "Charged" when a charge finishes near full; - "Low battery" when a car drops under
 * [WEAR_LOW_PERCENT] and isn't charging.
 */
object WearNotifier {
    private const val CHANNEL_CHARGING = "watch_charging"
    private const val CHANNEL_ALERTS = "watch_alerts"
    private const val CHANNEL_LIVE = "watch_live"

    /**
     * Compare [old] with [new] and raise/clear whatever changed. [old] empty means first sight: no
     * alerts.
     */
    fun onVehicles(context: Context, old: List<VehicleSnapshot>, new: List<VehicleSnapshot>) {
        val app = context.applicationContext
        // The Tile shows the same cars, so it refreshes whenever they change.
        if (old != new) runCatching { androidx.wear.tiles.TileService.getUpdater(app).requestUpdate(BlooTileService::class.java) }
        if (!canPost(app)) return
        val prefs = WearNotificationPrefs(app)
        ensureChannels(app)
        val manager = app.getSystemService(NotificationManager::class.java)
        new.forEach { now ->
            val before = old.firstOrNull { it.vin == now.vin }
            val plan = planWearAlerts(before, now, prefs.current())
            val pct = now.percent
            val cardId = idFor(now.vin, "charging")
            if (plan.chargingCard) manager.notify(cardId, chargingCard(app, now)) else manager.cancel(cardId)
            // Climate running is a live activity too, with its own Stop, so the car's climate is visible and can be ended from the watch.
            val climateId = idFor(now.vin, "climate")
            if (now.climateOn == true) manager.notify(climateId, climateCard(app, now)) else manager.cancel(climateId)
            if (plan.chargeComplete) {
                manager.notify(idFor(now.vin, "complete"), alert(app, "${now.name} is charged", "Battery at $pct%"))
            }
            if (plan.lowBattery) {
                manager.notify(idFor(now.vin, "low"), alert(app, "${now.name} battery is low", "$pct% left" + (now.rangeMi?.let { " · $it mi" } ?: "")))
            }
        }
    }

    /**
     * A notification id per (car, kind).
     *
     * These used to be `vin.hashCode()`, `+ 1` and `+ 2` -- ids that OVERLAP across cars: one car's
     * "charged" alert lands on the same id as another car's charging card whenever the two hashes
     * happen to be adjacent, so one silently replaces the other. Hashing the pair makes a collision as
     * unlikely as it is for any single key, rather than near-certain for adjacent ones.
     */
    private fun idFor(vin: String, kind: String): Int = (vin + kind).hashCode()

    /**
     * The live charging card: an ongoing notification paired with an ongoing activity, so the car's charge shows on the watch face and in
     * Recents while it charges, not only in the shade. The activity's status carries the percentage, and the card is rebuilt (and so
     * re-applied) each time the car's state changes; stopping the charge cancels it.
     */
    private fun chargingCard(app: Context, v: VehicleSnapshot): Notification {
        val touch = openApp(app)
        val builder = NotificationCompat.Builder(app, CHANNEL_CHARGING)
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setContentTitle("${v.name} charging")
            .setContentText(listOfNotNull(v.percent?.let { "$it%" }, v.rangeMi?.let { "$it mi" }).joinToString(" · "))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(touch)
            .apply {
                v.percent?.let { setProgress(100, it.coerceIn(0, 100), false) }
                addAction(0, "Stop", actionIntent(app, v.vin, CarAction.CHARGE_OFF))
            }
        val status = Status.Builder()
            .addTemplate("#what# #level#")
            .addPart("what", Status.TextPart("Charging"))
            .addPart("level", Status.TextPart(v.percent?.let { "$it%" } ?: ""))
            .build()
        OngoingActivity.Builder(app, idFor(v.vin, "charging"), builder)
            .setStaticIcon(R.drawable.ic_stat_bloo)
            .setTouchIntent(touch)
            .setStatus(status)
            .build()
            .apply(app)
        return builder.build()
    }

    /**
     * The live climate card: an ongoing notification and activity while the car's climate runs, with a Stop. Same shape as the charging card.
     */
    private fun climateCard(app: Context, v: VehicleSnapshot): Notification {
        val touch = openApp(app)
        val builder = NotificationCompat.Builder(app, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setContentTitle("${v.name} climate on")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(touch)
            .addAction(0, "Stop", actionIntent(app, v.vin, CarAction.CLIMATE_OFF))
        val status = Status.Builder()
            .addTemplate("#what#")
            .addPart("what", Status.TextPart("Climate on"))
            .build()
        OngoingActivity.Builder(app, idFor(v.vin, "climate"), builder)
            .setStaticIcon(R.drawable.ic_stat_bloo)
            .setTouchIntent(touch)
            .setStatus(status)
            .build()
            .apply(app)
        return builder.build()
    }

    /**
     * A one-shot alert. It takes no action button, and no longer takes an unused `action` parameter
     * either: both call sites passed null, so the button it could have drawn never appeared.
     */
    /**
     * What the watch's own Auto lock did, as a lasting alert: it locked the car, asked the phone to, or could not. [ok] true is
     * done here, null is handed to the phone, false is a failure. Off only if the wearer turned the Auto lock alerts off.
     */
    fun autoLockResult(context: Context, vin: String, carName: String, ok: Boolean?) {
        val app = context.applicationContext
        if (!canPost(app) || !WearNotificationPrefs(app).autoLock) return
        ensureChannels(app)
        val (title, text) = when (ok) {
            true -> "Locked $carName" to "Auto lock locked it after you walked away."
            null -> "Locking $carName" to "Auto lock asked your phone to lock it."
            false -> "Couldn't lock $carName" to "Auto lock tried and it did not go through."
        }
        app.getSystemService(NotificationManager::class.java).notify(idFor(vin, "autolock"), alert(app, title, text))
    }

    private fun alert(app: Context, title: String, text: String) =
        NotificationCompat.Builder(app, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openApp(app))
            .clearsAfterAWhile()
            .build()

    private fun openApp(app: Context): PendingIntent = PendingIntent.getActivity(
        // No activity flags: the system launches a notification's content intent in the app's own task (WearRecents).
        app, 0, Intent(app, WearMainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(app: Context, vin: String, action: String): PendingIntent = PendingIntent.getBroadcast(
        app, (vin + action).hashCode(),
        Intent(app, WearActionReceiver::class.java).putExtra(WearActionReceiver.EXTRA_VIN, vin).putExtra(WearActionReceiver.EXTRA_ACTION, action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannels(app: Context) {
        ensureNotificationChannel(app, CHANNEL_CHARGING, "Charging", NotificationManager.IMPORTANCE_LOW, "Live charge progress", showBadge = false)
        ensureNotificationChannel(app, CHANNEL_LIVE, "Live car status", NotificationManager.IMPORTANCE_LOW, "Climate and other running states", showBadge = false)
        ensureNotificationChannel(app, CHANNEL_ALERTS, "Car alerts", NotificationManager.IMPORTANCE_DEFAULT, "Charge complete, low battery and Auto lock")
    }

    /** Notifications need a runtime grant from Android 13 (watches included). */
    fun canPost(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

/**
 * Runs a car action chosen from a watch notification, then lets the normal update path refresh the
 * card.
 */
class WearActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val vin = intent.getStringExtra(EXTRA_VIN) ?: return
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val pending = goAsync()
        ioScope().launch {
            try {
                runWatchCommand(context, vin, action)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_VIN = com.bloo.bluelink.data.IntentExtras.VIN
        const val EXTRA_ACTION = com.bloo.bluelink.data.IntentExtras.ACTION
    }
}
