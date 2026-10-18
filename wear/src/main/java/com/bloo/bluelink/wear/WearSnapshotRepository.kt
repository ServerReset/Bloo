package com.bloo.bluelink.wear

import android.content.Context
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.ioScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch

/**
 * The watch's one gateway to car state and car commands.
 *
 * Commands run ON the watch whenever it has been signed in by the phone
 * ([WearCredentialSync]), so it works on its own -- no phone nearby, no Bluetooth link. A watch
 * that hasn't been signed in yet hands the command to the phone instead. Vehicles arrive from
 * [WearDataLayerSync], pushed by the phone and refreshed by the watch's own commands.
 */
class WearSnapshotRepository(private val context: Context) {
    private val scope = ioScope()

    /** Every car the phone has pushed, live. */
    val vehicles: Flow<List<VehicleSnapshot>> = WearDataLayerSync.vehicles

    /** Lock/unlock. */
    fun lock(vin: String) = run(vin, CarAction.TOGGLE_LOCK)

    /** Toggle climate. */
    fun climate(vin: String) = run(vin, CarAction.TOGGLE_CLIMATE)

    /** Start or stop charging. */
    fun charge(vin: String) = run(vin, CarAction.TOGGLE_CHARGE)

    /** Flash the hazards (Hyundai/Genesis). */
    fun flashLights(vin: String) = run(vin, CarAction.FLASH_LIGHTS)

    /** Pull fresh status when the watch has its own session. */
    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        scope.launch {
            try {
                WearCredentialSync.refresh(context)
            } finally {
                _refreshing.value = false
            }
        }
    }

    private val _refreshing = MutableStateFlow(false)

    /** True while a refresh is in flight, so a pull-to-refresh indicator can spin. */
    val refreshing: StateFlow<Boolean> = _refreshing

    /** Send any of the app's command names for [vin]; the face's controls all go through here. */
    fun command(vin: String, action: String) {
        // Refresh is not a car command (the runner has no such action): it pulls fresh status, like the pull-down.
        if (action == CarAction.REFRESH) refresh() else run(vin, action)
    }

    private fun run(vin: String, action: String) {
        scope.launch { WearCommands.track(vin, action) { runWatchCommand(context, vin, action) } }
    }

    /** The last command result, so the UI can clear pending + show a failure. */
    val lastCommandResult = WearDataLayerSync.lastCommandResult

    /** Whether a snapshot has been received from the phone at least once this session. */
    val connected = WearDataLayerSync.connected
}

/**
 * Runs [action] for [vin] on the watch itself when it has its own session, else hands it to the
 * phone. The watch's one command path, shared by [WearSnapshotRepository] and [WearActionReceiver]
 * (a notification's Stop action).
 */
internal suspend fun runWatchCommand(context: Context, vin: String, action: String): Boolean {
    if (WearCredentialSync.runLocally(context, vin, action) != null) return true
    WearDataLayerSync.sendCommand(context, vin, action)
    return false
}

/**
 * Which commands are in flight, so a button can say "Sending..." and ignore a second tap instead of
 * firing the same command twice. A command the watch runs itself is done when it returns; one handed
 * to the phone is done when its result comes back (or after 20 seconds, so a lost link never leaves a
 * button stuck).
 */
internal object WearCommands {
    private val _pending = MutableStateFlow<Set<String>>(emptySet())
    val pending: StateFlow<Set<String>> = _pending

    fun key(vin: String, action: String) = "$vin:$action"

    /** Runs [block] (true = it finished on the watch) and keeps [vin]/[action] marked pending meanwhile. */
    suspend fun track(vin: String, action: String, block: suspend () -> Boolean) {
        val k = key(vin, action)
        if (k in _pending.value) return
        _pending.update { it + k }
        try {
            val before = WearDataLayerSync.lastCommandResult.value
            val ranHere = block()
            if (!ranHere) {
                withTimeoutOrNull(20_000L) {
                    WearDataLayerSync.lastCommandResult.first { it != before && it?.vin == vin }
                }
            }
        } finally {
            _pending.update { it - k }
        }
    }
}
