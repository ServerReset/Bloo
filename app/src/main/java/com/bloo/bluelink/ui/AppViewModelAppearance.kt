package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.deleteCustomPalette
import com.bloo.bluelink.data.saveCustomPalette
import com.bloo.bluelink.data.searchBubblePosition
import com.bloo.bluelink.data.setActiveCustomPaletteId
import com.bloo.bluelink.data.setAuroraBackground
import com.bloo.bluelink.data.setAuroraMotion
import com.bloo.bluelink.data.setColorPalette
import com.bloo.bluelink.data.setColumnsFlipped
import com.bloo.bluelink.data.setDynamicColor
import com.bloo.bluelink.data.setFontChoice
import com.bloo.bluelink.data.setHapticsEnabled
import com.bloo.bluelink.data.setPebbleOutline
import com.bloo.bluelink.data.setSeamlessInstallShizuku
import com.bloo.bluelink.data.setSearchBubblePosition
import com.bloo.bluelink.data.setThemeMode
import com.bloo.bluelink.data.setUiScale
import com.bloo.bluelink.data.setUnitSystem
import com.bloo.bluelink.data.setTempUnit
import com.bloo.bluelink.data.setDistanceUnit
import com.bloo.bluelink.data.setGlassClarity
import com.bloo.bluelink.data.setUltraGlass
import com.bloo.bluelink.data.setVibrancy

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

// Deferred variants for the settings sliders: these two values recompose ~the whole app
// (colorScheme / LocalDensity), so the commit waits a beat past slider release to let the
// settle-bounce animation get a clean run.
fun AppViewModel.setUiScaleSoon(value: Float) =
    viewModelScope.launch { settingsStore.setUiScale(value) }
fun AppViewModel.setGlassClaritySoon(value: Float) =
    viewModelScope.launch { settingsStore.setGlassClarity(value) }

fun AppViewModel.setUltraGlass(value: Boolean) =
    viewModelScope.launch { settingsStore.setUltraGlass(value) }
fun AppViewModel.setVibrancySoon(value: Float) =
    viewModelScope.launch { settingsStore.setVibrancy(value) }
fun AppViewModel.setHapticsEnabled(value: Boolean) = viewModelScope.launch { settingsStore.setHapticsEnabled(value) }

// More of the same appearance-setter pattern described above the setThemeMode group: DataStore
// write only, UI updates via the `appearance` StateFlow mirror. setAuroraMotion configures the
// animated background's speed; its colors always derive from the current theme.
fun AppViewModel.setPebbleOutline(value: Boolean) = viewModelScope.launch { settingsStore.setPebbleOutline(value) }

/**
 * Where the floating search bubble was last dragged to (fractions of its own drag range), or null
 * if never dragged. See SettingsStore's own doc.
 */
suspend fun AppViewModel.searchBubblePosition(): Pair<Float, Float>? = settingsStore.searchBubblePosition()
fun AppViewModel.setSearchBubblePosition(xFrac: Float, yFrac: Float) =
    viewModelScope.launch { settingsStore.setSearchBubblePosition(xFrac, yFrac) }

/**
 * Toggle the opt-in Shizuku silent-install path (device-local; see SettingsStore). Turning it ON
 * prompts for the Shizuku permission immediately — that request is also what makes Bloo appear in
 * the Shizuku manager's app list (declaring the provider alone isn't enough). If Shizuku isn't
 * running, guide the user.
 */
fun AppViewModel.setSeamlessInstallShizuku(value: Boolean) {
    viewModelScope.launch { settingsStore.setSeamlessInstallShizuku(value) }
    if (value) {
        val installer = com.bloo.bluelink.update.ShizukuInstaller
        if (installer.hasPermission()) return
        val queued = installer.requestPermissionOnEnable(SHIZUKU_INSTALL_REQUEST_CODE)
        if (!queued && !installer.isAvailable()) {
            _state.update {
                it.copy(message = "Start Shizuku, then Bloo can install updates silently.", messageType = ToastKind.INFO)
            }
        }
    }
}

/**
 * Re-probe Shizuku availability off the main thread (binder ping). Called from init and on warm
 * resume, so starting Shizuku while the app is open reveals the "Updates" toggle without needing a
 * cold restart.
 */
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

fun AppViewModel.setTempUnit(value: String) = viewModelScope.launch { settingsStore.setTempUnit(value) }

fun AppViewModel.setDistanceUnit(value: String) = viewModelScope.launch { settingsStore.setDistanceUnit(value) }
