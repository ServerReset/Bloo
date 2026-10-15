package com.bloo.bluelink

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.ui.DebugSampleGarage

/**
 * DEBUG-ONLY switch for the sample garage (lives in the debug source set, so it is absent from a
 * release APK). See [DebugSampleGarage] for why the state has to be fabricated rather than signed
 * into.
 *
 * Target the component explicitly (`-n`) — a bare `-a` broadcast is implicit and does NOT reach a
 * manifest receiver in a backgrounded app on modern Android:
 *
 *   adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd garage
 *   adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd lock
 *   adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd off
 *
 * The flags are persisted, so the effect lands on the next cold start rather than needing the app
 * to already be running.
 */
class DebugFixturesReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: "garage"
        when (cmd) {
            "garage" -> DebugSampleGarage.set(context, garage = true, lock = false)
            "lock" -> DebugSampleGarage.set(context, garage = true, lock = true)
            "unlock" -> DebugSampleGarage.set(context, garage = true, lock = false)
            "off" -> DebugSampleGarage.set(context, garage = false, lock = false)
            else -> {
                AppLog.log("DebugFixturesReceiver: unknown cmd '$cmd'")
                return
            }
        }
        AppLog.log("DebugFixturesReceiver: cmd=$cmd -> sample garage=${DebugSampleGarage.enabled(context)} lock=${DebugSampleGarage.locked(context)}")
    }
}
