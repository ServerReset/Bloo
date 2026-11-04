package com.bloo.bluelink.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** After the phone restarts, sets again the timed commands that were still pending (alarms do not survive a reboot). */
class ScheduledCommandBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        ScheduledCommands.rearmAll(context)
    }
}
