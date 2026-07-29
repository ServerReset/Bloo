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
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
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
 * Runs a widget button command in the background so the tap never has to open
 * the app. On completion clears the pending-action flag and refreshes the widget.
 */
class WidgetCommandWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    /**
     * WorkManager's entry point for this worker. Reads the command parameters that were
     * packed into [inputData] by [enqueue], resolves them into a concrete [WidgetAction],
     * runs it via [execute], and -- regardless of success/failure/cancellation -- clears
     * the widget's pending-spinner flag and forces a widget redraw in the `finally` block.
     * On success it also pushes the fresh snapshot out to the watch and quick-settings tile
     * so every surface stays in sync after a command completes.
     */
    override suspend fun doWork(): Result {
        val widgetId = inputData.getInt(KEY_WIDGET_ID, -1)
        val vin = inputData.getString(KEY_VIN) ?: return Result.failure()
        val actionKey = inputData.getString(KEY_ACTION) ?: return Result.failure()
        val action = WidgetAction.fromKey(actionKey) ?: return Result.failure()
        // The toggle verb the dispatcher resolved from the pre-flip snapshot; falls
        // back to the action's own verb for enqueues that didn't resolve.
        val wearAction = inputData.getString(KEY_WEAR_ACTION) ?: action.wearAction

        val ctx = applicationContext
        try {
            execute(ctx, widgetId, vin, action, wearAction)
        } finally {
            // NonCancellable: if WorkManager stops this worker, the cleanup below is
            // the first suspension after cancellation and would otherwise throw
            // immediately - leaving the spinner overlay stuck on the widget forever.
            withContext(kotlinx.coroutines.NonCancellable) {
                runCatching { SettingsStore(ctx).setWidgetPendingAction(widgetId, null) }
                runCatching { BlooWidget().updateAll(ctx) }
            }
        }
        // Fan out the updated snapshot to all other surfaces after a successful command.
        runCatching { WearBridge.publishNow(ctx) }
        BlooTileService.requestUpdates(ctx)
        return Result.success()
    }

    /**
     * Dispatches the tapped [action] according to its [WidgetAction.Kind]:
     * - COMMAND: sends [wearAction] to the car via [WearCommandRunner.execute]; on failure,
     *   reverts the optimistic snapshot flip (see [dispatch]) and surfaces a Toast, then bails
     *   out early. On success it waits 4s (giving the car time to actually act before polling)
     *   and force-refreshes the snapshot so the widget shows the real post-command state.
     * - REFRESH: just re-fetches the current snapshot, no command sent.
     * - LOCATION: refreshes the snapshot, then reverse-geocodes the vehicle's last known
     *   lat/lon into a human-readable address and renders a small map tile, both cached
     *   per-widget for the Glance UI to read back synchronously.
     * - OPEN: a no-op here; this kind is handled by directly launching the app and never
     *   reaches the worker/enqueue path at all.
     */
    private suspend fun execute(ctx: Context, widgetId: Int, vin: String, action: WidgetAction, wearAction: String?) {
        when (action.kind) {
            WidgetAction.Kind.COMMAND -> {
                if (wearAction != null) {
                    val result = WearCommandRunner.execute(ctx, WearCommand(vin, wearAction))
                    if (!result.ok) {
                        AppLog.log("⚠ Widget command failed: ${wearAction} → ${result.message}")
                        runCatching {
                            val store = SnapshotStore(ctx)
                            store.current().vehicles.firstOrNull { it.vin == vin }?.let {
                                store.updateVehicle(WearCommandRunner.optimistic(it, WearCommandRunner.inverse(wearAction)))
                            }
                        }
                        // The button reverting is the only signal a tap had happened
                        // before this -- indistinguishable from a render glitch. A
                        // toast is the one feedback channel available with no
                        // activity/UI context, matching how in-app command failures
                        // already surface a message via runCommand/AppViewModel.
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                ctx,
                                result.message ?: "Widget command failed",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                        return
                    }
                    AppLog.log("Widget: ${wearAction} → ok")
                }
                kotlinx.coroutines.delay(4000)
                WearCommandRunner.refresh(ctx, vin)
                AppLog.log("Widget: refreshed after command")
            }

            WidgetAction.Kind.REFRESH -> {
                WearCommandRunner.refresh(ctx, vin)
                AppLog.log("Widget refresh")
            }

            WidgetAction.Kind.LOCATION -> {
                runCatching { WearCommandRunner.refresh(ctx, vin) }
                val snap = runCatching { SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin } }.getOrNull()
                val lat = snap?.lat
                val lon = snap?.lon
                if (lat != null && lon != null && !(lat == 0.0 && lon == 0.0)) {
                    runCatching {
                        // Shared with the phone's Location/Info pebbles and the
                        // watch's Location card -- this used to build its own
                        // separate street-address string inline (the same logic,
                        // just not shared), which is exactly how the OTHER two
                        // surfaces ended up on the coarser "city, state"-only
                        // formatPlaceName instead of this one's real address.
                        val addr = geocode(ctx, lat, lon)?.let { com.bloo.bluelink.data.formatPlaceName(it) }
                        if (!addr.isNullOrBlank()) runCatching { SettingsStore(ctx).setWidgetLocationAddress(widgetId, addr) }
                    }
                    downloadAndCacheMapTile(ctx, widgetId, lat, lon)
                }
            }

            WidgetAction.Kind.OPEN -> { /* opens the app directly, never enqueued */ }
        }
    }

    /**
     * Reverse-geocode with a hard timeout. Uses the non-blocking listener API on
     * API 33+ (the legacy blocking overload can hang and stall the worker — and the
     * pending-spinner clear — indefinitely).
     */
    private suspend fun geocode(ctx: Context, lat: Double, lon: Double): android.location.Address? {
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

    /** Fetch a 2×2 OpenStreetMap tile mosaic around the car and cache it as a PNG
     *  the widget renders in its location box. */
    private suspend fun downloadAndCacheMapTile(ctx: Context, widgetId: Int, lat: Double, lon: Double) {
        withContext(Dispatchers.IO) {
            runCatching {
                // Standard Web Mercator (Slippy Map) tile math: at zoom level `zoom` the world
                // is an n×n grid of 256px tiles. xFull/yFull are the car's *fractional* tile
                // coordinates -- the integer part picks the tile, the fractional part is where
                // inside that tile the car actually sits (used below to place the pin and to
                // decide which of the 4 neighboring tiles to stitch together).
                val zoom = 15
                val n = 1 shl zoom
                val xFull = (lon + 180.0) / 360.0 * n
                val latRad = Math.toRadians(lat)
                val yFull = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n
                val xt = xFull.toInt()
                val yt = yFull.toInt()
                val xOff = xFull - xt
                val yOff = yFull - yt
                // Pick the top-left tile of the 2x2 mosaic: if the car sits in the right/bottom
                // half of its tile, that tile becomes the top-left of the mosaic (so the car ends
                // up roughly centered); otherwise the tile to its left/above is used instead.
                // Clamp so we never request tile -1 or n (tile servers 404 those).
                val x0 = (if (xOff > 0.5) xt else xt - 1).coerceAtLeast(0)
                val y0 = (if (yOff > 0.5) yt else yt - 1).coerceAtLeast(0)

                // 512x512 canvas = 2x2 grid of 256px tiles, drawn one at a time below.
                val stitched = android.graphics.Bitmap.createBitmap(512, 512, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(stitched)

                for (dy in 0..1) {
                    for (dx in 0..1) {
                        // Each tile is fetched and drawn independently inside its own runCatching,
                        // so one failed/edge-of-world tile just leaves a gap instead of aborting
                        // the whole mosaic.
                        runCatching {
                            val tx = x0 + dx
                            val ty = y0 + dy
                            if (tx > n - 1 || ty > n - 1) return@runCatching
                            val url = java.net.URL("https://tile.openstreetmap.org/$zoom/$tx/$ty.png")
                            val conn = url.openConnection() as java.net.HttpURLConnection
                            conn.setRequestProperty("User-Agent", "Bloo/1.0 (Android; widget location map)")
                            conn.connectTimeout = 5000
                            conn.readTimeout = 5000
                            // try/finally so disconnect() and the stream close always run,
                            // even if decodeStream throws on a corrupt tile (was leaking the
                            // connection on that path).
                            val tile = try {
                                conn.connect()
                                conn.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
                            } finally {
                                conn.disconnect()
                            }
                            if (tile != null) {
                                canvas.drawBitmap(tile, (dx * 256).toFloat(), (dy * 256).toFloat(), null)
                                tile.recycle()
                            }
                        }
                    }
                }

                // Draw a pin at the car's exact position.
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
        // Keys used to pack/unpack the WorkManager Data bundle (see enqueue/doWork) --
        // WorkManager persists this data, so it must be primitive/String, not the enum itself.
        const val KEY_WIDGET_ID = "widget_id"
        const val KEY_VIN = "vin"
        const val KEY_ACTION = "action"
        const val KEY_WEAR_ACTION = "wear_action"

        /**
         * Optimistically flip the snapshot, mark the button pending, refresh the
         * widget, and queue the background command. Shared by [WidgetActionCallback]
         * (the no-app-open path) and [WidgetAuthActivity] (after the auth prompt).
         */
        suspend fun dispatch(ctx: Context, widgetId: Int, vin: String, action: WidgetAction) {
            var resolved = action.wearAction
            val wa = action.wearAction
            // Resolve the toggle direction and compute the flipped snapshot WITHOUT
            // writing it yet -- we only commit the optimistic flip below once the work
            // is actually enqueued (see enqueue()'s acceptance return).
            var store: SnapshotStore? = null
            var flipped: VehicleSnapshot? = null
            if (wa != null) {
                runCatching {
                    val s = SnapshotStore(ctx)
                    s.current().vehicles.firstOrNull { it.vin == vin }?.let { snap ->
                        // Resolve TOGGLE_* from the PRE-flip snapshot; the worker uses this
                        // already-resolved verb (KEY_WEAR_ACTION) and never re-resolves from
                        // the store, so passing the raw toggle through made every toggle
                        // re-assert the current state.
                        val r = WearCommandRunner.resolveToggle(snap, wa)
                        resolved = r
                        store = s
                        flipped = WearCommandRunner.optimistic(snap, r)
                    }
                }
            }
            // Enqueue first; only commit the optimistic snapshot flip / pending flag when
            // the work was actually accepted. ExistingWorkPolicy.KEEP drops the new request
            // when a command for this widget is already in flight (e.g. a double-tap within
            // ~4s) -- flipping unconditionally left the widget showing a state no command
            // was sent for until the next refresh corrected it.
            val accepted = enqueue(ctx, widgetId, vin, action, resolved)
            if (!accepted) return
            flipped?.let { snap -> runCatching { store?.updateVehicle(snap) } }
            runCatching { SettingsStore(ctx).setWidgetPendingAction(widgetId, action.key) }
            runCatching { BlooWidget().updateAll(ctx) }
        }

        /**
         * Builds the WorkManager [Data] payload for a single command and enqueues it as
         * unique work keyed by widget id, so at most one [WidgetCommandWorker] instance
         * ever runs per widget at a time (see policy note below).
         *
         * Returns true when the request was actually accepted, false when
         * [ExistingWorkPolicy.KEEP] dropped it because a command for this widget was
         * already in flight. Callers use this to avoid applying an optimistic snapshot
         * flip for a command that will never run (see [dispatch]).
         */
        fun enqueue(
            ctx: Context,
            widgetId: Int,
            vin: String,
            action: WidgetAction,
            wearAction: String? = action.wearAction,
        ): Boolean {
            val data = workDataOf(
                KEY_WIDGET_ID to widgetId,
                KEY_VIN to vin,
                KEY_ACTION to action.key,
                KEY_WEAR_ACTION to wearAction,
            )
            val request = OneTimeWorkRequestBuilder<WidgetCommandWorker>()
                .setInputData(data)
                .build()
            val wm = WorkManager.getInstance(ctx)
            val name = "widget_cmd_$widgetId"
            // KEEP drops the new request when work under this name is already pending/running.
            // Detect that up front so the caller knows whether the command was actually
            // accepted -- there's an inherent narrow race here, but this is only used to
            // gate an optimistic UI flip, and the next refresh reconciles either way.
            val alreadyActive = runCatching {
                wm.getWorkInfosForUniqueWork(name).get().any { !it.state.isFinished }
            }.getOrDefault(false)
            if (alreadyActive) return false
            // One command at a time per widget: the pending spinner covers the whole
            // widget, so a second tap while one is in flight raced the first worker.
            wm.enqueueUniqueWork(name, androidx.work.ExistingWorkPolicy.KEEP, request)
            return true
        }
    }
}

/**
 * Glance in-place action callback: runs a widget button command with NO activity
 * (so the app never opens) when the widget doesn't require authentication.
 */
class WidgetActionCallback : ActionCallback {
    /**
     * Glance invokes this directly on the UI/main coroutine when a widget button wired to
     * this callback is tapped. [parameters] carries whatever was attached to the button's
     * `actionParametersOf(...)` at compose time; all three keys are required, so a missing
     * one (shouldn't happen in practice) silently no-ops the tap rather than crashing.
     */
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val widgetId = parameters[KEY_WIDGET] ?: return
        val vin = parameters[KEY_VIN] ?: return
        val action = WidgetAction.fromKey(parameters[KEY_ACTION]) ?: return
        WidgetCommandWorker.dispatch(context, widgetId, vin, action)
    }

    companion object {
        val KEY_WIDGET = ActionParameters.Key<Int>("bloo_widget_id")
        val KEY_VIN = ActionParameters.Key<String>("bloo_vin")
        val KEY_ACTION = ActionParameters.Key<String>("bloo_action")
    }
}
