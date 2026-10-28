package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.setDoorOpenMinutes
import com.bloo.bluelink.data.setNotifyAutoLock
import com.bloo.bluelink.data.setNotifyCarStarted
import com.bloo.bluelink.data.setNotifyChargeComplete
import com.bloo.bluelink.data.setNotifyCharging
import com.bloo.bluelink.data.setNotifyDoor
import com.bloo.bluelink.data.setNotifyRunning
import com.bloo.bluelink.data.setNotifyService
import com.bloo.bluelink.data.setNotifyUnlocked
import com.bloo.bluelink.data.setNotifyWatchLowBattery
import com.bloo.bluelink.data.setRunningMinutes
import com.bloo.bluelink.data.setUnlockedMinutes
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// --- Notification preference setters (extracted from AppViewModel) --

// Simple notification-preference setters: each just fires a coroutine that writes one field to
// SettingsStore's DataStore.







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


fun AppViewModel.setNotifyChargeComplete(v: Boolean) = viewModelScope.launch {
    settingsStore.setNotifyChargeComplete(v)
    com.bloo.bluelink.wear.PhoneWatchSyncService.pushNow(getApplication())
}

/** Whether AutoLock posts its "Locked" / "Would have locked" notification. */

/** The watch's own low-battery notification; pushed to the watch with the next sync. */
fun AppViewModel.setNotifyWatchLowBattery(v: Boolean) = viewModelScope.launch {
    settingsStore.setNotifyWatchLowBattery(v)
    com.bloo.bluelink.wear.PhoneWatchSyncService.pushNow(getApplication())
}
