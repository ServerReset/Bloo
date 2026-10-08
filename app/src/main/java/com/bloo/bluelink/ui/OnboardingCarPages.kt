package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.setPlatform
import com.bloo.bluelink.data.setPowertrain
import com.bloo.bluelink.data.setSeatFlag

/**
 * Card 1 of a car: what powers it, which decides whether it shows a battery, a fuel gauge, or both.
 * Picking an option applies it straight away; there is no separate confirm step.
 */
@Composable
internal fun OnboardingPowertrainPage(
    vehicle: com.bloo.bluelink.data.Vehicle,
    state: UiState,
    vm: AppViewModel,
) {
    BodySmallText("Sets the right status tiles: battery for an EV, fuel for gas, both for a plug-in hybrid. Pick what your ${vehicle.name} is.")
    PowertrainPicker(current = state.powertrainOf(vehicle)) { pt ->
        vm.setPowertrain(vehicle, pt)
    }
}

/**
 * Card 2 of a Hyundai/Genesis US car: its head-unit generation, which the API can't always tell.
 */
@Composable
internal fun OnboardingPlatformPage(
    vehicle: com.bloo.bluelink.data.Vehicle,
    state: UiState,
    vm: AppViewModel,
) {
    BodySmallText("Confirm the ${vehicle.name}'s head unit. Some features only show when the car supports them.")
    PlatformPicker(current = state.platformOf(vehicle)) { pt ->
        vm.setPlatform(vehicle, pt)
    }
}

/**
 * Card 3 of a car: which seats heat or cool, and whether the wheel heats, so the climate controls
 * match.
 */
@Composable
internal fun OnboardingClimatePage(
    vehicle: com.bloo.bluelink.data.Vehicle,
    state: UiState,
    vm: AppViewModel,
) {
    val sc = state.seatConfigs[vehicle.vin] ?: com.bloo.bluelink.data.SeatConfig()
    BodySmallText("Switch on what your ${vehicle.name} actually has. Leave a seat off if it can't heat or cool.")
    Column(Modifier.fillMaxWidth().outlinedPanel(12.dp)) {
        SeatPositions.forEachIndexed { i: Int, pos: SeatPosition ->
            if (i > 0) SectionDivider(alpha = 0.35f)
            SeatConfigRow(
                pos.label,
                pos.heat(sc),
                pos.cool(sc),
                onHeat = { enabled: Boolean -> vm.setSeatFlag(vehicle, pos.heatKey, enabled) },
                onCool = { enabled: Boolean -> vm.setSeatFlag(vehicle, pos.coolKey, enabled) },
            )
        }
    }
    ToggleRow("Heated steering wheel", sc.steeringWheel) { vm.setSeatFlag(vehicle, "sw", it) }
}
