package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.AppLog
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.VehicleRepository
import com.bloo.bluelink.data.brand
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- Sign-out (extracted from AppViewModel) --

/**
 * Sign out of one brand. The server-side logout call is best-effort
 * (`runCatching` — a failed logout call shouldn't block clearing local
 * state), but clearing the cached credentials and dropping the brand's
 * cached [VehicleRepository] from [repos] always happens so the app
 * forgets that brand for good. If that was the LAST signed-in account, the
 * whole [UiState] is replaced with a fresh one pointed at the login screen
 * (wiping any stale per-VIN data for the signed-out cars); otherwise the
 * garage is reloaded so it no longer shows that brand's vehicles.
 */
fun AppViewModel.logout(brand: Brand) {
    viewModelScope.launch {
        runCatching { repoFor(brand).logout() }
        credentialStore.clear(brand)
        repos.remove(brand)
        AppLog.log("Signed out of ${brand.label}")
        val remaining = credentialStore.loadAll()
        if (remaining.isEmpty()) {
            // Wipe account-derived telemetry from disk on full sign-out: the
            // last-known car GPS/lock/charge state and reverse-geocoded place
            // names persist as plaintext JSON and would otherwise re-load into
            // the UI on the next cold start. Session tokens + credentials are
            // already cleared above; this closes the derived-location leak.
            runCatching { statusCache.clear() }
            runCatching { snapshotStore.saveVehicles(emptyList()) }
            // AutoLock config is account-derived too (it's keyed by VIN): a car left
            // registered here after its account is gone means every future Bluetooth
            // connect/disconnect this phone sees keeps checking it, forever, against a
            // vehicle AutoLockController can now never find. See both functions' own docs.
            runCatching {
                val autoLockVins = settingsStore.autoLockConfiguredVins()
                com.bloo.bluelink.autolock.AutoLockController.forgetAll(getApplication(), autoLockVins)
                settingsStore.clearAllAutoLockConfigs()
            }
            // `sessionFetched` is add-only and lives for the ViewModel's life, and
            // [ensureStatus] returns immediately for any VIN in it. Left uncleared here,
            // signing out and back in within the same process meant every car's
            // ensureStatus short-circuited against a set describing a session whose
            // statusCache and snapshot we had just wiped two lines above -- so the garage
            // sat showing unknown lock, charge and range for every car, with no spinner
            // and no error, until the user found pull-to-refresh.
            //
            // Belongs exactly here, beside the other two things being cleared because the
            // account is gone.
            sessionFetched.clear()
            // Preserve everything that is NOT account state across the full reset.
            //
            // This already preserved the four device-capability probes, with a comment
            // giving the right rule -- "they're device capabilities, not account state" --
            // and then applied it to four fields when nine more qualify. Signing out of
            // a car account says nothing about which Drive file this DEVICE backs up to,
            // or whether this user picked Advanced settings.
            //
            // The Drive fields are the ones that actually broke something. The debounced
            // settings push gates on `_state.value.syncUri == null` (see the
            // dirtyKeysFlow collector), so wiping it here silently stops Drive sync for
            // the rest of the process even though the URI is still on disk -- and
            // `thisDeviceId`/`syncDeviceName`/`syncPrimaryId` are this device's identity
            // in the sync registry, which a car sign-out has no business resetting.
            // It recovers on the next sign-in, because loadGarage re-reads the store,
            // but "recovers if you sign back in" is not the same as "works".
            //
            // `settingsMode` is the visible one: sign out and the Settings screen drops
            // from Advanced back to Simple.
            //
            // Deliberately NOT preserved: defaultClimatePresets, which is keyed per VIN
            // and therefore genuinely account state.
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
