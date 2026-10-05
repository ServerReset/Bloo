package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.VehicleRepository
import com.bloo.bluelink.data.brand
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.aiEnabled
import com.bloo.bluelink.data.autoLockConfiguredVins
import com.bloo.bluelink.data.clearAllAutoLockConfigs
import com.bloo.bluelink.data.lastSyncMs
import com.bloo.bluelink.data.settingsMode
import com.bloo.bluelink.data.syncDeviceName
import com.bloo.bluelink.data.syncUri
import com.bloo.bluelink.data.syncWifiOnly

// --- Sign-out (extracted from AppViewModel) --

/**
 * Signs out of one brand. The server logout is best-effort; clearing credentials and dropping the cached
 * [VehicleRepository] always happens. The last account replaces [UiState] with a fresh login state; otherwise the garage reloads.
 */
fun AppViewModel.logout(brand: Brand) {
    viewModelScope.launch {
        runCatching { repoFor(brand).logout() }
        credentialStore.clear(brand)
        repos.remove(brand)
        AppLog.log("Signed out of ${brand.label}")
        val remaining = credentialStore.loadAll()
        if (remaining.isEmpty()) {
            // Wipe account-derived telemetry (plaintext JSON: car GPS/lock/charge state, place names) so it cannot reload on the next cold start.
            runCatching { statusCache.clear() }
            runCatching { snapshotStore.saveVehicles(emptyList()) }
            // AutoLock config is keyed by VIN; leaving it would make every Bluetooth event check a car that can never be found.
            runCatching {
                val autoLockVins = settingsStore.autoLockConfiguredVins()
                com.bloo.bluelink.autolock.AutoLockController.forgetAll(getApplication(), autoLockVins)
                settingsStore.clearAllAutoLockConfigs()
            }
            // `sessionFetched` is add-only and short-circuits ensureStatus; clear it so signing back in refetches statuses.
            sessionFetched.clear()
            // Preserve everything that is not account state across the full reset: device capability probes, Drive sync
            // fields (the settings push gates on `syncUri`; the others are this device's sync identity) and `settingsMode`.
            // defaultClimatePresets is keyed per VIN, so it is account state and not preserved.
            val keep = _state.value
            _state.value = UiState(
                screen = Screen.Login,
                aiSupported = keep.aiSupported,
                aiEnabled = keep.aiEnabled,
                shizukuAvailable = keep.shizukuAvailable,
                settingsMode = keep.settingsMode,
                syncUri = keep.syncUri,
                lastSyncMs = keep.lastSyncMs,
                syncWifiOnly = keep.syncWifiOnly,
                syncError = keep.syncError,
                syncDevices = keep.syncDevices,
                syncPrimaryId = keep.syncPrimaryId,
                thisDeviceId = keep.thisDeviceId,
                syncDeviceName = keep.syncDeviceName,
            )
        } else {
            _state.update { it.copy(accounts = remaining) }
            loadGarage()
        }
    }
}
