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
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.WearCommandRunner
import com.bloo.bluelink.tiles.BlooTileService
import com.bloo.bluelink.wear.WearBridge
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Keeps all surfaces fresh even when the phone app is closed: polls the server's
 * latest status for each car, re-renders home-screen widgets, and pushes the new
 * snapshots to any paired watch and phone Quick Settings tiles. The pull is
 * server-cached (no car wake), so running it frequently is cheap. On-demand taps
 * still do a full forcing refresh.
 */
class WidgetRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    /**
     * Runs on WorkManager's periodic 15-min schedule (see [schedule]). Each step is wrapped
     * in its own `runCatching` so one surface failing (e.g. no paired watch) never blocks the
     * others from updating -- the worker still reports success even if some pushes failed,
     * since there's nothing actionable to retry differently next cycle. Order: refresh the
     * cached vehicle snapshot for all vehicles (empty [vin] + `force = false` means "whichever
     * cars are known, server cache is fine"), then fan that snapshot out to widgets, watch,
     * and the quick-settings tile.
     */
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        runCatching { WearCommandRunner.refresh(ctx, vin = "", force = false) }
        // Fan out to every surface outside the app.
        runCatching { BlooWidget().updateAll(ctx) }
        runCatching { WearBridge.publishNow(ctx) }
        runCatching { WearBridge.publishAuth(ctx) }
        // Safety net: appearance/settings are normally pushed the moment they change
        // (AppViewModel collects the appearance flow), but re-publishing here on the
        // same 15-min heartbeat as everything else means a missed push can't leave
        // the watch's theme/units/pebble-order stale indefinitely.
        runCatching { WearBridge.publishSettingsNow(ctx, SettingsStore(ctx).appearance.first()) }
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
                    // Skip runs entirely while offline rather than letting them fail/retry --
                    // there'd be nothing to fetch anyway.
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            // KEEP policy: if this periodic work is already scheduled (e.g. called again on
            // every app launch), leave the existing schedule alone instead of resetting its
            // timer, so the 15-min cadence isn't perpetually restarted.
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Stops the periodic refresh entirely, e.g. when the last widget is removed
         *  or the user signs out, so it doesn't keep polling for nothing. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
