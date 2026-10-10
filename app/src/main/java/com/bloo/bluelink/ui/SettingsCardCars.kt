package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.ReorderColumn

/** "Cars" section content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun CarsCardContent(state: UiState, vm: AppViewModel, pick: (String) -> Unit) {
    var expandedCar by remember { mutableStateOf<String?>(null) }
    if (state.vehicles.size == 1) {
        // With one car, the car card IS the section -- forceExpanded gives it the same always-open,
        // no-chevron header every other top-level Settings card has.
        val v = state.vehicles[0]
        Box(Modifier.settingsCardSlot()) {
            CarSettingsCard(
                v = v, state = state, vm = vm,
                expanded = true, dragging = false, modifier = Modifier,
                collapsible = false,
                onToggle = {}, onPickPhoto = { pick(v.vin) },
            )
        }
    } else {
        // Each car is its OWN top-level pebble, not nested inside a wrapping "Cars" card: reaching a
        // car's settings is a single tap instead of opening a card to find another one, and there is
        // no pebble-inside-a-pebble chrome. The column reorders the cars by drag.
        ReorderColumn(
            items = state.vehicles,
            keyOf = { it.vin },
            onReorder = { vm.reorderVehicles(it) },
            spacing = 0.dp,
        ) { v, itemDragHandle, dragging ->
            Box(Modifier.settingsCardSlot()) {
                CarSettingsCard(
                    v = v, state = state, vm = vm,
                    expanded = expandedCar == v.vin, dragging = dragging, modifier = itemDragHandle,
                    onToggle = { expandedCar = if (expandedCar == v.vin) null else v.vin },
                    onPickPhoto = { pick(v.vin) },
                )
            }
        }
    }
}
