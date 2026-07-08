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
import com.bloo.bluelink.tiles.BlooTileService
import com.bloo.bluelink.wear.WearBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

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
            execute(ctx, widgetId, vin, action, wearAction)
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

    private suspend fun execute(ctx: Context, widgetId: Int, vin: String, action: WidgetAction, wearAction: String?) {
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

            WidgetAction.Kind.LOCATION -> {
                WearCommandRunner.refresh(ctx, vin)
                val snap = SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin }
                val lat = snap?.lat
                val lon = snap?.lon
                if (lat != null && lon != null) {
                    runCatching {
                        val a = geocode(ctx, lat, lon)
                        val addr = a?.let {
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

    /**
     * Reverse-geocode with a hard timeout. Uses the non-blocking listener API on
     * API 33+ (the legacy blocking overload can hang with no timeout and would stall
     * the worker — and the pending-spinner clear — indefinitely).
     */
    private suspend fun geocode(ctx: Context, lat: Double, lon: Double): android.location.Address? {
        // Skip geocoding for default/null coordinates — the car is not at (0, 0).
        if (lat == 0.0 && lon == 0.0) return null
        if (!android.location.Geocoder.isPresent()) return null
        val geocoder = android.location.Geocoder(ctx, java.util.Locale.getDefault())
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            withTimeoutOrNull(6000) {
                runCatching {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lon, 1, object : android.location.Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<android.location.Address>) {
                                if (cont.isActive) cont.resume(addresses.firstOrNull())
                            }
                            override fun onError(message: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    }
                }.getOrNull()
            }
        } else {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(6000) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
                }
            }
        }
    }

    private suspend fun downloadAndCacheMapTile(ctx: Context, widgetId: Int, lat: Double, lon: Double) {
        withContext(Dispatchers.IO) {
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
            // Clamp tile coords so we never request tile -1 or n, which tile servers
            // return 404 for. When flush against the edge we can only fetch 1×2 or 2×1.
            val x0 = (if (xOff > 0.5) xt else xt - 1).coerceAtLeast(0)
            val y0 = (if (yOff > 0.5) yt else yt - 1).coerceAtLeast(0)
            val x1 = (x0 + 1).coerceAtMost(n - 1)
            val y1 = (y0 + 1).coerceAtMost(n - 1)

            val stitched = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(stitched)

            for (dy in 0..1) {
                for (dx in 0..1) {
                    runCatching {
                        val tx = x0 + dx
                        val ty = y0 + dy
                        if (tx > n - 1 || ty > n - 1) return@runCatching
                        val url = java.net.URL("https://tile.openstreetmap.org/$zoom/$tx/$ty.png")
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
