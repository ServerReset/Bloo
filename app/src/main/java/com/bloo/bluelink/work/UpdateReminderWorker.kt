package com.bloo.bluelink.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import com.bloo.bluelink.data.Notifications
import com.bloo.bluelink.update.UpdateCheckResult
import com.bloo.bluelink.update.UpdateChecker
import com.bloo.bluelink.data.UpdateStore
import java.util.concurrent.TimeUnit

/**
 * The "Remind me" follow-up on the update tile. [snoozeUpdate] hides the tile and
 * sets an UpdateChecker snooze (so it doesn't re-surface on every refresh in the
 * meantime); this worker fires ~1 day later to close that loop:
 *
 * 1. Clears the snooze (setSnoozeUntil(0)) so the next in-app / periodic update
 *    check surfaces the tile again instead of short-circuiting to UpToDate.
 * 2. Does a forced check: if a newer build is (still) available, posts a reminder
 *    notification pointing the user back into the app. If the user already updated
 *    in the interim, the check reports UpToDate and NO notification is posted —
 *    so we never nag about a build that's already installed.
 *
 * One-time, delayed work (not periodic): "Remind me" is a single deferral, not a
 * recurring schedule. Enqueued as unique work with REPLACE so tapping "Remind me"
 * again just resets the 1-day timer rather than stacking multiple reminders.
 */
class UpdateReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        // Clear the snooze first so the check below (and every later check) can surface the
        // tile again -- this is the "bring the update thing back" half, and an active snooze
        // short-circuits checkPhone even when forced, so clearing it is also what lets this
        // check reach the network at all.
        UpdateStore(ctx).setSnoozeUntil(0L)
        // Force past the 1-minute debounce.
        val result = UpdateChecker.checkPhone(ctx, force = true)
        if (result is UpdateCheckResult.Failed) {
            // Retry rather than report success. This is a ONE-SHOT request, so succeeding here
            // consumed the user's "Remind me" and nothing re-armed it -- a phone in airplane
            // mode or behind a captive portal at the 24h mark simply never got reminded again.
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
        if (result is UpdateCheckResult.Available) {
            val run = result.info.run
            Notifications.post(
                ctx,
                id = NOTIF_ID,
                title = "Reminder: Bloo update available",
                text = (run.displayTitle?.takeIf { it.isNotBlank() } ?: "Build #${run.runNumber}") +
                    " — open Bloo to download and install.",
            )
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "bloo_update_reminder"
        // Distinct from UpdateCheckWorker's 90210 so the reminder and the periodic
        // "update available" notification don't replace each other.
        private const val NOTIF_ID = 90211

        /** Schedule the 1-day "Remind me" follow-up. REPLACE so re-tapping resets
         *  the timer rather than queuing a second reminder. */
        /** Bounded retries, so a persistently offline phone stops rather than retrying forever. */
        private const val MAX_ATTEMPTS = 5

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<UpdateReminderWorker>()
                .setInitialDelay(1, TimeUnit.DAYS)
                // The only network worker in this package that lacked the constraint every
                // other one carries -- so it was the only one that could fire with no
                // connection, fail, and burn a one-shot reminder doing it.
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManagerInit.of(context).enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
