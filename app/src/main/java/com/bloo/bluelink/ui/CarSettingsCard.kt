package com.bloo.bluelink.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.climatePresets
import com.bloo.bluelink.data.lastServiceMiles
import com.bloo.bluelink.data.serviceIntervalMiles
import com.bloo.bluelink.data.setDefaultClimatePreset
import com.bloo.bluelink.data.setLastServiceMiles
import com.bloo.bluelink.data.setLicensePlate
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
            // Clear of the photo by default for a cut-out, which is where the text would otherwise cover the car. Any other picture, or
            // the brand gradient, can be switched on the same way.
            val textClear = state.textClearPhotos[v.vin] ?: (storedImage != null && isCutoutPhoto(storedImage))
            // A live preview, so the effect of a change is visible without leaving Settings. It is the hero as the car will show it, so it
            // is there for the gradient too.
            HeroPhotoPreview(state, v, storedImage, LocalAppearance.current.metricDistance, clearOfText = textClear)
            CarPhotoButtons(hasPhoto, onPick = onPickPhoto, onClear = { vm.setVehicleImage(v.vin, "") })
            CarPictureButton(v, storedImage, vm)
            ToggleRow(
                "Floating photo",
                textClear,
                description = "The name and charge sit around the photo.",
            ) { vm.setTextClearPhoto(v, it) }
        }

        SettingsGroup("Car") {
            SecondaryText("Powertrain")
            PowertrainPicker(current = state.powertrainOf(v)) { pt -> vm.setPowertrain(v, pt) }
            // The head unit is detected from the car's own generation; there is nothing to set.
            SecondaryText("Head unit")
            BodySmallText(if (state.isGen5WEffective(v)) "Gen5W, detected from the car." else "ccNC, detected from the car.")
            // Surround view is a brand feature (see BrandCapabilities); the choice then decides per car, by trim.
            if (vm.brandOf(v).capabilities.surroundView) {
                SecondaryText("Surround view")
                BodySmallText("Limited trims: ccNC limited cars only. Always: every ccNC car that takes the photos. Off: hidden.")
                if (state.isGen5WEffective(v)) {
                    BodySmallText("Needs a ccNC head unit. This car's head unit is Gen5W, so the feature stays hidden.")
                }
                if (state.surroundRefused[v.vin] == true) {
                    BodySmallText("Bluelink refused this car's photo request, so the feature is hidden. Choose a setting to try again.")
                }
                SurroundViewPicker(current = state.surroundAccessOf(v)) { a -> vm.setSurroundView(v, a) }
            }
        }

        SettingsGroup("Climate") {
            BodySmallText("Which seats heat or cool.")
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
                    BodySmallText("What Start runs: smart climate or a preset.")
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
            BodySmallText("When it was last serviced.")
            if (advanced) {
                SectionDivider()
                SelectionContainer { StatusRow("VIN", v.vin) }
                LabeledField(state.licensePlates[v.vin] ?: "", { vm.setLicensePlate(v.vin, it) }, "Licence plate")
            }
        }
      }
    }
}
