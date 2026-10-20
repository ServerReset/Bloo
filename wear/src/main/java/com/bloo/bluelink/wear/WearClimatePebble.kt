package com.bloo.bluelink.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import com.bloo.uicommon.AppIcons
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.VehicleSnapshot

/**
 * The car's climate on the watch, the phone's own: smart climate (the temperature the weather at the car calls for), then
 * every preset saved on the phone for this car, each starting at its settings, and a stop while it runs. A start goes
 * through the same PIN gate as a tap on the face ([requestCommand]).
 */
@Composable
internal fun WearClimatePebble(
    repo: WearSnapshotRepository,
    v: VehicleSnapshot,
    pending: Set<String>,
    requestCommand: ((() -> Unit)) -> Unit,
) {
    val presets by WearPresets.presets.collectAsStateWithLifecycle()
    val saved = presets[v.vin].orEmpty()
    fun busy(action: String) = WearCommands.key(v.vin, action) in pending
    if (v.climateOn == true) {
        WearPebble(title = "Climate", icon = AppIcons.Climate) {
            WearActionRow("Stop climate", AppIcons.Close, pending = busy(CarAction.CLIMATE_OFF)) {
                requestCommand { repo.command(v.vin, CarAction.CLIMATE_OFF) }
            }
        }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(SectionGap)) {
        WearPebble(title = "Climate", icon = AppIcons.Climate) {
            WearActionRow("Smart climate", AppIcons.SmartClimate, pending = busy(CarAction.CLIMATE_SMART)) {
                requestCommand { repo.command(v.vin, CarAction.CLIMATE_SMART) }
            }
            if (saved.isEmpty()) {
                WearCaption(
                    "Smart climate sets the temperature for the weather at the car. Presets you save in Bloo on your phone show up here.",
                    maxLines = 5,
                )
            }
        }
        // The presets from the phone, as a list of their own: a row each, the name over what it sets. The page scrolls, so any
        // number fit and nothing needs opening first.
        if (saved.isNotEmpty()) {
            WearPebble(title = "Presets", icon = AppIcons.Thermostat) {
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
            }
        }
    }
}
