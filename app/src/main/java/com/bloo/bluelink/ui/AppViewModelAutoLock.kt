package com.bloo.bluelink.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.allAutoLockConfigs
import com.bloo.bluelink.data.autoLockConfig
import com.bloo.bluelink.data.setAutoLockConfig

// --- AutoLock config, watcher and simulation (extracted from AppViewModel) --

suspend fun AppViewModel.autoLockConfig(vin: String): com.bloo.bluelink.autolock.AutoLockConfig =
    settingsStore.autoLockConfig(vin)

fun AppViewModel.setAutoLockConfig(vin: String, config: com.bloo.bluelink.autolock.AutoLockConfig) {
    viewModelScope.launch {
        settingsStore.setAutoLockConfig(vin, config)
        val anyEnabled = settingsStore.allAutoLockConfigs().values.any { it.enabled }
        val action = if (anyEnabled) {
            com.bloo.bluelink.autolock.AutoLockService.ACTION_START_WATCH
        } else {
            com.bloo.bluelink.autolock.AutoLockService.ACTION_STOP_WATCH
        }
        runCatching {
            getApplication<android.app.Application>().startForegroundService(
                android.content.Intent(
                    getApplication(),
                    com.bloo.bluelink.autolock.AutoLockService::class.java,
                ).setAction(action).putExtra(
                    com.bloo.bluelink.autolock.AutoLockService.EXTRA_VIN,
                    vin,
                ),
            )
        }.onFailure {
            com.bloo.bluelink.data.AppLog.log("AutoLock watcher start failed: ${it.javaClass.simpleName}")
        }
    }
}

/** Re-attaches the low-power Bluetooth watcher when the app returns to the foreground.
 *  This repairs installs upgraded from a build that had AutoLock enabled before the
 *  watcher was introduced: their persisted config is already ON, so they never visit the
 *  setting toggle again and would otherwise remain unwatched until the next reboot. */
fun AppViewModel.ensureAutoLockWatcher() {
    viewModelScope.launch {
        val entry = settingsStore.allAutoLockConfigs().entries.firstOrNull { it.value.enabled }
            ?: return@launch
        runCatching {
            getApplication<android.app.Application>().startForegroundService(
                android.content.Intent(
                    getApplication(),
                    com.bloo.bluelink.autolock.AutoLockService::class.java,
                ).setAction(com.bloo.bluelink.autolock.AutoLockService.ACTION_START_WATCH)
                    .putExtra(com.bloo.bluelink.autolock.AutoLockService.EXTRA_VIN, entry.key),
            )
        }.onFailure {
            com.bloo.bluelink.data.AppLog.log("AutoLock watcher recovery failed: ${it.javaClass.simpleName}")
        }
    }
}

/** Bonded (paired) Bluetooth devices, for the "which one is your car" picker. Empty
 *  without BLUETOOTH_CONNECT granted -- the Settings section prompts for it first. */
fun AppViewModel.pairedBluetoothDevices(): List<com.bloo.bluelink.autolock.PairedDevice> =
    com.bloo.bluelink.autolock.BluetoothDevices.bondedDevices(getApplication())

/** "Simulate leaving" test button: runs the exact same evaluation a real Bluetooth
 *  disconnect would, without needing to actually drive off and walk away.
 *
 *  It must go through [AutoLockTrigger.onCarDisconnected] (or call the controller itself),
 *  NOT just [AutoLockService.start]: the service is only the progress-notification wrapper,
 *  and it deliberately does not start an evaluation (see its own "No onTriggerFired here any
 *  more" note). This used to call only `.start(...)`, so the Test button showed a brief
 *  "Confirming" notification and then nothing -- the evaluation had never been started, which
 *  is exactly the "AutoLock never fires" it was reported as. The trigger starts the evaluation
 *  AND the service, which is what a real disconnect does. */
fun AppViewModel.simulateAutoLockLeaving(v: Vehicle) {
    viewModelScope.launch {
        val config = settingsStore.autoLockConfig(v.vin)
        com.bloo.bluelink.autolock.AutoLockTrigger.onCarDisconnected(getApplication(), v.vin, config, log = false)
        AppLog.log("AutoLock: simulated leaving ${v.name}.")
    }
}
