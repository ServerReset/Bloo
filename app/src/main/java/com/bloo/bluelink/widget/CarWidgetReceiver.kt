package com.bloo.bluelink.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking
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
        // Best-effort synchronous clear (onDeleted has no coroutine scope). The
        // config store's edit is a fast local DataStore write.
        runCatching {
            runBlocking {
                val store = WidgetConfigStore(context)
                appWidgetIds.forEach { store.clear(it) }
            }
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
        runCatching { com.bloo.bluelink.data.WearCommandRunner.refresh(applicationContext, vin = "", force = false) }
        runCatching { CarWidget().updateAll(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val WORK = "bloo_car_widget_refresh"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK)
        }
    }
}
