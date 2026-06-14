package com.bloo.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.SnapshotStore
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WearCommand
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the watch UI renders. */
data class WatchUi(
    val vehicles: List<VehicleSnapshot> = emptyList(),
    val selectedVin: String? = null,
    /** "vin:action" keys currently in flight, for per-button spinners. */
    val pending: Set<String> = emptySet(),
    val phoneConnected: Boolean = false,
    val loaded: Boolean = false,
)

class WearViewModel(app: Application) : AndroidViewModel(app) {

    private val snapshotStore = SnapshotStore(app)
    private val pending = MutableStateFlow<Set<String>>(emptySet())
    private val phoneConnected = MutableStateFlow(false)
    private val loaded = MutableStateFlow(false)

    val ui: StateFlow<WatchUi> =
        combine(snapshotStore.payload, pending, phoneConnected, loaded) { data, pend, conn, ready ->
            WatchUi(
                vehicles = data.vehicles,
                selectedVin = data.selectedVin,
                pending = pend,
                phoneConnected = conn,
                loaded = ready,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WatchUi())

    init {
        viewModelScope.launch {
            runCatching { WearComms.pullLatest(getApplication()) }
            loaded.value = true
        }
        refreshConnection()
    }

    fun refreshConnection() {
        viewModelScope.launch {
            phoneConnected.value = WearComms.phoneNodeId(getApplication()) != null
        }
    }

    /** Persist which car the pager settled on, so it's restored next launch. */
    fun selectVin(vin: String) {
        viewModelScope.launch { runCatching { snapshotStore.setSelected(vin) } }
    }

    fun send(vin: String, action: String, tempF: Int = 72, durationMinutes: Int = 10) {
        markPending("$vin:$action") {
            WearComms.send(
                getApplication(),
                WearCommand(vin = vin, action = action, tempF = tempF, durationMinutes = durationMinutes),
            )
        }
    }

    fun refresh(vin: String) {
        markPending("$vin:refresh") {
            WearComms.requestSync(getApplication(), vin, refresh = true)
            refreshConnection()
        }
    }

    private fun markPending(key: String, block: suspend () -> Unit) {
        pending.update { it + key }
        viewModelScope.launch {
            runCatching { block() }
            // Keep the spinner up briefly so a too-fast round-trip still reads as
            // "working", mirroring the phone app's command lock.
            delay(2_500)
            pending.update { it - key }
        }
    }
}
