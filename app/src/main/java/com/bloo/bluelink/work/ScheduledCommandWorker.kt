package com.bloo.bluelink.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.VehicleCommandRunner
import com.bloo.bluelink.rethrowIfCancellation
import java.util.concurrent.TimeUnit

/**
 * Runs one car command at a time the person asked for ("climate off at 9 pm"), through the same runner a search tap uses. A later request
 * for the same car and command replaces the earlier one, so only one of each is ever pending.
 *
 * WorkManager starts it roughly on time, not to the second: Android may hold a delayed job for a while in Doze or on a busy phone.
 */
class ScheduledCommandWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val vin = inputData.getString(KEY_VIN) ?: return Result.failure()
        val cmd = inputData.getString(KEY_CMD) ?: return Result.failure()
        val target = inputData.getString(KEY_TARGET) ?: "default"
        val outcome = try {
            VehicleCommandRunner.run(applicationContext, vin, cmd, target).message
        } catch (e: Exception) {
            rethrowIfCancellation(e)
            "failed: ${e.message}"
        }
        AppLog.log("Scheduled $cmd for $vin: $outcome")
        return Result.success()
    }

    companion object {
        private const val KEY_VIN = "vin"
        private const val KEY_CMD = "cmd"
        private const val KEY_TARGET = "target"

        /** Asks for [cmd] on the car [vin] in [delayMinutes]. Replaces any pending request for the same car and command. */
        fun schedule(context: Context, vin: String, cmd: String, climateTarget: String, delayMinutes: Int) {
            val request = OneTimeWorkRequestBuilder<ScheduledCommandWorker>()
                .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
                .setInputData(workDataOf(KEY_VIN to vin, KEY_CMD to cmd, KEY_TARGET to climateTarget))
                .build()
            WorkManagerInit.of(context).enqueueUniqueWork("bloo_scheduled_${vin}_$cmd", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
