package com.bloo.bluelink.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
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
     * 4. Posts a notification with a fixed, well-known id ([NOTIF_ID]) distinct
     *    from any per-VIN alert notification id, so a second update notification
     *    replaces the first rather than stacking. The body text prefers the CI
     *    run's own display title when present and non-blank, falling back to a
     *    generic "Build #N" label otherwise.
     * 5. Records the run number as notified only if that post ACTUALLY went out.
     *    This used to be step 4, recorded before posting on the argument that a
     *    crash in between should under-notify rather than nag. The argument had the
     *    wrong failure mode in view: the common case isn't a crash, it's
     *    POST_NOTIFICATIONS not being granted, which makes [Notifications.post] a
     *    silent no-op -- so the flag was burned and the build was never announced,
     *    even after the user turned notifications on. Repeating instead is free
     *    here, because the fixed [NOTIF_ID] means a repeat replaces its own
     *    notification rather than adding one.
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
        // Record AFTER a post that actually happened, not before. Notifications.post
        // returns early without posting when POST_NOTIFICATIONS isn't granted, so writing
        // the flag first burned the one announcement this build ever gets: the user turns
        // notifications on an hour later, the next tick sees `900 <= 900`, and build 900 is
        // never mentioned again.
        //
        // This reverses the failure direction the original comment argued for -- a crash
        // between posting and recording now re-notifies on the next tick. That is the right
        // way round here, and it costs nothing: NOTIF_ID is fixed, so a repeat REPLACES the
        // existing notification with identical text rather than stacking a second one.
        val posted = Notifications.post(
            ctx,
            id = NOTIF_ID,
            title = "Bloo update available",
            text = (run.displayTitle?.takeIf { it.isNotBlank() } ?: "Build #${run.runNumber}") +
                " — open Bloo to download and install.",
        )
        if (posted) store.setLastNotifiedRun(run.runNumber)
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
            WorkManagerInit.of(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
