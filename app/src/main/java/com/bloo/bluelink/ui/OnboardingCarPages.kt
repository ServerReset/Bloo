@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.setPlatform
import com.bloo.bluelink.data.setPowertrain
import com.bloo.bluelink.data.setSeatFlag


/** The "this is right" button every per-car card ends with: it confirms the answers on the card and
 *  lets the deck move on. Once confirmed it reads as done and stops being a button. */
@Composable
private fun ConfirmButton(label: String, confirmed: Boolean, onConfirm: () -> Unit) {
    if (confirmed) {
        IconLeadRow(AppIcons.CheckCircle, tint = MaterialTheme.colorScheme.primary, title = "Confirmed", badgeSize = 32.dp)
    } else {
        MorphActionButton(
            label = label,
            icon = AppIcons.Check,
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            emphasis = ButtonEmphasis.Primary,
        )
    }
}

/** Card 1 of a car: what powers it, which decides whether it shows a battery, a fuel gauge, or both. */
@Composable
internal fun OnboardingPowertrainPage(
    vehicle: com.bloo.bluelink.data.Vehicle,
    state: UiState,
    vm: AppViewModel,
    confirmed: Boolean,
    onConfirm: () -> Unit,
) {
    BodySmallText("Sets the right status tiles: battery for an EV, fuel for gas, both for a plug-in hybrid. Pick what your ${vehicle.name} is.")
    PowertrainPicker(current = state.powertrainOf(vehicle)) { pt ->
        vm.setPowertrain(vehicle, pt)
        onConfirm()
    }
    ConfirmButton("Yes, that's my car", confirmed, onConfirm)
}

/** Card 2 of a Hyundai/Genesis US car: its head-unit generation, which the API can't always tell. */
@Composable
internal fun OnboardingPlatformPage(
    vehicle: com.bloo.bluelink.data.Vehicle,
    state: UiState,
    vm: AppViewModel,
    confirmed: Boolean,
    onConfirm: () -> Unit,
) {
    BodySmallText("Confirm the ${vehicle.name}'s head unit. Some features only show when the car supports them.")
    PlatformPicker(current = state.platformOf(vehicle)) { pt ->
        vm.setPlatform(vehicle, pt)
        onConfirm()
    }
    ConfirmButton("That's right", confirmed, onConfirm)
}

/** Card 3 of a car: which seats heat or cool, and whether the wheel heats, so the climate controls match. */
@Composable
internal fun OnboardingClimatePage(
    vehicle: com.bloo.bluelink.data.Vehicle,
    state: UiState,
    vm: AppViewModel,
    confirmed: Boolean,
    onConfirm: () -> Unit,
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
    ConfirmButton("These are my car's features", confirmed, onConfirm)
}
