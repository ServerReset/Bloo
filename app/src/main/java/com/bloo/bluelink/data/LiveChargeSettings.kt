package com.bloo.bluelink.data

import com.bloo.bluelink.ui.tryStart

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/** The system-settings side of live charging -- the Live Update settings, developer options and the background-activity exemption -- as extensions of [LiveCharge], kept out of the object so it stays readable. */

/**
 * Deep-links to the OS page for this app's Live Updates permission --
 * `Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS`
 * (`"android.settings.APP_NOTIFICATION_PROMOTION_SETTINGS"`), which
 * takes the target package via `EXTRA_APP_PACKAGE` (an extra, NOT a
 * `package:` URI -- that's the older per-app-settings convention this
 * action doesn't use). Confirmed against the AOSP `Settings.java`
 * source directly rather than trusting a doc summary, since the wrong
 * extra means the intent resolves to nothing useful.
 *
 * Falls back to the app's general notification settings if the specific
 * page isn't resolvable (an OEM build without it, or a device below the
 * version this action shipped on), so the tap always lands somewhere
 * useful instead of silently doing nothing.
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
 * Deep-links to the OS Developer options screen -- `Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS`.
 *
 * Exists for one specific, confirmed-on-a-real-device reason: at least through One UI 8.5,
 * Samsung gates whether a Live Update actually renders as a chip behind its OWN switch,
 * "Live notifications for all apps" -- found in Developer options, not the generic
 * per-app Live Updates permission [isPromotable] already checks. A device can pass every
 * row of the promotion checklist plus [isPromotable] returning true and still never show
 * a chip because this Samsung-only gate is off, with nothing in the standard Android
 * notification APIs able to see or report that state. There is no known intent extra to
 * jump straight to that one row -- Developer options is a flat, OEM-arranged list -- so
 * this can only land on the screen, not the exact toggle; [SettingsScreen]'s
 * troubleshooting steps tell the user what to look for once there.
 *
 * If Developer options themselves aren't enabled yet, this intent resolves to nothing on
 * most OEM builds rather than opening a blocked screen -- silently, same as the two
 * runCatching calls elsewhere in this file. There is no reliable settings action for "the
 * screen you enable Developer options from" across OEM skins (Samsung places the
 * build-number tap under About phone > Software information, not the stock location), so
 * the troubleshooting text spells out the manual path instead of guessing an intent that
 * might resolve to the wrong screen on a given OEM.
 */
fun LiveCharge.openDeveloperOptions(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.tryStart(intent)
}

/**
 * Whether the OS will currently let Bloo run its background work (AlertWorker's
 * 30-minute poll, and the 5-minute chain this poll kicks off once it finds a car
 * charging) on schedule -- the standard Android "battery optimization" exemption,
 * `PowerManager.isIgnoringBatteryOptimizations`.
 *
 * This is a DIFFERENT question from everything else in this file. The nine-row
 * promotion checklist and [isPromotable] are about whether an ALREADY-POSTED
 * notification can become a status-bar chip; this is about whether the periodic
 * work that would post or update it in the first place gets to run at all while
 * the app isn't open. A car that starts charging with Bloo backgrounded and no
 * exemption can sit for up to the full 30-minute AlertWorker interval -- or
 * longer, since a non-exempt app's periodic work is exactly what Doze defers --
 * before anything notices, which reads as "the live notification isn't
 * triggering," not as a promotion problem. Reported from a real device.
 */
fun LiveCharge.isBackgroundUnrestricted(context: Context): Boolean {
    val pm = context.getSystemService(android.os.PowerManager::class.java) ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/**
 * Requests the standard Android battery-optimization exemption directly --
 * `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which prompts the user
 * with a system "Allow" dialog rather than merely opening a settings page they'd
 * have to find the right toggle on themselves. Requires
 * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` in the manifest (a normal, non-runtime
 * permission) to resolve at all.
 *
 * Falls back to the app's own battery-settings page if the direct-request intent
 * isn't resolvable (an OEM build without it), same "always land somewhere useful"
 * contract as [openLiveUpdateSettings].
 *
 * This is ONLY the standard Android mechanism. At least Samsung layers its OWN,
 * separate "put unused apps to sleep" restriction on top, which this exemption
 * does not touch and which has no known intent to jump straight to -- that is
 * exactly the kind of second, invisible-to-this-app OEM gate [isPromotable]'s own
 * doc already describes for a different feature, so the troubleshooting text
 * spells out the manual Samsung path too rather than claiming this one request
 * covers everything.
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
