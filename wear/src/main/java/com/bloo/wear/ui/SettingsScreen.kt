package com.bloo.wear.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.bloo.wear.WearUi
import com.bloo.wear.WearViewModel

/**
 * The Settings screen: a single scrollable [RotaryScalingColumn] made up of one
 * [SettingSection] card per topic (Accounts, Appearance, PIN lock, AI
 * Summaries, Aurora, text size, Tile chips, per-slot Tile car pinning, Tile
 * order, Sync, Phone status, Refresh, Sign out). Most of the state driving
 * these sections lives upstream in [WearUi] (itself derived from
 * [WearViewModel]'s combination of phone-synced settings and local watch-only
 * settings); this composable is mostly read-only rendering plus wiring each
 * control's `onClick`/`onSelect` back to the corresponding `vm.setXxx` call.
 * Two pieces of genuinely local state exist here: [pinFlow] (which PIN
 * management flow, if any, is currently overlaid on top of this screen) and
 * `confirmSignOut` (a local two-tap-to-confirm guard for the destructive
 * sign-out action, see below).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(vm: WearViewModel, ui: WearUi, onAddAccount: () -> Unit) {
    var pinFlow by remember { mutableStateOf<PinFlowMode?>(null) }
    // Mirrors the phone's Simple/Advanced settings mode (synced one-way via
    // WearSettingsPayload.settingsMode) -- the watch had no such concept at
    // all before, showing every power-user row unconditionally even when the
    // phone hides the same rows in simple mode.
    //
    // Standalone (no phone reachable) the watch has no synced `settings` to read a
    // mode from, and there's no phone to defer these controls to — so surface them
    // here against the watch's own local values rather than hiding them. Paired, the
    // phone's mode still governs.
    val standalone = !ui.phoneConnected
    val advanced = ui.settings?.settingsMode == "advanced" || standalone
    var confirmSignOut by remember { mutableStateOf(false) }
    // Auto-reset the destructive confirm so a stale "tap again" can't sign you out later.
    LaunchedEffect(confirmSignOut) {
        if (confirmSignOut) {
            delay(4000)
            confirmSignOut = false
        }
    }

    // Hoisted so ScreenScaffold's curved scroll indicator tracks the very same
    // list RotaryScalingColumn scrolls (they must share one state).
    val listState = rememberScalingLazyListState()
    Box(Modifier.fillMaxSize()) {
    // timeText = {} suppresses the inherited AppScaffold clock on this content
    // screen: the curved clock overlapped section titles ("PIN LOCK", "AURORA…")
    // at the top of the list. The clock stays on the main garage where the layout
    // is designed around it.
    ScreenScaffold(scrollState = listState, timeText = {}) {
    RotaryScalingColumn(state = listState) {
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

        item { SettingGroupLabel("APPEARANCE") }
        item {
            SettingSection("Appearance") {
                Text(
                    "Theme synced from phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text("Units", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                val metric = ui.localSettings.unitSystem == "metric"
                // Was a hand-rolled clickable Row with no background/border/
                // icon -- the only setting on this screen with zero visual
                // affordance that it's interactive, next to segmented
                // controls and MorphButtons everywhere else. Same control
                // this screen already uses for Lock-after/Aurora colour mode.
                MorphSegmented(
                    options = listOf(
                        WearSegmentOption("imperial", "Imperial"),
                        WearSegmentOption("metric", "Metric"),
                    ),
                    selectedKey = if (metric) "metric" else "imperial",
                    onSelect = { key -> vm.setUnitSystem(key) },
                )
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
                    label = "Use my location",
                    icon = Icons.Filled.MyLocation,
                    active = false,
                    activeColor = MaterialTheme.colorScheme.primary,
                    pending = false,
                    onClick = { vm.setWeatherFromDeviceLocation() },
                )
            }
        }

        item { SettingGroupLabel("SECURITY") }
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
                        // Arming the lock needs no proof; turning it off is
                        // functionally the same as removing the PIN (the watch
                        // will never lock again), so it goes through the same
                        // "confirm your current PIN first" flow REMOVE already
                        // uses -- otherwise anyone holding the watch during the
                        // exact window the lock protects could disable it with
                        // no PIN at all.
                        onClick = {
                            if (ls.pinLockEnabled) pinFlow = PinFlowMode.DISABLE
                            else vm.setPinLockEnabled(true)
                        },
                        toggled = ls.pinLockEnabled,
                    )
                    if (ls.pinLockEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text("Lock after", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        // Five options split across TWO stacked segmented rows ("dual
                        // stack"): five segments in one row on a narrow round face made
                        // each pill ~20dp and cramped even at 11sp. Splitting 3 + 2
                        // gives every option room. Both rows share the same selectedKey
                        // and onSelect, so it stays ONE logical choice; indicatorVisible
                        // lights the highlight only on the row that actually holds the
                        // current value (the other row would otherwise falsely light its
                        // first segment via the shared control's index-0 fallback).
                        val lockRow1 = listOf("off", "immediate", "1min")
                        val timing = ls.pinLockTiming
                        MorphSegmented(
                            options = listOf(
                                WearSegmentOption("off", "Off"),
                                WearSegmentOption("immediate", "Now"),
                                WearSegmentOption("1min", "1m"),
                            ),
                            selectedKey = timing,
                            onSelect = { key -> vm.setPinLockTiming(key) },
                            indicatorVisible = timing in lockRow1,
                        )
                        Spacer(Modifier.height(4.dp))
                        MorphSegmented(
                            options = listOf(
                                WearSegmentOption("5min", "5m"),
                                WearSegmentOption("10min", "10m"),
                            ),
                            selectedKey = timing,
                            onSelect = { key -> vm.setPinLockTiming(key) },
                            indicatorVisible = timing !in lockRow1,
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
                if (standalone) {
                    // AI runs on the phone's on-device model — the watch hardware
                    // can't run it, so there's nothing to toggle standalone. Explain
                    // rather than show a dead switch.
                    Text(
                        "AI summaries run on your phone. Connect your phone to turn them on and generate one.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "On-device summaries of a car's status, generated on your phone.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    MorphButton(
                        label = if (enabled) "On" else "Off",
                        icon = Icons.Filled.AutoAwesome,
                        active = enabled,
                        activeColor = MaterialTheme.colorScheme.primary,
                        pending = false,
                        onClick = { vm.setAiEnabled(!enabled) },
                        toggled = enabled,
                    )
                }
            }
        }

        if (advanced) item {
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
                    toggled = enabled,
                )
                // The watch could previously only mirror whatever colour mode
                // the phone had chosen -- this is the only "background/
                // appearance" setting on the watch with real watch-side
                // control, syncing back the same way the AI/Aurora on-off
                // toggles already do. Watch has no colour-picker keyboard for
                // "Custom" hex entry, so that mode isn't offered here; it's
                // still honoured (using whatever hex the phone published) if
                // the phone itself set it.
                AnimatedVisibility(visible = enabled, enter = fadeIn(tween(150)), exit = fadeOut(tween(120))) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        MorphSegmented(
                            options = listOf(
                                WearSegmentOption("complementary", "Auto"),
                                WearSegmentOption("material", "Material"),
                            ),
                            selectedKey = (ui.settings?.auroraColorMode ?: "complementary")
                                .let { if (it == "custom") "complementary" else it },
                            onSelect = { vm.setAuroraColorMode(it) },
                        )
                    }
                }
            }
        }

        if (advanced) item {
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

        // TILES group — only meaningful in advanced mode, where the tile-config
        // sections live; gate the label on the same condition so it never heads an
        // empty group in simple mode.
        if (advanced) item { SettingGroupLabel("TILES") }
        if (advanced) item {
            SettingSection("Tile chips") {
                Text(
                    "Pick up to two actions for the glanceable Tile.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                val actions = ui.localSettings.tileActions
                listOf(
                    Triple("lock", "Lock", Icons.Filled.Lock),
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
                            // Toggling a chip on appends it then keeps only the
                            // last two distinct entries -- since the Tile only
                            // has room for two chips, adding a third silently
                            // bumps out the oldest-picked one rather than the
                            // toggle being rejected outright or growing an
                            // unbounded list that setTileActions would have to
                            // truncate anyway.
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
                            // Car names ("Lanas Whip", …) are user-set and can be long;
                            // wrap to 2 lines rather than truncating.
                            maxLines = 2,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    carOption("Selected car", null)
                    ui.cars.forEach { car -> carOption(car.name, car.vin) }
                }
            }
        }

        // "Tile order" is a phone-mirror: paired, the order syncs FROM the phone and
        // you reorder there, so this note is just clutter on the small screen and is
        // hidden (the user asked companion-mode to drop settings that only mirror the
        // phone). Standalone there's no phone to sync from, so surface a short note
        // that tiles follow the on-watch default order.
        if (standalone) item {
            SettingSection("Tile order") {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tiles follow the default order on the watch. Connect your phone to customize a car's tile order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // The Updates settings section (manual "Check now", auto-check toggle)
        // used to live here -- removed. Updates check automatically and
        // present themselves via the More tile's banner (see HomeScreen's
        // MoreCard), no settings/manual controls needed any more.

        item { SettingGroupLabel("SYNC & ACCOUNT") }
        item {
            SettingSection("Sync") {
                if (standalone) {
                    // Both sync actions need the phone: "Sync from phone" pulls the
                    // phone's state, and Drive backup is stored through the phone's
                    // Google account (the watch has no Drive file of its own). Explain
                    // rather than show buttons that can only say "bring your phone near".
                    Text(
                        "Sync needs your phone. Your watch keeps working on its own. Connect your phone to sync settings and Drive backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    // Read-only "your devices" summary, published by the phone (it owns
                    // the Drive registry). Watch can't set a primary — that's a phone
                    // action — so this is display-only.
                    ui.settings?.syncDeviceSummary?.takeIf { it.isNotBlank() }?.let { summary ->
                        Text(
                            "Devices".uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = EyebrowLetterSpacing,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // Cap so a long multi-device string can't balloon, but 3
                            // lines (was 2): real device names ("Galaxy Watch6 Classic")
                            // were ellipsizing the primary's name away at 2. The card is
                            // a ScalingLazyColumn item so it just grows; nothing clips.
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    MorphButton(
                        label = if (ui.resyncBusy) "Syncing…" else "Sync",
                        icon = Icons.Filled.Sync,
                        active = false,
                        activeColor = MaterialTheme.colorScheme.primary,
                        pending = ui.resyncBusy,
                        onClick = { vm.resync() },
                    )
                    Spacer(Modifier.height(4.dp))
                    MorphButton(
                        label = if (ui.driveSyncBusy) "Syncing…" else "Drive sync",
                        icon = Icons.Filled.Bolt,
                        active = false,
                        activeColor = MaterialTheme.colorScheme.tertiary,
                        pending = ui.driveSyncBusy,
                        onClick = { vm.syncDrive() },
                    )
                }
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
            // Every other network-triggered button on this screen (Sync from
            // phone, Sync via Drive, Check now) shows a busy state; this
            // one never did despite kicking off a refresh per car.
            val refreshingAny = ui.cars.any { "${it.vin}:refresh" in ui.pending }
            MorphButton(
                label = "Refresh all",
                icon = Icons.Filled.Refresh,
                active = false,
                activeColor = MaterialTheme.colorScheme.primary,
                pending = refreshingAny,
                onClick = { vm.refreshAll() },
            )
        }

        item {
            MorphButton(
                label = if (confirmSignOut) "Tap to confirm" else "Sign out",
                icon = Icons.AutoMirrored.Filled.Logout,
                active = confirmSignOut,
                activeColor = MaterialTheme.colorScheme.error,
                pending = false,
                onClick = { if (confirmSignOut) vm.signOutAll() else confirmSignOut = true },
            )
        }

        item {
            Text(
                "Bloo for Wear OS · " + com.bloo.bluelink.data.buildLabel(
                    vm.currentBuildNumber,
                    com.bloo.wear.BuildConfig.BUILD_BRANCH,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
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

/** A small, centered uppercase group divider that chunks the long Settings list
 *  into scannable sections (Appearance / Security / Tiles / Sync & account)
 *  instead of one undifferentiated scroll of ~14 cards. Primary-tinted + tracked
 *  so it reads as a section break, not another card. */
@Composable
private fun SettingGroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = EyebrowLetterSpacing,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp).semantics { heading() },
    )
}
