package com.bloo.bluelink.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bloo.bluelink.data.WearCommandRunner
import com.bloo.bluelink.tiles.BlooTileService
import com.bloo.bluelink.wear.WearBridge
import java.util.concurrent.TimeUnit

/**
 * Keeps all surfaces fresh even when the phone app is closed: polls the server's
 * latest status for each car, re-renders home-screen widgets, and pushes the new
 * snapshots to any paired watch and phone Quick Settings tiles. The pull is
 * server-cached (no car wake), so running it frequently is cheap. On-demand taps
 * still do a full forcing refresh.
 */
class WidgetRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        runCatching { WearCommandRunner.refresh(ctx, vin = "", force = false) }
        // Fan out to every surface outside the app.
        runCatching { BlooWidget().updateAll(ctx) }
        runCatching { WearBridge.publishNow(ctx) }
        runCatching { WearBridge.publishAuth(ctx) }
        BlooTileService.requestUpdates(ctx)
        return Result.success()
    }

    companion object {
        private const val NAME = "bloo_widget_refresh"

        /** 15 min is the WorkManager periodic floor — far fresher than the previous
         *  app-open-only updates. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES)
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

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
