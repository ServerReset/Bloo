package com.bloo.bluelink.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.SettingsStore
import java.util.concurrent.TimeUnit

/**
 * Runs the Drive auto-sync periodically in the background, so a settings change
 * made on another device shows up here (and vice versa) even if this device's
 * app isn't opened for a while — sync otherwise only ran when the app happened
 * to be in the foreground and a refresh settled.
 *
 * A no-op (cheap early exit) when Drive sync isn't configured; [SettingsStore.
 * performMainToMainSync] itself re-checks the Wi-Fi-only preference each run, so the
 * WorkManager-level constraint only needs "any network."
 */
class MainToMainSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    /**
     * WorkManager's entry point, invoked on each periodic tick (see [schedule]).
     *
     * Mechanism, in order:
     * 1. Cheap early exit: if the user never configured a Drive sync file
     *    ([SettingsStore.syncUri] is null), returns success immediately without
     *    doing any work -- this keeps the periodic job registered (so it's
     *    ready to go the moment sync *is* configured) while making every tick
     *    a no-op cost until then.
     * 2. Runs the actual sync via [SettingsStore.performMainToMainSync], wrapped in
     *    [runCatching] so an unexpected exception doesn't crash the worker;
     *    an unexpected throwable (e.g. a DataStore IOException) is logged and
     *    turned into [Result.retry] rather than being swallowed, so a real
     *    failure is actually surfaced and retried instead of masquerading as a
     *    silent success.
     * 3. If the sync reported an error, logs it and returns [Result.retry] so
     *    WorkManager retries with the exponential backoff configured in
     *    [schedule] instead of waiting for the next full periodic interval.
     * 4. Otherwise (no error, sync attempted and either succeeded or found
     *    nothing new to import) returns [Result.success].
     */
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = SettingsStore(ctx)
        if (store.syncUri() == null) return Result.success()
        val result = runCatching { store.performMainToMainSync() }
        val outcome = result.getOrElse { t ->
            // An unexpected throwable (e.g. a DataStore IOException from the
            // trailing writes) must not be swallowed into a silent success --
            // surface it and let WorkManager retry with the configured backoff.
            AppLog.log("⚠ Background Drive sync threw: $t")
            return retryWhileAttemptsRemain()
        }
        if (outcome.error != null) {
            AppLog.log("⚠ Background Drive sync: ${outcome.error}")
            // Retry with WorkManager's own backoff (set below) instead of just
            // waiting up to 2h for the next periodic tick -- most failures here
            // are transient (a momentary network hiccup, Drive briefly
            // unreachable), so recovering within minutes instead of hours is a
            // real reliability difference for a "seamless" sync experience. A
            // permanently-broken cause (revoked permission) just retries a
            // few times with growing delays and gives up until the next
            // periodic tick, which is still bounded and cheap.
            //
            // That last sentence was not true until this was bounded: WorkManager never
            // gives up on Result.retry(), so a revoked Drive grant retried forever,
            // each attempt paying performMainToMainSync's ~15 DataStore reads, a Drive round
            // trip and (when photos sync) a full per-car JPEG re-encode. Backoff caps
            // the RATE, not the count. LiveChargePollWorker already bounds its own
            // retries; this is the same shape.
            return retryWhileAttemptsRemain()
        }
        return Result.success()
    }

    /**
     * [Result.retry] while attempts remain, otherwise [Result.success].
     *
     * Success rather than failure on giving up, deliberately: this is a PERIODIC worker,
     * and returning failure would cancel the whole periodic chain, so a temporary
     * problem would permanently stop background sync. Success means "stand down and
     * wait for the next 2-hour tick", which is what the KDoc above describes.
     */
    private fun retryWhileAttemptsRemain(): Result =
        if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()

    companion object {
        // Unique work name used below so re-calling schedule() (e.g. on every
        // app start) doesn't stack up duplicate periodic jobs.
        private const val NAME = "bloo_drive_sync"

        /** How many consecutive failures to retry before standing down and waiting for
         *  the next periodic tick. Matches LiveChargePollWorker.MAX_RETRIES. */
        private const val MAX_RETRIES = 3

        /** Every 2 hours is frequent enough that changes propagate within a normal
         *  day of use, without the battery/data cost of anything tighter for a
         *  low-urgency settings-sync convenience feature. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MainToMainSyncWorker>(2, TimeUnit.HOURS)
                // Requires any usable network connection (not Wi-Fi-only at the
                // WorkManager level) since the Wi-Fi-only preference, if the user
                // set one, is instead re-checked inside performMainToMainSync() itself
                // every run -- letting the constraint here stay the loosest
                // possible so the worker is scheduled to run as often as intended
                // and only skips the actual network call when the user's own
                // preference says to.
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                // Explicit (rather than relying on the platform default) so a
                // transient failure is retried within a minute, not the
                // default's much longer first backoff step.
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            // enqueueUniquePeriodicWork + KEEP: if a periodic job under this name
            // is already scheduled, leave the existing one running as-is rather
            // than replacing it -- so calling schedule() again (e.g. on every app
            // launch) doesn't reset the periodic timer or cancel an in-flight run.
            WorkManagerInit.of(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
