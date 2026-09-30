package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// --- Notification preference setters (extracted from AppViewModel) --

// Simple notification-preference setters: each just fires a coroutine that
// writes one field to SettingsStore's DataStore. They don't touch _state
// directly because `notifications` above is already a StateFlow mirroring
// settingsStore.notifications, so the UI picks up the change automatically
// once the write completes and the underlying Flow re-emits.
fun AppViewModel.setNotifyService(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyService(v) }

fun AppViewModel.setNotifyDoor(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyDoor(v) }

fun AppViewModel.setDoorOpenMinutes(m: Int) = viewModelScope.launch { settingsStore.setDoorOpenMinutes(m) }

fun AppViewModel.setNotifyRunning(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyRunning(v) }

fun AppViewModel.setRunningMinutes(m: Int) = viewModelScope.launch { settingsStore.setRunningMinutes(m) }

fun AppViewModel.setNotifyUnlocked(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyUnlocked(v) }

fun AppViewModel.setUnlockedMinutes(m: Int) = viewModelScope.launch { settingsStore.setUnlockedMinutes(m) }

/** Turning the live charging bar off clears anything already posted at
 *  once, and kills the poll chain, rather than leaving both to linger
 *  until they happen to notice the setting changed. */
fun AppViewModel.setNotifyCharging(v: Boolean) = viewModelScope.launch {
    settingsStore.setNotifyCharging(v)
    if (!v) {
        LiveCharge.cancelAll(getApplication(), _state.value.vehicles.map { it.vin })
        com.bloo.bluelink.work.LiveChargePollWorker.cancel(getApplication())
    }
}

fun AppViewModel.setNotifyCarStarted(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyCarStarted(v) }

fun AppViewModel.setNotifyChargeComplete(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyChargeComplete(v) }
