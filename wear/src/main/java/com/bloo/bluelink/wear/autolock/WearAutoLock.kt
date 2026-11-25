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
import com.bloo.bluelink.data.privatePrefs

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
    private const val KEY_LAST_RUN = "last_run_elapsed"

    /** The countdown notification's Cancel. */
    internal const val ACTION_CANCEL = "com.bloo.bluelink.wear.AUTOLOCK_CANCEL"

    /**
     * After an auto lock has run, further motion is ignored this long. Significant motion fires again and again on a walk, and each
     * trigger used to judge the cars and lock again: a walk could send a run of lock commands against the brand's daily limit.
     */
    internal const val COOLDOWN_MS = 10L * 60_000L

    private fun prefs(context: Context) = context.privatePrefs(PREFS)

    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
        if (enabled) arm(context) else disarm(context)
    }

    /**
     * The cars a trigger would lock: everything the watch knows that is not already locked and whose
     * engine is not running. Pure, so it is unit-tested without a watch.
     */
    fun targets(vehicles: List<VehicleSnapshot>): List<String> = com.bloo.bluelink.autolock.LockPolicy.targets(vehicles)

    /** Whether a trigger at [nowElapsed] falls inside the cooldown after a run at [lastRunElapsed] (0 = never). Pure, for tests. */
    fun inCooldown(lastRunElapsed: Long, nowElapsed: Long): Boolean =
        lastRunElapsed > 0L && nowElapsed >= lastRunElapsed && nowElapsed - lastRunElapsed < COOLDOWN_MS

    /** Records that an auto lock has just run, which starts the cooldown. */
    internal fun markRun(context: Context) {
        prefs(context).edit { putLong(KEY_LAST_RUN, SystemClock.elapsedRealtime()) }
    }

    /** The person cancelled the countdown: the pending lock is dropped, and the cooldown starts so the same walk does not re-arm it. */
    internal fun cancelPending(context: Context) {
        val app = context.applicationContext
        runCatching { (app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)?.cancel(pending(app)) }
        markRun(app)
        WearNotifier.clearAutoLockCountdown(app)
    }

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
        // Cancel any registration already held BEFORE adding one, because arm() runs on every app
        // open (WearMainActivity.startWork) as well as on enable and after a reboot. Re-arming used to
        // overwrite the `listener` field and leave the old registration live: two triggers per motion,
        // then four, and each one schedules its own grace alarm -- so a single walk-away could fire
        // several locks. Cancelling is a no-op once a trigger has fired, which is the common case.
        listener?.let { runCatching { mgr.cancelTriggerSensor(it, armedSensor) } }
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
        val now = SystemClock.elapsedRealtime()
        if (inCooldown(prefs(context).getLong(KEY_LAST_RUN, 0L), now)) return
        val at = now + GRACE_MS
        runCatching { mgr.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, pending(context)) }
        WearNotifier.autoLockCountdown(context, (GRACE_MS / 1000).toInt())
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
