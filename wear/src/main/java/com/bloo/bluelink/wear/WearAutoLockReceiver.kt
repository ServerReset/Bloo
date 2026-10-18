package com.bloo.bluelink.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.SnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The grace period after a significant-motion trigger has elapsed: lock the cars the watch knows
 * about that are not already locked (see [WearAutoLock.targets]).
 *
 * It reads the PERSISTED snapshot, not the live Data Layer: this can run with the app cold, woken
 * only by the alarm, and the local mirror is exactly the last thing the phone pushed. The lock runs
 * through the watch's own command path, so it works with or without its own session. It sends LOCK, not a toggle:
 * the persisted snapshot can be older than the car, and a toggle on a car that was locked meanwhile would UNLOCK it.
 */
class WearAutoLockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!WearAutoLock.isEnabled(context)) return
        // Sealed after a restart: nothing runs until the PIN has been entered.
        if (WearBootGate.isLocked(context)) return
        val app = context.applicationContext
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val vehicles = runCatching { SnapshotStore(app).current().vehicles }.getOrDefault(emptyList())
                for (vin in WearAutoLock.targets(vehicles)) {
                    runCatching { runWatchCommand(app, vin, CarAction.LOCK) }
                }
            } finally {
                result.finish()
            }
        }
    }
}
