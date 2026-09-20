package com.bloo.bluelink.autolock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.BlueLinkGate
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.CarCommand
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.repositoryFor
import com.bloo.bluelink.data.runCarCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * AutoLock's fallback trigger path, for the case the service-based path cannot handle: the OS
 * refusing a background foreground-service start.
 *
 * THE BUG THIS EXISTS FOR: Android 12+ blocks starting a foreground service from the
 * background except for a short list of exemptions, and a Bluetooth ACL connect/disconnect
 * broadcast is NOT one of them. [AutoLockBluetoothReceiver] fires exactly that way, so on
 * every modern Android the service start was rejected with
 * `ForegroundServiceStartNotAllowedException` -- which the old code caught and only logged.
 * AutoLock therefore never ran at all when the phone was in a pocket, i.e. in every situation
 * it exists for.
 *
 * HOW THIS PATH AVOIDS THE RESTRICTION: it uses no service. The trigger writes an
 * [AutoLockPending] record, posts the same countdown notification the service would have
 * posted (with the same Cancel / Lock now actions), and schedules an EXACT alarm for the
 * grace deadline. Alarms are delivered to a manifest receiver with no background-start
 * restriction, and a broadcast receiver may hold the process for ~10s via `goAsync`, which is
 * plenty for one cached-status read plus one lock command. Cancels and "Lock now" arrive as
 * notification actions -- user interaction, which DOES grant the exemption -- so those still
 * route through the service and its live UI.
 *
 * WALK-AWAY CONFIRMATION IS STILL REQUIRED, matching the service path: [AutoLockActivityReceiver]
 * marks the pending record and locks immediately when the walk transition arrives; the alarm
 * only exists as the deadline. If the deadline arrives with no confirmation, this SKIPS --
 * exactly the rule [AutoLockController] applies on its own timeout.
 */
internal object AutoLockAlarm {

    /** Alarm request code per VIN, in the same 0x43 range discipline as the notifications. */
    private fun requestCode(vin: String): Int = 0x43_0000 or (vin.hashCode() and 0xFFFF)

    private fun pendingIntent(context: Context, vin: String): PendingIntent {
        val intent = Intent(context, AutoLockAlarmReceiver::class.java)
            .putExtra(AutoLockAlarmReceiver.EXTRA_VIN, vin)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode(vin), intent, flags)
    }

    /**
     * Schedules the deadline for [vin]. Prefers an exact alarm (so the countdown the
     * notification promises is honest); on API 31+ an exact alarm needs the
     * SCHEDULE_EXACT_ALARM special access, so a device without it falls back to the inexact
     * `setAndAllowWhileIdle` -- late rather than never, and the notification still says when
     * it will act.
     */
    fun schedule(context: Context, vin: String, delayMs: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val at = System.currentTimeMillis() + delayMs
        val pi = pendingIntent(context, vin)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        runCatching {
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                // No "alarms & reminders" access: still schedule, just without the exactness
                // guarantee. Logged so a late lock is explicable rather than mysterious.
                AppLog.log("AutoLock: exact alarms not permitted — deadline may be delayed.")
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }.onFailure { AppLog.log("⚠ AutoLock: couldn't schedule the lock deadline: ${it.message}") }
    }

    fun cancel(context: Context, vin: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching { manager.cancel(pendingIntent(context, vin)) }
    }

    /** Milliseconds a service-path evaluation can take before its own alarm deadline should
     *  fire: the walk-confirmation window plus the grace countdown plus a margin for the
     *  status read and the command. Only used as a safety net for the service path -- the
     *  controller cancels this alarm the moment it reaches any terminal state. */
    fun servicePathDeadlineMs(graceSeconds: Int): Long =
        AutoLockController.CONFIRM_TIMEOUT_MS + graceSeconds * 1000L + 20_000L
}

/**
 * Receives the [AutoLockAlarm] deadline (and the "walk-away confirmed" moment, forwarded here
 * by [AutoLockActivityReceiver]) and performs the lock without any service.
 */
class AutoLockAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val vin = intent.getStringExtra(EXTRA_VIN) ?: return
        val confirmed = intent.getBooleanExtra(EXTRA_WALK_CONFIRMED, false)
        val pending = goAsync()
        val ctx = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                evaluate(ctx, vin, confirmed)
            } catch (t: Throwable) {
                AppLog.log("⚠ AutoLock (deadline path): ${t.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun evaluate(context: Context, vin: String, walkConfirmed: Boolean) {
        android.util.Log.i("AutoLockAlarm", "evaluate vin=$vin walkConfirmed=$walkConfirmed")
        val record = AutoLockPending.get(context, vin) ?: run {
            android.util.Log.i("AutoLockAlarm", "no pending record for $vin — nothing to do")
            return
        }
        if (walkConfirmed) AutoLockPending.markWalkConfirmed(context, vin)

        val cfg = runCatching { SettingsStore(context).autoLockConfig(vin) }.getOrNull() ?: run {
            android.util.Log.w("AutoLockAlarm", "no config for $vin")
            return
        }
        android.util.Log.i("AutoLockAlarm", "config: enabled=${cfg.enabled} dryRun=${cfg.dryRun} grace=${cfg.graceSeconds}")
        if (!cfg.enabled) {
            AutoLockPending.clear(context, vin)
            return
        }
        val name = record.carName ?: "your car"

        // Same mandatory walk-away confirmation the service path enforces: no confirmation
        // means the user may still be sitting in the car (a dropped Bluetooth link alone is
        // not proof they left), so skip rather than lock.
        val confirmedNow = walkConfirmed || AutoLockPending.get(context, vin)?.walkConfirmed == true
        if (!confirmedNow) {
            android.util.Log.i("AutoLockAlarm", "no walk confirmation — skipping (faithful to the service path)")
            AppLog.log("AutoLock: no walk-away confirmation for $name — not locking (deadline path).")
            AutoLockPending.clear(context, vin)
            AutoLockAlarm.cancel(context, vin)
            return
        }

        val snapshot = runCatching { SnapshotStore(context).current() }.getOrNull()
        val vehicle = snapshot?.vehicles?.firstOrNull { it.vin == vin }?.toVehicle()
        if (vehicle == null) {
            AppLog.log("AutoLock: $vin is no longer in the garage — nothing to lock.")
            AutoLockPending.clear(context, vin)
            return
        }

        // Cached status only (refresh = false), matching AlertWorker and the service path:
        // never wake the car for the pre-lock check.
        val status = runCatching {
            withTimeoutOrNull(30_000) {
                BlueLinkGate.statusMutex.withLock {
                    repositoryFor(
                        Brand.fromIndicator(vehicle.brandIndicator),
                        SessionStore(context),
                        CredentialStore(context),
                    ).status(vehicle, refresh = false)
                }
            }
        }.getOrNull()
        if (status == null) {
            AppLog.log("AutoLock: couldn't read ${vehicle.name}'s status — not locking.")
            AutoLockPending.clear(context, vin)
            return
        }

        when (val decision = LockPolicy.decide(status)) {
            is LockDecision.Skip -> {
                android.util.Log.i("AutoLockAlarm", "policy skip: ${decision.reason}")
                AppLog.log("AutoLock: skipped ${vehicle.name} — ${decision.reason}.")
            }
            LockDecision.Lock -> {
                if (record.dryRun) {
                    android.util.Log.i("AutoLockAlarm", "DRY RUN: posting the would-have-locked notification")
                    AppLog.log("AutoLock DRY RUN: would have locked ${vehicle.name}. (No command sent.)")
                    AutoLockEventNotifier.notifyLocked(context, vin, vehicle.name, dryRun = true)
                } else {
                    val result = runCatching {
                        runCarCommand(context, CarCommand(vin, CarAction.LOCK))
                    }.getOrNull()
                    if (result?.ok == true) {
                        android.util.Log.i("AutoLockAlarm", "locked ${vehicle.name}")
                        AppLog.log("AutoLock: car locked automatically (${vehicle.name}).")
                        AutoLockEventNotifier.notifyLocked(context, vin, vehicle.name, dryRun = false)
                    } else {
                        AppLog.log("⚠ AutoLock: lock command failed for ${vehicle.name}${result?.message?.let { " — $it" } ?: ""}.")
                    }
                }
            }
        }
        AutoLockPending.clear(context, vin)
        AutoLockAlarm.cancel(context, vin)
        // The in-flight progress notification belongs to the service path; clear it in case
        // this deadline raced a service that never got to finish.
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(context)
                .cancel(AutoLockNotification.notificationId(vin))
        }
    }

    companion object {
        const val EXTRA_VIN = "vin"
        const val EXTRA_WALK_CONFIRMED = "walk_confirmed"
    }
}
