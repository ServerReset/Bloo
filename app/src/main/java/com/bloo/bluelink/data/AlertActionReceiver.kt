package com.bloo.bluelink.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles an action button tapped on a Bloo alert notification (from the phone
 * shade or bridged to the watch). Runs the remote command with the stored
 * session via [WearCommandRunner], dismisses the alert, and posts a short
 * follow-up so the user sees whether it worked — important for "Lock" when a
 * door is still physically open and the car refuses to lock.
 */
class AlertActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN) return
        val vin = intent.getStringExtra(EXTRA_VIN) ?: return
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "Command"
        val ctx = context.applicationContext

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Clear the original alert immediately so the tap feels responsive.
                if (notifId != -1) runCatching { NotificationManagerCompat.from(ctx).cancel(notifId) }
                val result = runCatching { WearCommandRunner.execute(ctx, WearCommand(vin, action)) }
                    .getOrNull()
                val ok = result?.ok == true
                val title = if (ok) "$label sent" else "$label failed"
                val text = when {
                    ok -> "Bloo sent the command to your car."
                    result?.message != null -> result.message
                    else -> "Couldn't reach the car. Try again from the app."
                }
                // Reuse the same id so the follow-up replaces the (now-cancelled) alert.
                if (notifId != -1) Notifications.post(ctx, notifId, title, text)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_RUN = "com.bloo.bluelink.ALERT_ACTION"
        const val EXTRA_VIN = "vin"
        const val EXTRA_ACTION = "action"
        const val EXTRA_NOTIF_ID = "notif_id"
        const val EXTRA_LABEL = "label"
    }
}
