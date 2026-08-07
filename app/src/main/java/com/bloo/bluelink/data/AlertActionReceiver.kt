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

    /**
     * Entry point invoked when the user taps an action button on a Bloo alert
     * notification. Runs in order:
     * 1. Ignore anything that isn't our own [ACTION_RUN] intent (defensive --
     *    this receiver shouldn't be reachable any other way, but `onReceive`
     *    is a public entry point so it's still checked) and bail if any of the
     *    required extras (VIN, action id) are missing.
     * 2. Call [goAsync] to tell the system this BroadcastReceiver needs to keep
     *    running past the synchronous return of `onReceive` -- without it,
     *    Android is free to consider the receiver finished (and kill the
     *    process) before the coroutine below, which does real network I/O, gets
     *    a chance to complete.
     * 3. Launch a coroutine on [Dispatchers.IO] (a fresh, receiver-scoped
     *    CoroutineScope, since a BroadcastReceiver has no lifecycle scope of its
     *    own to hang a coroutine off of) that:
     *    a. Cancels the original alert notification right away, before the
     *       network call even starts, purely so tapping the button feels
     *       instantaneous rather than waiting on network round-trip latency.
     *    b. Runs the actual remote command via [WearCommandRunner.execute],
     *       wrapped in [runCatching] so a thrown exception becomes a null
     *       result rather than crashing this coroutine.
     *    c. Posts a short follow-up notification reusing the *same* notification
     *       id as the original alert, so it visually replaces the (already
     *       cancelled) alert rather than stacking a second notification. Success
     *       text is generic; failure text prefers the command's own error
     *       message when present, falling back to a generic one otherwise.
     * 4. Always calls `pending.finish()` in a `finally`, regardless of success or
     *    failure, to release the goAsync hold and let the system reclaim the
     *    receiver -- forgetting this would eventually trigger an ANR-style
     *    ("didn't call finish()") ill effect.
     */
    override fun onReceive(context: Context, intent: Intent) {
        // The live charging bar's deleteIntent: the user swiped it away, so record that
        // and stop reposting it for this charging session. Handled before the ACTION_RUN
        // guard below because it carries no command -- see
        // SettingsStore.liveChargeDismissed for why this is persisted rather than kept
        // in memory, and LiveCharge.sync for where it's read and cleared.
        if (intent.action == ACTION_LIVE_CHARGE_DISMISSED) {
            val vin = intent.getStringExtra(EXTRA_VIN) ?: return
            val ctx = context.applicationContext
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    runCatching { SettingsStore(ctx).setLiveChargeDismissed(vin, true) }
                } finally {
                    pending.finish()
                }
            }
            return
        }
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
                val text = if (ok) {
                    "Bloo sent the command to your car."
                } else {
                    result?.message ?: "Couldn't reach the car. Try again from the app."
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

        /** Fired by the live charging bar's own deleteIntent when the user dismisses it. */
        const val ACTION_LIVE_CHARGE_DISMISSED = "com.bloo.bluelink.LIVE_CHARGE_DISMISSED"
        const val EXTRA_VIN = "vin"
        const val EXTRA_ACTION = "action"
        const val EXTRA_NOTIF_ID = "notif_id"
        const val EXTRA_LABEL = "label"
    }
}
