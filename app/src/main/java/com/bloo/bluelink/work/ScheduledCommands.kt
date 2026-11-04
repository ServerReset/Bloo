package com.bloo.bluelink.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.VehicleCommandRunner
import com.bloo.bluelink.rethrowIfCancellation
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Timed car commands ("climate off at 9 p.m."), each set as an exact alarm for its minute. A later request for the same car and command
 * replaces the earlier one, so only one of each is ever pending. The alarm fires [ScheduledCommandReceiver], which runs the command the
 * same way a search tap does.
 *
 * Exact alarms need the SCHEDULE_EXACT_ALARM permission on Android 12 and later, which the person grants in system settings. Without it
 * the alarm still fires, but Android may hold it for a while; [schedule] says which.
 */
object ScheduledCommands {
    const val EXTRA_VIN = "vin"
    const val EXTRA_CMD = "cmd"
    const val EXTRA_TARGET = "target"

    /**
     * Asks for [cmd] on the car [vin] at [minuteOfDay] (0 to 1439) today, or tomorrow when that minute has passed. Returns true when the
     * alarm is exact, false when the permission is missing and the alarm is only approximate.
     */
    fun schedule(context: Context, vin: String, cmd: String, climateTarget: String, minuteOfDay: Int): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val at = nextEpochMillis(minuteOfDay)
        val intent = Intent(context, ScheduledCommandReceiver::class.java)
            .putExtra(EXTRA_VIN, vin)
            .putExtra(EXTRA_CMD, cmd)
            .putExtra(EXTRA_TARGET, climateTarget)
        // One PendingIntent per car and command: setting it again replaces the earlier alarm.
        val pending = PendingIntent.getBroadcast(
            context, requestCode(vin, cmd), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
        if (exact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
        remember(context, vin, cmd, climateTarget, minuteOfDay)
        AppLog.log("Scheduled $cmd for $vin at ${java.time.Instant.ofEpochMilli(at)}${if (exact) "" else " (approximate: exact alarms not allowed)"}")
        return exact
    }

    private const val PREFS = "bloo_scheduled_commands"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Keeps a pending command across a reboot: an alarm does not survive one. Stored as "vin|cmd" -> "target|minute". */
    private fun remember(context: Context, vin: String, cmd: String, target: String, minuteOfDay: Int) {
        prefs(context).edit().putString("$vin|$cmd", "$target|$minuteOfDay").apply()
    }

    /** Called once a command has fired: it is no longer pending. */
    fun forget(context: Context, vin: String, cmd: String) {
        prefs(context).edit().remove("$vin|$cmd").apply()
    }

    /** After a reboot: sets the alarm again for every command still pending, at its next occurrence of the same minute. */
    fun rearmAll(context: Context) {
        prefs(context).all.forEach { (key, value) ->
            val vin = key.substringBefore('|')
            val cmd = key.substringAfter('|')
            val text = value as? String ?: return@forEach
            val target = text.substringBeforeLast('|')
            val minute = text.substringAfterLast('|').toIntOrNull() ?: return@forEach
            schedule(context, vin, cmd, target, minute)
        }
    }

    /** The next occurrence of [minuteOfDay] on the phone's clock, in epoch milliseconds. */
    internal fun nextEpochMillis(minuteOfDay: Int, now: LocalDateTime = LocalDateTime.now()): Long {
        var at = now.toLocalDate().atTime(minuteOfDay / 60, minuteOfDay % 60)
        if (!at.isAfter(now)) at = at.plusDays(1)
        return at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun requestCode(vin: String, cmd: String): Int = (vin + "|" + cmd).hashCode()

    /** Runs a command that came due, and logs what happened. The alarm receiver awaits it before it lets the system sleep. */
    suspend fun execute(context: Context, vin: String, cmd: String, target: String) {
        val outcome = try {
            VehicleCommandRunner.run(context.applicationContext, vin, cmd, target).message
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            "failed: ${e.message}"
        }
        AppLog.log("Scheduled $cmd for $vin: $outcome")
    }
}
