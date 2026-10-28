package com.bloo.bluelink.wear

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.BlooJson
import com.bloo.bluelink.data.CarCommandRunner
import com.bloo.bluelink.data.CredentialStore
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.WatchCommandRequest
import com.bloo.bluelink.data.WatchCommandResult
import com.bloo.bluelink.data.WatchAccent
import com.bloo.bluelink.data.WatchLookPrefs
import com.bloo.bluelink.data.WatchNotifyPrefs
import com.bloo.bluelink.data.WatchSyncPayload
import com.bloo.bluelink.data.WatchSyncProtocol
import com.bloo.bluelink.data.notificationPrefs
import com.bloo.bluelink.data.setNotifyChargeComplete
import com.bloo.bluelink.data.setNotifyCharging
import com.bloo.bluelink.data.setNotifyAutoLock
import com.bloo.bluelink.data.setNotifyWatchLowBattery
import com.bloo.bluelink.data.setWatchLook
import com.bloo.bluelink.data.watchLook
import com.bloo.bluelink.data.climatePresets
import kotlinx.coroutines.flow.first
import com.bloo.bluelink.data.watchLockTiming
import com.bloo.bluelink.ioScope
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json

/**
 * Phone side of the watch sync. The watch is an AUXILIARY surface: it never talks to the
 * network or Drive -- everything it shows is pushed from here, and every command it wants run
 * is executed HERE through the normal [CarCommandRunner] and reported back. This service:
 *
 *  - PUSHES a [WatchSyncPayload] (vehicles + the watch lock timing + the stretched PIN record)
 *    whenever anything the watch shows changes, so the watch mirrors the phone in real time.
 *  - RECEIVES [WatchCommandRequest] items and runs them, then pushes [WatchCommandResult].
 *
 * A [WearableListenerService] so a push can be triggered even when the phone app is not in the
 * foreground (a command from the watch wakes this). [pushNow] is called from the phone app's
 * own snapshot writes to keep the watch live while it is open.
 */
class PhoneWatchSyncService : WearableListenerService() {
    private val scope = ioScope()
    private val json = BlooJson

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        // Sealed after a restart: nothing runs until the user signs in, and that includes a command from the watch
        // (the credential messages below already stop here; the command path did not).
        if (com.bloo.bluelink.data.BootGate.isLocked(applicationContext)) return
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val path = event.dataItem.uri.path ?: continue
            if (!WatchSyncProtocol.isCommandPath(path)) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            // A command that sat in the Data Layer while the phone was unreachable must not run hours later: an unlock
            // delivered then would open a car the wearer has long since left. The watch stamps each one (_ts).
            val sentAt = map.getLong("_ts")
            if (sentAt > 0L && System.currentTimeMillis() - sentAt > MAX_COMMAND_AGE_MS) {
                AppLog.log("WatchSync: dropped a stale command (${(System.currentTimeMillis() - sentAt) / 1000}s old)")
                continue
            }
            val bytes = map.getByteArray(KEY_PAYLOAD) ?: continue
            val request = runCatching {
                json.decodeFromString(WatchCommandRequest.serializer(), bytes.decodeToString())
            }.getOrNull() ?: continue
            // Delivered twice (a retry of the transport, or the same item seen again): run it once.
            if (!firstSighting(request.requestId)) continue
            AppLog.log("WatchSync: command ${request.command.action} for ${request.command.vin}")
            // Taken off the Data Layer once seen, so it can never be delivered (and run) again later.
            runCatching { Wearable.getDataClient(applicationContext).deleteDataItems(event.dataItem.uri) }
            scope.launch { runCommand(request) }
        }
    }

    override fun onMessageReceived(messageEvent: com.google.android.gms.wearable.MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (com.bloo.bluelink.data.BootGate.isLocked(applicationContext)) return
        when (messageEvent.path) {
            WatchSyncProtocol.PATH_CRED_KEY -> WatchSignIn.onKey(messageEvent.sourceNodeId, messageEvent.data)
            WatchSyncProtocol.PATH_CRED_ACK -> WatchSignIn.onAck()
            WatchSyncProtocol.PATH_LOOK_PREFS -> scope.launch {
                runCatching { json.decodeFromString(WatchLookPrefs.serializer(), messageEvent.data.decodeToString()) }
                    .getOrNull()?.let { look ->
                        // Stored with the phone's settings (so Drive carries it); only echoed back when it really changed.
                        if (SettingsStore(applicationContext).setWatchLook(look)) pushNow(applicationContext)
                    }
            }
            WatchSyncProtocol.PATH_NOTIF_PREFS -> scope.launch {
                runCatching { json.decodeFromString(WatchNotifyPrefs.serializer(), messageEvent.data.decodeToString()) }
                    .getOrNull()?.let { p ->
                        val store = SettingsStore(applicationContext)
                        store.setNotifyCharging(p.charging)
                        store.setNotifyChargeComplete(p.chargeComplete)
                        store.setNotifyWatchLowBattery(p.lowBattery)
                        store.setNotifyAutoLock(p.autoLock)
                        if (!p.charging) {
                            com.bloo.bluelink.data.LiveCharge.cancelAll(applicationContext, SnapshotStore(applicationContext).current().vehicles.map { it.vin })
                        }
                        pushNow(applicationContext)
                    }
            }
        }
    }

    private suspend fun runCommand(request: WatchCommandRequest) {
        val result = if (request.command.action == com.bloo.bluelink.data.CarAction.REFRESH) {
            // Not a car command: the watch is asking for fresh status. Fetch it, and the snapshot write pushes it back.
            val ok = runCatching { CarCommandRunner.refresh(applicationContext, vin = request.command.vin, force = false) }.getOrDefault(false)
            com.bloo.bluelink.data.CarCommandResult(request.command.vin, request.command.action, ok = ok, message = if (ok) null else "Couldn't refresh")
        } else runCatching { com.bloo.bluelink.data.CarCommandRunner.execute(applicationContext, request.command) }
            .getOrElse {
                com.bloo.bluelink.data.CarCommandResult(request.command.vin, request.command.action, ok = false, message = it.message)
            }
        val out = WatchCommandResult(
            requestId = request.requestId, vin = result.vin, action = result.action, ok = result.ok,
            message = result.message,
        )
        pushBytes(WatchSyncProtocol.PATH_COMMAND_RESULT, json.encodeToString(WatchCommandResult.serializer(), out))
    }

    private fun pushBytes(path: String, jsonString: String) {
        val req = PutDataMapRequest.create(path).apply {
            dataMap.putByteArray(KEY_PAYLOAD, jsonString.encodeToByteArray())
            dataMap.putLong("_ts", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        runCatching { Wearable.getDataClient(applicationContext).putDataItem(req) }
    }

    companion object {
        private const val KEY_PAYLOAD = "payload"

        /** How old a watch command may be and still run: a few minutes covers a slow link, not a day offline. */
        private const val MAX_COMMAND_AGE_MS = 90_000L

        private val seenRequests = LinkedHashSet<String>()

        /** True the first time [requestId] is seen (the last 64 are remembered). */
        @Synchronized
        private fun firstSighting(requestId: String): Boolean {
            if (!seenRequests.add(requestId)) return false
            if (seenRequests.size > 64) seenRequests.remove(seenRequests.first())
            return true
        }

        /** The last watch update the phone advertised, so a plain snapshot push does not erase the watch's offer. */
        @Volatile private var lastAdvice: Triple<Int, String, String?>? = null

        /**
         * Download the watch APK at [url] on a background thread and push it to any paired watch as
         * a Data Layer asset, so the watch can offer to install the new build. A no-op when no watch
         * is paired (the push simply goes nowhere).
         */
        fun pushWatchApk(context: Context, url: String) {
            val app = context.applicationContext
            ioScope().launch {
                runCatching {
                    val bytes = com.bloo.bluelink.data.ApiHttp.client.newCall(
                        okhttp3.Request.Builder().url(url).get().build(),
                    ).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        resp.body.bytes()
                    }
                    val asset = com.google.android.gms.wearable.Asset.createFromBytes(bytes)
                    val req = PutDataMapRequest.create(WatchSyncProtocol.PATH_WATCH_APK).apply {
                        dataMap.putAsset("apk", asset)
                        dataMap.putLong("_ts", System.currentTimeMillis())
                    }.asPutDataRequest().setUrgent()
                    Wearable.getDataClient(app).putDataItem(req).await()
                    AppLog.log("WatchSync: pushed watch APK (${bytes.size} bytes)")
                }.onFailure { AppLog.log("WatchSync: watch APK push failed (${it.javaClass.simpleName})") }
            }
        }

        /**
         * Push the current snapshot + lock config to every paired watch, now. Called from the
         * phone app whenever it writes the snapshot so the watch stays live; a no-op (and
         * cheap) when no watch is paired. Runs off the caller's thread via the Data Layer's own
         * executor, so it never blocks a phone UI frame.
         *
         * [watchUpdateRunNumber]/[watchUpdateApkUrl]/[watchUpdateNotes] advertise a newer WATCH
         * build for the watch to offer installing; null (the default) means "nothing to
         * advertise" and the watch shows no update affordance.
         */
        fun pushNow(
            context: Context,
            watchUpdateRunNumber: Int? = null,
            watchUpdateApkUrl: String? = null,
            watchUpdateNotes: String? = null,
        ) {
            val app = context.applicationContext
            val job = ioScope().launch {
                val snapshot = SnapshotStore(app).current()
                val pinRecord = runCatching { CredentialStore(app).getPinRecord() }.getOrNull()
                val timing = SettingsStore(app).watchLockTiming()
                if (watchUpdateRunNumber != null && watchUpdateApkUrl != null) {
                    lastAdvice = Triple(watchUpdateRunNumber, watchUpdateApkUrl, watchUpdateNotes)
                }
                val advice = lastAdvice
                val appearance = SettingsStore(app).appearance.first()
                val payload = WatchSyncPayload(
                    vehicles = snapshot.vehicles,
                    selectedVin = snapshot.selectedVin,
                    lockTiming = timing.wireKey,
                    pinRecord = pinRecord,
                    sentAtMs = System.currentTimeMillis(),
                    watchUpdateRunNumber = advice?.first,
                    watchUpdateApkUrl = advice?.second,
                    watchUpdateNotes = advice?.third,
                    notify = SettingsStore(app).notificationPrefs().let { WatchNotifyPrefs(it.charging, it.chargeComplete, it.watchLowBattery, it.autoLockAlerts) },
                    look = SettingsStore(app).watchLook(),
                    climatePresets = snapshot.vehicles.associate { it.vin to SettingsStore(app).climatePresets(it.vin) }
                        .filterValues { it.isNotEmpty() },
                    fahrenheit = appearance.useFahrenheit,
                    accent = watchAccentOf(app, appearance),
                )
                val jsonString = Json.encodeToString(WatchSyncPayload.serializer(), payload)
                val req = PutDataMapRequest.create(WatchSyncProtocol.PATH_SNAPSHOT).apply {
                    dataMap.putByteArray(KEY_PAYLOAD, jsonString.encodeToByteArray())
                    dataMap.putLong("_ts", System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                runCatching { Wearable.getDataClient(app).putDataItem(req) }
                    .onFailure { AppLog.log("WatchSync: push failed (${it.javaClass.simpleName})") }
            }
            // Fire-and-forget: the caller is a snapshot write, not a place to await the network.
            job.invokeOnCompletion { }
        }
    }
}

/**
 * The phone's palette as the watch should wear it: the same colour scheme the phone resolves for its own screens (dark, as the
 * watch is), with the same dynamic colour, palette, custom palette and vibrancy. Null if the scheme cannot be built (the watch
 * then keeps what it has).
 */
private fun watchAccentOf(context: Context, appearance: SettingsStore.Appearance): WatchAccent? = runCatching {
    val custom = if (!appearance.dynamicColor) appearance.customPalettes.find { it.id == appearance.activeCustomPaletteId } else null
    val scheme = com.bloo.bluelink.ui.blooColorScheme(
        context = context,
        dark = true,
        dynamicColor = appearance.dynamicColor,
        colorPalette = appearance.colorPalette,
        customPalette = custom,
        vibrancy = appearance.vibrancy,
    )
    WatchAccent(
        primary = scheme.primary.toArgb(), onPrimary = scheme.onPrimary.toArgb(),
        secondary = scheme.secondary.toArgb(), tertiary = scheme.tertiary.toArgb(),
    )
}.getOrNull()
