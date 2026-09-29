package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.SnapshotStore
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Phone-side watch presence + the live snapshot push.
 *
 * [isWatchPaired] / [hasWatch] is what the Settings "under the phone" watch-lock option keys
 * off: the option only shows when there IS a paired watch, because there is nothing to lock
 * otherwise. Presence is the Data Layer's connected-node list, refreshed on start and on any
 * node change.
 *
 * [startSnapshotPush] mirrors every SnapshotStore write to the watch in real time by observing
 * the store's own payload flow -- one listener catches every writer (the ViewModel, the alert
 * and live-charge workers, the tile command runner) without each having to remember to push.
 * The push is a cheap no-op when no watch is paired, and the Data Layer coalesces/queues it.
 */
object WatchPresence {

    private val _hasWatch = MutableStateFlow(false)
    /** Live "is a watch paired right now", for the Settings UI. */
    val hasWatch: StateFlow<Boolean> = _hasWatch

    private var started = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        // Node presence: LISTEN for changes and seed once. Guarded throughout -- on a device
        // with no Play Services / no wearable surface any of these throws, and a missing watch
        // must never crash or slow the phone.
        runCatching {
            val nodeClient = Wearable.getNodeClient(app)
            nodeClient.connectedNodes.addOnSuccessListener { nodes ->
                _hasWatch.value = nodes.isNotEmpty()
            }
        }
        // Observe the snapshot store and push on every change.
        scope.launch {
            runCatching {
                SnapshotStore(app).payload.collect {
                    if (_hasWatch.value) PhoneWatchSyncService.pushNow(app)
                }
            }
        }
    }

    /**
     * Advertise a newer WATCH build to any paired watch. A no-op when no watch is paired, so
     * the update check never pays for a push nobody will read. [run] is the phone's newest
     * [com.bloo.bluelink.data.WorkflowRun]; its watch asset URL is what the watch offers to
     * download.
     */
    fun pushWatchUpdateAdvice(context: Context, run: com.bloo.bluelink.data.WorkflowRun) {
        if (!_hasWatch.value) return
        val watchUrl = run.watchApkUrl ?: return
        PhoneWatchSyncService.pushNow(
            context.applicationContext,
            watchUpdateRunNumber = run.runNumber,
            watchUpdateApkUrl = watchUrl,
            watchUpdateNotes = run.releaseNotes,
        )
    }
}
