package com.bloo.bluelink.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Hosts the [CarWidget] and manages its lifecycle: schedules a light periodic
 * background refresh when the first widget is added, and clears a removed widget's
 * per-instance config so a reused appWidgetId can't inherit stale layout choices.
 */
class CarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CarWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshWorker.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Best-effort clear. Used to run inside runBlocking on whatever thread
        // dispatched onDeleted (the main thread, in the common case), blocking
        // it on the DataStore's disk write for every removed id -- a home
        // screen wipe with several car widgets meant several sequential
        // blocking writes back to back. goAsync() extends the receiver's
        // lifetime past this call returning, so the actual suspend work can
        // run on a background dispatcher instead of blocking the caller.
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val store = WidgetConfigStore(appContext)
                appWidgetIds.forEach { store.clear(it) }
            }
            pending.finish()
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Last widget removed — stop the background refresh.
        WidgetRefreshWorker.cancel(context)
    }
}

/**
 * A light periodic worker that re-fetches the server's last-known status (no
 * live wake of the car — [WearCommandRunner.refresh] with force=false) and
 * repaints every placed widget, so a widget left on the home screen stays
 * reasonably current without the user opening the app. 30-minute cadence matches
 * the alert poll; WorkManager clamps the true minimum to 15 minutes anyway.
 */
class WidgetRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        // Only go to the network if the data would actually read as stale. This job runs
        // every 30 minutes, and AlertWorker ALSO polls every car's status every 30
        // minutes on its own schedule (its own comment describes itself as doubling as a
        // general data refresh for exactly this reason) -- so whenever alerts are
        // enabled, the app was fetching every car twice per half hour for two purposes.
        // Opening the app refreshes too. Skipping a fetch whose result is already on
        // disk costs the user nothing, because STALE_STATUS_MS is the same threshold the
        // widget uses to decide whether to show its own "stale" treatment: if we skip,
        // the widget was not going to complain anyway.
        //
        // `fetchedAt <= 0` counts as needing a refresh, which is deliberately the
        // opposite of how the widget's stale BADGE treats it -- a car that has never
        // been fetched should not be labelled stale, but it is exactly the car most
        // worth fetching.
        val needsFetch = runCatching {
            val now = System.currentTimeMillis()
            com.bloo.bluelink.data.SnapshotStore(applicationContext).current().vehicles.any {
                it.fetchedAt <= 0 || now - it.fetchedAt > com.bloo.bluelink.data.STALE_STATUS_MS
            }
        }.getOrDefault(true)
        if (needsFetch) {
            runCatching { com.bloo.bluelink.data.WearCommandRunner.refresh(applicationContext, vin = "", force = false) }
        }
        // Always repaint, fetch or no fetch: relative timestamps ("updated 12 min ago")
        // and the stale treatment both drift with wall-clock time even when nothing new
        // has arrived, and a repaint is local work.
        runCatching { CarWidget().updateAll(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val WORK = "bloo_car_widget_refresh"

        fun schedule(context: Context) {
            // Gated on connectivity, like DriveSyncWorker and UpdateCheckWorker. This
            // job's whole purpose is a network fetch; without the constraint an offline
            // device still woke every 30 minutes to attempt one against a 30s-connect,
            // 60s-read client, while holding BlueLinkGate.statusMutex.
            val req = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
    }
}
