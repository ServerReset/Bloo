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
        if (walkedAway) AutoLockController.onWalkingConfirmedAny()
    }
}
