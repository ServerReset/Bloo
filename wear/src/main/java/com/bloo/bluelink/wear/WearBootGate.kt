package com.bloo.bluelink.wear

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bloo.bluelink.data.cancelNotice
import com.bloo.bluelink.data.postSealedNotice
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
    private const val ID = 4108

    fun post(context: Context) {
        val app = context.applicationContext
        postSealedNotice(
            app,
            channelId = "bloo_watch_reauth",
            channelName = "Enter PIN after restart",
            id = ID,
            smallIcon = R.drawable.ic_stat_bloo,
            title = "Bloo is locked",
            text = "Your watch restarted. Open Bloo and enter your PIN.",
            contentIntent = PendingIntent.getActivity(
                app, 0,
                // No activity flags: the system launches a notification's content intent in the app's own
                // task, and Wear's own lint flags NEW_TASK|CLEAR_TOP here (WearRecents).
                Intent(app, WearMainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

    fun cancel(context: Context) = cancelNotice(context, ID)
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
