package com.bloo.bluelink.ui

import com.bloo.uicommon.MorphIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.bloo.bluelink.data.DEFAULT_SECTIONS
import com.bloo.bluelink.data.SettingsCatalog
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.Vehicle
import com.bloo.uicommon.RevealText
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton
import com.bloo.uicommon.content

/**
 * Search controls for the settings that are layout rather than a switch or a slider: car order, saved
 * palettes, the weather place, and each car's pinned pebble, pebble order, collapsed pebbles and photo.
 * Each is the real control (reorder, pick, reset, clear), not a read-only value, so every setting the
 * catalog marks searchable can be changed from the search box as well as found by it.
 */
internal fun addStructuralEntries(
    entries: MutableList<SearchEntry>,
    state: UiState,
    appearance: SettingsStore.Appearance,
    vm: AppViewModel,
) {
    fun words(key: String) = SettingsCatalog.families.firstOrNull { it.key == key }?.keywords.orEmpty()
    fun add(title: String, key: String, extra: String = "", content: @Composable () -> Unit) {
        if (!isSettingAvailableInMode(title, state.settingsMode)) return
        entries.add(SearchEntry(title, "$title ${words(key)} $extra".lowercase(), content))
    }

    if (state.vehicles.size > 1) {
        add("Car order", "vehicle_order") { CarOrderControl(state, vm) }
    }
    add("Custom palettes", "custom_palettes") { CustomPaletteControl(appearance, vm) }
    add("Weather location", "weather_label", "weather follows my location device gps") {
        WeatherPlaceControls(appearance, vm)
    }
    state.vehicles.forEach { v ->
        add("Pinned pebble · ${v.name}", "hotspots_", v.name) { PinnedPebbleControl(v, state, vm) }
        add("Pebble order · ${v.name}", "sections_", v.name) { PebbleOrderControl(v, state, vm) }
        add("Collapsed pebbles · ${v.name}", "collapsed_", v.name) { CollapsedPebblesControl(v, state, vm) }
        add("Car photo · ${v.name}", "img_", v.name) { CarPhotoControl(v, state, vm) }
    }
}

/** The pebble names a person sees, by section id. */
internal fun sectionLabel(section: String): String = when (section) {
    "summary" -> "Hero card"
    "update" -> "Update"
    "controls" -> "Controls"
    "charge" -> "Charge"
    "climate" -> "Climate"
    "ai" -> "AI summary"
    "info" -> "Car info"
    "location" -> "Location"
    "trips" -> "Trips"
    "diagnostics" -> "Diagnostics"
    else -> section.replaceFirstChar { it.uppercase() }
}

/** The pebbles that can be pinned to the hot seat: the movable ones this car actually has. */
internal fun pinnablePebbles(v: Vehicle, state: UiState): List<String> =
    state.sectionsFor(v).filter { it !in setOf("summary", "update", "controls") && state.isSectionAvailable(v, it) }

@Composable
private fun CarOrderControl(state: UiState, vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        BodySmallText("Move a car up or down; the garage uses this order.")
        state.vehicles.forEachIndexed { i, v ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RevealText("${i + 1}. ${v.name}", style = SecondaryTextStyle(), modifier = Modifier.weight(1f))
                MorphIconButton(
                    onClick = { vm.reorderVehicles(state.vehicles.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) },
                    enabled = i > 0,
                ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move ${v.name} up") }
                MorphIconButton(
                    onClick = { vm.reorderVehicles(state.vehicles.toMutableList().also { it.add(i + 1, it.removeAt(i)) }) },
                    enabled = i < state.vehicles.lastIndex,
                ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move ${v.name} down") }
            }
        }
    }
}

@Composable
private fun CustomPaletteControl(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        if (appearance.customPalettes.isEmpty()) {
            BodySmallText("No custom palettes saved yet. Make one under Settings, Visuals.")
        } else {
            ChoiceRow("Built-in palette", appearance.activeCustomPaletteId == null) { vm.setActiveCustomPaletteId(null) }
            appearance.customPalettes.forEach { p ->
                ChoiceRow(p.name, appearance.activeCustomPaletteId == p.id) { vm.setActiveCustomPaletteId(p.id) }
            }
        }
    }
}

@Composable
private fun PinnedPebbleControl(v: Vehicle, state: UiState, vm: AppViewModel) {
    val pinned = state.hotspotSections[v.vin]
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        BodySmallText("The pebble kept beside the controls in the full-screen view.")
        ChoiceRow("None", pinned == null) { pinned?.let { vm.setHotspot(v, it) } }
        pinnablePebbles(v, state).forEach { section ->
            // setHotspot toggles, so choosing the pebble that is already pinned must do nothing.
            ChoiceRow(sectionLabel(section), pinned == section) { if (pinned != section) vm.setHotspot(v, section) }
        }
    }
}

@Composable
private fun PebbleOrderControl(v: Vehicle, state: UiState, vm: AppViewModel) {
    val order = state.sectionsFor(v).filter { state.isSectionAvailable(v, it) }
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        RevealText(order.joinToString(" · ") { sectionLabel(it) }, style = SecondaryTextStyle())
        BodySmallText("Drag pebbles on the car page to reorder them.")
        MorphTextButton(
            "Reset to default order",
            enabled = state.sectionOrders[v.vin] != null && state.sectionOrders[v.vin] != DEFAULT_SECTIONS,
            onClick = { vm.setSectionOrder(v, DEFAULT_SECTIONS) },
        )
    }
}

@Composable
private fun CollapsedPebblesControl(v: Vehicle, state: UiState, vm: AppViewModel) {
    val sections = state.sectionsFor(v).filter { it != "summary" && state.isSectionAvailable(v, it) }
    val open = sections.count { state.isPebbleExpanded(v.vin, it) }
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        StatusRow("Open pebbles", "$open of ${sections.size}")
        ExpressiveButtonRow(spacing = GapRow) {
            MorphTextButton("Collapse all", onClick = {
                sections.filter { state.isPebbleExpanded(v.vin, it) }.forEach { vm.togglePebble(v, it) }
            })
            MorphTextButton("Expand all", onClick = {
                sections.filter { !state.isPebbleExpanded(v.vin, it) }.forEach { vm.togglePebble(v, it) }
            })
        }
    }
}

@Composable
private fun CarPhotoControl(v: Vehicle, state: UiState, vm: AppViewModel) {
    val hasPhoto = !state.imageUrls[v.vin].isNullOrBlank()
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        StatusRow("Photo", if (hasPhoto) "Custom photo set" else "Using the default")
        CarPhotoButtons(hasPhoto, onPick = { vm.requestPhotoPick(v.vin) }, onClear = { vm.setVehicleImage(v.vin, "") })
        CarPictureButton(v, state.imageUrls[v.vin], vm)
    }
}

/** The car photo's two actions, shared by the car settings card and settings search: choose (or change) and clear. */
@Composable
internal fun CarPhotoButtons(hasPhoto: Boolean, onPick: () -> Unit, onClear: () -> Unit) {
    // A group, not a plain Row: the buttons share the row's width and press against each other.
    ExpressiveButtonRow(spacing = GapRow) {
        MorphTextButton(if (hasPhoto) "Change photo" else "Choose photo", onClick = onPick)
        if (hasPhoto) MorphTextButton("Clear", onClick = onClear, emphasis = ButtonEmphasis.Deny)
    }
}
