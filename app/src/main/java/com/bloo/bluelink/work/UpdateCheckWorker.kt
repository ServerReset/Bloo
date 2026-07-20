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
         *  internal debounce/snooze gates still apply on top of this. */
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
