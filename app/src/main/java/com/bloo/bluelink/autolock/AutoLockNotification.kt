package com.bloo.bluelink.autolock

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.bloo.bluelink.R
import com.bloo.bluelink.data.ensureNotificationChannel

/** Builds the short-lived foreground notification AutoLock shows while an evaluation is in
 *  flight -- countdown, a "Lock now" shortcut, and a Cancel that aborts (the car reconnect
 *  path cancels the same way). Ported/condensed from i5-AutoLock's `AutoLockNotification`. */
object AutoLockNotification {
    private const val CHANNEL_ID = "bloo_autolock"

    // build() is called on every state transition of a live evaluation -- once a second for
    // however long the grace countdown runs, up to the configured max of 120. Without this
    // flag, ensureNotificationChannel's own existence check is a Binder IPC call to
    // NotificationManagerService, repeated every single one of those ticks, to re-confirm a
    // channel that (after the very first call in this process's life) is certain to already
    // exist -- channels persist across app runs, not just within one. @Volatile: build() can
    // be called from AutoLockService's Main-dispatcher coroutine and, via a "Simulate
    // leaving" test, potentially interleaved calls -- a plain var risked two threads both
    // observing false and both paying for the (harmless but pointless) double IPC once.
    @Volatile private var channelEnsured = false

    private fun ensureChannel(context: Context) {
        if (channelEnsured) return
        ensureNotificationChannel(
            context,
            id = CHANNEL_ID,
            name = "AutoLock",
            importance = NotificationManager.IMPORTANCE_LOW,
            description = "Shows while AutoLock is deciding whether to lock your car",
            showBadge = false,
        )
        channelEnsured = true
    }

    fun notificationId(vin: String): Int = 0x41_0000 or (vin.hashCode() and 0xFFFF)

    // Both PendingIntent factories below are cached: build() fires on every state transition
    // of a live evaluation, including once a second for up to 120 seconds of grace countdown,
    // and every PendingIntent.getService()/getActivity() call is itself a Binder round trip to
    // the system (to register or, with FLAG_UPDATE_CURRENT, look up and refresh an existing
    // one) even though the underlying intent -- same vin, same action, same target component
    // -- is identical every single tick. openAppIntent takes no vin at all, so one instance
    // genuinely serves every car; actionIntent is cached per (vin, action), since those DO
    // vary. Neither cache is ever invalidated: nothing about "how do I open the app" or "run
    // this action for this VIN" changes for the life of the process.
    @Volatile private var cachedOpenAppIntent: PendingIntent? = null
    private val actionIntents = java.util.concurrent.ConcurrentHashMap<String, PendingIntent>()

    fun build(context: Context, vin: String, carName: String, state: DetectionState, graceRemaining: Int): Notification {
        ensureChannel(context)
        val (title, text) = when (state) {
            DetectionState.CONFIRMING -> "Watching $carName" to "Confirming you've left before locking…"
            DetectionState.GRACE -> "Locking $carName in ${graceRemaining}s" to "Tap Cancel if you're coming right back."
            DetectionState.VERIFYING -> "Checking $carName" to "Reading the car's status…"
            DetectionState.LOCKING -> "Locking $carName" to "Sending the lock command…"
            DetectionState.LOCKED -> "$carName locked" to "AutoLock locked it for you."
            DetectionState.SKIPPED -> "AutoLock skipped $carName" to "Nothing to do — see the activity log for why."
            DetectionState.ABORTED -> "AutoLock cancelled" to "$carName wasn't touched."
            DetectionState.ERROR -> "AutoLock error" to "Couldn't finish evaluating $carName."
            else -> "AutoLock" to carName
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(!state.isTerminal)
            .setAutoCancel(state.isTerminal)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent(context))
        if (state == DetectionState.GRACE) {
            builder.addAction(0, "Lock now", actionIntent(context, vin, AutoLockService.ACTION_LOCK_NOW))
        }
        if (!state.isTerminal) {
            builder.addAction(0, "Cancel", actionIntent(context, vin, AutoLockService.ACTION_CANCEL))
        }
        return builder.build()
    }

    private fun openAppIntent(context: Context): PendingIntent =
        cachedOpenAppIntent ?: run {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
            PendingIntent.getActivity(context, 0, intent, flags).also { cachedOpenAppIntent = it }
        }

    private fun actionIntent(context: Context, vin: String, action: String): PendingIntent =
        actionIntents.getOrPut("$vin:$action") {
            val intent = Intent(context, AutoLockService::class.java).apply {
                this.action = action
                putExtra(AutoLockService.EXTRA_VIN, vin)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            PendingIntent.getService(context, notificationId(vin) + action.hashCode(), intent, flags)
        }
}
