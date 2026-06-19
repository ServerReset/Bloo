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
                    SettingsStore(ctx).setWidgetLocationLatLon(widgetId, lat, lon)
                    downloadAndCacheMapTile(ctx, widgetId, lat, lon)
                }
            }

            WidgetAction.Kind.OPEN -> { /* handled directly in WidgetAuthActivity */ }
        }
    }

    private suspend fun downloadAndCacheMapTile(ctx: Context, widgetId: Int, lat: Double, lon: Double) {
        runCatching {
            val zoom = 15
            val n = 1 shl zoom
            val xFull = (lon + 180.0) / 360.0 * n
            val latRad = Math.toRadians(lat)
            val yFull = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n
            val xt = xFull.toInt()
            val yt = yFull.toInt()
            val xOff = xFull - xt
            val yOff = yFull - yt
            val x0 = if (xOff > 0.5) xt else xt - 1
            val y0 = if (yOff > 0.5) yt else yt - 1

            val stitched = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(stitched)

            for (dy in 0..1) {
                for (dx in 0..1) {
                    runCatching {
                        val url = java.net.URL("https://tile.openstreetmap.org/$zoom/${x0 + dx}/${y0 + dy}.png")
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.setRequestProperty("User-Agent", "Bloo/1.0 (Android; widget location map)")
                        conn.connectTimeout = 5000
                        conn.readTimeout = 5000
                        conn.connect()
                        val tile = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                        conn.disconnect()
                        if (tile != null) {
                            canvas.drawBitmap(tile, (dx * 256).toFloat(), (dy * 256).toFloat(), null)
                            tile.recycle()
                        }
                    }
                }
            }

            // Draw a pin at the location center
            val px = ((xFull - x0) * 256).toFloat()
            val py = ((yFull - y0) * 256).toFloat()
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            paint.color = android.graphics.Color.parseColor("#CC1A73E8")
            canvas.drawCircle(px, py, 14f, paint)
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(px, py, 7f, paint)

            val file = java.io.File(ctx.cacheDir, "widget_map_$widgetId.png")
            file.outputStream().use { out -> stitched.compress(android.graphics.Bitmap.CompressFormat.PNG, 85, out) }
            stitched.recycle()
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
