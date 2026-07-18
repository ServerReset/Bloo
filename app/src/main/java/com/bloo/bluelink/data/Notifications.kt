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

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

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

    suspend fun evaluate(settings: SettingsStore, v: Vehicle, status: VehicleStatus?): List<Alert> {
        val prefs = settings.notificationPrefs()
        val out = mutableListOf<Alert>()

        if (prefs.service) {
            val odo = v.odometer?.replace(",", "")?.trim()?.toDoubleOrNull()?.toInt()
            val last = settings.lastServiceMiles(v.vin)
            val interval = settings.serviceIntervalMiles(v.vin)
            val due = if (last != null && interval != null) last + interval else null
            val key = "service_${v.vin}"
            if (due != null && odo != null && odo >= due) {
                if (!settings.alertFired(key)) {
                    out += Alert(serviceId(v), "${v.name} is due for service", "Odometer $odo mi is past the $due mi service interval.")
                    settings.setAlertFired(key, true)
                }
            } else {
                settings.setAlertFired(key, false)
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
                val since = settings.doorOpenSince(v.vin)
                if (since == null) {
                    settings.setDoorOpenSince(v.vin, now)
                } else if (now - since > prefs.doorOpenMinutes * 60_000L && !settings.alertFired(key)) {
                    out += Alert(
                        doorId(v),
                        "${v.name} door is open",
                        "A door/trunk/hood has been open for over ${prefs.doorOpenMinutes} min.",
                        actions = listOf(Notifications.Action("Lock", v.vin, WearAction.LOCK)),
                    )
                    settings.setAlertFired(key, true)
                }
            } else {
                settings.setDoorOpenSince(v.vin, null)
                settings.setAlertFired(key, false)
            }
        }

        if (prefs.running && status != null) {
            // Remote start / climate (and on supported cars, the engine) report as "on".
            val on = status.engine == true || status.airCtrlOn == true
            val key = "running_${v.vin}"
            val now = System.currentTimeMillis()
            if (on) {
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
                settings.setEngineOnSince(v.vin, null)
                settings.setAlertFired(key, false)
            }
        }
        return out
    }

    private fun serviceId(v: Vehicle) = ("svc" + v.vin).hashCode()
    private fun doorId(v: Vehicle) = ("door" + v.vin).hashCode()
    private fun runningId(v: Vehicle) = ("run" + v.vin).hashCode()
}
