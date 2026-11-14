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
 * Ported from i5-AutoLock's `ActivityRecognitionManager`. App-wide (not per-VIN): only one
 * evaluation actually needs it live at a time in practice, and [AutoLockController] starts/
 * stops it around each evaluation's confirmation window.
 */
object ActivityRecognitionManager {
    private fun pendingIntent(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, 0, Intent(context, AutoLockActivityReceiver::class.java), flags)
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Guarded by hasPermission() + runCatching.
    fun start(context: Context) {
        if (!hasPermission(context)) return
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

    @SuppressLint("MissingPermission") // Guarded by hasPermission() + runCatching.
    fun stop(context: Context) {
        if (!hasPermission(context)) return
        runCatching { ActivityRecognition.getClient(context).removeActivityTransitionUpdates(pendingIntent(context)) }
    }
}
