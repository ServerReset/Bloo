package com.bloo.bluelink.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.AppWidgetId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.bloo.bluelink.data.AppLog
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import com.bloo.bluelink.work.WorkManagerInit
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Hosts [CarWidget] and keeps it fresh: schedules a light periodic background
 * refresh when the first widget is added, and updates every placed widget
 * directly by its real Android id rather than through [GlanceAppWidget.updateAll].
 *
 * The direct-by-id approach is deliberate, not a style choice: `updateAll`
 * walks Glance's own internal id registry, which is populated by the same
 * onUpdate-to-session machinery that has been observed to silently never
 * complete on some devices -- leaving a widget stuck on its placeholder
 * forever with no crash and nothing in the log. [AppWidgetId], Glance's public
 * wrapper around a raw platform widget id, sidesteps that registry entirely:
 * the ids here always come straight from [AppWidgetManager], a plain platform
 * query that can never be stale or empty the way Glance's own bookkeeping can.
 */
class CarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CarWidget()

    override fun onEnabled(context: Context) {
        AppLog.log("Widget: onEnabled")
        super.onEnabled(context)
        runCatching { WidgetRefreshWorker.schedule(context) }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        AppLog.log("Widget: onUpdate for ${appWidgetIds.size} widget(s)")
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Deliberately not another goAsync() -- super.onUpdate() already calls it once
        // for its own async composition, and a second call from the same dispatch
        // throws IllegalStateException. A plain fire-and-forget scope is used instead.
        val appContext = context.applicationContext
        val ids = appWidgetIds.toList()
        CoroutineScope(Dispatchers.IO).launch {
            ids.forEach { id ->
                runCatching {
                    CarWidget().update(appContext, AppWidgetId(id))
                }.onFailure {
                    AppLog.log("Widget: direct update of id=$id failed: ${it.message}")
                }
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshWorker.cancel(context)
    }
}

/**
 * Repaints every currently-placed [CarWidget] by asking the platform's own
 * [AppWidgetManager] for the real ids this provider currently owns, then
 * updating each directly -- see [CarWidgetReceiver.onUpdate]'s own doc for why
 * this is not `updateAll`. [WidgetRefreshWorker] is this function's one
 * caller: it fires on its own periodic schedule with no onUpdate broadcast
 * behind it, so it needs this same Glance-independent id source.
 */
private suspend fun updateAllCarWidgetsDirectly(context: Context) {
    val ids = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, CarWidgetReceiver::class.java))
    ids.forEach { id ->
        runCatching {
            CarWidget().update(context, AppWidgetId(id))
        }.onFailure {
            AppLog.log("Widget: periodic refresh of id=$id failed: ${it.message}")
        }
    }
}

/**
 * A light periodic worker that repaints every placed widget so it stays
 * reasonably current (relative timestamps, a percent that changed) without
 * the user opening the app. 30-minute cadence; WorkManager clamps the true
 * minimum to 15 minutes regardless.
 */
class WidgetRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { updateAllCarWidgetsDirectly(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val WORK = "bloo_car_widget_refresh"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManagerInit.of(context)
                .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        fun cancel(context: Context) {
            WorkManagerInit.of(context).cancelUniqueWork(WORK)
        }
    }
}
