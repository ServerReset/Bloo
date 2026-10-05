package com.bloo.bluelink.data

import com.bloo.bluelink.ui.tryStart

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/**
 * The system-settings side of live charging -- the Live Update settings, developer options and the
 * background-activity exemption -- as extensions of [LiveCharge], kept out of the object so it
 * stays readable.
 */

/**
 * Deep-links to the OS page for this app's Live Updates permission --
 * `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`
 * (`"android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"`), which takes the target package via
 * `EXTRA_APP_PACKAGE` (an extra, NOT a `package:` URI -- that's the older per-app-settings
 * convention this action doesn't use).
 */
fun LiveCharge.openLiveUpdateSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= 36) {
        val intent = Intent("android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS").apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (context.tryStart(intent)) return
    }
    // Fallback: the app's general notification settings page (works on any OS version).
    val fallback = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.tryStart(fallback)
}

/**
 * Deep-links to the OS Developer options screen --
 * `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS`.
 */
fun LiveCharge.openDeveloperOptions(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.tryStart(intent)
}

/**
 * Whether the OS will currently let Bloo run its background work (AlertWorker's 30-minute poll, and
 * the 5-minute chain this poll kicks off once it finds a car charging) on schedule -- the standard
 * Android "battery optimization" exemption, `PowerManager.isIgnoringBatteryOptimizations`.
 */
fun LiveCharge.isBackgroundUnrestricted(context: Context): Boolean {
    val pm = context.getSystemService(android.os.PowerManager::class.java) ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Requests the standard Android battery-optimization exemption directly --
 * `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which prompts the user with a system
 * "Allow" dialog rather than merely opening a settings page they'd have to find the right toggle on
 * themselves.
 */
// @SuppressLint("BatteryLife"): lint flags ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS as
// a Play Content Policy risk. That policy governs Play-distributed apps; Bloo is
// sideloaded from a GitHub Release, and AutoLock (the only caller) genuinely cannot work
// from a Doze-suspended process -- its whole job is reacting to leaving the car. The
// documented flow still falls back to the general battery-optimization settings screen
// when the direct request is refused (see below), so a user who declines is never stuck.
@android.annotation.SuppressLint("BatteryLife")
fun LiveCharge.requestBackgroundUnrestricted(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData("package:${context.packageName}".toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (context.tryStart(intent)) return
    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.tryStart(fallback)
}
