package com.bloo.bluelink.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkerParameters
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.CarCommand
import com.bloo.bluelink.data.CarCommandRunner
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.DEFAULT_CLIMATE_DURATION_MIN
import com.bloo.bluelink.data.DEFAULT_CLIMATE_TEMP_F
import com.bloo.bluelink.data.DEPARTURE_CHECK_MIN
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.departureLastRuns
import com.bloo.bluelink.data.isDriving
import com.bloo.bluelink.data.departuresNow
import com.bloo.bluelink.data.setDepartureLastRun
import com.bloo.bluelink.data.smartClimateRequestFor
import com.bloo.bluelink.data.withClimate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Starts each car's departures on time: every [DEPARTURE_CHECK_MIN] minutes it looks for a departure whose warm-up window has come, and
 * starts the car for it, unless the car is already running or being driven. Runs through the same command path as every other start.
 */
/** Notification ids for departure starts start here, clear of the other notifications. */
private const val DEPARTURE_NOTICE_BASE = 300_000

class DepartureWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val now = LocalDateTime.now()
        val minute = now.hour * 60 + now.minute
        val epochDay = now.toLocalDate().toEpochDay()
        val store = SettingsStore(applicationContext)
        for (snap in SnapshotStore(applicationContext).current().vehicles) {
            val schedule = store.departuresNow(snap.vin)
            val due = schedule.dueSlot(minute, epochDay, store.departureLastRuns(snap.vin)) ?: continue
            if (snap.climateOn == true || snap.isDriving) continue
            val request = departureRequest(snap)
            val result = runCatching {
                CarCommandRunner.execute(applicationContext, CarCommand(snap.vin, CarAction.CLIMATE_ON).withClimate(request))
            }.getOrNull()
            if (result?.ok == true) {
                store.setDepartureLastRun(snap.vin, due, epochDay)
                announce(snap, due, schedule.slots[due].minuteOfDay)
            }
        }
        return Result.success()
    }

    /** Says the car is warming up, and when it should be ready, so the person knows it has started without opening the app. */
    private fun announce(snap: VehicleSnapshot, slot: Int, readyMinute: Int) {
        val ready = com.bloo.bluelink.data.formatMinuteOfDay(readyMinute)
        com.bloo.bluelink.data.AppLog.log("Departure: started ${snap.name} to be ready by $ready")
        com.bloo.bluelink.data.Notifications.post(
            applicationContext,
            id = DEPARTURE_NOTICE_BASE + (snap.vin.hashCode() and 0x7FFF) * 2 + slot,
            title = "Warming up ${snap.name}",
            text = "Ready by $ready",
            channelId = com.bloo.bluelink.data.Notifications.CHANNEL_EVENTS,
        )
    }

    /** The start for a departure: smart climate's request for the weather. Without a weather reading, the car's usual default. */
    private suspend fun departureRequest(snap: VehicleSnapshot, slot: DepartureSlot): ClimateRequest =
        smartClimateRequestFor(applicationContext, snap.vin)
            ?: ClimateRequest(tempF = DEFAULT_CLIMATE_TEMP_F, defrost = false, durationMinutes = DEFAULT_CLIMATE_DURATION_MIN)

    companion object {
        fun schedule(context: Context) = enqueuePeriodic<DepartureWorker>(
            context, "bloo_departures", DEPARTURE_CHECK_MIN.toLong(), TimeUnit.MINUTES, ExistingPeriodicWorkPolicy.UPDATE,
        )
    }
}
