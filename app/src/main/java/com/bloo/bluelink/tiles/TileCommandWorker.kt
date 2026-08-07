package com.bloo.bluelink.tiles

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.TileCommandRunner
import com.bloo.bluelink.data.WearCommandRunner

/**
 * Runs a Quick Settings tile command off the tile's own (very short-lived)
 * lifecycle. A [android.service.quicksettings.TileService] is destroyed almost
 * immediately after onClick, which would cancel a coroutine started in the
 * service's scope before the network call finishes — that's why tile commands
 * appeared to do nothing. WorkManager gives the command a process-lifetime home,
 * then re-fetches status so the tiles reflect the result.
 *
 * How that reaches the tiles is worth being precise about, because it used to be
 * described as a direct refresh and was not one: the fetch writes into SnapshotStore,
 * and a tile that is currently visible observes that store (see
 * BlooTileService.onStartListening) and repaints itself. The requestUpdates() call is
 * a no-op on these tiles -- none declares META_DATA_ACTIVE_TILE -- so the store is the
 * only path. A tile that is NOT visible simply reads the new value next time it is.
 */
class TileCommandWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val vin = inputData.getString(KEY_VIN) ?: return Result.failure()
        val cmd = inputData.getString(KEY_CMD) ?: return Result.failure()
        if (cmd == CMD_REFRESH) {
            val refreshed = runCatching {
                WearCommandRunner.refresh(applicationContext, vin)
                AppLog.log("Tile refresh for $vin")
            }.onFailure { AppLog.log("⚠ Tile refresh failed: ${it.message}") }
            runCatching { BlooTileService.requestUpdates(applicationContext) }
            // A status refresh is read-only, so a transient network hiccup is safe
            // to retry a bounded number of times. If it keeps failing we give up
            // (success) rather than leaving a silent background refresh stuck.
            return when {
                refreshed.isSuccess -> Result.success()
                runAttemptCount < MAX_ATTEMPTS -> Result.retry()
                else -> Result.success()
            }
        }
        val target = inputData.getString(KEY_TARGET) ?: "default"
        val result = TileCommandRunner.run(applicationContext, vin, cmd, target)
        runCatching { BlooTileService.requestUpdates(applicationContext) }
        // Don't mask a failed command as success: TileCommandRunner already logs
        // the ⚠ line, and returning failure() surfaces it to WorkManager instead
        // of the old always-success behaviour. A mutating car command is not
        // auto-retried here on purpose -- the runner collapses transient (network)
        // and terminal (e.g. "can't start climate while driving", "car not found")
        // outcomes into one message, and blindly re-dispatching a real
        // lock/climate/charge command that may have already reached the car is
        // riskier than surfacing the failure and letting the user re-tap.
        //
        // ...but "surfacing" it to WorkManager isn't surfacing it to the USER: the
        // tile already showed an optimistic ack toast on tap, and the runner only
        // writes the optimistic snapshot on success, so a failure just silently
        // reverted the tile. That's indistinguishable from a render glitch, and it
        // threw away deliberately-written, user-actionable text (the climate
        // driving-gate message exists purely to be read). Toast it, exactly as
        // WidgetCommandWorker already does for the same reason.
        if (!result.ok) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(
                    applicationContext,
                    result.message ?: "Tile command failed",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
        return if (result.ok) Result.success() else Result.failure()
    }

    companion object {
        const val KEY_VIN = "vin"
        const val KEY_CMD = "cmd"
        const val KEY_TARGET = "target"
        const val CMD_REFRESH = "__refresh"
        /** Bounded retries for the read-only refresh path (runAttemptCount is 0-based). */
        private const val MAX_ATTEMPTS = 3

        fun enqueue(ctx: Context, vin: String, cmd: String, target: String) {
            val req = OneTimeWorkRequestBuilder<TileCommandWorker>()
                .setInputData(workDataOf(KEY_VIN to vin, KEY_CMD to cmd, KEY_TARGET to target))
                .build()
            // Serialize same-(vin,cmd) taps rather than running them concurrently:
            // a double-tap of the same tile used to enqueue two workers that raced,
            // and because the toggle direction is read from the last-known snapshot,
            // the second could read the first's optimistic flip and send the OPPOSITE
            // command (two "lock" taps ending with the car unlocked). APPEND_OR_REPLACE
            // chains them so they run one after another, each seeing the prior result.
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "tile_cmd_${vin}_$cmd",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                req,
            )
        }

        /** Enqueue a lightweight status refresh for a tile's car. */
        fun enqueueRefresh(ctx: Context, vin: String) = enqueue(ctx, vin, CMD_REFRESH, "default")
    }
}
