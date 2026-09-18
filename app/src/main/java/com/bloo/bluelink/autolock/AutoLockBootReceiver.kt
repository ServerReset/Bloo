package com.bloo.bluelink.autolock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bloo.bluelink.MainActivity

/**
 * Relaunches [MainActivity] once an in-place app update finishes installing. Bloo self-
 * updates from GitHub releases (it isn't on the Play Store), and BOTH install paths (the
 * Shizuku silent-install session and the tap-through system installer) end with this
 * process gone -- a replace-install force-stops the process the instant the swap lands,
 * and there was previously nothing to bring the app back afterward, so the user was simply
 * dropped on whatever the OS fell through to (home screen, or the keyguard if the screen
 * had locked mid-install). ACTION_MY_PACKAGE_REPLACED is the one signal that fires reliably
 * exactly once the swap actually completes, regardless of which install path did it or
 * whether this process survived to see it -- and a manifest-registered receiver responding
 * to it is one of the platform's background-activity-start exemptions, so starting
 * MainActivity from here (unlike a plain background startActivity call) is allowed.
 *
 * Named for AutoLock's own boot/reboot concerns historically, but that job (re-arming a
 * geofence a reboot silently dropped) went away with the geofence trigger itself -- this
 * receiver's only remaining job is the update relaunch above, which only ever listens for
 * ACTION_MY_PACKAGE_REPLACED, never ACTION_BOOT_COMPLETED. Auto-launching on every device
 * boot would be a surprising, unwanted app launch with nothing to do with an update at all.
 */
class AutoLockBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        runCatching {
            val relaunch = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(relaunch)
        }
    }
}
