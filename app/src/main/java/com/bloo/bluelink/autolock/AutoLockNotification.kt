package com.bloo.bluelink.autolock

import com.bloo.bluelink.data.ChannelOnce
import com.bloo.bluelink.data.clearsAfterAWhile
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
    /** The low-importance watcher/evaluation channel. Settings can open this channel so the
     *  user can hide its shade entry while keeping AutoLock's background watcher alive. */
    internal const val CHANNEL_ID = "bloo_autolock"

    private val channel = ChannelOnce()

    private fun ensureChannel(context: Context) = channel.ensure {
        ensureNotificationChannel(
            context,
            id = CHANNEL_ID,
            name = "AutoLock",
            importance = NotificationManager.IMPORTANCE_LOW,
            description = "Shows while AutoLock is deciding whether to lock your car",
            showBadge = false,
        )
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
    private val openAppIntents = java.util.concurrent.ConcurrentHashMap<String, PendingIntent>()
    private val actionIntents = java.util.concurrent.ConcurrentHashMap<String, PendingIntent>()

    fun build(context: Context, vin: String, carName: String, state: DetectionState, graceRemaining: Int): Notification {
        ensureChannel(context)
        val (title, text) = when (state) {
            DetectionState.CONFIRMING -> "Watching $carName" to "Confirming you've left before locking…"
            DetectionState.GRACE -> "Locking $carName in ${graceRemaining}s" to "Tap Cancel if you're coming right back."
            DetectionState.VERIFYING -> "Checking $carName" to "Reading the car's status…"
            DetectionState.LOCKING -> "Locking $carName" to "Sending the lock command…"
            DetectionState.LOCKED -> "$carName locked" to "AutoLock locked it for you."
            DetectionState.SKIPPED -> "AutoLock skipped $carName" to "Nothing to do. See the activity log for why."
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
            .setContentIntent(openAppIntent(context, vin))
        if (state == DetectionState.GRACE) {
            builder.addAction(0, "Lock now", actionIntent(context, vin, AutoLockService.ACTION_LOCK_NOW))
        }
        if (!state.isTerminal) builder.addAction(0, "Cancel", actionIntent(context, vin, AutoLockService.ACTION_CANCEL))
        // The steps are ongoing and replaced as they go; the end of one (locked, skipped, cancelled, error) is a notice that clears itself.
        if (state.isTerminal) builder.clearsAfterAWhile()
        return builder.build()
    }

    // The car's own card (the tap a lock or unlock notification opens), not a bare launch of the app.
    private fun openAppIntent(context: Context, vin: String): PendingIntent =
        openAppIntents.getOrPut(vin) { com.bloo.bluelink.Shortcuts.openCardPendingIntent(context, vin, requestCode = notificationId(vin)) }

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
