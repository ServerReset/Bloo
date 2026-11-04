package com.bloo.bluelink.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires when a scheduled command's alarm goes off. It holds the broadcast open (goAsync) until the command has run, so the system does
 * not put the process to sleep half way through the car call.
 */
class ScheduledCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val vin = intent.getStringExtra(ScheduledCommands.EXTRA_VIN) ?: return
        val cmd = intent.getStringExtra(ScheduledCommands.EXTRA_CMD) ?: return
        val target = intent.getStringExtra(ScheduledCommands.EXTRA_TARGET) ?: "default"
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ScheduledCommands.execute(context, vin, cmd, target)
            } finally {
                pending.finish()
            }
        }
    }
}
