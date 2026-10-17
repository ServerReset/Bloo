package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    // No intro line on any of the three car cards: the card's own summary carries the question
    // ("Gas, hybrid, plug-in or electric?"), and the body used to open by restating it at length.
    // One card, one line, then the control.
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
    // innerGlassPanel(GapSection) and a 0.5 divider, matching the alerts list and SettingsGroup: this
    // panel was the last outlinedPanel(12.dp) in the deck, and its divider was the only 0.35 in the app.
    Column(Modifier.fillMaxWidth().innerGlassPanel(GapSection)) {
        SeatPositions.forEachIndexed { i: Int, pos: SeatPosition ->
            if (i > 0) SectionDivider()
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
