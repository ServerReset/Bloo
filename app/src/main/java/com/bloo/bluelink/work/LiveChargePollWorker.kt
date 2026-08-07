package com.bloo.bluelink.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bloo.bluelink.data.BlueLinkGate
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.repositoryFor
import com.bloo.bluelink.data.targetForCurrentPlug
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
        for (brand in store.loggedInBrands()) {
            val repo = runCatching { repositoryFor(brand, store, CredentialStore(applicationContext)) }
                .getOrNull() ?: continue
            val vehicles = runCatching { BlueLinkGate.statusMutex.withLock { repo.vehicles() } }
                .getOrElse { emptyList() }
            for (v in vehicles) {
                val status = runCatching {
                    BlueLinkGate.statusMutex.withLock { repo.status(v, refresh = false) }
                }.getOrNull()
                val ev = status?.evStatus
                val charging = ev?.batteryCharge == true
                if (charging) anyStillCharging = true
                runCatching {
                    LiveCharge.update(
                        context = applicationContext,
                        vin = v.vin,
                        carName = v.name,
                        charging = charging,
                        percent = ev?.batteryStatus,
                        minutesToFull = ev?.minutesToFull,
                        pluggedInLabel = ev?.pluggedInLabel,
                        enabled = true,
                        chargeLimit = ev?.targetForCurrentPlug(),
                    )
                }
            }
        }
        if (anyStillCharging) scheduleNext(applicationContext)
        return Result.success()
    }

    companion object {
        /** How often to re-poll while charging continues. */
        private const val INTERVAL_MINUTES = 5L
        private const val UNIQUE_WORK_NAME = "bloo_live_charge_poll"

        /**
         * Starts the poll chain right away -- called the moment some other
         * code path (a foreground status fetch, the 30-minute AlertWorker
         * tick) first observes a car charging. `KEEP` so a chain already in
         * flight isn't reset back to a fresh delay by a second caller
         * noticing the same charging car a moment later.
         */
        fun kick(context: Context) {
            val request = OneTimeWorkRequestBuilder<LiveChargePollWorker>().build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        /** Queues the next tick [INTERVAL_MINUTES] out. `REPLACE` since this
         *  is the chain rescheduling itself, not a competing start. */
        private fun scheduleNext(context: Context) {
            val request = OneTimeWorkRequestBuilder<LiveChargePollWorker>()
                .setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES)
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
