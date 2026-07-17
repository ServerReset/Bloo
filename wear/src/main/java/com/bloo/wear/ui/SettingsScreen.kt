package com.bloo.wear.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.MyLocation
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
    var pinFlow by remember { mutableStateOf<PinFlowMode?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    // Auto-reset the destructive confirm so a stale "tap again" can't sign you out later.
    LaunchedEffect(confirmSignOut) {
        if (confirmSignOut) {
            delay(4000)
            confirmSignOut = false
        }
    }

    Box(Modifier.fillMaxSize()) {
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
            SettingSection("Accounts") {
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
            SettingSection("Appearance") {
                Text(
                    "Theme synced from phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                val metric = ui.localSettings.unitSystem == "metric"
                Row(
                    Modifier.fillMaxWidth().clickable { vm.setUnitSystem(if (metric) "imperial" else "metric") },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Units", style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (metric) "Metric (km, °C)" else "Imperial (mi, °F)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                ui.settings?.uiScale?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("Text scale: ${"%.2f".format(it)}×", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Weather location is set on your phone. Use its current spot:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                MorphButton(
                    label = "Use phone's location",
                    icon = Icons.Filled.MyLocation,
                    active = false,
                    activeColor = MaterialTheme.colorScheme.primary,
                    pending = false,
                    onClick = { vm.setWeatherFromDeviceLocation() },
                )
            }
        }

        item {
            SettingSection("PIN lock") {
                val ls = ui.localSettings
                Text(
                    if (ls.hasPin) "Locks the watch app after it's put away." else "Set a 4-digit PIN to lock the watch app when you're not wearing it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                if (ls.hasPin) {
                    MorphButton(
                        label = if (ls.pinLockEnabled) "Lock: On" else "Lock: Off",
                        icon = Icons.Filled.Lock,
                        active = ls.pinLockEnabled,
                        activeColor = MaterialTheme.colorScheme.primary,
                        pending = false,
                        onClick = { vm.setPinLockEnabled(!ls.pinLockEnabled) },
                    )
                    if (ls.pinLockEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text("Lock after", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        // A single segmented row (matching the app's established
                        // "pick exactly one of a short list" control, e.g. the
                        // brand picker on LoginScreen) instead of five stacked
                        // full-width buttons -- much less vertical space on a
                        // round face and one visual language instead of two.
                        MorphSegmented(
                            options = listOf(
                                WearSegmentOption("off", "Off"),
                                WearSegmentOption("immediate", "Now"),
                                WearSegmentOption("1min", "1m"),
                                WearSegmentOption("5min", "5m"),
                                WearSegmentOption("10min", "10m"),
                            ),
                            selectedKey = ls.pinLockTiming,
                            onSelect = { key -> vm.setPinLockTiming(key) },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    MorphButton(
                        label = "Change PIN",
                        icon = Icons.Filled.Lock,
                        active = false,
                        activeColor = MaterialTheme.colorScheme.primary,
                        pending = false,
                        onClick = { pinFlow = PinFlowMode.CHANGE },
                    )
                    Spacer(Modifier.height(4.dp))
                    MorphButton(
                        label = "Remove PIN",
                        icon = Icons.Filled.Lock,
                        active = false,
                        activeColor = MaterialTheme.colorScheme.error,
                        pending = false,
                        onClick = { pinFlow = PinFlowMode.REMOVE },
                    )
                } else {
                    MorphButton(
                        label = "Set PIN",
                        icon = Icons.Filled.Lock,
                        active = false,
                        activeColor = MaterialTheme.colorScheme.primary,
                        pending = false,
                        onClick = { pinFlow = PinFlowMode.SET },
                    )
                }
            }
        }

        item {
            SettingSection("AI Summaries") {
                val enabled = ui.settings?.aiEnabled == true
                Text(
                    "On-device summaries of a car's status, generated on your phone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                MorphButton(
                    label = if (enabled) "On" else "Off",
                    icon = Icons.Filled.Bolt,
                    active = enabled,
                    activeColor = MaterialTheme.colorScheme.primary,
                    pending = false,
                    onClick = { vm.setAiEnabled(!enabled) },
                )
            }
        }

        item {
            SettingSection("Aurora background") {
                val enabled = ui.settings?.auroraEnabled == true
                Text(
                    "A soft gradient behind the watch app instead of a solid surface.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                MorphButton(
                    label = if (enabled) "On" else "Off",
                    icon = Icons.Filled.Bolt,
                    active = enabled,
                    activeColor = MaterialTheme.colorScheme.primary,
                    pending = false,
                    onClick = { vm.setAuroraEnabled(!enabled) },
                )
            }
        }

        item {
            SettingSection("Watch text size") {
                // A local draft during the drag -- setFontScale does a DataStore
                // write AND a Wearable Data Layer push to the phone, so it
                // should commit once on release, not on every drag tick (which
                // fired dozens of near-simultaneous writes/IPC sends per drag,
                // racing each other with no ordering guarantee).
                var draft by remember(ui.localSettings.fontScale) { mutableStateOf(ui.localSettings.fontScale) }
                Text(
                    "${"%.1f".format(draft)}×",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(4.dp))
                SliderRow(
                    label = "Scale",
                    valueLabel = "${"%.1f".format(draft)}×",
                    value = ((draft - 0.8f) / 0.05f).roundToInt(),
                    min = 0,
                    max = 12,
                    step = 1,
                    onSettle = { vm.setFontScale(draft) },
                ) { step -> draft = 0.8f + step * 0.05f }
            }
        }

        item {
            SettingSection("Tile chips") {
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
                SettingSection("Tile ${index + 1}") {
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
            SettingSection("Tile order") {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Reorder a car's tiles from its More tile → Reorder tiles. The order stays in sync with that car on your phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SettingSection("Updates") {
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
                // "Check now" finishing used to just silently revert its own
                // label, with no confirmation an update was (or wasn't) found
                // beyond a transient snackbar -- fade in real result content
                // instead, the same way AiCard swaps in its summary.
                AnimatedVisibility(
                    visible = !ui.updateChecking && ui.updateRun != null,
                    enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
                ) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Update available: build #${ui.updateRun?.runNumber ?: ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        MorphButton(
                            label = "Open on phone",
                            icon = Icons.Filled.Sync,
                            active = false,
                            activeColor = MaterialTheme.colorScheme.tertiary,
                            pending = false,
                            onClick = { vm.openUpdateOnPhone() },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = !ui.updateChecking && ui.updateRun == null && ui.localSettings.updateLastCheckedAt > 0,
                    enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150)),
                ) {
                    Text(
                        "You're up to date",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        item {
            SettingSection("Sync") {
                MorphButton(
                    label = "Sync from phone",
                    icon = Icons.Filled.Sync,
                    active = false,
                    activeColor = MaterialTheme.colorScheme.primary,
                    pending = false,
                    onClick = { vm.resync() },
                )
                Spacer(Modifier.height(4.dp))
                MorphButton(
                    label = if (ui.driveSyncBusy) "Syncing…" else "Import from Drive",
                    icon = Icons.Filled.Bolt,
                    active = false,
                    activeColor = MaterialTheme.colorScheme.tertiary,
                    pending = ui.driveSyncBusy,
                    onClick = { vm.syncDrive() },
                )
            }
        }

        item {
            SectionCard(null) {
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
        // Surfaces "Check now" results (and any other message) while in Settings -
        // the home snackbar isn't on screen here.
        MessageSnackbar(ui.message) { vm.dismissMessage() }
        pinFlow?.let { mode -> PinManagementOverlay(vm, mode, onDone = { pinFlow = null }) }
    }
}

/** A settings section -- now a thin wrapper over the shared [SectionCard] so
 *  Settings uses the exact same card language (rounded corners, uppercase
 *  bold primary-tinted header) as Home's tiles, instead of a plain default
 *  Card with a different corner radius/container tone/title style. */
@Composable
private fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionCard(title, content = content)
}
