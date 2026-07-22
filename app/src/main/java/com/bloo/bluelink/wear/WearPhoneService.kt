package com.bloo.bluelink.wear

import com.bloo.bluelink.data.Ai
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.ClimateSyncStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WearAction
import com.bloo.bluelink.data.WearAiResult
import com.bloo.bluelink.data.WearCommandRunner
import com.bloo.bluelink.data.WearExtras
import com.bloo.bluelink.data.WearSync
import com.bloo.bluelink.data.WearSyncResult
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

/**
 * Receives the watch's messages on the phone. Bound by the system whenever a
 * Data Layer message arrives on a `/bloo` path, even if the phone app's UI
 * isn't running — so "lock from my watch" works with the phone in your pocket.
 *
 * Mechanism: [WearableListenerService] is a manifest-declared bound Service that Play
 * Services starts on demand (with no persistent process needed) whenever Wearable Data
 * Layer traffic for this app arrives from a connected node. There are two distinct kinds
 * of traffic it can deliver, handled by two separate callbacks below:
 * - [onMessageReceived]: a one-shot, fire-and-forget message (`MessageClient.sendMessage`)
 *   used here for request/response-style RPCs -- the watch sends a command, the phone runs
 *   it and sends a reply message back to the same `event.sourceNodeId`. Messages aren't
 *   persisted or replayed; if the phone is unreachable when sent, it's simply lost (the
 *   watch's UI has to handle that as a timeout, not an explicit failure).
 * - [onDataChanged]: fired when a *Data Item* (`DataClient.putDataItem`, written by the
 *   watch the same way [WearBridge] writes them phone-side) changes. Data items ARE
 *   persisted and synced-on-reconnect, so they suit state the phone must eventually learn
 *   even if the phone was offline when the watch wrote it (climate drafts, presets, toggle
 *   states edited on-watch).
 * The system may deliver either callback on any binder thread, so both immediately hand
 * off to [scope] (an IO-dispatched coroutine scope owned by this service instance) to do
 * the actual work, keeping the callbacks themselves fast and non-blocking.
 */
class WearPhoneService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        // Guards the PATH_EXTRAS read-modify-write in runAiSummary/
        // setWeatherFromDeviceLocation -- both read the current extras item,
        // patch one field, and republish the whole thing. Two requests landing
        // close together (e.g. an AI summary for one car plus a weather-location
        // request) could otherwise both read the same stale snapshot, and
        // whichever publish lands second would silently drop the other's update.
        private val extrasMutex = kotlinx.coroutines.sync.Mutex()
    }

    /**
     * Routes an incoming watch message by its Data Layer path (`event.path`), each path
     * corresponding to one request "kind" the watch can send. [event.data] is the raw byte
     * payload the watch encoded with [WearSync]'s matching `encode*`/`decode*` pair; a
     * failure to decode (malformed/unexpected payload) simply returns and drops the message.
     * - [WearSync.PATH_COMMAND]: a car command (lock/unlock/climate/etc) OR one of the two
     *   special pseudo-commands handled inline below (AI_SUMMARY, WEATHER_DEVICE_LOCATION)
     *   that don't touch the car at all. Ordinary commands run via [WearCommandRunner.execute]
     *   and always send a reply on [WearSync.PATH_COMMAND_RESULT] back to `event.sourceNodeId`
     *   (the specific watch node that sent the request) so its UI knows the outcome, then
     *   fans the refreshed state out to every other surface via [WearBridge.refreshAllSurfaces].
     * - [WearSync.PATH_SYNC_REQUEST]: the watch asking the phone to either force a status
     *   refresh or kick off a Drive settings sync; REFRESH replies via the general surface
     *   fan-out (no dedicated result message), DRIVE_SYNC replies with an explicit outcome
     *   on [WearSync.PATH_SYNC_RESULT] since the watch needs to know success/failure/reason.
     */
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearSync.PATH_COMMAND -> {
                val command = WearSync.decodeCommand(String(event.data ?: ByteArray(0))) ?: return
                if (command.action == WearAction.AI_SUMMARY) {
                    scope.launch {
                        val ctx = applicationContext
                        val result = runAiSummary(ctx, command.vin)
                        runCatching {
                            Tasks.await(
                                Wearable.getMessageClient(ctx).sendMessage(
                                    event.sourceNodeId,
                                    WearSync.PATH_AI_RESULT,
                                    WearSync.encodeAiResult(result).toByteArray(),
                                )
                            )
                        }
                    }
                    return
                }
                if (command.action == WearAction.WEATHER_DEVICE_LOCATION) {
                    // Fire-and-forget, like resync: the watch's own extras
                    // collector already reacts the moment the fresh weather
                    // publishes below, no dedicated result message needed.
                    scope.launch { runCatching { setWeatherFromDeviceLocation(applicationContext) } }
                    return
                }
                scope.launch {
                    val result = WearCommandRunner.execute(applicationContext, command)
                    if (result.ok) AppLog.log("Phone relay: ${command.action} → ok")
                    else AppLog.log("⚠ Phone relay: ${command.action} → ${result.message}")
                    runCatching {
                        Tasks.await(
                            Wearable.getMessageClient(applicationContext).sendMessage(
                                event.sourceNodeId,
                                WearSync.PATH_COMMAND_RESULT,
                                WearSync.encodeResult(result).toByteArray(),
                            )
                        )
                    }
                    val ctx = applicationContext
                    WearBridge.refreshAllSurfaces(ctx)
                }
            }

            WearSync.PATH_SYNC_REQUEST -> {
                val command = WearSync.decodeCommand(String(event.data ?: ByteArray(0)))
                scope.launch {
                    val ctx = applicationContext
                    when (command?.action) {
                        WearAction.REFRESH -> {
                            WearCommandRunner.refresh(ctx, command.vin)
                            WearBridge.refreshAllSurfaces(ctx)
                        }
                        WearAction.DRIVE_SYNC -> {
                            val outcome = WearBridge.driveSync(ctx)
                            val result = if (outcome == null) {
                                WearSyncResult(ok = false, message = "Drive sync isn't set up on this phone")
                            } else {
                                WearSyncResult(ok = outcome.uploaded, message = outcome.error)
                            }
                            runCatching {
                                Tasks.await(
                                    Wearable.getMessageClient(ctx).sendMessage(
                                        event.sourceNodeId,
                                        WearSync.PATH_SYNC_RESULT,
                                        WearSync.encodeSyncResult(result).toByteArray(),
                                    )
                                )
                            }
                        }
                        else -> WearBridge.refreshAllSurfaces(ctx)
                    }
                }
            }
        }
    }

    /**
     * The watch writes data items too: its live climate draft on
     * [WearSync.PATH_CLIMATE] and presets it created/edited on
     * [WearSync.PATH_PRESETS]. Persist both so the phone reflects them.
     *
     * [events] is a buffer of every Data Item that changed since the last delivery, which
     * can batch multiple paths together (and the buffer must not outlive this call, hence
     * everything needed from it is extracted synchronously below before handing off to the
     * coroutine). Each entry can be a TYPE_CHANGED (item written/updated) or TYPE_DELETED
     * event; only CHANGED is handled since nothing here is ever cleared by deleting an item.
     * The path allowlist below is a belt-and-suspenders filter -- in practice this service
     * is only ever notified for paths it's registered interest in, but Data Layer delivery
     * can occasionally include stragglers from other apps/paths, so anything unrecognized is
     * dropped rather than falling through to the `when` and being silently ignored there too.
     */
    override fun onDataChanged(events: DataEventBuffer) {
        val updates = events.mapNotNull { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@mapNotNull null
            val item = event.dataItem
            val path = item.uri.path
            if (path != WearSync.PATH_CLIMATE && path != WearSync.PATH_PRESETS &&
                path != WearSync.PATH_PEBBLE_ORDER && path != WearSync.PATH_LOCAL &&
                path != WearSync.PATH_AI_TOGGLE && path != WearSync.PATH_AURORA_TOGGLE
            ) return@mapNotNull null
            val raw = DataMapItem.fromDataItem(item).dataMap.getString(WearSync.KEY_PAYLOAD)
                ?: return@mapNotNull null
            path to raw
        }
        if (updates.isEmpty()) return
        scope.launch {
            updates.forEach { (path, raw) ->
              runCatching {
                when (path) {
                    WearSync.PATH_CLIMATE -> ClimateSyncStore(applicationContext).save(raw)
                    WearSync.PATH_PRESETS -> {
                        val store = SettingsStore(applicationContext)
                        WearSync.decodePresets(raw).byVin.forEach { (vin, list) ->
                            store.setClimatePresets(vin, list)
                        }
                    }
                    WearSync.PATH_PEBBLE_ORDER -> {
                        val po = WearSync.decodePebbleOrder(raw) ?: return@forEach
                        if (po.vin.isNotBlank() && po.order.isNotEmpty()) {
                            SettingsStore(applicationContext).setSectionOrder(po.vin, po.order)
                            // Mirror the saved order straight back so every device
                            // (and the watch's optimistic override) lines up.
                            runCatching {
                                val appearance = SettingsStore(applicationContext).appearance.first()
                                WearBridge.publishSettingsNow(applicationContext, appearance)
                            }
                        }
                    }
                    WearSync.PATH_LOCAL -> {
                        val payload = WearSync.decodeLocal(raw) ?: return@forEach
                        val store = SettingsStore(applicationContext)
                        store.setUiScale(payload.uiScale.coerceIn(0.8f, 1.4f))
                        payload.unitSystem?.let { store.setUnitSystem(it) }
                        // Backup record only -- see WearLocalPayload's doc comment
                        // for why this is one-directional (watch -> phone) and
                        // never pushed back down to reconfigure the watch.
                        store.setWatchPinLock(payload.watchPinLockEnabled, payload.watchPinLockTiming)
                    }
                    WearSync.PATH_AI_TOGGLE -> {
                        val payload = WearSync.decodeAiToggle(raw) ?: return@forEach
                        val store = SettingsStore(applicationContext)
                        store.setAiEnabled(payload.enabled)
                        // Mirror straight back so the watch's optimistic toggle
                        // settles on the confirmed value, same as pebble order.
                        runCatching {
                            val appearance = store.appearance.first()
                            WearBridge.publishSettingsNow(applicationContext, appearance)
                        }
                    }
                    WearSync.PATH_AURORA_TOGGLE -> {
                        val payload = WearSync.decodeAuroraToggle(raw) ?: return@forEach
                        val store = SettingsStore(applicationContext)
                        store.setAuroraBackground(payload.enabled)
                        // null means this push only touched `enabled` -- leave
                        // the phone's current colour mode alone.
                        payload.colorMode?.let { store.setAuroraColorMode(it) }
                        // Same settle-back pattern as the AI toggle above.
                        runCatching {
                            val appearance = store.appearance.first()
                            WearBridge.publishSettingsNow(applicationContext, appearance)
                        }
                    }
                }
              }
            }
        }
    }

    /** The system destroys this service once it's idle with nothing pending; cancel
     *  [scope] so any in-flight coroutines are torn down rather than leaking. */
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** Generate an AI summary for the watch's [WearAction.AI_SUMMARY] request,
     *  always returning a result so the watch's busy spinner resolves either
     *  way -- unlike the extras push (only sent on success), this is the
     *  watch's only feedback for a disabled, unsupported, or failed request. */
    private suspend fun runAiSummary(ctx: android.content.Context, vin: String): WearAiResult {
        if (!SettingsStore(ctx).aiEnabled()) {
            return WearAiResult(vin, ok = false, message = "AI summaries are turned off in Settings")
        }
        if (!Ai(ctx).isSupported()) {
            return WearAiResult(vin, ok = false, message = "AI summaries aren't supported on this phone")
        }
        val snap = SnapshotStore(ctx).current().vehicles.firstOrNull { it.vin == vin }
            ?: return WearAiResult(vin, ok = false, message = "Car not found")
        val summary = runCatching {
            Ai(ctx).summarize(
                "${snap.name} vehicle status: The doors are ${if (snap.locked == true) "locked" else "unlocked"}. " +
                    "Climate is ${if (snap.climateOn == true) "on" else "off"}." +
                    "${snap.percent?.let { " Battery is at $it%." } ?: ""}${snap.rangeMi?.let { " Range is $it miles." } ?: ""}",
            )
        }.getOrNull() ?: return WearAiResult(vin, ok = false, message = "Couldn't generate a summary")

        // Read the current extras item from the Data Layer, patch the ai map, republish.
        extrasMutex.withLock {
            val dataClient = Wearable.getDataClient(ctx)
            val items = runCatching { Tasks.await(dataClient.getDataItems(android.net.Uri.parse("wear://*${WearSync.PATH_EXTRAS}"))) }.getOrNull()
            val existing = items?.map { WearSync.decodeExtras(DataMapItem.fromDataItem(it).dataMap.getString(WearSync.KEY_PAYLOAD)) }?.firstOrNull() ?: WearExtras()
            items?.release()
            val updated = existing.copy(ai = existing.ai + (vin to summary))
            WearBridge.publishExtras(ctx, updated)
        }
        AppLog.log("AI summary generated for ${snap.name}")
        return WearAiResult(vin, ok = true)
    }

    /** Handle the watch's [WearAction.WEATHER_DEVICE_LOCATION] request: the
     *  watch has no weather fetch of its own, so this sets the phone's home
     *  weather location from the phone's OWN GPS (mirrors the phone Settings
     *  screen's "My location" action) and republishes fresh weather to the
     *  watch immediately rather than waiting for the next natural refresh. */
    private suspend fun setWeatherFromDeviceLocation(ctx: android.content.Context) {
        val store = SettingsStore(ctx)
        if (!store.setWeatherFromDeviceLocation()) {
            AppLog.log("⚠ Watch weather-location request: no device location available")
            return
        }
        val appearance = store.appearance.first()
        val lat = appearance.weatherLat
        val lon = appearance.weatherLon
        if (lat == null || lon == null) return
        val weather = runCatching { com.bloo.bluelink.data.WeatherApi.fetch(lat, lon) }.getOrNull() ?: return
        extrasMutex.withLock {
            val dataClient = Wearable.getDataClient(ctx)
            val items = runCatching { Tasks.await(dataClient.getDataItems(android.net.Uri.parse("wear://*${WearSync.PATH_EXTRAS}"))) }.getOrNull()
            val existing = items?.map { WearSync.decodeExtras(DataMapItem.fromDataItem(it).dataMap.getString(WearSync.KEY_PAYLOAD)) }?.firstOrNull() ?: WearExtras()
            items?.release()
            WearBridge.publishExtras(ctx, existing.copy(homeWeather = weather.toWear()))
        }
        AppLog.log("Weather location set from watch request")
    }
}
