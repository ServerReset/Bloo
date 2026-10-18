package com.bloo.bluelink.wear

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bloo.bluelink.data.BootGate

/**
 * The watch's half of "sealed after a restart" (see [BootGate]). After the watch reboots, nothing
 * runs and nothing is shown until the watch PIN has been entered -- the watch has no account form
 * or fingerprint, so the PIN the phone mirrored is its sign-in. With no PIN set there is nothing to
 * unseal with, so the watch opens itself rather than lock the user out.
 */
object WearBootGate {
    /** True while this boot is sealed and a watch PIN exists to open it. */
    fun isLocked(context: Context): Boolean {
        if (!BootGate.isLocked(context)) return false
        if (!WatchPinStore(context).hasPin) {
            BootGate.markOpen(context)
            return false
        }
        return true
    }

    /** The PIN was entered: this boot is open. */
    fun open(context: Context) {
        BootGate.markOpen(context)
        WearBootNotice.cancel(context)
    }
}

/** The one notification allowed while the watch is sealed: it asks for the PIN. */
object WearBootNotice {
    private const val CHANNEL = "bloo_watch_reauth"
    private const val ID = 4108

    fun post(context: Context) {
        val app = context.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Enter PIN after restart", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            app, 0,
            // No activity flags: the system launches a notification's content intent in the app's own
            // task, and Wear's own lint flags NEW_TASK|CLEAR_TOP here (WearRecents).
            Intent(app, WearMainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bloo)
            .setContentTitle("Bloo is locked")
            .setContentText("Your watch restarted. Open Bloo and enter your PIN.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(ID, n) }
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(ID) }
    }
}

/** After a reboot, posts the PIN reminder when the watch is sealed. */
class WearBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (WearBootGate.isLocked(context)) WearBootNotice.post(context)
        // The AutoLock trigger registration does not survive a reboot.
        // Not while sealed: nothing runs until the PIN is entered (startWork arms it then).
        if (!WearBootGate.isLocked(context)) WearAutoLock.rearmIfEnabled(context)
    }
}
