package com.bloo.bluelink.autolock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity

/** Receives Activity Recognition transitions and confirms "walking" to every evaluation
 *  currently waiting on it. Ported from i5-AutoLock's `ActivityTransitionReceiver`. */
class AutoLockActivityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val walkedAway = result.transitionEvents.any {
            it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER &&
                (it.activityType == DetectedActivity.WALKING || it.activityType == DetectedActivity.ON_FOOT)
        }
        if (!walkedAway) return
        // In-process evaluations (the service path) get the state-machine nudge...
        AutoLockController.onWalkingConfirmedAny()
        // ...and any evaluation owned by the alarm fallback (no service running, so the
        // controller has no job for it) is driven directly: mark the persisted record and
        // hand the confirmation to the deadline receiver, which locks now instead of at the
        // deadline. Without this the fallback would always wait out the full grace period.
        val ctx = context.applicationContext
        AutoLockPending.all(ctx).forEach { record ->
            AutoLockPending.markWalkConfirmed(ctx, record.vin)
            ctx.sendBroadcast(
                Intent(ctx, AutoLockAlarmReceiver::class.java)
                    .putExtra(AutoLockAlarmReceiver.EXTRA_VIN, record.vin)
                    .putExtra(AutoLockAlarmReceiver.EXTRA_WALK_CONFIRMED, true),
            )
        }
    }
}
