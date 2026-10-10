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

/** Relaunches [MainActivity] once an in-place app update finishes installing. */
class AutoLockBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val ctx = context.applicationContext
            CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                val entry = com.bloo.bluelink.data.SettingsStore(ctx).allAutoLockConfigs()
                    .entries.firstOrNull { it.value.enabled }
                if (entry != null) {
                    runCatching {
                        ctx.startForegroundService(
                            Intent(ctx, AutoLockService::class.java)
                                .setAction(AutoLockService.ACTION_START_WATCH)
                                .putExtra(AutoLockService.EXTRA_VIN, entry.key),
                        )
                    }
                }
            }
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
}
