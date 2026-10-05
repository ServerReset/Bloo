package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.setDoorOpenMinutes
import com.bloo.bluelink.data.setNotifyCarStarted
import com.bloo.bluelink.data.setNotifyChargeComplete
import com.bloo.bluelink.data.setNotifyAutoLock
import com.bloo.bluelink.data.setNotifyCharging
import com.bloo.bluelink.data.setNotifyDoor
import com.bloo.bluelink.data.setNotifyRunning
import com.bloo.bluelink.data.setNotifyService
import com.bloo.bluelink.data.setNotifyUnlocked
import com.bloo.bluelink.data.setNotifyWatchLowBattery
import com.bloo.bluelink.data.setRunningMinutes
import com.bloo.bluelink.data.setUnlockedMinutes

// --- Notification preference setters (extracted from AppViewModel) --

// Simple notification-preference setters: each just fires a coroutine that writes one field to
// SettingsStore's DataStore.
fun AppViewModel.setNotifyService(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyService(v) }

fun AppViewModel.setNotifyDoor(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyDoor(v) }

fun AppViewModel.setDoorOpenMinutes(m: Int) = viewModelScope.launch { settingsStore.setDoorOpenMinutes(m) }

fun AppViewModel.setNotifyRunning(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyRunning(v) }

fun AppViewModel.setRunningMinutes(m: Int) = viewModelScope.launch { settingsStore.setRunningMinutes(m) }

fun AppViewModel.setNotifyUnlocked(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyUnlocked(v) }

fun AppViewModel.setUnlockedMinutes(m: Int) = viewModelScope.launch { settingsStore.setUnlockedMinutes(m) }

/**
 * Turning the live charging bar off clears anything already posted at once, and kills the poll
 * chain, rather than leaving both to linger until they happen to notice the setting changed.
 */
fun AppViewModel.setNotifyCharging(v: Boolean) = viewModelScope.launch {
    settingsStore.setNotifyCharging(v)
    com.bloo.bluelink.wear.PhoneWatchSyncService.pushNow(getApplication())
    if (!v) {
        LiveCharge.cancelAll(getApplication(), _state.value.vehicles.map { it.vin })
        com.bloo.bluelink.work.LiveChargePollWorker.cancel(getApplication())
    }
}

fun AppViewModel.setNotifyCarStarted(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyCarStarted(v) }

fun AppViewModel.setNotifyChargeComplete(v: Boolean) = viewModelScope.launch {
    settingsStore.setNotifyChargeComplete(v)
    com.bloo.bluelink.wear.PhoneWatchSyncService.pushNow(getApplication())
}

/** Whether AutoLock posts its "Locked" / "Would have locked" notification. */
fun AppViewModel.setNotifyAutoLock(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyAutoLock(v) }

/** The watch's own low-battery notification; pushed to the watch with the next sync. */
fun AppViewModel.setNotifyWatchLowBattery(v: Boolean) = viewModelScope.launch {
    settingsStore.setNotifyWatchLowBattery(v)
    com.bloo.bluelink.wear.PhoneWatchSyncService.pushNow(getApplication())
}
