package com.bloo.bluelink.wear

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Thermostat
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
    WearPebble(title = "Climate", icon = Icons.Filled.AcUnit) {
        if (v.climateOn == true) {
            WearActionRow("Stop climate", Icons.Filled.Close, pending = busy(CarAction.CLIMATE_OFF)) {
                requestCommand { repo.command(v.vin, CarAction.CLIMATE_OFF) }
            }
            return@WearPebble
        }
        WearActionRow("Smart climate", Icons.Filled.AutoMode, pending = busy(CarAction.CLIMATE_SMART)) {
            requestCommand { repo.command(v.vin, CarAction.CLIMATE_SMART) }
        }
        saved.forEach { preset ->
            WearActionRow(preset.name, Icons.Filled.Thermostat, pending = busy(CarAction.CLIMATE_ON)) {
                requestCommand { repo.command(v.vin, CarAction.CLIMATE_ON, preset.request) }
            }
            WearCaption(WearPresets.detail(preset.request), small = true, maxLines = 1)
        }
        if (saved.isEmpty()) {
            WearCaption(
                "Smart climate sets the temperature for the weather at the car. Presets you save in Bloo on your phone show up here.",
                maxLines = 5,
            )
        }
    }
}
