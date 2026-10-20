package com.bloo.bluelink.wear

import com.bloo.uicommon.AppIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.data.VehicleSnapshot

/**
 * The climate presets as a scrolling list: smart climate first (the temperature the weather at the car calls for), then every
 * preset saved on the phone for this car, each with what it sets. A tap hands back its key -- [WearFaceControls.CLIMATE_SMART_KEY]
 * or "preset:" and the id -- and the caller decides what that means: assign it to a face quarter, or start it now.
 */
@Composable
internal fun WearClimateList(
    car: VehicleSnapshot,
    title: String,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val presets by WearPresets.presets.collectAsStateWithLifecycle()
    val saved = presets[car.vin].orEmpty()
    WearBackable(onBack) {
        WearListScreen {
            item(key = "title") { WearCenteredText(title, style = MaterialTheme.typography.titleSmall) }
            item(key = "smart") {
                WearActionRow("Smart climate", AppIcons.SmartClimate) { onPick(WearFaceControls.CLIMATE_SMART_KEY) }
            }
            saved.forEach { preset ->
                item(key = preset.id) {
                    WearActionRow(preset.name, AppIcons.Thermostat) { onPick(WearFaceControls.PRESET_PREFIX + preset.id) }
                }
                item(key = preset.id + ":detail") { WearCaption(WearPresets.detail(preset.request), small = true, maxLines = 1) }
            }
            if (saved.isEmpty()) {
                item(key = "none") {
                    WearCaption("Presets you save in Bloo on your phone show up here.", maxLines = 3)
                }
            }
        }
    }
}
