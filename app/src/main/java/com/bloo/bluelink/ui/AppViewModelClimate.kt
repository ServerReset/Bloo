package com.bloo.bluelink.ui

import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.ClimateRequest
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.toClimateSync
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.climatePresets
import com.bloo.bluelink.data.deleteClimatePreset
import com.bloo.bluelink.data.saveClimatePreset
import com.bloo.bluelink.data.savedClimate
import com.bloo.bluelink.data.setClimatePresets

// --- Saved climate draft, presets, and the cross-composition climate mirror (extracted from AppViewModel) --

/** Restore the last-used climate settings for a car (null if never saved). */
suspend fun AppViewModel.loadSavedClimate(v: Vehicle): ClimateRequest? = settingsStore.savedClimate(v.vin)

/** Debounced cross-composition mirror of the live climate draft. */
fun AppViewModel.saveClimateDebounced(v: Vehicle, req: ClimateRequest, activePresetId: String?) {
    climateSaveJobs[v.vin]?.cancel()
    climateSaveJobs[v.vin] = viewModelScope.launch {
        kotlinx.coroutines.delay(400)
        publishClimateState(v.vin, activePresetId, req)
    }
}

// Climate-preset CRUD: each of these three follows the same optimistic pattern -- compute the new
// per-VIN preset list, write it into UiState.climatePresets immediately so the UI updates without
// waiting on disk I/O, then persist the same change to SettingsStore asynchronously.

/**
 * Save the current climate draft as a new named preset (a fresh timestamp-based id, so presets
 * never collide even if named the same).
 */
fun AppViewModel.saveClimatePreset(v: Vehicle, name: String, req: ClimateRequest) {
    val preset = ClimatePreset(
        id = System.currentTimeMillis().toString(),
        name = name.trim().ifBlank { "Preset" },
        request = req,
    )
    _state.update {
        it.copy(climatePresets = it.climatePresets + (v.vin to (it.climatePresets[v.vin].orEmpty() + preset)))
    }
    viewModelScope.launch { settingsStore.saveClimatePreset(v.vin, preset) }
}

/** Remove one saved preset by id. */
fun AppViewModel.deleteClimatePreset(v: Vehicle, id: String) {
    _state.update {
        val updated = it.climatePresets[v.vin].orEmpty().filter { p -> p.id != id }
        it.copy(climatePresets = it.climatePresets + (v.vin to updated))
    }
    viewModelScope.launch { settingsStore.deleteClimatePreset(v.vin, id) }
}

/** Persist a new drag-and-drop order for a car's saved presets. */
fun AppViewModel.reorderClimatePresets(v: Vehicle, ordered: List<ClimatePreset>) {
    _state.update { it.copy(climatePresets = it.climatePresets + (v.vin to ordered)) }
    viewModelScope.launch { settingsStore.setClimatePresets(v.vin, ordered) }
}

/**
 * Mirror this car's live climate draft + active preset to the cross-composition state. Skips the
 * write when nothing changed, so state received from another live composition doesn't echo straight
 * back and loop.
 */
fun AppViewModel.publishClimateState(vin: String, presetId: String?, req: ClimateRequest) {
    val cs = req.toClimateSync(presetId)
    if (_state.value.climateSync[vin] == cs) return
    val merged = _state.value.climateSync + (vin to cs)
    _state.update { it.copy(climateSync = merged) }
}
