package com.bloo.wear

import com.bloo.bluelink.data.WearSync
import com.bloo.wear.tile.refreshWearGlanceables
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The watch's system-managed entry point onto the Wearable Data Layer.
 *
 * FROZEN: the class name `WearListenerService` and its package are referenced by
 * name from AndroidManifest.xml (the `<service>` bound to DATA_CHANGED +
 * MESSAGE_RECEIVED for scheme `wear`, host `*`, pathPrefix `/bloo`). Renaming it
 * silently breaks every phone-to-watch push. Do not rename.
 *
 * ## What it does
 * Android instantiates (or wakes) this service whenever the phone-side app
 * touches the Data Layer — *regardless of whether the watch's own UI process is
 * running*. That "even when the app is closed" property is the whole point: it
 * lets the phone keep the watch's on-disk stores, Tiles, and complications fresh
 * in the background, and it lets result acks reach a live ViewModel the instant
 * they land.
 *
 * Two transports, two callbacks:
 *  - [onDataChanged] — batched DataItem changes (`DataClient.putDataItem` on the
 *    phone). Used for durable, last-write-wins *state*: vehicle snapshots, auth
 *    sessions, settings, presets, climate drafts, extras. Several DataItems
 *    often change in one phone-side publish, so events arrive as a batch.
 *  - [onMessageReceived] — one-shot messages (`MessageClient.sendMessage`).
 *    Used for the transient *results* of things the watch asked the phone to do:
 *    command acks, drive-sync outcomes, AI-summary outcomes. Also carries a
 *    fresh auth push in the login-handoff flow.
 *
 * Each callback dispatches on the shared [WearSync] path constants. The `:shared`
 * wire protocol (paths, keys, codecs, DTOs) is frozen — the un-rewritten phone
 * speaks it verbatim — so this file only ever *reads* those constants and codecs.
 *
 * ## Threading
 * Both callbacks run on a binder thread. All persistence (DataStore disk I/O)
 * and result handling is dispatched to [serviceScope] so the binder thread is
 * never blocked. The scope is cancelled in [onDestroy] so no work outlives the
 * service.
 */
class WearListenerService : WearableListenerService() {

    /** IO-dispatched scope for all off-binder work; torn down in [onDestroy]. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /**
     * Handle a batch of DataItem changes pushed by the phone.
     *
     * The binder-thread portion is kept minimal: filter to TYPE_CHANGED (this
     * app takes no action on TYPE_DELETED), pull each item's raw payload string
     * out of its DataMap under [WearSync.KEY_PAYLOAD], and collect the
     * `(path, payload)` pairs. All persistence then happens on [serviceScope].
     *
     * Per-item [runCatching]: one malformed or version-skewed payload (say a
     * corrupt PATH_STATE write) must not abort the rest of the same batch —
     * PATH_SETTINGS / PATH_EXTRAS / etc. commonly change together in a single
     * phone-side publish burst, and each must still be applied.
     *
     * PATH_STATE and PATH_SETTINGS both flip [tileNeedsRefresh]: the Tiles and
     * complications glance at vehicle state *and* the phone-synced theme colors
     * (resolveRoles() in BlooTileService), so either changing warrants an
     * immediate glanceable refresh rather than waiting on the Tile's own
     * freshness-interval poll (up to ~10 minutes idle).
     */
    override fun onDataChanged(events: DataEventBuffer) {
        // Drain the buffer synchronously on the binder thread — the buffer is
        // only valid for the duration of this callback — then hand the extracted
        // strings off to the coroutine scope.
        // The DataMap is carried along with the payload string, not just read
        // for it: PATH_EXTRAS also holds the car photos as Assets, and the
        // buffer these events come from is recycled the moment this returns --
        // so anything still needed inside the coroutine has to be taken now.
        val updates = events.mapNotNull { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@mapNotNull null
            val item = event.dataItem
            val map = DataMapItem.fromDataItem(item).dataMap
            val raw = map.getString(WearSync.KEY_PAYLOAD) ?: return@mapNotNull null
            Triple(item.uri.path, raw, map)
        }
        if (updates.isEmpty()) return

        serviceScope.launch {
            var tileNeedsRefresh = false
            updates.forEach { (path, raw, dataMap) ->
                runCatching {
                    when (path) {
                        WearSync.PATH_STATE -> {
                            WearStateWriter.persistState(applicationContext, raw)
                            tileNeedsRefresh = true
                        }
                        WearSync.PATH_AUTH -> {
                            WearStateWriter.persistAuth(applicationContext, raw)
                            // Nudge a live WearViewModel so a watch parked on its
                            // login screen (having tapped "Set up on phone")
                            // AUTO-ADVANCES the moment the session lands, instead
                            // of only noticing on the next manual resync/launch.
                            WearAuthEvents.emit()
                        }
                        WearSync.PATH_SETTINGS -> {
                            WearStateWriter.persistSettings(applicationContext, raw)
                            tileNeedsRefresh = true
                        }
                        WearSync.PATH_PRESETS -> WearStateWriter.persistPresets(applicationContext, raw)
                        WearSync.PATH_CLIMATE -> WearStateWriter.persistClimate(applicationContext, raw)
                        WearSync.PATH_EXTRAS -> {
                            WearStateWriter.persistExtras(applicationContext, raw)
                            // The photos ride in the same item as Assets, whose
                            // bytes have to be pulled explicitly -- an Asset in
                            // a DataMap is a handle, not a payload. The VIN list
                            // comes from the JSON we just persisted, so a car
                            // removed on the phone stops being fetched here on
                            // the same beat rather than one publish later.
                            val vins = runCatching { WearSync.decodeExtras(raw).images.keys }
                                .getOrDefault(emptySet())
                            // prune runs even when vins is empty -- removing
                            // the LAST car is the one case an isNotEmpty guard
                            // around both calls would have skipped it
                            // entirely, leaving that car's cached photo file
                            // on the watch forever.
                            if (vins.isNotEmpty()) WearPhotoCache.ingest(applicationContext, vins, dataMap)
                            WearPhotoCache.prune(applicationContext, vins)
                        }
                        // Other paths (pebble order, local prefs, AI/aurora
                        // toggles) are published by the watch itself, not the
                        // phone; the service takes no action on its own writes.
                    }
                }
            }
            // Refresh Tiles + complications once per batch, after all writes have
            // landed, so the glanceable surfaces reflect the newest snapshot.
            if (tileNeedsRefresh) runCatching { refreshWearGlanceables(applicationContext) }
        }
    }

    /**
     * Handle a one-shot message pushed by the phone — a *result* the watch was
     * waiting on. The raw bytes are decoded to UTF-8 and dispatched by path.
     *
     * Every branch does two things off [serviceScope]:
     *  1. Emits the decoded result on the matching in-process event bus
     *     ([WearCommandEvents] / [WearSyncEvents] / [WearAiEvents]) so a
     *     currently-running WearViewModel resolves its optimistic/busy state
     *     into a real success-or-failure outcome the user sees.
     *  2. Posts a system notification as a *backstop* for when the app isn't
     *     running to consume that bus event — except for AI success, which is
     *     already visible on the AI card once the extras push lands, so notifying
     *     there would just be noise.
     *
     * Per-branch [runCatching] guards the decode+emit; a null decode short-
     * circuits with `return@launch` (an unrecognised or truncated payload is
     * dropped rather than crashing the service). Unknown paths fall through the
     * `when` and are ignored.
     */
    override fun onMessageReceived(event: MessageEvent) {
        val raw = String(event.data ?: ByteArray(0))
        when (event.path) {
            WearSync.PATH_COMMAND_RESULT -> serviceScope.launch {
                runCatching {
                    val result = WearSync.decodeResult(raw) ?: return@launch
                    // Lets a live ViewModel revert the optimistic toggle and
                    // surface a message; before this ack channel existed a real
                    // failure (e.g. a BlueLink 502) left the watch stuck showing
                    // the wrong optimistic state with no error. Do NOT regress.
                    WearCommandEvents.emit(result)
                    WearNotifications.post(
                        applicationContext,
                        ("result" + result.vin + result.action).hashCode(),
                        if (result.ok) "Command succeeded" else "Command failed",
                        result.message ?: "Done",
                    )
                }
            }
            WearSync.PATH_SYNC_RESULT -> serviceScope.launch {
                runCatching {
                    val result = WearSync.decodeSyncResult(raw) ?: return@launch
                    // The Settings screen's "Sync now" busy spinner clears off
                    // this; notification is the app-closed backstop.
                    WearSyncEvents.emit(result)
                    WearNotifications.post(
                        applicationContext,
                        "drivesync".hashCode(),
                        if (result.ok) "Drive sync complete" else "Drive sync failed",
                        result.message ?: if (result.ok) "Settings synced" else "Couldn't sync settings",
                    )
                }
            }
            WearSync.PATH_AI_RESULT -> serviceScope.launch {
                runCatching {
                    val result = WearSync.decodeAiResult(raw) ?: return@launch
                    // Clears the AI card's aiBusy spinner. Only notify on failure:
                    // a success is already visible once the extras push lands.
                    WearAiEvents.emit(result)
                    if (!result.ok) {
                        WearNotifications.post(
                            applicationContext,
                            ("ai" + result.vin).hashCode(),
                            "Summary failed",
                            result.message ?: "Couldn't generate a summary",
                        )
                    }
                }
            }
        }
    }
}
