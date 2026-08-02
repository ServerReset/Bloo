package com.bloo.bluelink.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bloo.bluelink.data.BlueLinkGate
import com.bloo.bluelink.data.ChargingLive
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.repositoryFor
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Keeps the live charging notification actually LIVE.
 *
 * [AlertWorker] refreshes it too, but that runs every 30 minutes -- fine for
 * "you left a door open", useless for a progress bar, which would sit on a
 * stale percentage for half an hour at a time and jump in visible steps. A
 * Live Update that only moves twice an hour isn't one.
 *
 * WorkManager's *periodic* minimum interval is 15 minutes, which is still too
 * slow, so this is a one-shot worker that re-enqueues itself while charging
 * continues and simply stops when it doesn't. That gives a poll every
 * [INTERVAL_MINUTES] for exactly as long as it's useful, and nothing at all
 * the rest of the time -- strictly less background work than a periodic job
 * that ticks all day regardless.
 *
 * Status is fetched with `refresh = false`, the same as [AlertWorker]: that
 * reads the server's own cached figure rather than waking the car, so a
 * five-minute cadence costs the car nothing. It still tracks reality, because
 * the number the server holds updates as the car reports in.
 */
class ChargingPollWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val settings = SettingsStore(applicationContext)
        // Switched off since the chain was scheduled: stop rather than
        // re-enqueue, and let ChargingLive clear anything still showing.
        if (!settings.notificationPrefs().charging) return Result.success()

        val store = SessionStore(applicationContext)
        var anyCharging = false
        for (brand in store.loggedInBrands()) {
            val repo = runCatching {
                repositoryFor(brand, store, CredentialStore(applicationContext))
            }.getOrNull() ?: continue
            val vehicles = runCatching {
                BlueLinkGate.statusMutex.withLock { repo.vehicles() }
            }.getOrElse { emptyList() }
            for (v in vehicles) {
                val status = runCatching {
                    BlueLinkGate.statusMutex.withLock { repo.status(v, refresh = false) }
                }.getOrNull()
                val ev = status?.evStatus
                val charging = ev?.batteryCharge == true
                if (charging) anyCharging = true
                runCatching {
                    ChargingLive.update(
                        context = applicationContext,
                        vin = v.vin,
                        carName = v.name,
                        charging = charging,
                        percent = ev?.batteryStatus,
                        minutesToFull = ev?.remainTime2?.atc?.value?.toInt(),
                        pluggedInLabel = ev?.pluggedInLabel,
                        enabled = true,
                    )
                }
            }
        }
        // Only keep the chain alive while there's something to show. A failed
        // fetch leaves anyCharging false and ends it; the 30-minute
        // AlertWorker is the backstop that restarts it once a poll succeeds
        // again, so a transient network blip can't strand the notification.
        if (anyCharging) scheduleNext(applicationContext)
        return Result.success()
    }

    companion object {
        /**
         * How often to refresh while charging. Six times more often than the
         * 30-minute alert poll it replaces for this purpose, and gentle
         * enough for a cached read -- BlueLink rate-limits, and every request
         * here shares [BlueLinkGate.statusMutex] with the foregrounded app,
         * so a shorter interval would start queueing behind live UI refreshes
         * rather than delivering fresher numbers.
         */
        const val INTERVAL_MINUTES = 5L

        private const val NAME = "bloo_charging_poll"

        /** Enqueues the next poll [INTERVAL_MINUTES] out. */
        fun scheduleNext(context: Context) = enqueue(context, INTERVAL_MINUTES)

        /**
         * Starts (or restarts) the chain right away -- called the moment
         * charging is noticed, so the notification begins tracking
         * immediately rather than after one full interval.
         *
         * KEEP, not REPLACE: if a chain is already pending, replacing it
         * would push the next poll back to a full interval away every time
         * something noticed charging, which for a foregrounded app refreshing
         * often could postpone it indefinitely.
         */
        fun kick(context: Context) = enqueue(context, 0L, ExistingWorkPolicy.KEEP)

        private fun enqueue(
            context: Context,
            delayMinutes: Long,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
        ) {
            val request = OneTimeWorkRequestBuilder<ChargingPollWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                // Unlike AlertWorker this DOES require network: its whole job
                // is fetching a fresh number, and running offline would just
                // burn a slot to re-post the value already showing.
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, policy, request)
        }

        /** Cancels any pending poll -- used when the setting is switched off,
         *  so the chain dies with the notification rather than continuing to
         *  wake up and immediately no-op. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
