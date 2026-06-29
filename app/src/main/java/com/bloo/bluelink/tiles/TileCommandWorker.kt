package com.bloo.bluelink.tiles

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bloo.bluelink.data.TileCommandRunner

/**
 * Runs a Quick Settings tile command off the tile's own (very short-lived)
 * lifecycle. A [android.service.quicksettings.TileService] is destroyed almost
 * immediately after onClick, which would cancel a coroutine started in the
 * service's scope before the network call finishes — that's why tile commands
 * appeared to do nothing. WorkManager gives the command a process-lifetime home,
 * then refreshes the tiles so their state reflects the result.
 */
class TileCommandWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val vin = inputData.getString(KEY_VIN) ?: return Result.failure()
        val cmd = inputData.getString(KEY_CMD) ?: return Result.failure()
        val target = inputData.getString(KEY_TARGET) ?: "default"
        runCatching { TileCommandRunner.run(applicationContext, vin, cmd, target) }
        runCatching { BlooTileService.requestUpdates(applicationContext) }
        return Result.success()
    }

    companion object {
        const val KEY_VIN = "vin"
        const val KEY_CMD = "cmd"
        const val KEY_TARGET = "target"

        fun enqueue(ctx: Context, vin: String, cmd: String, target: String) {
            val req = OneTimeWorkRequestBuilder<TileCommandWorker>()
                .setInputData(workDataOf(KEY_VIN to vin, KEY_CMD to cmd, KEY_TARGET to target))
                .build()
            WorkManager.getInstance(ctx).enqueue(req)
        }
    }
}
