package com.bloo.bluelink.autolock

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity

/**
 * Registers Activity Recognition transition updates so a driving -> walking transition can
 * confirm the user actually left the car (a second signal beyond the Bluetooth disconnect).
 * Ported from i5-AutoLock's `ActivityRecognitionManager`. App-wide (not per-VIN): it's one
 * Google Play Services request, not one per car.
 *
 * Reference-counted, unlike the original: [AutoLockController] starts this around EACH
 * evaluation's own confirmation window and stops it in that evaluation's own cleanup, and
 * two cars can each have AutoLock + "confirm with walking" enabled and be mid-evaluation at
 * once. Without a count, car A finishing (or erroring out) first would call the bare
 * stop()/removeActivityTransitionUpdates() and silently kill car B's still-pending
 * confirmation too -- there is exactly one system registration underneath, and an
 * unconditional stop() doesn't know whether anyone else still needs it.
 */
object ActivityRecognitionManager {
    private val activeCount = java.util.concurrent.atomic.AtomicInteger(0)

    private fun pendingIntent(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, 0, Intent(context, AutoLockActivityReceiver::class.java), flags)
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    /** Call once per evaluation that wants confirmation; pair with exactly one [stop] call
     *  from that same evaluation (regardless of how it ends -- success, skip, error, or
     *  cancel) so the count always balances. */
    @SuppressLint("MissingPermission") // Guarded by hasPermission() + runCatching.
    fun start(context: Context) {
        if (!hasPermission(context)) return
        // Only the FIRST concurrent caller actually registers with Play Services; the
        // request is identical every time (same two transitions, same PendingIntent), so a
        // second registration while one is already live would just be redundant work, not a
        // second independent subscription to later balance separately.
        if (activeCount.getAndIncrement() > 0) return
        val transitions = listOf(DetectedActivity.WALKING, DetectedActivity.ON_FOOT).map { type ->
            ActivityTransition.Builder()
                .setActivityType(type)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        }
        runCatching {
            ActivityRecognition.getClient(context)
                .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pendingIntent(context))
        }
    }

    /** See [start] -- only actually tears down the system registration once every caller
     *  that started it has also stopped. */
    @SuppressLint("MissingPermission") // Guarded by hasPermission() + runCatching.
    fun stop(context: Context) {
        if (!hasPermission(context)) return
        // updateAndGet, not decrementAndGet: a stop() with no matching start() (defensive --
        // shouldn't happen given the pairing contract above, but this is reached from
        // several receivers/services) must not walk the count negative and leave every
        // future start() thinking it's a redundant no-op forever.
        val remaining = activeCount.updateAndGet { (it - 1).coerceAtLeast(0) }
        if (remaining > 0) return
        runCatching { ActivityRecognition.getClient(context).removeActivityTransitionUpdates(pendingIntent(context)) }
    }
}
