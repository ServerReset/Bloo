package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bloo.uicommon.ReorderColumn

/** "Cars" section content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun CarsCardContent(state: UiState, vm: AppViewModel, pick: (String) -> Unit) {
                var expandedCar by remember { mutableStateOf<String?>(null) }
                val single = state.vehicles.size == 1
                if (single) {
                    // With one car, CarSettingsCard IS the section's card -- forceExpanded already
                    // gives it the exact same always-open, no-chevron header every other top-level
                    // SettingsCard has.
                    val v = state.vehicles[0]
                    // The exact same wrapper SettingsCard itself uses (gap + heading() semantics),
                    // via settingsCardSlot() -- this bypasses SettingsCard to avoid stacking two
                    // pebble headers for one car, but still wants its outer chrome, so it shares
                    // that one definition instead of a second hand-written copy.
                    Box(Modifier.settingsCardSlot()) {
                        CarSettingsCard(
                            v = v, state = state, vm = vm,
                            expanded = true, dragging = false, modifier = Modifier,
                            collapsible = false,
                            onToggle = {}, onPickPhoto = { pick(v.vin) },
                        )
                    }
                } else {
                    SettingsCard("Cars", vm = vm) {
                        ReorderColumn(
                            items = state.vehicles,
                            keyOf = { it.vin },
                            onReorder = { vm.reorderVehicles(it) },
                            spacing = GapRow,
                        ) { v, itemDragHandle, dragging ->
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
