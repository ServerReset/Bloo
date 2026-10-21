package com.bloo.bluelink.wear

import com.bloo.uicommon.AppIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.data.CarLabels
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
    val hints by WearSmartClimate.hints.collectAsStateWithLifecycle()
    LaunchedEffect(car.vin, car.lat, car.lon) { WearSmartClimate.refresh(car) }
    val saved = presets[car.vin].orEmpty()
    WearBackable(onBack) {
        WearListScreen {
            item(key = "title") { WearCenteredText(title, style = MaterialTheme.typography.titleSmall) }
            // The same two-line rows as the details page's Climate section: smart climate saying what it will set, then each preset
            // saying what it sets.
            item(key = "climate") {
                WearPebble(title = "Climate", icon = AppIcons.Climate) {
                    WearListRow(
                        title = CarLabels.SMART_CLIMATE,
                        subtitle = hints[car.vin]?.let(WearSmartClimate::describe) ?: "For the weather at the car",
                        icon = AppIcons.SmartClimate,
                    ) { onPick(WearFaceControls.CLIMATE_SMART_KEY) }
                    saved.forEach { preset ->
                        WearListRow(
                            title = preset.name,
                            subtitle = WearPresets.detail(preset.request),
                            icon = AppIcons.Thermostat,
                        ) { onPick(WearFaceControls.PRESET_PREFIX + preset.id) }
                    }
                    if (saved.isEmpty()) {
                        WearCaption("Presets you save in Bloo on your phone show up here.", maxLines = 3)
                    }
                }
            }
        }
    }
}
