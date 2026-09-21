package com.bloo.bluelink.autolock

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.SnapshotStore

/**
 * What happens when a car's Bluetooth link goes away (and when it comes back), shared by the
 * manifest [AutoLockBluetoothReceiver] and the debug-only
 * [com.bloo.bluelink.autolock.AutoLockDebugReceiver].
 *
 * This is a separate object, not the receiver's own private helper, for a specific reason:
 * the debug trigger used to re-implement this sequence by hand, and it hard-coded the alarm
 * deadline it THOUGHT the real path used (the grace period) while the real path armed the
 * fallback at 0ms and never locked at all. The two drifted, and the only test path anyone
 * could run was the one that worked. One implementation, called by both, removes the
 * possibility.
 */
internal object AutoLockTrigger {

    /**
     * A car's Bluetooth just disconnected: start an evaluation.
     *
     * [settings] is passed in rather than read here so the debug trigger can supply its own
     * dry-run/short-grace config while still exercising exactly this code.
     */
    suspend fun onCarDisconnected(
        context: Context,
        vin: String,
        settings: AutoLockConfig,
        log: Boolean = true,
        forceFallback: Boolean = false,
    ) {
        val ctx = context.applicationContext
        val graceSeconds = settings.graceSeconds
        val started = !forceFallback && AutoLockService.start(ctx, vin)
        if (!started) {
            // No service, so this receiver owns the whole countdown. The record has to be
            // persisted because the walk-away confirmation and the deadline can land in a
            // later process.
            val carName = SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin }?.name
            AutoLockPending.begin(
                ctx,
                AutoLockPending.Record(
                    vin = vin,
                    carName = carName,
                    deadlineMs = System.currentTimeMillis() +
                        AutoLockAlarm.alarmPathDeadlineMs(graceSeconds),
                    dryRun = settings.dryRun,
                ),
            )
            // Same mandatory walk-away confirmation the service path waits for; the
            // transition is delivered to AutoLockActivityReceiver, which forwards it to the
            // alarm receiver for an immediate lock.
            ActivityRecognitionManager.start(ctx)
            // The countdown the user can actually see and cancel. Its Cancel / Lock now
            // actions are user interaction, which DOES permit the service start, so they keep
            // working here.
            runCatching {
                NotificationManagerCompat.from(ctx).notify(
                    AutoLockNotification.notificationId(vin),
                    AutoLockNotification.build(
                        ctx, vin, carName ?: "your car",
                        DetectionState.GRACE, graceSeconds,
                    ),
                )
            }
        }
        if (log) {
            AppLog.log(
                "AutoLock: car Bluetooth disconnected for $vin — " +
                    if (started) "evaluating." else "using the alarm fallback.",
            )
        }
        // Always arm the deadline alarm, whichever path runs: with the service it is a safety
        // net (the controller cancels it on any outcome, so it only ever fires if the process
        // died mid-way); without it, the alarm IS the mechanism.
        //
        // ARMED AFTER the record write above, and at a real deadline -- never zero. It used to
        // run first and use 0ms for the fallback: the deadline fired instantly, found no
        // walk-away confirmation yet and took the "may still be in the car" skip branch,
        // clearing the record before the confirmation could arrive, so the alarm path never
        // locked at all.
        AutoLockAlarm.schedule(
            ctx,
            vin,
            if (started) AutoLockAlarm.servicePathDeadlineMs(graceSeconds)
            else AutoLockAlarm.alarmPathDeadlineMs(graceSeconds),
        )
    }

    /** The car reconnected (the user got back in): abort whatever is pending for it. */
    fun onCarConnected(context: Context, vin: String) {
        AutoLockController.cancel(context.applicationContext, vin)
    }

    /**
     * The walk-away confirmation arrived for [vin]: mark its persisted record and hand the
     * confirmation to the deadline receiver, which locks now instead of waiting for the
     * deadline. Shared with [AutoLockActivityReceiver] and the debug trigger for the same
     * "one implementation" reason as [onCarDisconnected].
     */
    fun onWalkConfirmed(context: Context, vin: String) {
        val ctx = context.applicationContext
        AutoLockPending.markWalkConfirmed(ctx, vin)
        ctx.sendBroadcast(
            Intent(ctx, AutoLockAlarmReceiver::class.java)
                .putExtra(AutoLockAlarmReceiver.EXTRA_VIN, vin)
                .putExtra(AutoLockAlarmReceiver.EXTRA_WALK_CONFIRMED, true),
        )
    }
}
