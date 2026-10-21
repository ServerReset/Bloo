package com.bloo.bluelink.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.CarLabels
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.uicommon.AppIcons

/**
 * The car's climate on the watch, the phone's own, in ONE pebble: smart climate at the top, saying what it is going to set
 * ("Cool to 62° · 98° outside"), then every preset saved on the phone for this car as a row (the name over what it sets), and a
 * stop in their place while it runs. The page scrolls, so any number of presets fit. A start goes through the same PIN gate as a
 * tap on the face ([requestCommand]).
 */
@Composable
internal fun WearClimatePebble(
    repo: WearSnapshotRepository,
    v: VehicleSnapshot,
    pending: Set<String>,
    requestCommand: ((() -> Unit)) -> Unit,
) {
    val presets by WearPresets.presets.collectAsStateWithLifecycle()
    val hints by WearSmartClimate.hints.collectAsStateWithLifecycle()
    LaunchedEffect(v.vin, v.lat, v.lon) { WearSmartClimate.refresh(v) }
    val saved = presets[v.vin].orEmpty()
    fun busy(action: String) = WearCommands.key(v.vin, action) in pending
    WearPebble(title = "Climate", icon = AppIcons.Climate) {
        if (v.climateOn == true) {
            WearActionRow(CarLabels.STOP_CLIMATE, AppIcons.Close, pending = busy(CarAction.CLIMATE_OFF)) {
                requestCommand { repo.command(v.vin, CarAction.CLIMATE_OFF) }
            }
            return@WearPebble
        }
        WearListRow(
            title = CarLabels.SMART_CLIMATE,
            subtitle = hints[v.vin]?.let(WearSmartClimate::describe) ?: "For the weather at the car",
            icon = AppIcons.SmartClimate,
            pending = busy(CarAction.CLIMATE_SMART),
        ) {
            requestCommand { repo.command(v.vin, CarAction.CLIMATE_SMART) }
        }
        saved.forEach { preset ->
            WearListRow(
                title = preset.name,
                subtitle = WearPresets.detail(preset.request),
                icon = AppIcons.Thermostat,
                pending = busy(CarAction.CLIMATE_ON),
            ) {
                requestCommand { repo.command(v.vin, CarAction.CLIMATE_ON, preset.request) }
            }
        }
        if (saved.isEmpty()) WearCaption("Presets you save in Bloo on your phone show up here.", small = true)
    }
}
