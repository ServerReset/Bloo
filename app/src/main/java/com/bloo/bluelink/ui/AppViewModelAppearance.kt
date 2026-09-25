package com.bloo.bluelink.ui

import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- Appearance / UI preference setters (extracted from AppViewModel) ------
//
// Each of these just writes one field to SettingsStore's DataStore and
// returns. None of them touch _state directly (aside from the couple noted
// below) because `appearance` is already a StateFlow mirroring
// settingsStore.appearance -- the UI picks up the change automatically once
// the DataStore write completes and that Flow re-emits. setDynamicColor is
// the exception that does extra work (see its own comment, where it still
// lives, above this group).

fun AppViewModel.setThemeMode(mode: ThemeMode) = viewModelScope.launch {
    settingsStore.setThemeMode(mode)
}
fun AppViewModel.setFontChoice(choice: FontChoice) = viewModelScope.launch { settingsStore.setFontChoice(choice) }
fun AppViewModel.setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsStore.setDynamicColor(enabled) }
fun AppViewModel.setColorPalette(palette: ColorPalette) = viewModelScope.launch { settingsStore.setColorPalette(palette) }
fun AppViewModel.saveCustomPalette(palette: CustomPaletteData) = viewModelScope.launch { settingsStore.saveCustomPalette(palette) }
fun AppViewModel.deleteCustomPalette(id: String) = viewModelScope.launch { settingsStore.deleteCustomPalette(id) }
fun AppViewModel.setActiveCustomPaletteId(id: String?) = viewModelScope.launch { settingsStore.setActiveCustomPaletteId(id) }

/** Swap which dual-column side the "hot spot" pebble lives on. */
fun AppViewModel.setColumnsFlipped(flipped: Boolean) = viewModelScope.launch { settingsStore.setColumnsFlipped(flipped) }

// Deferred variants for the settings sliders: these two values recompose
// ~the whole app (colorScheme / LocalDensity), so the commit waits a beat
// past slider release to let the settle-bounce animation get a clean run.
// In viewModelScope, not a screen-tied scope, so closing Settings inside
// that beat can't drop the change.
fun AppViewModel.setUiScaleSoon(value: Float) =
    viewModelScope.launch { settingsStore.setUiScale(value) }
fun AppViewModel.setVibrancySoon(value: Float) =
    viewModelScope.launch { settingsStore.setVibrancy(value) }
fun AppViewModel.setHapticsEnabled(value: Boolean) = viewModelScope.launch { settingsStore.setHapticsEnabled(value) }

// More of the same appearance-setter pattern described above the
// setThemeMode group: DataStore write only, UI updates via the
// `appearance` StateFlow mirror. setAuroraMotion configures the animated
// background's speed; its colors always derive from the current theme.
fun AppViewModel.setPebbleOutline(value: Boolean) = viewModelScope.launch { settingsStore.setPebbleOutline(value) }
fun AppViewModel.setShowSearch(value: Boolean) = viewModelScope.launch { settingsStore.setShowSearch(value) }

/** Where the cover screen's floating search bubble was last dragged to (fractions
 *  of its own drag range), or null if never dragged. See SettingsStore's own doc. */
suspend fun AppViewModel.searchBubblePosition(): Pair<Float, Float>? = settingsStore.searchBubblePosition()
fun AppViewModel.setSearchBubblePosition(xFrac: Float, yFrac: Float) =
    viewModelScope.launch { settingsStore.setSearchBubblePosition(xFrac, yFrac) }

/** Toggle the opt-in Shizuku silent-install path (device-local; see SettingsStore).
 *  Turning it ON prompts for the Shizuku permission immediately — that request is
 *  also what makes Bloo appear in the Shizuku manager's app list (declaring the
 *  provider alone isn't enough). If Shizuku isn't running, guide the user. */
fun AppViewModel.setSeamlessInstallShizuku(value: Boolean) {
    viewModelScope.launch { settingsStore.setSeamlessInstallShizuku(value) }
    if (value) {
        val installer = com.bloo.bluelink.update.ShizukuInstaller
        if (installer.hasPermission()) return
        val queued = installer.requestPermissionOnEnable(SHIZUKU_INSTALL_REQUEST_CODE)
        if (!queued && !installer.isAvailable()) {
            _state.update {
                it.copy(message = "Start Shizuku, then Bloo can install updates silently.", messageType = "info")
            }
        }
    }
}

/** Re-probe Shizuku availability off the main thread (binder ping). Called from
 *  init and on warm resume, so starting Shizuku while the app is open reveals the
 *  "Updates" toggle without needing a cold restart. */
fun AppViewModel.refreshShizukuAvailable() {
    com.bloo.bluelink.data.StartupTrace.markIfStarting("Shizuku probe: begin")
    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val avail = com.bloo.bluelink.update.ShizukuInstaller.isAvailable()
        _state.update { it.copy(shizukuAvailable = avail) }
    }
}

fun AppViewModel.setAuroraBackground(value: Boolean) = viewModelScope.launch { settingsStore.setAuroraBackground(value) }

fun AppViewModel.setAuroraMotion(value: String) = viewModelScope.launch { settingsStore.setAuroraMotion(value) }

/** Imperial vs. metric display throughout the app. */
fun AppViewModel.setUnitSystem(value: String) = viewModelScope.launch { settingsStore.setUnitSystem(value) }
