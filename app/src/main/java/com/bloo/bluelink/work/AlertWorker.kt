package com.bloo.bluelink.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bloo.bluelink.data.BlueLinkGate
import com.bloo.bluelink.data.CarAlerts
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.repositoryFor
import com.bloo.bluelink.data.Notifications
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.targetForCurrentPlug
import com.bloo.bluelink.wear.WearBridge
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Periodically refreshes each signed-in car's status in the background and posts
 * service-due / door-open notifications even when the app is closed.
 */
class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /**
     * WorkManager's entry point, invoked on each ~30-minute periodic tick.
     *
     * Mechanism, in order:
     * 1. Cheap early exit: if none of the service-due, door-open, "running"
     *    (engine-left-on), or "unlocked" alert types are enabled in the user's
     *    [SettingsStore.notificationPrefs], returns success immediately without
     *    logging into anything or making a single network call. All four types
     *    that [CarAlerts.evaluate] handles below are checked here, so a
     *    "running"-only preference configuration still runs the poll.
     * 2. Otherwise, iterates every brand the user is actually logged into (
     *    [SessionStore.loggedInBrands]) -- each brand gets its own repository
     *    instance via [repositoryFor]; if building that repo fails for one
     *    brand (bad/missing credentials), that brand is skipped via `continue`
     *    rather than aborting the whole run for every other logged-in brand.
     * 3. For each brand, fetches the vehicle list and then, per vehicle, a
     *    status snapshot (`refresh = false`, i.e. accept a cached/last-known
     *    status rather than forcing a fresh poll of the car itself) -- both
     *    calls acquire [BlueLinkGate.statusMutex] before talking to the
     *    backend, the same app-wide lock the foregrounded app's own status
     *    refreshes take, so this worker running in the background can never
     *    race a live app session and trigger BlueLink's 502-on-overlapping-
     *    requests behavior. Any failure fetching vehicles or a given vehicle's
     *    status is swallowed via [runCatching]/[getOrElse]/[getOrNull] --
     *    treated as "no vehicles"/"no status" for that iteration rather than
     *    failing the whole worker run over one bad car or brand.
     * 4. Runs [CarAlerts.evaluate] per vehicle against whatever status was
     *    obtained (possibly null, which `evaluate` itself interprets as "poll
     *    failed, don't guess") and posts every alert it returns.
     * 5. After all brands/vehicles are processed, unconditionally calls
     *    [WearBridge.refreshAllSurfaces] once -- this 30-minute alert poll
     *    already fetched fresh-ish status for every car, so it doubles as a
     *    general data refresh, letting the watch/widgets/tiles pick up new data
     *    now instead of waiting for the separate, less frequent 15-minute
     *    widget-refresh job.
     * Always returns [Result.success] -- there's no retry path here; a failed
     * fetch for one car/brand is silently absorbed per-item as described above,
     * and the whole thing just runs again on the next periodic tick regardless.
     */
    override suspend fun doWork(): Result {
        val store = SessionStore(applicationContext)
        val settings = SettingsStore(applicationContext)
        val prefs = settings.notificationPrefs()
        // `charging` belongs in this early-exit too -- it's driven by the
        // same poll below, so leaving it out would mean a user who disabled
        // every alert but kept the live charging bar got no poll at all,
        // and therefore a bar that never updates or clears.
        if (!prefs.service && !prefs.doorOpen && !prefs.running && !prefs.unlocked && !prefs.charging) {
            return Result.success()
        }

        for (brand in store.loggedInBrands()) {
            val repo = runCatching { repositoryFor(brand, store, CredentialStore(applicationContext)) }.getOrNull() ?: continue
            // Share the app-wide status gate so a foregrounded app and this worker
            // never issue overlapping requests (Blue Link 502s otherwise).
            val vehicles = runCatching { BlueLinkGate.statusMutex.withLock { repo.vehicles() } }
                .getOrElse { emptyList() }
            for (v in vehicles) {
                val status = runCatching {
                    BlueLinkGate.statusMutex.withLock { repo.status(v, refresh = false) }
                }.getOrNull()
                runCatching {
                    // `prefs` is already loaded above; pass it so evaluate() doesn't
                    // re-read the same DataStore value once per vehicle per tick.
                    CarAlerts.evaluate(settings, v, status, prefs).forEach {
                        Notifications.post(applicationContext, it.id, it.title, it.text, it.actions)
                    }
                }
                // The live charging bar rides the same 30-minute poll so it
                // tracks the real percentage instead of going stale between
                // app opens. LiveCharge.update() clears the bar on its own
                // once `charging` is false, so this is what stops it too.
                if (prefs.charging) {
                    val ev = status?.evStatus
                    // Hand off to the 5-minute chain the instant this worker
                    // sees charging start -- 30 minutes between ticks is far
                    // too coarse for a bar meant to look live.
                    if (ev?.batteryCharge == true) LiveChargePollWorker.kick(applicationContext)
                    runCatching {
                        LiveCharge.update(
                            context = applicationContext,
                            vin = v.vin,
                            carName = v.name,
                            charging = ev?.batteryCharge == true,
                            percent = ev?.batteryStatus,
                            minutesToFull = ev?.minutesToFull,
                            pluggedInLabel = ev?.pluggedInLabel,
                            enabled = true,
                            chargeLimit = ev?.targetForCurrentPlug(),
                        )
                    }
                }
            }
        }
        // The 30-min alert poll also constitutes a data refresh — fan out to the
        // watch + QS tiles so they don't wait for their own next scheduled update.
        WearBridge.refreshAllSurfaces(applicationContext)
        return Result.success()
    }

    companion object {
        /**
         * Registers the 30-minute periodic alert poll. No network [Constraints]
         * are set here (unlike the other workers in this app's work package) --
         * status fetches inside [doWork] are individually wrapped in
         * [runCatching] and simply produce no alerts for that car if offline, so
         * there's no need to gate the whole periodic schedule on connectivity.
         * `enqueueUniquePeriodicWork` with [ExistingPeriodicWorkPolicy.KEEP]
         * means calling `schedule` again (e.g. every app start) leaves an
         * already-registered job alone rather than resetting its timer.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AlertWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "bloo_alerts",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
