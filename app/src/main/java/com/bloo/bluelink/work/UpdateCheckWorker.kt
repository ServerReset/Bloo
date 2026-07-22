package com.bloo.bluelink.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bloo.bluelink.data.Notifications
import com.bloo.bluelink.data.UpdateStore
import com.bloo.bluelink.update.UpdateChecker
import com.bloo.bluelink.update.UpdateCheckResult
import java.util.concurrent.TimeUnit

/**
 * Bloo isn't on the Play Store, so it has to check for its own updates. This is
 * that check running on its own schedule, independent of the app being open --
 * the in-app check (AppViewModel.checkForUpdate, cold start + every refresh)
 * covers the "app is already open" case; this covers "app hasn't been opened in
 * a while," posting a notification instead of a tile nobody's there to see.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /**
     * WorkManager's entry point, invoked on each ~12-hour periodic tick.
     *
     * Mechanism, in order:
     * 1. Delegates the actual "is there a newer build" logic entirely to
     *    [UpdateChecker.checkPhone] with `force = false` -- so this call is
     *    still subject to that function's own 1-minute debounce and snooze
     *    checks, meaning a recent foreground check or an active user snooze
     *    can make this periodic tick a no-op even though 12 hours have passed.
     * 2. If the result isn't [UpdateCheckResult.Available] (i.e. up-to-date or
     *    the check itself failed), returns success without posting anything --
     *    a failed check here just quietly waits for the next tick rather than
     *    notifying the user about a check failure.
     * 3. Otherwise, before notifying, compares the found run's number against
     *    [UpdateStore.lastNotifiedRun]: if we already notified about this exact
     *    build (or a newer one) on a previous tick, skips posting again --
     *    without this, every ~12h re-check would re-notify about the same
     *    not-yet-installed update indefinitely until the user actually updates.
     * 4. Records the new run number as notified *before* posting (so a crash
     *    between recording and posting would under-notify rather than
     *    over-notify on retry -- the safer failure direction for a "don't nag"
     *    guard), then posts a notification with a fixed, well-known id
     *    ([NOTIF_ID]) distinct from any per-VIN alert notification id, so a
     *    second update notification replaces the first rather than stacking.
     *    The body text prefers the CI run's own display title when present and
     *    non-blank, falling back to a generic "Build #N" label otherwise.
     */
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val result = UpdateChecker.checkPhone(ctx, force = false)
        if (result !is UpdateCheckResult.Available) return Result.success()
        val run = result.info.run
        val store = UpdateStore(ctx)
        // Only notify once per build -- otherwise every ~12h re-check would nag
        // about the same not-yet-installed update indefinitely.
        if (run.runNumber <= store.lastNotifiedRun()) return Result.success()
        store.setLastNotifiedRun(run.runNumber)
        Notifications.post(
            ctx,
            id = NOTIF_ID,
            title = "Bloo update available",
            text = (run.displayTitle?.takeIf { it.isNotBlank() } ?: "Build #${run.runNumber}") +
                " — open Bloo to download and install.",
        )
        return Result.success()
    }

    companion object {
        private const val NAME = "bloo_update_check"
        private const val NOTIF_ID = 90210 // arbitrary, distinct from per-VIN alert IDs

        /** Matches UpdateChecker's own ~12h intended cadence; the checker's
         *  internal debounce/snooze gates still apply on top of this.
         *  Requires a connected network (WorkManager-level constraint) since an
         *  update check is inherently a network call to GitHub and there's no
         *  point waking the worker up when there's clearly no connectivity to use.
         *  Registered as unique periodic work with KEEP so re-invoking schedule()
         *  (e.g. on every app start) doesn't reset the already-running periodic
         *  timer. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
