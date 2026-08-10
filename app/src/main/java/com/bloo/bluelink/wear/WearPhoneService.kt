package com.bloo.bluelink.wear

import com.bloo.bluelink.data.Ai
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.AiSummaryStore
import com.bloo.bluelink.data.ClimateSyncStore
import com.bloo.bluelink.data.Notifications
import com.bloo.bluelink.data.SessionStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WearAction
import com.bloo.bluelink.data.WearAiResult
import com.bloo.bluelink.data.WearClimateState
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
import java.util.concurrent.TimeUnit
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

        /** Attempts per result message, and the first backoff between them
         *  (doubling: 300ms, then 600ms). See sendResult. */
        private const val RESULT_ATTEMPTS = 3
        private const val RESULT_RETRY_MS = 300L
    }

    /**
     * Sends one result message back to the watch, retrying briefly.
     *
     * These are the replies the watch is actively waiting on -- a command's real
     * outcome, a Drive sync's, an AI summary's -- and unlike a DataItem a message
     * is not queued: it needs the node reachable at that instant or it is simply
     * gone. The watch then sits on a spinner until its own timeout and tells the
     * user nothing useful. A command takes seconds to run, which is plenty of
     * time for a watch screen to blank or a Bluetooth link to blip, so the one
     * moment we most need to reach it is the one most likely to fail. Two extra
     * attempts a few hundred ms apart cover that without pretending a genuinely
     * absent watch will answer.
     */
    private suspend fun sendResult(nodeId: String, path: String, payload: String) {
        // Shared retry loop (retryWithBackoff, :shared). This suspend fun is only ever called
        // from the message-handling coroutine, which is off the main thread, satisfying
        // Tasks.await's requirement.
        com.bloo.bluelink.data.retryWithBackoff(
            attempts = RESULT_ATTEMPTS,
            firstDelayMs = RESULT_RETRY_MS,
            onExhausted = { AppLog.log("⚠ Watch reply undelivered ($path): ${it?.message}") },
        ) {
            Tasks.await(
                Wearable.getMessageClient(applicationContext).sendMessage(
                    nodeId, path, payload.toByteArray(),
                ),
                10, TimeUnit.SECONDS,
            )
        }
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
                                ),
                                10, TimeUnit.SECONDS,
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
                    // runCarCommand: this is the watch's normal (relayed) path, so a climate
                    // stop from the watch cancels the auto-extend chain here.
                    val result = com.bloo.bluelink.data.runCarCommand(applicationContext, command)
                    if (result.ok) AppLog.log("Phone relay: ${command.action} → ok")
                    else AppLog.log("⚠ Phone relay: ${command.action} → ${result.message}")
                    sendResult(event.sourceNodeId, WearSync.PATH_COMMAND_RESULT, WearSync.encodeResult(result))
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
                        WearAction.RESYNC -> WearBridge.publishAll(ctx)
                        WearAction.DRIVE_SYNC -> {
                            val outcome = WearBridge.driveSync(ctx)
                            val result = if (outcome == null) {
                                WearSyncResult(ok = false, message = "Drive sync isn't set up on this phone")
                            } else {
                                // Success = the pass ran without error, NOT "we wrote
                                // bytes": with the content-hash gate a no-op sync
                                // (nothing changed) legitimately may not re-upload, and
                                // that must not read as "Drive sync failed" on the watch.
                                WearSyncResult(ok = outcome.error == null, message = outcome.error)
                            }
                            sendResult(event.sourceNodeId, WearSync.PATH_SYNC_RESULT, WearSync.encodeSyncResult(result))
                        }
                        // Anything else on this path is the watch's "Sync from
                        // phone" button, which means everything -- not just the
                        // state + settings refreshAllSurfaces covers. See
                        // WearBridge.publishAll.
                        else -> WearBridge.publishAll(ctx)
                    }
                }
            }

            WearSync.PATH_SETUP_REQUEST -> {
                // The watch asked us to finish setting up its sign-in (the "Set up on
                // phone" handoff). Two cases, both credential-free — auth only ever
                // flows phone→watch:
                //  - Already signed in → push the session down NOW so the watch's
                //    PATH_AUTH listener advances it past its login screen.
                //  - Not signed in → post a notification opening the app's login, so
                //    the user completes sign-in here; login-success then pushes auth
                //    down automatically (see AppViewModel.login / finishKiaLogin).
                scope.launch {
                    val ctx = applicationContext
                    val signedIn = runCatching { SessionStore(ctx).loggedInBrands().isNotEmpty() }.getOrDefault(false)
                    if (signedIn) {
                        AppLog.log("Watch setup request: already signed in, pushing auth")
                        runCatching { WearBridge.publishAuth(ctx) }
                    } else {
                        AppLog.log("Watch setup request: not signed in, prompting on phone")
                        Notifications.post(
                            ctx,
                            "watch_setup".hashCode(),
                            "Finish setting up Bloo",
                            "Sign in on your phone so your watch can control your car.",
                        )
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
                    WearSync.PATH_CLIMATE -> {
                        // Merge per-VIN rather than saving the incoming payload
                        // wholesale: a raw overwrite would drop drafts for cars
                        // the watch didn't include this time. Symmetric with the
                        // PATH_PRESETS merge below; incoming wins per shared VIN.
                        val store = ClimateSyncStore(applicationContext)
                        val incoming = WearSync.decodeClimate(raw)
                        val current = store.flow.first()
                        val merged = WearClimateState(byVin = current.byVin + incoming.byVin)
                        store.save(WearSync.encodeClimate(merged))
                    }
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
                        // NOT store.setUiScale(payload.uiScale). That was a feedback loop and it
                        // squared the watch's own text-size slider.
                        //
                        // The watch fills this field with its LOCAL fontScale -- WearComms
                        // .publishLocalSettings takes it as a parameter literally named
                        // `uiScale`, and WearViewModel passes `ls.fontScale` into it. Writing it
                        // here made it the PHONE's appearance uiScale, which the phone then
                        // publishes straight back down in PATH_SETTINGS. MainActivity multiplies
                        // `phoneScale * localScale` believing them independent, so the watch's
                        // slider arrived on both sides of that product: set it to 1.2 and text
                        // rendered at 1.44.
                        //
                        // The 1.4 cap in MainActivity was written to stop "two maxed sliders"
                        // compounding to ~1.82. There were never two sliders -- it was one value
                        // squared, and the cap hid it by clamping every setting above ~1.18 to
                        // the same result.
                        //
                        // It was also wrong in the other direction: adjusting TEXT SIZE ON THE
                        // WATCH silently changed the phone app's own UI scale.
                        //
                        // The correct pattern is three lines below, for the PIN lock: a
                        // watch-originated value is stored as a "backup record only ...
                        // one-directional (watch -> phone) and never pushed back down". uiScale
                        // is pushed back down, so it cannot be stored there.
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

    /**
     * Read the current PATH_EXTRAS item, apply [transform] to it under [extrasMutex], and
     * republish -- the read-modify-write both the AI-summary and the weather-location handlers
     * need, so a patch to one car's field can't clobber every other car's extras.
     *
     * Returns true if it published, false if the read timed out / Play Services was wedged (in
     * which case it publishes NOTHING and logs [timeoutLog], because republishing off a blank
     * WearExtras would drop every other car's data). Callers map that Boolean to their own return
     * -- runAiSummary to a WearAiResult, setWeatherFromDeviceLocation to a bare return -- which is
     * why this returns a Boolean rather than taking the whole handler: the two callers' failure
     * values differ, but the read/lock/null-guard/release/publish around them was identical.
     *
     * [transform] returning null means "nothing to change" and skips the publish (currently
     * unused; both callers always produce an updated copy, but it keeps the helper honest).
     */
    private suspend fun updateExtras(
        ctx: android.content.Context,
        timeoutLog: String,
        transform: (WearExtras) -> WearExtras?,
    ): Boolean = extrasMutex.withLock {
        val dataClient = Wearable.getDataClient(ctx)
        val items = runCatching {
            Tasks.await(
                dataClient.getDataItems(android.net.Uri.parse("wear://*${WearSync.PATH_EXTRAS}")),
                10, TimeUnit.SECONDS,
            )
        }.getOrNull()
        if (items == null) {
            AppLog.log(timeoutLog)
            return@withLock false
        }
        val existing = items.map {
            WearSync.decodeExtras(DataMapItem.fromDataItem(it).dataMap.getString(WearSync.KEY_PAYLOAD))
        }.firstOrNull() ?: WearExtras()
        items.release()
        val updated = transform(existing) ?: return@withLock false
        WearBridge.publishExtrasNow(ctx, updated)
        true
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

        // Persist to the store the ViewModel mirrors into _state.aiSummaries (see
        // AiSummaryStore). Without this, the next phone-side state change republishes the
        // whole extras payload from _state -- which never held this watch-requested summary
        // -- and silently drops it. Written before the extras publish so it survives even if
        // the read below times out.
        AiSummaryStore(ctx).put(vin, summary)

        // Read the current extras item, patch the ai map, republish. A false return means the
        // read timed out, in which case the summary reached no watch -- surface that to the
        // spinner rather than a bare "generated".
        val published = updateExtras(
            ctx,
            timeoutLog = "⚠ AI summary: extras read timed out, skipping publish",
        ) { it.copy(ai = it.ai + (vin to summary)) }
        if (!published) {
            return WearAiResult(vin, ok = false, message = "Couldn't sync the summary to your watch")
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
        // Same read-patch-republish as the AI-summary path; the timeout case logs and bails
        // inside the helper. This caller returns Unit, so the published Boolean is not needed --
        // the "set from watch request" log below is only reached when the whole handler runs.
        val published = updateExtras(
            ctx,
            timeoutLog = "⚠ Watch weather-location request: extras read timed out, skipping publish",
        ) { it.copy(homeWeather = weather.toWear()) }
        if (!published) return
        AppLog.log("Weather location set from watch request")
    }
}
