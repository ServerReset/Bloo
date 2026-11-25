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
import com.bloo.bluelink.suspendRunCatching

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
        if (intent.action == WearAutoLock.ACTION_CANCEL) {
            WearAutoLock.cancelPending(context)
            return
        }
        if (!WearAutoLock.isEnabled(context)) return
        val app = context.applicationContext
        // Ran: the countdown comes down, and the cooldown starts, so the rest of this walk does not lock again.
        WearNotifier.clearAutoLockCountdown(app)
        WearAutoLock.markRun(app)
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // A fresh read before the cars are judged: the saved snapshot can be older than the car, so a car that was started or
                // unlocked since would be judged on stale data.
                runCatching { WearCredentialSync.refresh(app) }
                val vehicles = runCatching { SnapshotStore(app).current().vehicles }.getOrDefault(emptyList())
                for (vin in com.bloo.bluelink.autolock.LockPolicy.targets(vehicles)) {
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
     * One lock under the phone's own retry rule (com.bloo.bluelink.autolock.LockRetry): again after 2.5 s and 5 s when the car does not
     * answer, but not when the day's limit is reached or the lock is already being sent. With no session of its own the watch hands the
     * lock to the phone once, and that is the end of it: it used to count the hand-over as "no answer" and send it three times.
     * Returns true locked, false failed, null handed to the phone.
     */
    private suspend fun lockWithRetry(app: Context, vin: String): Boolean? {
        val (result, _) = com.bloo.bluelink.autolock.LockRetry.run(stopOnNull = true) {
            suspendRunCatching { WearCredentialSync.runLocally(app, vin, CarAction.LOCK, null) }.getOrNull()
        }
        if (result == null) {
            WearDataLayerSync.sendCommand(app, vin, CarAction.LOCK, null)
            return null
        }
        return result.ok
    }
}
