package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.PinRecord
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WatchCommandRequest
import com.bloo.bluelink.data.WatchSyncPayload
import com.bloo.bluelink.data.WatchSyncProtocol
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * The watch's ONLY data source: everything it shows is pushed from the phone over the
 * Wearable Data Layer. The watch never touches the network or Google Drive -- it is a pure
 * auxiliary surface that mirrors the phone in real time.
 *
 * Two directions:
 *  - phone → watch: [WatchSyncProtocol.PATH_SNAPSHOT] carries vehicles + the watch lock
 *    timing + the stretched PIN record. Received here, mirrored into the local
 *    [SnapshotStore] (so the existing repository/UI read the same shape as the phone) and
 *    [WatchPinStore].
 *  - watch → phone: a command is SENT (see [sendCommand]) as a [WatchCommandRequest]; the
 *    phone runs it and pushes [WatchSyncProtocol.PATH_COMMAND_RESULT] back, which clears the
 *    pending state and surfaces a failure.
 *
 * Live, not polled: the phone pushes on every snapshot write and the Data Layer delivers it
 * within a second or two of both sides being connected.
 */
object WearDataLayerSync {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _vehicles = MutableStateFlow<List<VehicleSnapshot>>(emptyList())
    val vehicles: StateFlow<List<VehicleSnapshot>> = _vehicles

    private val _selectedVin = MutableStateFlow<String?>(null)
    val selectedVin: StateFlow<String?> = _selectedVin

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _lastCommandResult = MutableStateFlow<com.bloo.bluelink.data.WatchCommandResult?>(null)
    val lastCommandResult: StateFlow<com.bloo.bluelink.data.WatchCommandResult?> = _lastCommandResult

    /** A newer WATCH build the phone is advertising, or null. */
    private val _updateAdvice = MutableStateFlow<UpdateAdvice?>(null)
    val updateAdvice: StateFlow<UpdateAdvice?> = _updateAdvice

    data class UpdateAdvice(val runNumber: Int, val apkUrl: String, val notes: String?)

    private var started = false
    // The Application context only -- never an Activity or Service context. WatchPinStore holds
    // this context.applicationContext, so the static reference cannot leak a component.
    // (Lint's StaticFieldLeak cannot see through the applicationContext call.)
    @Suppress("StaticFieldLeak")
    @Volatile private var pendingStore: WatchPinStore? = null

    /**
     * Begin listening. Registers a Data Layer listener (via a [WearableListenerService]
     * re-dispatch -- see [WatchDataListenerService]) AND seeds from whatever the phone has
     * already pushed, so opening the app shows the last known state immediately.
     */
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        pendingStore = WatchPinStore(app)
        runCatching {
            Wearable.getDataClient(app).addListener { }
        }
        // Seed once from anything already on the Data Layer.
        scope.launch { runCatching { pullAll(app) } }
    }

    /** Read every current data item the phone has pushed and mirror it. */
    suspend fun pullAll(context: Context) {
        val app = context.applicationContext
        val items = runCatching { Wearable.getDataClient(app).dataItems.await() }.getOrNull() ?: return
        for (item in items) {
            when (item.uri.path) {
                WatchSyncProtocol.PATH_SNAPSHOT ->
                    mirrorSnapshot(app, DataMapItem.fromDataItem(item).dataMap.getByteArray(KEY_PAYLOAD))
            }
        }
    }

    /** Apply a snapshot payload: mirror into SnapshotStore + WatchPinStore. */
    suspend fun mirrorSnapshot(context: Context, bytes: ByteArray?) {
        if (bytes == null) return
        val payload = runCatching {
            json.decodeFromString(WatchSyncPayload.serializer(), bytes.decodeToString())
        }.getOrNull() ?: return
        val app = context.applicationContext
        _vehicles.value = payload.vehicles
        _selectedVin.value = payload.selectedVin
        _connected.value = true
        // Offer an update only when the phone advertises a NEWER watch build than this one.
        _updateAdvice.value = payload.watchUpdateRunNumber
            ?.takeIf { it > com.bloo.bluelink.wear.BuildConfig.BUILD_RUN_NUMBER }
            ?.let { run ->
                payload.watchUpdateApkUrl?.let { url -> UpdateAdvice(run, url, payload.watchUpdateNotes) }
            }
        // Mirror into the shared store so anything reading SnapshotStore sees the same data.
        runCatching { SnapshotStore(app).saveVehicles(payload.vehicles) }
        // PIN + timing.
        pendingStore?.let { store ->
            store.timing = com.bloo.bluelink.data.WatchLockTiming.fromWire(payload.lockTiming)
            store.record = payload.pinRecord?.let { PinRecord.decode(it) }
        }
    }

    fun applyCommandResult(context: Context, bytes: ByteArray?) {
        if (bytes == null) return
        val result = runCatching {
            json.decodeFromString(com.bloo.bluelink.data.WatchCommandResult.serializer(), bytes.decodeToString())
        }.getOrNull() ?: return
        _lastCommandResult.value = result
    }

    /**
     * The phone pushed the watch APK bytes: write them to a cache file and return it so the
     * caller can hand it to the system package installer. Returns null on any failure.
     */
    fun writePushedApk(context: Context, bytes: ByteArray?): java.io.File? {
        if (bytes == null || bytes.isEmpty()) return null
        return runCatching {
            val dir = java.io.File(context.cacheDir, "updates").apply { mkdirs() }
            val file = java.io.File(dir, "bloo-watch.apk")
            file.writeBytes(bytes)
            file
        }.getOrNull()
    }

    /**
     * Send a command to the phone to run. Returns a request id the caller can watch for in
     * [lastCommandResult]. The watch does NOT run commands itself.
     */
    fun sendCommand(context: Context, vin: String, action: String) {
        val app = context.applicationContext
        val request = WatchCommandRequest(
            requestId = UUID.randomUUID().toString(),
            command = com.bloo.bluelink.data.CarCommand(vin, action),
        )
        scope.launch {
            val req = PutDataMapRequest.create(WatchSyncProtocol.PATH_COMMAND).apply {
                dataMap.putByteArray(KEY_PAYLOAD, json.encodeToString(WatchCommandRequest.serializer(), request).encodeToByteArray())
                dataMap.putLong("_ts", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            runCatching { Wearable.getDataClient(app).putDataItem(req) }
        }
    }

    /** Ask the phone to push this watch the latest watch APK (see [WatchSyncProtocol.PATH_REQUEST_APK]). */
    fun requestWatchApk(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                com.google.android.gms.wearable.Wearable.getMessageClient(app)
                    .sendMessage("*", WatchSyncProtocol.PATH_REQUEST_APK, ByteArray(0)).await()
            }
        }
    }

    private const val KEY_PAYLOAD = "payload"
}
