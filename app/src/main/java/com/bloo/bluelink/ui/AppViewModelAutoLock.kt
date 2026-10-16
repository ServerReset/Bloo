package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.allAutoLockConfigs
import com.bloo.bluelink.data.autoLockConfig
import com.bloo.bluelink.data.setAutoLockConfig
import kotlinx.coroutines.launch

// --- AutoLock config, watcher and simulation (extracted from AppViewModel) --

suspend fun AppViewModel.autoLockConfig(vin: String): com.bloo.bluelink.autolock.AutoLockConfig =
    settingsStore.autoLockConfig(vin)

fun AppViewModel.setAutoLockConfig(vin: String, config: com.bloo.bluelink.autolock.AutoLockConfig) {
    viewModelScope.launch {
        settingsStore.setAutoLockConfig(vin, config)
        refreshAutoLockAddresses()
        // Turning AutoLock OFF must also CANCEL an evaluation already in flight for this car. The
        // config read at the START of an evaluation only guards NEW ones.
        if (!config.enabled) {
            runCatching { com.bloo.bluelink.autolock.AutoLockController.cancel(vin) }
        }
        val anyEnabled = settingsStore.allAutoLockConfigs().values.any { it.enabled }
        val action = if (anyEnabled) {
            com.bloo.bluelink.autolock.AutoLockService.ACTION_START_WATCH
        } else {
            com.bloo.bluelink.autolock.AutoLockService.ACTION_STOP_WATCH
        }
        // Through AutoLockService.start, not startForegroundService directly: that funnel refuses when
        // the app is sealed or the permission is missing, and a raw startForegroundService here is how
        // the sealed case reached the service and crashed the app (see AutoLockService.promoteThenStop).
        com.bloo.bluelink.autolock.AutoLockService.start(getApplication(), vin, action)
    }
}

/**
 * Whether any OTHER car already has AutoLock configured. The prerequisites the first-car setup asks
 * for are app-wide -- a permission, the background exemption, a hidden watcher notification -- so they
 * are asked once, on the first car that turns AutoLock on, and every car after that goes straight to
 * the settings.
 */
suspend fun AppViewModel.autoLockConfiguredElsewhere(vin: String): Boolean =
    settingsStore.allAutoLockConfigs().any { (other, config) -> other != vin && config.isUsable }

/**
 * Rebuild the synchronous address mirror the Bluetooth receiver reads (see AutoLockAddresses).
 *
 * Written from the two places that can change the answer -- a config write, and every foreground --
 * rather than from a background job, because the receiver may run in a process that has no store open
 * and no time to open one.
 */
suspend fun AppViewModel.refreshAutoLockAddresses() {
    val entries = settingsStore.allAutoLockConfigs()
        .mapNotNull { (vin, config) ->
            config.deviceAddress?.takeIf { config.enabled }?.let { it to vin }
        }
        .toMap()
    runCatching { com.bloo.bluelink.autolock.AutoLockAddresses.write(getApplication(), entries) }
}

/** Re-attaches the low-power Bluetooth watcher when the app returns to the foreground. */
fun AppViewModel.ensureAutoLockWatcher() {
    viewModelScope.launch {
        // Every foreground: cheap, and it is what repairs the mirror after an upgrade from a build
        // that did not have one.
        refreshAutoLockAddresses()
        val entry = settingsStore.allAutoLockConfigs().entries.firstOrNull { it.value.enabled }
            ?: return@launch
        // Through the funnel, like every other caller -- see the note in setAutoLockConfig.
        com.bloo.bluelink.autolock.AutoLockService.start(
            getApplication(),
            entry.key,
            com.bloo.bluelink.autolock.AutoLockService.ACTION_START_WATCH,
        )
    }
}

/**
 * Bonded (paired) Bluetooth devices, for the "which one is your car" picker. Empty without
 * BLUETOOTH_CONNECT granted -- the Settings section prompts for it first.
 */
fun AppViewModel.pairedBluetoothDevices(): List<com.bloo.bluelink.autolock.PairedDevice> =
    com.bloo.bluelink.autolock.BluetoothDevices.bondedDevices(getApplication())

/**
 * "Simulate leaving" test button: runs the exact same evaluation a real Bluetooth disconnect would,
 * without needing to actually drive off and walk away.
 */
fun AppViewModel.simulateAutoLockLeaving(v: Vehicle) {
    viewModelScope.launch {
        val config = settingsStore.autoLockConfig(v.vin)
        com.bloo.bluelink.autolock.AutoLockTrigger.onCarDisconnected(getApplication(), v.vin, log = false)
        AppLog.log("AutoLock: simulated leaving ${v.name}.")
    }
}
