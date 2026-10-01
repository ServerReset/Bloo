package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.PinRecord
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WatchCommandRequest
import com.bloo.bluelink.data.WatchSyncPayload
import com.bloo.bluelink.data.WatchSyncProtocol
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
 * The watch's window onto the phone: cars, lock timing and the PIN record are pushed from the
 * phone over the Wearable Data Layer and mirrored here in real time. Once the phone has signed the
 * watch in ([WearCredentialSync]) the watch also talks to the car's service itself, and uses the
 * phone only for what the phone owns.
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
        // The listener service can receive the first push before the Activity has ever opened.
        // Initialise the PIN store here too, otherwise that first payload updates vehicles but
        // silently drops the phone's lock timing/PIN and the watch opens unguarded until the
        // user launches it once.
        if (!started) start(context)
        val payload = runCatching {
            json.decodeFromString(WatchSyncPayload.serializer(), bytes.decodeToString())
        }.getOrNull() ?: return
        val app = context.applicationContext
        WearNotificationPrefs(app).apply(payload.notify)
        WearNotifier.onVehicles(app, _vehicles.value, payload.vehicles)
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

    /**
     * Re-read the on-watch store after a command or refresh the watch ran ITSELF, so the UI shows
     * what it just did, and report the command's outcome the same way the phone's reply would.
     */
    suspend fun reloadFromStore(context: Context, result: com.bloo.bluelink.data.CarCommandResult?) {
        val snapshot = SnapshotStore(context.applicationContext).current()
        if (snapshot.vehicles.isNotEmpty()) {
            WearNotifier.onVehicles(context, _vehicles.value, snapshot.vehicles)
            _vehicles.value = snapshot.vehicles
        }
        result?.let {
            _lastCommandResult.value = com.bloo.bluelink.data.WatchCommandResult(
                requestId = "local-${System.nanoTime()}", vin = it.vin, action = it.action, ok = it.ok, message = it.message,
            )
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
     * Send a command to the phone to run; its outcome arrives in [lastCommandResult]. Used when
     * the watch has no session of its own (see [WearCredentialSync.runLocally]).
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
                val nodes = com.google.android.gms.wearable.Wearable.getNodeClient(app)
                    .connectedNodes.await()
                nodes.forEach { node ->
                    com.google.android.gms.wearable.Wearable.getMessageClient(app)
                        .sendMessage(node.id, WatchSyncProtocol.PATH_REQUEST_APK, ByteArray(0)).await()
                }
            }
        }
    }

    private const val KEY_PAYLOAD = "payload"
}
