package com.bloo.bluelink.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.wear.WearBridge
import com.bloo.bluelink.widget.BlooWidget
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Runs the Drive auto-sync periodically in the background, so a settings change
 * made on another device shows up here (and vice versa) even if this device's
 * app isn't opened for a while — sync otherwise only ran when the app happened
 * to be in the foreground and a refresh settled.
 *
 * A no-op (cheap early exit) when Drive sync isn't configured; [SettingsStore.
 * performDriveSync] itself re-checks the Wi-Fi-only preference each run, so the
 * WorkManager-level constraint only needs "any network."
 */
class DriveSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = SettingsStore(ctx)
        if (store.syncUri() == null) return Result.success()
        val outcome = runCatching { store.performDriveSync() }.getOrNull()
        if (outcome?.imported == true) {
            // A live ViewModel would pick up the DataStore change reactively, but
            // this worker can run with the app process dead — explicitly push the
            // newly-imported settings out so the watch/widgets don't wait for the
            // app to next be opened.
            runCatching { WearBridge.publishSettingsNow(ctx, store.appearance.first()) }
            runCatching { BlooWidget().updateAll(ctx) }
        }
        if (outcome?.error != null) AppLog.log("⚠ Background Drive sync: ${outcome.error}")
        return Result.success()
    }

    companion object {
        private const val NAME = "bloo_drive_sync"

        /** Every 2 hours is frequent enough that changes propagate within a normal
         *  day of use, without the battery/data cost of anything tighter for a
         *  low-urgency settings-sync convenience feature. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DriveSyncWorker>(2, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
