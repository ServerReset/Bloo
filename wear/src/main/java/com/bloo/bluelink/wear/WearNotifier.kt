package com.bloo.bluelink.wear

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.ensureNotificationChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Which watch notifications the user wants. Plain prefs: nothing here is secret. */
class WearNotificationPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bloo_watch_notifications", Context.MODE_PRIVATE)

    /** Take the phone's choices (it is the source of truth; see [WatchNotifyPrefs]). */
    fun apply(p: com.bloo.bluelink.data.WatchNotifyPrefs) {
        prefs.edit { putBoolean("charging", p.charging); putBoolean("charge_complete", p.chargeComplete); putBoolean("low_battery", p.lowBattery) }
    }

    fun current() = com.bloo.bluelink.data.WatchNotifyPrefs(charging, chargeComplete, lowBattery)

    /** Tell the phone about a change made here, so both sides stay the same. */
    fun sendToPhone(context: Context) {
        val bytes = kotlinx.serialization.json.Json.encodeToString(com.bloo.bluelink.data.WatchNotifyPrefs.serializer(), current()).encodeToByteArray()
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
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
            val id = now.vin.hashCode()
            if (plan.chargingCard) manager.notify(id, chargingCard(app, now)) else manager.cancel(id)
            if (plan.chargeComplete) {
                manager.notify(id + 1, alert(app, now, "${now.name} is charged", "Battery at $pct%", null))
            }
            if (plan.lowBattery) {
                manager.notify(id + 2, alert(app, now, "${now.name} battery is low", "$pct% left" + (now.rangeMi?.let { " · $it mi" } ?: ""), null))
            }
        }
    }

    private fun chargingCard(app: Context, v: VehicleSnapshot) =
        NotificationCompat.Builder(app, CHANNEL_CHARGING)
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setContentTitle("${v.name} charging")
            .setContentText(listOfNotNull(v.percent?.let { "$it%" }, v.rangeMi?.let { "$it mi" }).joinToString(" · "))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openApp(app))
            .apply { v.percent?.let { setProgress(100, it.coerceIn(0, 100), false) } }
            .addAction(0, "Stop", actionIntent(app, v.vin, CarAction.CHARGE_OFF))
            .build()

    private fun alert(app: Context, v: VehicleSnapshot, title: String, text: String, action: String?) =
        NotificationCompat.Builder(app, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openApp(app))
            .apply { action?.let { addAction(0, "Do it", actionIntent(app, v.vin, it)) } }
            .build()

    private fun openApp(app: Context): PendingIntent = PendingIntent.getActivity(
        app, 0, Intent(app, WearMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(app: Context, vin: String, action: String): PendingIntent = PendingIntent.getBroadcast(
        app, (vin + action).hashCode(),
        Intent(app, WearActionReceiver::class.java).putExtra(WearActionReceiver.EXTRA_VIN, vin).putExtra(WearActionReceiver.EXTRA_ACTION, action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannels(app: Context) {
        ensureNotificationChannel(app, CHANNEL_CHARGING, "Charging", NotificationManager.IMPORTANCE_LOW, "Live charge progress", showBadge = false)
        ensureNotificationChannel(app, CHANNEL_ALERTS, "Car alerts", NotificationManager.IMPORTANCE_DEFAULT, "Charge complete and low battery")
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
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (WearCredentialSync.runLocally(context, vin, action) == null) {
                    WearDataLayerSync.sendCommand(context, vin, action)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_VIN = "vin"
        const val EXTRA_ACTION = "action"
    }
}
