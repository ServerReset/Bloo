package com.bloo.bluelink.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WearCommand
import com.bloo.bluelink.data.WearCommandRunner
import com.bloo.bluelink.tiles.BlooTileService
import com.bloo.bluelink.wear.WearBridge
import kotlinx.coroutines.withContext

/**
 * Runs a widget button command in the background so [WidgetAuthActivity] can
 * finish immediately after auth. On completion clears the pending-action flag
 * and refreshes the widget.
 */
class WidgetCommandWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val widgetId = inputData.getInt(KEY_WIDGET_ID, -1)
        val vin = inputData.getString(KEY_VIN) ?: return Result.failure()
        val actionKey = inputData.getString(KEY_ACTION) ?: return Result.failure()
        val action = WidgetAction.fromKey(actionKey) ?: return Result.failure()
        // The toggle verb WidgetAuthActivity resolved from the pre-flip snapshot;
        // falls back to the action's own verb for enqueues that didn't resolve.
        val wearAction = inputData.getString(KEY_WEAR_ACTION) ?: action.wearAction

        val ctx = applicationContext
        try {
            execute(ctx, vin, action, wearAction)
        } finally {
            // NonCancellable: if WorkManager stops this worker, the cleanup below is
            // the first suspension after cancellation and would otherwise throw
            // immediately - leaving the spinner overlay stuck on the widget forever.
            withContext(kotlinx.coroutines.NonCancellable) {
                SettingsStore(ctx).setWidgetPendingAction(widgetId, null)
                runCatching { BlooWidget().updateAll(ctx) }
            }
        }
        // Fan out the updated snapshot to all other surfaces after a successful command.
        runCatching { WearBridge.publishNow(ctx) }
        BlooTileService.requestUpdates(ctx)
        return Result.success()
    }

    private suspend fun execute(ctx: Context, vin: String, action: WidgetAction, wearAction: String?) {
        when (action.kind) {
            WidgetAction.Kind.COMMAND -> {
                if (wearAction != null) {
                    val result = WearCommandRunner.execute(ctx, WearCommand(vin, wearAction))
                    if (!result.ok) {
                        // The car never got the command: undo WidgetAuthActivity's
                        // optimistic flip so the widget doesn't keep asserting a
                        // lock/climate state that isn't true (refresh below can't be
                        // counted on to correct it - offline/expired-session failures
                        // fail the refresh too, silently).
                        runCatching {
                            val store = SnapshotStore(ctx)
                            store.current().vehicles.firstOrNull { it.vin == vin }?.let {
                                store.updateVehicle(WearCommandRunner.optimistic(it, WearCommandRunner.inverse(wearAction)))
                            }
                        }
                        return
                    }
                }
                // Brief pause for the car to process the command, then fetch actual state.
                kotlinx.coroutines.delay(4000)
                WearCommandRunner.refresh(ctx, vin)
            }

            WidgetAction.Kind.REFRESH -> WearCommandRunner.refresh(ctx, vin)

            // OPEN and LOCATION launch intents directly from WidgetAuthActivity.
            WidgetAction.Kind.LOCATION, WidgetAction.Kind.OPEN -> {}
        }
    }

    companion object {
        const val KEY_WIDGET_ID = "widget_id"
        const val KEY_VIN = "vin"
        const val KEY_ACTION = "action"
        const val KEY_WEAR_ACTION = "wear_action"

        fun enqueue(
            ctx: Context,
            widgetId: Int,
            vin: String,
            action: WidgetAction,
            wearAction: String? = action.wearAction,
        ) {
            val data = workDataOf(
                KEY_WIDGET_ID to widgetId,
                KEY_VIN to vin,
                KEY_ACTION to action.key,
                KEY_WEAR_ACTION to wearAction,
            )
            val request = OneTimeWorkRequestBuilder<WidgetCommandWorker>()
                .setInputData(data)
                .build()
            // One command at a time per widget: the pending spinner covers the whole
            // widget, so a second tap while one is in flight raced the first worker
            // for the shared pending flag (first to finish cleared the other's
            // spinner) and stacked duplicate car commands.
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "widget_cmd_$widgetId", androidx.work.ExistingWorkPolicy.KEEP, request,
            )
        }
    }
}
