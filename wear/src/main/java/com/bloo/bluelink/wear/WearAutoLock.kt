package com.bloo.bluelink.wear

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.os.SystemClock
import androidx.core.content.edit
import com.bloo.bluelink.data.VehicleSnapshot

/**
 * The watch's own AutoLock -- **beta**.
 *
 * The phone's AutoLock keys off the car's paired Bluetooth head unit, which the watch cannot see at
 * all. The watch, though, is ON the person: it can feel them set off. So this uses the hardware
 * significant-motion TRIGGER sensor -- the chip wakes the app for a single event and then sleeps, so
 * there is no polling and no wakelock, and the feature costs nothing while the wearer is still --
 * then waits [GRACE_MS] and, if the watch is signed in to a car whose engine is off and which is not
 * already locked, locks it.
 *
 * It is deliberately conservative and deliberately optional: off by default, tagged beta in the UI,
 * and it never locks a car that reports its engine running. A false trigger is harmless in the worst
 * case (locking an already-parked car); a missed one is just the phone's AutoLock's job instead.
 */
object WearAutoLock {

    /** It is a beta: the watch UI labels the toggle, and it defaults off. */
    const val BETA = true

    /** A lock half a minute late is fine, so the alarm is inexact and needs no special permission. */
    internal const val GRACE_MS = 45_000L

    private const val PREFS = "bloo_wear_autolock"
    private const val KEY_ENABLED = "enabled"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
        if (enabled) arm(context) else disarm(context)
    }

    /**
     * The cars a trigger would lock: everything the watch knows that is not already locked and whose
     * engine is not running. Pure, so it is unit-tested without a watch.
     */
    fun targets(vehicles: List<VehicleSnapshot>): List<String> =
        vehicles.filter { it.locked != true && it.engineOn != true }.map { it.vin }

    // Held so the registration is not collected while armed; the sensor is one-shot, so this is a
    // single lightweight object, not a live stream.
    private var listener: TriggerEventListener? = null
    private var armedSensor: Sensor? = null

    /**
     * (Re)register the one-shot significant-motion trigger. Safe to call any time; does nothing when
     * the feature is off or the hardware has no such sensor.
     */
    fun arm(context: Context) {
        if (!isEnabled(context)) return
        val app = context.applicationContext
        val mgr = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sig = mgr.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION) ?: return
        val l = object : TriggerEventListener() {
            override fun onTrigger(event: TriggerEvent?) {
                // Trigger sensors are one-shot: re-arm first, then start the grace countdown.
                runCatching { mgr.requestTriggerSensor(this, sig) }
                scheduleGrace(app)
            }
        }
        listener = l
        armedSensor = sig
        runCatching { mgr.requestTriggerSensor(l, sig) }
    }

    /** Cancel the trigger and any pending grace alarm. */
    fun disarm(context: Context) {
        val app = context.applicationContext
        val mgr = app.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        listener?.let { runCatching { mgr?.cancelTriggerSensor(it, armedSensor) } }
        listener = null
        armedSensor = null
        runCatching { (app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(pending(app)) }
    }

    /** Called after a restart: the trigger registration does not survive a reboot. */
    fun rearmIfEnabled(context: Context) {
        if (isEnabled(context)) arm(context)
    }

    private fun scheduleGrace(context: Context) {
        val mgr = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = SystemClock.elapsedRealtime() + GRACE_MS
        runCatching { mgr.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(context)) }
    }

    internal fun pending(context: Context): PendingIntent {
        val app = context.applicationContext
        return PendingIntent.getBroadcast(
            app,
            0,
            Intent(app, WearAutoLockReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
