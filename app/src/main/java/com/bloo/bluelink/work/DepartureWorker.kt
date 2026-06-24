package com.bloo.bluelink.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.BlueLinkGate
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.Notifications
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.repositoryFor
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Departure preconditioning: warms the car so it's comfortable by a scheduled
 * time. Runs every 15 minutes but does no network unless a car is actually due —
 * it checks the locally-stored schedules first, and only then sends a climate
 * start for cars whose departure is ~10–25 min away today.
 */
class DepartureWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val store = SettingsStore(ctx)
        val snaps = SnapshotStore(ctx).current().vehicles
        if (snaps.isEmpty()) return Result.success()

        val now = Calendar.getInstance()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
        val dow = now.get(Calendar.DAY_OF_WEEK)
        val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        // Pick cars whose preconditioning window is open now (purely local — no network).
        val due = snaps.filter { snap ->
            val s = store.departureSchedule(snap.vin)
            s.enabled && dow in s.days &&
                nowMin in (s.minutes - WINDOW_MAX)..(s.minutes - WINDOW_MIN) &&
                store.departureLastFired(snap.vin) != today
        }
        if (due.isEmpty()) return Result.success()

        val session = SessionStore(ctx)
        val creds = CredentialStore(ctx)
        for (snap in due) {
            val v = snap.toVehicle()
            val req = store.savedClimate(snap.vin)
                ?: ClimateRequest(tempF = 72, defrost = false, durationMinutes = 10)
            val ok = runCatching {
                val repo = repositoryFor(Brand.fromIndicator(v.brandIndicator), session, creds)
                BlueLinkGate.statusMutex.withLock { repo.startClimate(v, req) }
            }.isSuccess
            store.setDepartureLastFired(snap.vin, today)
            if (ok) {
                Notifications.post(
                    ctx, ("dep" + snap.vin).hashCode(),
                    "Warming up ${snap.name}",
                    "Preconditioning started for your scheduled departure.",
                )
            }
        }
        return Result.success()
    }

    companion object {
        // Fire when departure is between WINDOW_MIN and WINDOW_MAX minutes away, so
        // the 15-minute worker always catches the window and starts climate early.
        private const val WINDOW_MIN = 10
        private const val WINDOW_MAX = 25

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DepartureWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "bloo_departure",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
