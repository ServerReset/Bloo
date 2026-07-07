package com.bloo.wear.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(vm: WearViewModel, ui: WearUi, onAddAccount: () -> Unit) {
    val state = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    var confirmSignOut by remember { mutableStateOf(false) }
    // Auto-reset the destructive confirm so a stale "tap again" can't sign you out later.
    LaunchedEffect(confirmSignOut) {
        if (confirmSignOut) {
            delay(4000)
            confirmSignOut = false
        }
    }

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { e ->
                scope.launch { state.scrollBy(e.verticalScrollPixels) }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
        state = state,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { ListHeader { Text("Settings", textAlign = TextAlign.Center) } }

        item {
            Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Accounts", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (ui.accounts.isEmpty()) {
                    Text("Synced from phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ui.accounts.forEach { email -> Text(email, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }

        item {
            MorphButton(
                label = "Add account",
                icon = Icons.Filled.PersonAdd,
                active = false,
                activeColor = MaterialTheme.colorScheme.primary,
                pending = false,
                onClick = onAddAccount,
            )
        }

        item {
            Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Appearance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Theme and units synced from phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Units: ${if (ui.settings?.useFahrenheit != false) "°F" else "°C"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                ui.settings?.uiScale?.let {
                    Text("Text scale: ${"%.2f".format(it)}×", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Watch text size", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${"%.1f".format(ui.localSettings.fontScale)}×",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                SliderRow(
                    label = "Scale",
                    valueLabel = "${"%.1f".format(ui.localSettings.fontScale)}×",
                    value = ((ui.localSettings.fontScale - 0.8f) / 0.05f).roundToInt(),
                    min = 0,
                    max = 12,
                    step = 1,
                ) { step -> vm.setFontScale(0.8f + step * 0.05f) }
            }
        }

        item {
            Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Tile chips", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Pick up to two actions for the glanceable Tile.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                val actions = ui.localSettings.tileActions
                listOf(
                    Triple("lock", "Lock / unlock", Icons.Filled.Lock),
                    Triple("climate", "Climate", Icons.Filled.Thermostat),
                    Triple("charge", "Charge", Icons.Filled.Bolt),
                ).forEach { (key, label, icon) ->
                    val checked = key in actions
                    MorphButton(
                        label = label,
                        icon = icon,
                        active = checked,
                        activeColor = MaterialTheme.colorScheme.primary,
                        pending = false,
                        onClick = {
                            val on = !checked
                            val next = if (on) (actions + key).distinct().takeLast(2) else actions - key
                            vm.setTileActions(next)
                        },
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }

        if (ui.cars.size > 1) {
            // One card per pool slot (up to WearTilePool.SIZE, or one per car if
            // fewer) so a multi-car household can add a separate glanceable Tile
            // for each car to their watch face, pinned independently.
            val slotCount = minOf(com.bloo.wear.WearTilePool.SIZE, ui.cars.size)
            for (index in 0 until slotCount) item(key = "tileSlot$index") {
                Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text("Tile ${index + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Which car this glanceable Tile shows on your watch face.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    // null = "Follow selected" - BlooTileService resolves a null
                    // slot to the app's selected car, NOT the same-index car. The
                    // old same-index fallback here made "Follow selected"
                    // impossible to show as active and highlighted a car the Tile
                    // wasn't actually going to render.
                    val selectedVin = ui.localSettings.tileCarVins.getOrNull(index)
                    @Composable
                    fun carOption(label: String, vin: String?) {
                        MorphButton(
                            label = label,
                            icon = Icons.Filled.DirectionsCar,
                            active = vin == selectedVin,
                            activeColor = MaterialTheme.colorScheme.primary,
                            pending = false,
                            onClick = { vm.setTileCarVin(index, vin) },
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    carOption("Follow selected", null)
                    ui.cars.forEach { car -> carOption(car.name, car.vin) }
                }
            }
        }

        item {
            Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Tile order", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Reorder a car's tiles from its More tile → Reorder tiles. The order stays in sync with that car on your phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                Text("Updates", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (vm.currentBuildNumber > 0) "Build #${vm.currentBuildNumber}" else "Local build",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                MorphButton(
                    label = if (ui.updateChecking) "Checking…" else "Check now",
                    icon = Icons.Filled.Refresh,
                    active = false,
                    activeColor = MaterialTheme.colorScheme.primary,
                    pending = ui.updateChecking,
                    onClick = { vm.checkForUpdatesNow() },
                )
                Spacer(Modifier.height(4.dp))
                MorphButton(
                    label = if (ui.localSettings.updateChecksEnabled) "Auto-check on" else "Auto-check off",
                    icon = Icons.Filled.Refresh,
                    active = ui.localSettings.updateChecksEnabled,
                    activeColor = MaterialTheme.colorScheme.primary,
                    pending = false,
                    onClick = { vm.setUpdateChecksEnabled(!ui.localSettings.updateChecksEnabled) },
                )
            }
        }

        item {
            Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                StatusRow(
                    label = "Phone",
                    value = if (ui.phoneConnected) "Connected" else "Standalone",
                    valueColor = if (ui.phoneConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!ui.phoneConnected) {
                    Text(
                        "Commands run directly on the watch",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            MorphButton(
                label = "Re-sync from phone",
                icon = Icons.Filled.Sync,
                active = false,
                activeColor = MaterialTheme.colorScheme.primary,
                pending = false,
                onClick = { vm.resync() },
            )
        }

        item {
            MorphButton(
                label = "Refresh all cars",
                icon = Icons.Filled.Refresh,
                active = false,
                activeColor = MaterialTheme.colorScheme.primary,
                pending = false,
                onClick = { vm.refreshAll() },
            )
        }

        item {
            MorphButton(
                label = if (confirmSignOut) "Tap again to confirm" else "Sign out",
                icon = Icons.Filled.Logout,
                active = confirmSignOut,
                activeColor = MaterialTheme.colorScheme.error,
                pending = false,
                onClick = { if (confirmSignOut) vm.signOutAll() else confirmSignOut = true },
            )
        }

        item {
            Text(
                "Bloo for Wear OS · 0.1",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
