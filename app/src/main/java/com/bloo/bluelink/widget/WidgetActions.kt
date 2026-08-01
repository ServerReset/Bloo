package com.bloo.bluelink.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.ExistingWorkPolicy
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WearCommand
import com.bloo.bluelink.data.WearCommandRunner

/** Typed action-parameter keys shared by the widget's tap callbacks. */
object WidgetKeys {
    val VIN = ActionParameters.Key<String>("bloo.widget.vin")
    val ACTION = ActionParameters.Key<String>("bloo.widget.action")
}

/**
 * Handles a tap on a stateful control (Lock / Climate / Charge).
 *
 * Flow, mirroring the app's own command discipline: resolve the toggle direction
 * from the CURRENT snapshot, write the optimistic result to [SnapshotStore] and
 * repaint instantly ([CarWidget.updateAll]), then run the real command in a
 * [WidgetCommandWorker] (survives this async-receiver's short lifetime). The worker
 * reverts the optimistic flip if the command fails — so an offline/failed command
 * never leaves the widget showing a lock/charge state that didn't actually happen.
 */
class WidgetCommandAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val vin = parameters[WidgetKeys.VIN] ?: return
        val actionKey = parameters[WidgetKeys.ACTION] ?: return
        val action = WidgetAction.fromKey(actionKey) ?: return
        val wearAction = action.wearAction ?: return

        val store = SnapshotStore(context)
        val snap = store.current().vehicles.firstOrNull { it.vin == vin } ?: return

        // Resolve TOGGLE_* to an explicit verb from the pre-flip snapshot (the
        // command runner re-reads the store to decide direction, so an already-
        // flipped snapshot would invert the command — see WearCommandRunner).
        val resolved = WearCommandRunner.resolveToggle(snap, wearAction)

        // Only stateful toggles get an optimistic flip; momentary verbs (flash/horn)
        // have no snapshot field to change.
        if (action.kind == WidgetAction.Kind.TOGGLE) {
            store.updateVehicle(WearCommandRunner.optimistic(snap, resolved))
            CarWidget().updateAll(context)
        }

        WidgetCommandWorker.enqueue(context, vin, resolved, revertIfToggle = action.kind == WidgetAction.Kind.TOGGLE)
    }
}

/** Handles a tap on Refresh: light background re-fetch, then repaint. */
class WidgetRefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val vin = parameters[WidgetKeys.VIN] ?: ""
        WidgetCommandWorker.enqueueRefresh(context, vin)
    }
}

/** Handles a tap on the car-switcher chevron: advance selection, repaint. */
class WidgetSwitchCarAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SnapshotStore(context).selectNext()
        CarWidget().updateAll(context)
    }
}

/**
 * Runs a widget-issued car command (or refresh) off the async-receiver's thread,
 * in WorkManager, so it survives the widget process being torn down mid-flight.
 * Routes everything through the shared [WearCommandRunner] (driving guard, account
 * lock, and toggle discipline all live there). On a failed toggle it reverts the
 * optimistic flip locally and repaints, so the widget can't get stuck showing a
 * state the car never reached.
 */
class WidgetCommandWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val vin = inputData.getString(KEY_VIN).orEmpty()
        val action = inputData.getString(KEY_ACTION)
        val refresh = inputData.getBoolean(KEY_REFRESH, false)
        val revert = inputData.getBoolean(KEY_REVERT, false)

        if (refresh) {
            runCatching { WearCommandRunner.refresh(ctx, vin, force = true) }
            CarWidget().updateAll(ctx)
            return Result.success()
        }
        if (action == null) return Result.failure()

        val result = WearCommandRunner.execute(ctx, WearCommand(vin = vin, action = action))
        if (!result.ok && revert) {
            // Undo the optimistic flip the tap callback applied.
            runCatching {
                val store = SnapshotStore(ctx)
                store.current().vehicles.firstOrNull { it.vin == vin }?.let {
                    store.updateVehicle(WearCommandRunner.optimistic(it, WearCommandRunner.inverse(action)))
                }
            }
        }
        // Repaint either way — success confirmed the flip, failure just reverted it.
        // Not Result.retry(): the failure is already surfaced (reverted state), and a
        // WorkManager retry would re-fire the car command, which we don't want.
        CarWidget().updateAll(ctx)
        return Result.success()
    }

    companion object {
        private const val KEY_VIN = "vin"
        private const val KEY_ACTION = "action"
        private const val KEY_REFRESH = "refresh"
        private const val KEY_REVERT = "revert"

        fun enqueue(context: Context, vin: String, resolvedAction: String, revertIfToggle: Boolean) {
            val req = OneTimeWorkRequestBuilder<WidgetCommandWorker>()
                .setInputData(workDataOf(KEY_VIN to vin, KEY_ACTION to resolvedAction, KEY_REVERT to revertIfToggle))
                .build()
            // Unique per (vin, action) so a double-tap can't fire two concurrent
            // sessions for the same command.
            WorkManager.getInstance(context)
                .enqueueUniqueWork("widget_cmd_${vin}_$resolvedAction", ExistingWorkPolicy.REPLACE, req)
        }

        fun enqueueRefresh(context: Context, vin: String) {
            val req = OneTimeWorkRequestBuilder<WidgetCommandWorker>()
                .setInputData(workDataOf(KEY_VIN to vin, KEY_REFRESH to true))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("widget_refresh_$vin", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
