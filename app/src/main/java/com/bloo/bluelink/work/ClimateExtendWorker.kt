package com.bloo.bluelink.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.CLIMATE_DURATION_RANGE
import com.bloo.bluelink.data.DEFAULT_CLIMATE_TEMP_F
import com.bloo.bluelink.data.WearAction
import com.bloo.bluelink.data.WearCommand
import com.bloo.bluelink.data.WearCommandRunner
import java.util.concurrent.TimeUnit

/**
 * Auto-extends a remote climate run past the vendor API's single-command cap
 * ([CLIMATE_DURATION_RANGE], 10 minutes). The car itself has no concept of "run
 * for 25 minutes" -- only "run for up to 10" -- so a longer request is chained:
 * [com.bloo.bluelink.ui.AppViewModel.startClimate] sends the first chunk
 * immediately and schedules this worker (via [schedule]) to fire the moment
 * that chunk's duration elapses. If more than one chunk remains after this
 * one runs, it reschedules itself for the next chunk before finishing --
 * that's the whole chain, one worker firing at a time rather than every chunk
 * scheduled up front, so a manual stop only ever has to cancel ONE pending
 * work item regardless of how many chunks are left.
 *
 * Uses [WearCommandRunner] directly (not the phone's own repo/session
 * plumbing) -- the exact same standalone command path the widget and Quick
 * Settings tile workers already run from a bare [Context] with no live
 * ViewModel, which is exactly this worker's situation too.
 */
class ClimateExtendWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val vin = inputData.getString(KEY_VIN) ?: return Result.failure()
        val remainingMinutes = inputData.getInt(KEY_REMAINING_MIN, 0)
        // Nothing left to extend -- a schedule() call with 0 remaining is a
        // programming error elsewhere, not a reason to fail the work item.
        if (remainingMinutes <= 0) return Result.success()

        val command = WearCommand(
            vin = vin,
            action = WearAction.CLIMATE_ON,
            tempF = inputData.getInt(KEY_TEMP_F, DEFAULT_CLIMATE_TEMP_F),
            durationMinutes = remainingMinutes.coerceAtMost(CLIMATE_DURATION_RANGE.last),
            defrost = inputData.getBoolean(KEY_DEFROST, false),
            steeringWheelHeat = inputData.getBoolean(KEY_STEERING_HEAT, false),
            seatFrontLeft = inputData.getInt(KEY_SEAT_FL, 0),
            seatFrontRight = inputData.getInt(KEY_SEAT_FR, 0),
            seatRearLeft = inputData.getInt(KEY_SEAT_RL, 0),
            seatRearRight = inputData.getInt(KEY_SEAT_RR, 0),
        )
        val result = runCatching { WearCommandRunner.execute(applicationContext, command) }.getOrNull()
        if (result?.ok != true) {
            AppLog.log("⚠ Climate auto-extend for $vin failed: ${result?.message ?: "unknown error"}")
            // Not retried: a stale extend command landing minutes late (or after
            // the user has since driven off, or manually stopped/restarted
            // climate with different settings) is worse than just ending the
            // chain here and letting the user restart manually if they still
            // want more time.
            return Result.success()
        }
        AppLog.log("Climate auto-extended for $vin: +${command.durationMinutes} min")

        val stillRemaining = remainingMinutes - command.durationMinutes
        if (stillRemaining > 0) {
            schedule(
                context = applicationContext,
                vin = vin,
                remainingMinutes = stillRemaining,
                tempF = command.tempF,
                defrost = command.defrost,
                steeringWheelHeat = command.steeringWheelHeat,
                seatFrontLeft = command.seatFrontLeft,
                seatFrontRight = command.seatFrontRight,
                seatRearLeft = command.seatRearLeft,
                seatRearRight = command.seatRearRight,
                delayMinutes = command.durationMinutes,
            )
        }
        return Result.success()
    }

    companion object {
        private const val KEY_VIN = "vin"
        private const val KEY_REMAINING_MIN = "remaining_min"
        private const val KEY_TEMP_F = "temp_f"
        private const val KEY_DEFROST = "defrost"
        private const val KEY_STEERING_HEAT = "steering_heat"
        private const val KEY_SEAT_FL = "seat_fl"
        private const val KEY_SEAT_FR = "seat_fr"
        private const val KEY_SEAT_RL = "seat_rl"
        private const val KEY_SEAT_RR = "seat_rr"

        // One unique work slot per car: a second extend chain started for the
        // same VIN (the user stops and restarts climate with a new duration
        // before the old chain finishes) replaces the stale one instead of
        // running both.
        private fun uniqueName(vin: String) = "climate_extend_$vin"

        /** Schedules the next chunk of an auto-extending climate run, to fire
         *  [delayMinutes] from now (i.e. right as the currently-running command's
         *  duration elapses). [remainingMinutes] is the total still owed across
         *  this and any further chunks after it. */
        fun schedule(
            context: Context,
            vin: String,
            remainingMinutes: Int,
            tempF: Int,
            defrost: Boolean,
            steeringWheelHeat: Boolean,
            seatFrontLeft: Int,
            seatFrontRight: Int,
            seatRearLeft: Int,
            seatRearRight: Int,
            delayMinutes: Int,
        ) {
            val data = workDataOf(
                KEY_VIN to vin,
                KEY_REMAINING_MIN to remainingMinutes,
                KEY_TEMP_F to tempF,
                KEY_DEFROST to defrost,
                KEY_STEERING_HEAT to steeringWheelHeat,
                KEY_SEAT_FL to seatFrontLeft,
                KEY_SEAT_FR to seatFrontRight,
                KEY_SEAT_RL to seatRearLeft,
                KEY_SEAT_RR to seatRearRight,
            )
            val request = OneTimeWorkRequestBuilder<ClimateExtendWorker>()
                .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
                .setInputData(data)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(uniqueName(vin), ExistingWorkPolicy.REPLACE, request)
        }

        /**
         * Cancels any pending auto-extend chain for [vin], so a stale scheduled command
         * cannot silently restart climate after the user turned it off. (Turning it back
         * ON with different settings goes through [schedule]'s own REPLACE instead.)
         *
         * This used to say it was "called whenever climate is stopped manually", and it
         * was not: it had two callers, both in AppViewModel, so stopping climate from the
         * QS tile, the widget, the watch, or the "Turn off" button on the car-is-running
         * notification left the chain armed -- and up to ten minutes later it re-issued
         * CLIMATE_ON and restarted the car.
         *
         * Now reached from all of those: the three that go through
         * WearCommandRunner.execute do it via [com.bloo.bluelink.data.runCarCommand], and
         * TileCommandRunner, which calls the repo directly, via its own
         * stopClimateAndChain. Adding four separate cancel calls was the alternative, and
         * four copies of one rule is how this drifted in the first place.
         *
         * ⚠ Still not covered: the watch's STANDALONE path, which calls WearCommandRunner
         * from `:wear` and so cannot reference this worker. That path only runs when the
         * phone is unreachable, and the chain executes on the phone, so an unreachable
         * phone fires it regardless. Closing it needs a stop marker in the shared snapshot
         * payload -- a cross-process schema change that wants a device to validate.
         */
        fun cancel(context: Context, vin: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(vin))
        }
    }
}
