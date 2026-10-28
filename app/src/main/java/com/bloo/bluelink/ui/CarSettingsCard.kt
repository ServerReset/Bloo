package com.bloo.bluelink.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.climatePresets
import com.bloo.bluelink.data.lastServiceMiles
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.serviceIntervalMiles
import com.bloo.bluelink.data.setDefaultClimatePreset
import com.bloo.bluelink.data.setLastServiceMiles
import com.bloo.bluelink.data.setLicensePlate
import com.bloo.bluelink.data.setPlatform
import com.bloo.bluelink.data.setPowertrain
import com.bloo.bluelink.data.setSeatFlag
import com.bloo.bluelink.data.setServiceIntervalMiles
import com.bloo.bluelink.data.settingsMode
import com.bloo.uicommon.AppIcons
import com.bloo.uicommon.MotionShort
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.GapGroup
import com.bloo.uicommon.MorphSegmented
import com.bloo.uicommon.SegmentOption

/** One reorderable car entry in Settings; tap to expand its setup + photo. */
@Composable
internal fun CarSettingsCard(
    v: Vehicle,
    state: UiState,
    vm: AppViewModel,
    expanded: Boolean,
    dragging: Boolean,
    modifier: Modifier,
    onToggle: () -> Unit,
    onPickPhoto: () -> Unit,
    collapsible: Boolean = true,
) {
    val seats = state.seatConfigs[v.vin] ?: SeatConfig()
    val cardBg by androidx.compose.animation.animateColorAsState(
        if (dragging) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(MotionShort),
        label = "carCardBg",
    )
    PebbleShell(
        expanded = expanded,
        onToggle = onToggle,
        icon = AppIcons.DirectionsCar,
        title = v.name,
        modifier = modifier,
        summary = "${v.model} · ${state.powertrainLabel(v)}",
        containerColor = cardBg,
        forceExpanded = !collapsible,
    ) {
        val advanced = state.settingsMode == "advanced"
      Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
        // Order is the order you would set a car up in: how it looks, what it is, what it can do,
        // what it does by itself, and -- advanced only -- the paperwork.
        SettingsGroup("Photo") {
            val storedImage = state.imageUrls[v.vin]
            val hasPhoto = !storedImage.isNullOrBlank()
            // A live preview, so the effect of a change is visible without leaving Settings.
            if (hasPhoto) HeroPhotoPreview(state, v, storedImage, LocalAppearance.current.metricDistance)
            CarPhotoButtons(hasPhoto, onPick = onPickPhoto, onClear = { vm.setVehicleImage(v.vin, "") })
            CarPictureButton(v, storedImage, vm)
        }

        SettingsGroup("Vehicle") {
            SecondaryText("Powertrain")
            PowertrainPicker(current = state.powertrainOf(v)) { pt -> vm.setPowertrain(v, pt) }
            // Only Hyundai/Genesis US vehicles have a real head-unit generation to confirm -- see
            // Vehicle.platformOverridable. Everywhere else it resolves the same way regardless, so
            // a picker there would be a control with no effect.
            if (v.platformOverridable) {
                SecondaryText("Head unit")
                BodySmallText("Confirm this car's head unit. The service can't always tell, and some features only appear when supported.")
                PlatformPicker(current = state.platformOf(v)) { pt -> vm.setPlatform(v, pt) }
            }
        }

        SettingsGroup("Climate") {
            BodySmallText("Which seats your car has, and whether each can heat, cool, or both.")
            SeatPositions.forEach { pos ->
                SeatConfigRow(pos.label, pos.heat(seats), pos.cool(seats),
                    { vm.setSeatFlag(v, pos.heatKey, it) }, { vm.setSeatFlag(v, pos.coolKey, it) })
            }
            ToggleRow("Heated steering wheel", seats.steeringWheel) { vm.setSeatFlag(v, "sw", it) }
            PopVisible(visible = advanced) {
                Column {
                    SectionDivider()
                    Spacer(Modifier.height(GapRow))
                    SecondaryText("Default start")
                    BodySmallText("What the Start button runs: smart climate, or one of your presets.")
                    val carPresets = state.climatePresets[v.vin].orEmpty()
                    MorphSegmented(
                        options = buildList {
                            add(SegmentOption("smart", "Smart", null))
                            carPresets.forEach { p -> add(SegmentOption(p.id, p.name, null)) }
                        },
                        selectedKey = state.defaultClimatePresets[v.vin] ?: "smart",
                        onSelect = { key -> vm.setDefaultClimatePreset(v.vin, key.takeIf { it != "smart" }) },
                    )
                }
            }
        }

        AutoLockSettingsGroup(v, vm, gen5w = state.isGen5WEffective(v))

        // Service is shown in Simple mode too -- it feeds the "service due" alert -- not just
        // Advanced. Only the identity paperwork (VIN, plate) stays advanced.
        SettingsGroup(if (advanced) "Service & identity" else "Service") {
            Row(horizontalArrangement = Arrangement.spacedBy(GapRow)) {
                MilesField(state.lastServiceMiles[v.vin], "Last service (mi)", Modifier.weight(1f)) {
                    vm.setLastServiceMiles(v.vin, it)
                }
                MilesField(state.serviceIntervalMiles[v.vin], "Interval (mi)", Modifier.weight(1f)) {
                    vm.setServiceIntervalMiles(v.vin, it)
                }
            }
            BodySmallText("When the car was last serviced, and how often it is due.")
            if (advanced) {
                SectionDivider()
                SelectionContainer { StatusRow("VIN", v.vin) }
                LabeledField(state.licensePlates[v.vin] ?: "", { vm.setLicensePlate(v.vin, it) }, "Licence plate")
            }
        }
      }
    }
}
