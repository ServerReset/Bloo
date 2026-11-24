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
 * Target the component explicitly (`-n`) - a bare `-a` broadcast is implicit and does NOT reach a
 * manifest receiver in a backgrounded app on modern Android:
 *
 *   adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd garage
 *   adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd full
 *   adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd lock
 *   adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd off
 *   adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd light
 *   adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd dark
 *   adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd openmap   (the running app, now)
 *   adb shell am broadcast -n com.bloo.bluelink/.DebugFixturesReceiver --es cmd fullscreen   (the running app, now)
 *
 * The flags are persisted, so the effect lands on the next cold start rather than needing the app
 * to already be running.
 */
class DebugFixturesReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: "garage"
        when (cmd) {
            "garage" -> DebugSampleGarage.set(context, garage = true, lock = false, full = false)
            // Two cars on the map, for checking the app's own look and motion (never a store capture: it has locations).
            "full" -> DebugSampleGarage.set(context, garage = true, lock = false, full = true)
            "lock" -> DebugSampleGarage.set(context, garage = true, lock = true)
            "unlock" -> DebugSampleGarage.set(context, garage = true, lock = false)
            "off" -> DebugSampleGarage.set(context, garage = false, lock = false)
            // Display mode for the captures. The system night mode does not move this app, so the
            // theme has to be written through its own store.
            "light" -> DebugSampleGarage.setTheme(context, "light")
            "dark" -> DebugSampleGarage.setTheme(context, "dark")
            // The real-account test (.github/realtest.sh): the details come in as extras, from CI secrets, and are never logged.
            "testlogin", "testreauth" -> {
                // Base64, so a password's quotes, spaces or dollar signs survive the shell that adb runs the broadcast through.
                fun extra(name: String) = intent.getStringExtra(name)?.let {
                    String(android.util.Base64.decode(it, android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
                }.orEmpty()
                val user = extra("user64")
                val password = extra("password64")
                val pin = extra("pin64")
                val brand = runCatching { com.bloo.bluelink.data.Brand.valueOf(intent.getStringExtra("brand") ?: "HYUNDAI") }
                    .getOrDefault(com.bloo.bluelink.data.Brand.HYUNDAI)
                com.bloo.bluelink.ui.DebugAccountTest.commands.tryEmit(
                    if (cmd == "testlogin") com.bloo.bluelink.ui.DebugAccountCommand.Login(user, password, pin, brand)
                    else com.bloo.bluelink.ui.DebugAccountCommand.Reauth(user, password, pin, brand),
                )
                return
            }
            "testapplock" -> {
                com.bloo.bluelink.ui.DebugAccountTest.commands.tryEmit(com.bloo.bluelink.ui.DebugAccountCommand.AppLockOn)
                return
            }
            "testonboarded" -> {
                com.bloo.bluelink.ui.DebugAccountTest.commands.tryEmit(com.bloo.bluelink.ui.DebugAccountCommand.FinishOnboarding)
                return
            }
            "testautolockon", "testautolockoff" -> {
                com.bloo.bluelink.ui.DebugAccountTest.commands.tryEmit(com.bloo.bluelink.ui.DebugAccountCommand.AutoLock(cmd == "testautolockon"))
                return
            }
            "testlockcar" -> {
                com.bloo.bluelink.ui.DebugAccountTest.commands.tryEmit(com.bloo.bluelink.ui.DebugAccountCommand.LockCar)
                return
            }
            "fullscreen" -> {
                DebugSampleGarage.fullScreenRequests.tryEmit(Unit)
                AppLog.log("DebugFixturesReceiver: cmd=fullscreen")
                return
            }
            "openmap" -> {
                DebugSampleGarage.openMapRequests.tryEmit(Unit)
                AppLog.log("DebugFixturesReceiver: cmd=openmap")
                return
            }
            else -> {
                AppLog.log("DebugFixturesReceiver: unknown cmd '$cmd'")
                return
            }
        }
        AppLog.log("DebugFixturesReceiver: cmd=$cmd -> sample garage=${DebugSampleGarage.enabled(context)} lock=${DebugSampleGarage.locked(context)}")
    }
}
