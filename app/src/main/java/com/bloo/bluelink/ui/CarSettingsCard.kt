@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.climatePresets
import com.bloo.bluelink.data.lastServiceMiles
import com.bloo.bluelink.data.serviceIntervalMiles
import com.bloo.bluelink.data.setDefaultClimatePreset
import com.bloo.bluelink.data.setLastServiceMiles
import com.bloo.bluelink.data.setLicensePlate
import com.bloo.bluelink.data.setPlatform
import com.bloo.bluelink.data.setPowertrain
import com.bloo.bluelink.data.setSeatFlag
import com.bloo.bluelink.data.setServiceIntervalMiles
import com.bloo.bluelink.data.settingsMode



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
        animationSpec = tween(200),
        label = "carCardBg",
    )
    // The exact same collapsible pebble every car's own pebble list on the
    // garage screen uses -- same bounce-open/calm-close springs, same corner
    // morph, same per-row staggered reveal, same "hold the header to drag"
    // idiom (no separate drag-handle icon; PebbleShell never draws one, and
    // this card used to be the only place in Settings that did). It used to
    // be its own bespoke Card + Row + AnimatedVisibility, a lookalike that
    // drifted from every other pebble's motion any time that shared spec
    // changed, which is what "standard" was pointing at.
    //
    // The collapsed header traded the old car-photo thumbnail for the same
    // icon + title + summary shape every other pebble uses -- the photo
    // itself is unchanged and still front-and-centre in the Photo group
    // below once expanded, so nothing about it is actually lost, only where
    // it first appears.
    PebbleShell(
        expanded = expanded,
        onToggle = onToggle,
        icon = Icons.Filled.DirectionsCar,
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
            if (hasPhoto) {
                AsyncImage(
                    model = rememberPhotoModel(storedImage),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(StandardShape),
                )
            }
            // A group, not a plain Row: the buttons share the row's width and press against each other.
            ExpressiveButtonRow(spacing = 8.dp) {
                MorphTextButton(if (hasPhoto) "Change photo" else "Choose photo", onClick = onPickPhoto)
                if (hasPhoto) MorphTextButton("Clear", onClick = { vm.setVehicleImage(v.vin, "") })
            }
        }

        SettingsGroup("Vehicle") {
            LabelText("Powertrain")
            PowertrainPicker(current = state.powertrainOf(v)) { pt -> vm.setPowertrain(v, pt) }
            // Only Hyundai/Genesis US vehicles have a real head-unit generation to confirm -- see
            // Vehicle.platformOverridable. Everywhere else it resolves the same way regardless, so
            // a picker there would be a control with no effect.
            if (v.platformOverridable) {
                LabelText("Head unit")
                MutedText("Confirm this car's head unit -- the API can't always tell. Some features only show when supported.")
                PlatformPicker(current = state.platformOf(v)) { pt -> vm.setPlatform(v, pt) }
            }
        }

        SettingsGroup("Climate") {
            MutedText("Which seats your car has, and whether each can heat, cool, or both.")
            SeatPositions.forEach { pos ->
                SeatConfigRow(pos.label, pos.heat(seats), pos.cool(seats),
                    { vm.setSeatFlag(v, pos.heatKey, it) }, { vm.setSeatFlag(v, pos.coolKey, it) })
            }
            ToggleRow("Heated steering wheel", seats.steeringWheel) { vm.setSeatFlag(v, "sw", it) }
            PopVisible(visible = advanced) {
                Column {
                    SectionDivider(alpha = 0.5f)
                    Spacer(Modifier.height(GapRow))
                    LabelText("Default start")
                    MutedText("What the Start button runs: smart climate, or one of your presets.")
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

        AutoLockSettingsGroup(v, vm)

        if (advanced) {
            SettingsGroup("Identity & service") {
                SelectionContainer { StatusRow("VIN", v.vin) }
                BlooTextField(
                    value = state.licensePlates[v.vin] ?: "",
                    onValueChange = { vm.setLicensePlate(v.vin, it) },
                    label = { Text("License plate") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MilesField(state.lastServiceMiles[v.vin], "Last service (mi)", Modifier.weight(1f)) {
                        vm.setLastServiceMiles(v.vin, it)
                    }
                    MilesField(state.serviceIntervalMiles[v.vin], "Interval (mi)", Modifier.weight(1f)) {
                        vm.setServiceIntervalMiles(v.vin, it)
                    }
                }
            }
        }
      }
    }
}


