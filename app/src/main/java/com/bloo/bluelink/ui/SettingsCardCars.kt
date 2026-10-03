@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi

/**
 * "Cars" section content -- see the call site in [SettingsScreen] for context.
 * `expandedCar`/`single` are genuinely local to this section (nothing else reads
 * them), so they're declared here rather than threaded down as parameters. [pick]
 * is the one piece of behavior this section needs from its caller: it reaches into
 * `pickTarget`/`photoLauncher`, both of which live in [SettingsScreen] itself (the
 * crop flow below the scrolling list also reads `pickTarget`), so it's passed in
 * explicitly instead of being redeclared here.
 */
@Composable
internal fun CarsCardContent(state: UiState, vm: AppViewModel, pick: (String) -> Unit) {
                var expandedCar by remember { mutableStateOf<String?>(null) }
                val single = state.vehicles.size == 1
                if (single) {
                    // With one car, CarSettingsCard IS the section's card --
                    // forceExpanded already gives it the exact same always-open,
                    // no-chevron header every other top-level SettingsCard has.
                    // Wrapping it in another SettingsCard("Car") on top used to
                    // stack two pebble headers both announcing the same car for
                    // no reason (one titled "Car", the other the car's own
                    // name) -- redundant chrome with nothing to expand,
                    // collapse or reorder underneath it.
                    val v = state.vehicles[0]
                    // The exact same wrapper SettingsCard itself uses (gap + heading()
                    // semantics), via settingsCardSlot() -- this bypasses SettingsCard to
                    // avoid stacking two pebble headers for one car, but still wants its
                    // outer chrome, so it shares that one definition instead of a second
                    // hand-written copy.
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

