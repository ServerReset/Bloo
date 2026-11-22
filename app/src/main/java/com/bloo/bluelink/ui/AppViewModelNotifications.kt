package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.LiveCharge
import com.bloo.bluelink.data.setNotifyChargeComplete
import com.bloo.bluelink.data.setNotifyCharging
import com.bloo.bluelink.data.setNotifyWatchLowBattery
import com.bloo.bluelink.data.setNotifyLow12V
import com.bloo.bluelink.data.setNotifyTirePressure
import kotlinx.coroutines.flow.map
import com.bloo.bluelink.wear.PhoneWatchSyncService
import com.bloo.bluelink.work.LiveChargePollWorker
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
    PhoneWatchSyncService.pushNow(getApplication())
    if (!v) {
        LiveCharge.cancelAll(getApplication(), _state.value.vehicles.map { it.vin })
        LiveChargePollWorker.cancel(getApplication())
    }
}


fun AppViewModel.setNotifyChargeComplete(v: Boolean) = viewModelScope.launch {
    settingsStore.setNotifyChargeComplete(v)
    PhoneWatchSyncService.pushNow(getApplication())
}

/** Whether AutoLock posts its "Locked" / "Would have locked" notification. */

/** The watch's own low-battery notification; pushed to the watch with the next sync. */
fun AppViewModel.setNotifyWatchLowBattery(v: Boolean) = viewModelScope.launch {
    settingsStore.setNotifyWatchLowBattery(v)
    PhoneWatchSyncService.pushNow(getApplication())
}

fun AppViewModel.setNotifyLow12V(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyLow12V(v) }

fun AppViewModel.setNotifyTirePressure(v: Boolean) = viewModelScope.launch { settingsStore.setNotifyTirePressure(v) }
