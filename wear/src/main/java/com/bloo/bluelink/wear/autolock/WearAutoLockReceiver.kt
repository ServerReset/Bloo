package com.bloo.bluelink.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.SnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        val app = context.applicationContext
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val vehicles = runCatching { SnapshotStore(app).current().vehicles }.getOrDefault(emptyList())
                for (vin in WearAutoLock.targets(vehicles)) {
                    val locked = lockWithRetry(app, vin)
                    // What it did is said on the wrist (and the phone's own Auto lock says the same on the phone).
                    val name = vehicles.firstOrNull { it.vin == vin }?.name ?: "your car"
                    runCatching { WearNotifier.autoLockResult(app, vin, name, locked) }
                }
            } finally {
                result.finish()
            }
        }
    }

    /**
     * One lock, retried twice (2.5s, then 5s apart) when the car does not answer: a network blip should not leave the car open. The phone's
     * AutoLock does the same.
     */
    private suspend fun lockWithRetry(app: Context, vin: String): Boolean {
        repeat(3) { attempt ->
            if (runCatching { runWatchCommandChecked(app, vin, CarAction.LOCK) }.getOrNull() == true) return true
            if (attempt < 2) delay(if (attempt == 0) 2_500L else 5_000L)
        }
        return false
    }
}
