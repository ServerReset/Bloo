package com.bloo.wear.complication

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bloo.bluelink.data.WearCommand
import com.bloo.wear.WearComms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles a tap on the Lock / Climate complication: relays the toggle command to
 * the phone (or runs it standalone) via [WearComms.send], which applies an
 * optimistic snapshot update, then asks the complications to re-read so the
 * icon/label flips to the new state immediately.
 */
class ComplicationTapReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TAP) return
        val vin = intent.getStringExtra(EXTRA_VIN) ?: return
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val ctx = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                // goAsync()'s extended process lifetime is ~10s and not guaranteed
                // beyond that. WearComms.send can chain up to 10s on a phone-relay
                // attempt and then, on failure, an unbounded standalone network call
                // to the car API — easily exceeding that budget on a degraded
                // connection, which risks the process being reclaimed before
                // finish() runs (no refresh, tap silently does nothing). Capping the
                // whole attempt keeps this receiver predictable: finish() always runs
                // within budget and the complications still get a best-effort refresh
                // request even if the underlying command didn't finish in time.
                withTimeoutOrNull(9_000) {
                    runCatching { WearComms.send(ctx, WearCommand(vin, action)) }
                }
                ComplicationLink.requestUpdate(ctx)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TAP = "com.bloo.wear.COMPLICATION_TAP"
        const val EXTRA_VIN = "vin"
        const val EXTRA_ACTION = "action"

        fun pendingIntent(context: Context, vin: String, action: String): PendingIntent {
            val intent = Intent(context, ComplicationTapReceiver::class.java).apply {
                this.action = ACTION_TAP
                // Unique per (vin, action) so PendingIntents don't collapse into one.
                data = Uri.parse("bloo://comp/$vin/$action")
                putExtra(EXTRA_VIN, vin)
                putExtra(EXTRA_ACTION, action)
            }
            return PendingIntent.getBroadcast(
                context, (vin + action).hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
