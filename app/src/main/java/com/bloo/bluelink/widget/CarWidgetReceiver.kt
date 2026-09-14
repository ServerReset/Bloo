package com.bloo.bluelink.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
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
 * Hosts the [CarWidget] and manages its lifecycle: schedules a light periodic
 * background refresh when the first widget is added, and clears a removed widget's
 * per-instance config so a reused appWidgetId can't inherit stale layout choices.
 */
class CarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CarWidget()

    override fun onEnabled(context: Context) {
        AppLog.log("Widget: onEnabled")
        super.onEnabled(context)
        // Guarded, not a bare call -- this is a BroadcastReceiver callback with no
        // crash screen of its own to fall back to (unlike CarWidget.provideGlance,
        // which now catches and shows exactly this same class of failure). An
        // uncaught exception here doesn't just skip scheduling the periodic
        // refresh, it crashes the receiver itself with nothing visible anywhere.
        // CarWidget.provideGlance's own self-healing runCatching{} call to this
        // exact function is what actually recovers a widget that got added before
        // this was guarded; this stops the same failure from ever reaching here
        // unguarded on a FRESH widget add in the first place.
        runCatching { WidgetRefreshWorker.schedule(context) }
    }

    /**
     * Logged (AppLog, Settings' own in-app log) purely as a diagnostic: reported
     * directly as a widget stuck on its static initial layout even after a full
     * remove-and-re-add, which never reaches provideGlance's own crash screen
     * either -- meaning either provideGlance is throwing somewhere neither this
     * session's try/catch nor onCompositionError catches, or Glance's OWN
     * onUpdate/session machinery (this override's super call) never successfully
     * starts a session at all. Confirmed the latter, waiting 13 minutes with the
     * "Widget: provideGlance started" line never once appearing: super.onUpdate's
     * own session-starting path silently never gets there, on THIS device --
     * something inside androidx.glance.appwidget between onUpdate and
     * provideGlance, not anything this app's own code does.
     *
     * A first fix attempt called `updateAll` directly here instead of waiting on
     * Glance's own onUpdate/session chain -- and made things WORSE in a way that
     * only real AppLog evidence caught: confirmed via a fresh remove-and-re-add on
     * that build, "Widget: onUpdate calling updateAll() directly" was immediately
     * followed by "...returned", same millisecond, with "provideGlance started"
     * STILL never appearing. `updateAll` iterates
     * `GlanceAppWidgetManager(context).getGlanceIds(CarWidget::class.java)` --
     * Glance's own INTERNAL id registry, populated by the exact same
     * onUpdate-to-session machinery that has been silently failing to complete
     * this whole time. Called this soon after `super.onUpdate()` returns (which
     * only ever kicks that registration off asynchronously, per its own
     * documented behaviour, not synchronously before returning), that registry is
     * still empty, so `updateAll` looped over zero ids and returned instantly --
     * a silent no-op with nothing to catch, not a fix.
     *
     * The actual fix: [GlanceAppWidget.update] has a direct overload that takes a raw
     * Android `appWidgetId: Int` with no dependency on Glance's own internal registry.
     * `onUpdate` is handed the real, valid Android `appWidgetIds` directly by the platform;
     * calling `glanceAppWidget.update(context, id)` per id (where id is an Int) starts a
     * REAL composition session keyed on an id that is already known-good, sidestepping
     * whatever is stuck inside Glance's own automatic bootstrapping entirely.
     * (An earlier attempt reached for `GlanceAppWidgetManager.getGlanceIdBy(appWidgetId)`
     * to wrap the id in a GlanceId -- that worked at compile time but did not actually fix
     * the widget because the returned GlanceId did not trigger a real composition. The Int
     * overload is the correct API for this use case.)
     */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        AppLog.log("Widget: onUpdate for ${appWidgetIds.size} widget(s)")
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Deliberately NOT another goAsync() here -- super.onUpdate() (Glance's own
        // implementation) almost certainly already calls it once, to run its own
        // composition asynchronously past this method returning, and a SECOND
        // goAsync() call from the same onReceive() dispatch throws
        // IllegalStateException immediately, which would crash every single widget
        // update. A plain fire-and-forget scope carries a small theoretical risk of
        // being cut off if the process dies the instant this method returns, but
        // that's a far smaller risk than guaranteed-crashing on every update.
        val appContext = context.applicationContext
        val ids = appWidgetIds.toList()
        CoroutineScope(Dispatchers.IO).launch {
            AppLog.log("Widget: onUpdate updating ${ids.size} widget(s) directly by id")
            ids.forEach { id ->
                runCatching {
                    CarWidget().update(appContext, id)
                }.onFailure {
                    AppLog.log("Widget: direct update of id=$id failed: ${it.message}")
                }
            }
            AppLog.log("Widget: onUpdate direct-by-id update finished")
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        // Best-effort clear, fire-and-forget -- NOT goAsync() here, for the same reason
        // onUpdate above avoids a second one: GlanceAppWidgetReceiver's own onReceive()
        // dispatch already manages its own async lifecycle for this broadcast, and a
        // second goAsync() call for the same dispatch is not something this callback can
        // rely on. onUpdate's own doc predicted this as an IllegalStateException risk;
        // what actually happened, crash-reported from a real device, was worse and
        // silent at the call site -- goAsync() returned null instead of throwing, and
        // pending.finish() on that null crashed the receiver with a bare
        // NullPointerException. Matches onUpdate's already-proven pattern: a plain
        // CoroutineScope carries the same small "cut off if the process dies the instant
        // this method returns" risk that doc already accepts, which is the right trade
        // for a best-effort disk clear that must never crash the receiver.
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val store = WidgetConfigStore(appContext)
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
 * Repaints every currently-placed [CarWidget], the SAME way [CarWidgetReceiver.onUpdate]
 * does now -- by asking the platform's own [AppWidgetManager] for the real, currently-
 * placed widget ids for this provider and calling [GlanceAppWidget.update] directly per
 * id (using the Int overload), rather than `updateAll`, which walks Glance's OWN
 * internal id registry.
 *
 * That registry only ever gets populated by the exact onUpdate-to-session machinery
 * already documented (at length) on [CarWidgetReceiver.onUpdate] as unreliable on some
 * devices -- so a caller with no specific ids of its own (this function's one caller,
 * [WidgetRefreshWorker], fires on its own 30-minute schedule with no onUpdate broadcast
 * behind it) still needs a real, Glance-independent source of "which ids exist right
 * now." [AppWidgetManager.getAppWidgetIds] is that source: a plain platform query, not
 * anything Glance owns or could leave stale.
 */
private suspend fun updateAllCarWidgetsDirectly(context: Context, logPrefix: String) {
    val ids = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, CarWidgetReceiver::class.java))
    AppLog.log("$logPrefix updating ${ids.size} widget(s) directly by id")
    ids.forEach { id ->
        runCatching {
            CarWidget().update(context, id)
        }.onFailure {
            AppLog.log("$logPrefix direct update of id=$id failed: ${it.message}")
        }
    }
    AppLog.log("$logPrefix direct-by-id update finished")
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
        //
        // updateAllCarWidgetsDirectly, not updateAll -- see its own doc. This periodic
        // job was the thing this session's earlier fix leaned on as "the known-working
        // path" (it runs independently of onUpdate/onEnabled entirely), but that was
        // never actually confirmed with a log line either -- only assumed, because
        // nothing had run for the 30 minutes a real test window would need to prove it.
        // If Glance's own id registry is failing to populate at all rather than just
        // slowly, this call would have been silently doing nothing every 30 minutes too.
        runCatching { updateAllCarWidgetsDirectly(applicationContext, "Widget: periodic refresh") }
        return Result.success()
    }

    companion object {
        private const val WORK = "bloo_car_widget_refresh"

        fun schedule(context: Context) {
            // Gated on connectivity, like MainToMainSyncWorker and UpdateCheckWorker. This
            // job's whole purpose is a network fetch; without the constraint an offline
            // device still woke every 30 minutes to attempt one against a 30s-connect,
            // 60s-read client, while holding BlueLinkGate.statusMutex.
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
