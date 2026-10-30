package com.bloo.bluelink.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.ClimatePreset
import com.bloo.bluelink.data.CarLabels
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.uicommon.AppIcons

/**
 * The car's climate on the watch, the phone's own, in ONE pebble: smart climate at the top, saying what it is going to set
 * ("Cool to 62° · 98° outside"), then every preset saved on the phone for this car as a row (the name over what it sets), and a
 * stop in their place while it runs. The page scrolls, so any number of presets fit. A start goes through the same path as a
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
        WearClimateRows(
            smartSubtitle = hints[v.vin]?.let(WearSmartClimate::describe),
            saved = saved,
            smartPending = busy(CarAction.CLIMATE_SMART),
            presetPending = busy(CarAction.CLIMATE_ON),
            smallCaption = true,
            onSmart = { requestCommand { repo.command(v.vin, CarAction.CLIMATE_SMART) } },
            onPreset = { preset -> requestCommand { repo.command(v.vin, CarAction.CLIMATE_ON, preset.request) } },
        )
    }
}

/**
 * The rows every climate list on the watch shows: smart climate saying what it will set, then each of the phone's presets
 * saying what it sets, or a caption when there are none. The details page and the climate picker differ only in what a press does.
 */
@Composable
internal fun WearClimateRows(
    smartSubtitle: String?,
    saved: List<ClimatePreset>,
    smartPending: Boolean = false,
    presetPending: Boolean = false,
    smallCaption: Boolean = false,
    onSmart: () -> Unit,
    onPreset: (ClimatePreset) -> Unit,
) {
    WearListRow(
        title = CarLabels.SMART_CLIMATE, subtitle = smartSubtitle ?: "For the weather at the car",
        icon = AppIcons.SmartClimate, pending = smartPending, onClick = onSmart,
    )
    saved.forEach { preset ->
        WearListRow(
            title = preset.name,
            subtitle = WearPresets.detail(preset.request),
            icon = AppIcons.Thermostat,
            pending = presetPending,
        ) { onPreset(preset) }
    }
    if (saved.isEmpty()) {
        val text = "Presets you save in Bloo on your phone show up here."
        if (smallCaption) WearCaption(text, small = true) else WearCaption(text, maxLines = 3)
    }
}
