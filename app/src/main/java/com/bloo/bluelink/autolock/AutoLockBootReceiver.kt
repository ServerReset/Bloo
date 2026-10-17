package com.bloo.bluelink.autolock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.data.allAutoLockConfigs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.bloo.uicommon.content

/** Relaunches [MainActivity] once an in-place app update finishes installing. */
class AutoLockBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val ctx = context.applicationContext
            // After a restart the app is sealed until the user signs in (see BootGate): the only thing
            // that happens is a notification asking them to. AutoLock starts once they have.
            if (com.bloo.bluelink.data.BootGate.isLocked(ctx)) {
                com.bloo.bluelink.data.BootReauthNotice.post(ctx)
                return
            }
            startWatchIfEnabled(ctx)
            return
        }
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        runCatching {
            val relaunch = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(relaunch)
        }
    }

    companion object {
        /** Starts the background Bluetooth watcher when AutoLock is on for any car. */
        fun startWatchIfEnabled(ctx: Context) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                val entry = com.bloo.bluelink.data.SettingsStore(ctx).allAutoLockConfigs()
                    .entries.firstOrNull { it.value.enabled }
                if (entry != null) {
                    // Through the funnel: it refuses when the app is sealed, which is the case this
                    // receiver runs in right after a restart -- and a raw startForegroundService there
                    // is what crashed the app (see AutoLockService.promoteThenStop).
                    AutoLockService.start(ctx, entry.key, AutoLockService.ACTION_START_WATCH)
                }
            }
        }
    }
}
