package com.bloo.bluelink.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bloo.bluelink.data.BlueLinkGate
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleStatus
import com.bloo.bluelink.data.repositoryFor
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Keeps [LiveCharge]'s notification moving while a car keeps charging.
 *
 * [com.bloo.bluelink.work.AlertWorker] already polls every 30 minutes, which
 * is far too coarse for something meant to look "live" -- a progress bar
 * that visibly jumps once every half hour reads as broken, not live. This
 * worker fills the gap with a short interval, but ONLY while at least one
 * car is actually charging: it's a self-rescheduling one-time work request
 * (`enqueueUniqueWork`), not a fixed periodic one, so it reschedules itself
 * exactly once per tick and simply stops -- no wasted wake-ups once charging
 * ends, unlike a periodic request that would keep firing forever whether or
 * not there's anything to report.
 */
class LiveChargePollWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /**
     * One tick: re-poll every signed-in brand's vehicles (cached status,
     * `refresh = false` -- this is a frequent background poll, not a
     * user-triggered one, so it shouldn't burn the account's live-refresh
     * quota) and push whatever's current into [LiveCharge.update] for every
     * car, charging or not (a car that stopped charging needs its bar
     * cleared too, which `update` does on its own when `charging = false`).
     * Reschedules itself only if at least one car is still charging by the
     * end of the pass.
     */
    override suspend fun doWork(): Result {
        val store = SessionStore(applicationContext)
        val settings = SettingsStore(applicationContext)
        if (!settings.notificationPrefs().charging) return Result.success()

        var anyStillCharging = false
        // The distinction this worker was missing. "No car is charging" and "I couldn't
        // find out" are not the same answer, and only the first should end the chain.
        var learnedSomething = false
        // Same defect AlertWorker had, and more visible here: this chain relearns the
        // battery percentage every 5 minutes during a charge and used to keep all of it
        // to itself, feeding only the notification. So the widget's charge ring, the
        // watch and the tiles sat on whatever the phone app last persisted -- a ring
        // frozen at 43% for the whole session, beside a notification counting up.
        // Collected and written in one merge below.
        val fetched = mutableMapOf<String, VehicleStatus>()
        for (brand in store.loggedInBrands()) {
            val repo = runCatching { repositoryFor(brand, store, CredentialStore(applicationContext)) }
                .getOrNull() ?: continue
            val vehicles = runCatching { BlueLinkGate.statusMutex.withLock { repo.vehicles() } }
                .getOrElse { emptyList() }
            for (v in vehicles) {
                val status = runCatching {
                    BlueLinkGate.statusMutex.withLock { repo.status(v, refresh = false) }
                }.getOrNull()
                if (status != null) learnedSomething = true
                val ev = status?.evStatus
                val charging = ev?.batteryCharge == true
                if (charging) anyStillCharging = true
                // Only touch the notification for a car we actually heard back about.
                // Passing charging=false after a failed fetch would CANCEL a live bar
                // because the network blipped, which to the user is indistinguishable
                // from the charge having stopped.
                if (status == null) continue
                fetched[v.vin] = status
                runCatching {
                    // charging local was `ev?.batteryCharge == true`, which the overload derives.
                    LiveCharge.sync(
                        context = applicationContext,
                        settings = settings,
                        vin = v.vin,
                        carName = v.name,
                        ev = ev,
                    )
                }
            }
        }
        // Before any of the return paths below, including the retry one: whatever this
        // tick did learn is worth keeping even if the pass as a whole is being retried.
        // No-ops on an empty map, which is exactly the !learnedSomething case.
        runCatching { SnapshotStore(applicationContext).mergeStatuses(fetched) }

        // Nothing came back at all. That's transient, so hand it to WorkManager's own
        // backoff instead of reading silence as "charging finished" -- which is what
        // silently killed the chain before, freezing the bar at its last value until
        // AlertWorker's next half-hourly tick happened to re-kick it. Bounded by
        // runAttemptCount so a genuinely broken session can't retry forever; after that
        // the chain stops and AlertWorker re-kicks it as it always did.
        if (!learnedSomething) {
            return if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        }
        if (anyStillCharging) scheduleNext(applicationContext)
        return Result.success()
    }

    companion object {
        /** How often to re-poll while charging continues. */
        private const val INTERVAL_MINUTES = 5L
        private const val UNIQUE_WORK_NAME = "bloo_live_charge_poll"

        /** How many consecutive all-failed ticks to retry before letting the chain stop
         *  and waiting for AlertWorker to re-kick it. */
        private const val MAX_RETRIES = 3

        /**
         * Every tick is a network poll, so it is useless without connectivity -- and
         * worse than useless here, because a failed tick used to end the chain outright.
         * WorkManager defers rather than dropping, so a phone that regains signal
         * mid-charge picks the poll back up on its own.
         *
         * The other periodic jobs in this app already require network; this chain was one
         * of the two that didn't.
         */
        private fun constraints() =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /**
         * Starts the poll chain right away -- called the moment some other
         * code path (a foreground status fetch, the 30-minute AlertWorker
         * tick) first observes a car charging. `KEEP` so a chain already in
         * flight isn't reset back to a fresh delay by a second caller
         * noticing the same charging car a moment later.
         */
        fun kick(context: Context) {
            val request = OneTimeWorkRequestBuilder<LiveChargePollWorker>()
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        /** Queues the next tick [INTERVAL_MINUTES] out. `REPLACE` since this
         *  is the chain rescheduling itself, not a competing start. */
        private fun scheduleNext(context: Context) {
            val request = OneTimeWorkRequestBuilder<LiveChargePollWorker>()
                .setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        /** Stops the chain -- charging ended, or the user turned the
         *  feature off entirely. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
