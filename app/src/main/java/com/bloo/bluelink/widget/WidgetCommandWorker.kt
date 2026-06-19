package com.bloo.bluelink.widget

import android.content.Context
import android.net.Uri
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

        val ctx = applicationContext
        try {
            execute(ctx, widgetId, vin, action)
        } finally {
            SettingsStore(ctx).setWidgetPendingAction(widgetId, null)
            runCatching { BlooWidget().updateAll(ctx) }
        }
        return Result.success()
    }

    private suspend fun execute(ctx: Context, widgetId: Int, vin: String, action: WidgetAction) {
        when (action.kind) {
            WidgetAction.Kind.COMMAND ->
                action.wearAction?.let { WearCommandRunner.execute(ctx, WearCommand(vin, it)) }

            WidgetAction.Kind.REFRESH -> WearCommandRunner.refresh(ctx, vin)

            WidgetAction.Kind.LOCATION -> {
                WearCommandRunner.refresh(ctx, vin)
                val snap = SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin }
                val lat = snap?.lat
                val lon = snap?.lon
                if (lat != null && lon != null) {
                    runCatching {
                        val results = android.location.Geocoder(ctx, java.util.Locale.getDefault())
                            .getFromLocation(lat, lon, 1)
                        val addr = results?.firstOrNull()?.let { a ->
                            buildString {
                                if (!a.thoroughfare.isNullOrBlank()) append(a.thoroughfare)
                                if (!a.subThoroughfare.isNullOrBlank()) { if (isNotEmpty()) insert(0, "${a.subThoroughfare} ") }
                                if (!a.locality.isNullOrBlank()) { if (isNotEmpty()) append(", "); append(a.locality) }
                            }.takeIf { it.isNotBlank() } ?: a.getAddressLine(0)
                        }
                        if (!addr.isNullOrBlank()) {
                            SettingsStore(ctx).setWidgetLocationAddress(widgetId, addr)
                        }
                    }
                }
            }

            WidgetAction.Kind.OPEN -> { /* handled directly in WidgetAuthActivity */ }
        }
    }

    companion object {
        const val KEY_WIDGET_ID = "widget_id"
        const val KEY_VIN = "vin"
        const val KEY_ACTION = "action"

        fun enqueue(ctx: Context, widgetId: Int, vin: String, action: WidgetAction) {
            val data = workDataOf(KEY_WIDGET_ID to widgetId, KEY_VIN to vin, KEY_ACTION to action.key)
            val request = OneTimeWorkRequestBuilder<WidgetCommandWorker>()
                .setInputData(data)
                .build()
            WorkManager.getInstance(ctx).enqueue(request)
        }
    }
}
