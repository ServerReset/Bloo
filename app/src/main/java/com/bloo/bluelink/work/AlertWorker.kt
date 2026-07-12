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
import com.bloo.bluelink.data.repositoryFor
import com.bloo.bluelink.data.Notifications
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.wear.WearBridge
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Periodically refreshes each signed-in car's status in the background and posts
 * service-due / door-open notifications even when the app is closed.
 */
class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = SessionStore(applicationContext)
        val settings = SettingsStore(applicationContext)
        val prefs = settings.notificationPrefs()
        if (!prefs.service && !prefs.doorOpen) return Result.success()

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
                    CarAlerts.evaluate(settings, v, status).forEach {
                        Notifications.post(applicationContext, it.id, it.title, it.text, it.actions)
                    }
                }
            }
        }
        // The 30-min alert poll also constitutes a data refresh — fan out to all
        // surfaces so widgets and tiles don't wait for the 15-min WidgetRefreshWorker.
        WearBridge.refreshAllSurfaces(applicationContext)
        return Result.success()
    }

    companion object {
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
