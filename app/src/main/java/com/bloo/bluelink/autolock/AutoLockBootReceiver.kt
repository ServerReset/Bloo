package com.bloo.bluelink.autolock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bloo.bluelink.MainActivity
import com.bloo.bluelink.data.allAutoLockConfigs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.bloo.bluelink.data.BootReauthNotice
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.launch

/** After a restart, asks the person to sign in; after an app update, restarts the AutoLock watcher and relaunches [MainActivity]. */
class AutoLockBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val ctx = context.applicationContext
            // After a restart the app is sealed until the user signs in (see BootGate): the only thing
            // that happens is a notification asking them to. AutoLock starts once they have.
            if (com.bloo.bluelink.data.BootGate.isLocked(ctx)) {
                BootReauthNotice.post(ctx)
                return
            }
            startWatchIfEnabled(ctx)
            return
        }
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        // An update kills the running watcher, and nothing brought it back until the app was next opened. An update broadcast may
        // start a foreground service, so the watcher is restarted here (not while sealed after a restart: see BootGate).
        if (!com.bloo.bluelink.data.BootGate.isLocked(context.applicationContext)) startWatchIfEnabled(context.applicationContext)
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
                val entry = SettingsStore(ctx).allAutoLockConfigs()
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
