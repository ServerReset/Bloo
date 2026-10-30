package com.bloo.bluelink.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bloo.bluelink.data.BootGate

/**
 * Runs when the watch restarts. The watch's own device lock holds it until its first unlock after a boot, so the vaults
 * open here with no PIN of the app's own (see [BootGate]). AutoLock is armed again, since its registration does not survive
 * a reboot.
 */
class WearBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        BootGate.markOpen(context)
        WearAutoLock.rearmIfEnabled(context)
    }
}
