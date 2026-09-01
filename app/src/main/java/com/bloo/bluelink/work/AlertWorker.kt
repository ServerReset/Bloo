package com.bloo.bluelink.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.bloo.bluelink.data.BlueLinkGate
import com.bloo.bluelink.data.CarAlerts
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.repositoryFor
import com.bloo.bluelink.data.Notifications
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleStatus
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
     * 5. Folds every successfully-fetched status back into [SnapshotStore] in a
     *    single write, then calls [WearBridge.refreshAllSurfaces] once, so this
     *    30-minute poll doubles as a general data refresh for the watch, tiles
     *    and widgets instead of them waiting on their own schedules.
     *
     *    Step 5 used to be only the fan-out, and this doc used to claim on that
     *    basis that the poll let those surfaces "pick up new data". It did not:
     *    nothing here ever persisted what it fetched, and refreshAllSurfaces
     *    republishes from SnapshotStore, so every surface was re-handed whatever
     *    the phone APP last wrote. The relative timestamps they showed were
     *    measuring the last time the app was opened, not the last time anything
     *    spoke to the car.
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

        // This worker fetches fresh status for every car every 30 minutes and used to
        // throw all of it away: it read the status, raised alerts, updated the live
        // charging bar, and never wrote it anywhere. Then it called
        // WearBridge.refreshAllSurfaces() at the end, which republishes from
        // SnapshotStore -- so the watch, the QS tiles and the widgets were handed
        // whatever the phone app last persisted, however old, seconds after the phone
        // had learned the truth. Every "Updated 4h ago" on a glanceable surface was
        // reporting the last time the APP was opened, not the last time this worker
        // spoke to the car.
        //
        // Collected here and written ONCE below rather than per car: every write is a
        // full decode plus re-encode plus commit of the whole vehicle blob and emits on
        // `payload`, so N per-car writes mean N fsyncs for one poll.
        val fetched = mutableMapOf<String, VehicleStatus>()

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
                // Keep what we just paid a network round trip for. Only for a car we
                // actually heard back about: a failed fetch has nothing to contribute,
                // and this is the exact shape that once deleted the live charging bar
                // when the network blipped.
                if (status != null) fetched[v.vin] = status
                runCatching {
                    // `prefs` is already loaded above; pass it so evaluate() doesn't
                    // re-read the same DataStore value once per vehicle per tick.
                    //
                    // canDeliver: a system notification is this worker's ONLY way to reach
                    // the user -- there is no app on screen to snackbar into. Without it,
                    // evaluate() marked alerts as fired that Notifications.post then
                    // silently dropped for want of POST_NOTIFICATIONS, and the fire-once
                    // flag suppressed them for the rest of the episode. Re-read per tick
                    // rather than hoisted, so granting permission takes effect on the next
                    // tick instead of the next process start.
                    CarAlerts.evaluate(
                        settings, v, status, prefs,
                        canDeliver = Notifications.hasPermission(applicationContext),
                    ).forEach {
                        Notifications.post(applicationContext, it.id, it.title, it.text, it.actions)
                    }
                }
                // The live charging bar rides the same 30-minute poll so it
                // tracks the real percentage instead of going stale between
                // app opens. LiveCharge.update() clears the bar on its own
                // once `charging` is false, so this is what stops it too.
                // `status != null` guard, not just `prefs.charging`. LiveCharge.update
                // CANCELS the notification when told charging = false, and a failed fetch
                // produced exactly that: status null -> ev null -> charging false -> the
                // live bar deleted because the network blipped, which to the user is
                // indistinguishable from the charge having stopped. Same defect the
                // 5-minute poll worker had; fixed in both rather than one.
                if (prefs.charging && status != null) {
                    val ev = status.evStatus
                    // Hand off to the 5-minute chain the instant this worker
                    // sees charging start -- 30 minutes between ticks is far
                    // too coarse for a bar meant to look live.
                    if (ev?.batteryCharge == true) LiveChargePollWorker.kick(applicationContext)
                    runCatching {
                        LiveCharge.sync(
                            context = applicationContext,
                            settings = settings,
                            vin = v.vin,
                            carName = v.name,
                            ev = ev,
                        )
                    }
                }
            }
        }
        // Persist BEFORE fanning out, so the publish below carries this tick's data
        // rather than the previous one's. One write for every car, every brand.
        runCatching { SnapshotStore(applicationContext).mergeStatuses(fetched) }

        // The 30-min alert poll also constitutes a data refresh — fan out to the
        // watch + QS tiles so they don't wait for their own next scheduled update.
        WearBridge.refreshAllSurfaces(applicationContext)
        return Result.success()
    }

    companion object {
        /**
         * Registers the 30-minute periodic alert poll, gated on connectivity like the
         * other workers in this package.
         *
         * That constraint was deliberately absent, on the documented grounds that the
         * status fetches in [doWork] are each wrapped in [runCatching] and "simply
         * produce no alerts for that car if offline, so there's no need to gate the
         * whole periodic schedule on connectivity". That is correct about SAFETY and
         * wrong about COST -- runCatching makes a doomed fetch harmless, not free.
         * Without the constraint, every 30 minutes forever, an offline device still
         * wakes, builds a repository per signed-in brand, decodes the whole snapshot
         * payload, and then attempts `vehicles()` plus one `status()` per car against
         * clients configured for a 30-second connect and 60-second read timeout --
         * all of it inside [BlueLinkGate.statusMutex], so it also blocks anything else
         * that wants the car. In a dead zone or behind a captive portal, where packets
         * are dropped rather than refused, that is minutes of held wakelock and held
         * mutex per tick to accomplish nothing.
         *
         * A [Constraints] is how you say "don't even wake me": WorkManager simply
         * defers the run until connectivity exists, then fires it. Nothing about the
         * per-car runCatching changes; it is still the right guard for a fetch that
         * fails while online.
         *
         * Deliberately NOT adding a battery-not-low constraint. This job is what
         * surfaces "you left a door open" and "the car is still unlocked", and
         * suppressing those on a low phone battery would trade the user's car for a
         * few minutes of screen time.
         *
         * [ExistingPeriodicWorkPolicy.UPDATE], not KEEP. KEEP leaves an already-registered
         * job completely alone -- which preserved its timer, but also preserved its REQUEST:
         * an install carrying the old unconstrained version kept running it forever, so the
         * network constraint added here never reached a single existing user. Every fix to
         * this worker's definition was landing only for fresh installs.
         *
         * UPDATE refreshes the request in place while keeping the existing schedule, which is
         * what KEEP was actually chosen for -- not resetting the timer on every app start.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AlertWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .build()
            WorkManagerInit.of(context).enqueueUniquePeriodicWork(
                "bloo_alerts",
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
