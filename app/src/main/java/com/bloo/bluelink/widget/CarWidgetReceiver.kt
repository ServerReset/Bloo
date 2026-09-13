package com.bloo.bluelink.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
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
     * [GlanceAppWidgetManager.getGlanceId] sidesteps that registry entirely: it
     * wraps a raw Android [appWidgetId] the OS already guarantees is valid the
     * moment onUpdate is called with it (this is the plain [AppWidgetManager]'s
     * own id list, not Glance's), with no dependency on whatever is stuck inside
     * Glance's own session bootstrapping. [CarWidget.update] then runs
     * provideGlance for that id directly, the same way `updateAll` would have if
     * its lookup had ever actually found it.
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
        CoroutineScope(Dispatchers.IO).launch {
            updateAllCarWidgetsDirectly(appContext, "Widget: onUpdate")
        }
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
 * Repaints every currently-placed [CarWidget] by resolving each raw Android
 * appWidgetId straight through [GlanceAppWidgetManager.getGlanceId] and calling
 * [CarWidget.update] for it directly -- not [androidx.glance.appwidget.updateAll],
 * which instead iterates Glance's own INTERNAL id registry
 * (`getGlanceIds(CarWidget::class.java)`). See [CarWidgetReceiver.onUpdate]'s own
 * doc for the real evidence behind why that matters: that registry is populated
 * by the same onUpdate-to-session machinery that has been silently failing to
 * complete on some devices, so `updateAll` can silently iterate zero widgets and
 * return having done nothing, with nothing to catch. `getGlanceId(appWidgetId)`
 * instead wraps the id the OS's own [AppWidgetManager] already guarantees is
 * valid the moment it's in [appWidgetIds] here, independent of whatever is stuck
 * inside Glance's own bootstrapping.
 *
 * Shared by [CarWidgetReceiver.onUpdate] (fires on add/resize) and
 * [WidgetRefreshWorker] (fires every 30 minutes) so both the immediate and the
 * periodic repaint go through the one path actually confirmed to reach
 * provideGlance, rather than each independently trusting `updateAll` to.
 */
private suspend fun updateAllCarWidgetsDirectly(context: Context, logPrefix: String) {
    val appWidgetIds = AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, CarWidgetReceiver::class.java))
    val manager = GlanceAppWidgetManager(context)
    val widget = CarWidget()
    appWidgetIds.forEach { appWidgetId ->
        runCatching {
            val glanceId = manager.getGlanceId(appWidgetId)
            if (glanceId != null) {
                AppLog.log("$logPrefix calling update() for appWidgetId=$appWidgetId")
                widget.update(context, glanceId)
                AppLog.log("$logPrefix update() returned for appWidgetId=$appWidgetId")
            } else {
                AppLog.log("$logPrefix got a null GlanceId for appWidgetId=$appWidgetId")
            }
        }.onFailure {
            AppLog.log("$logPrefix update() threw for appWidgetId=$appWidgetId: ${it::class.simpleName}: ${it.message}")
        }
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
