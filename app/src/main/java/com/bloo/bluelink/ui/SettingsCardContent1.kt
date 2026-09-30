@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.brand
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import com.bloo.uicommon.ReorderColumn
import android.content.ClipData
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.bloo.bluelink.data.aiEnabled
import com.bloo.bluelink.data.platform
import com.bloo.bluelink.data.setAiEnabled
import com.bloo.bluelink.data.setFontChoice
import com.bloo.bluelink.data.setShowSearch
import com.bloo.bluelink.data.setUnitSystem
import com.bloo.bluelink.data.unitSystem

/** "Accounts" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun AccountsCardContent(state: UiState, vm: AppViewModel) {
            // Same summary the in-card header shows, now ALSO on the collapsed row's right
            // (see SettingsCard's `status`) so the card tells you how many accounts are
            // connected without having to be opened.
            val accountsStatus = if (state.accounts.isEmpty()) "No accounts"
                else "${state.accounts.size} account${if (state.accounts.size == 1) "" else "s"}"
            SettingsCard("Accounts", Icons.Filled.Person, vm, status = accountsStatus) {
                // Same icon-badge + status-line header as every other card that's had
                // this pass applied -- was straight into "Not signed in" or a wall of
                // per-account blocks with nothing summarizing how many were connected.
                val acctTint = if (state.accounts.isNotEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                StatusHeaderRow(
                    icon = Icons.Filled.Person,
                    tint = acctTint,
                    title = "Signed in",
                    status = accountsStatus,
                )
                Spacer(Modifier.height(GapGroup))
                if (state.accounts.isEmpty()) {
                    BodyMediumText(
                        "Not signed in",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.accounts.forEachIndexed { i, creds ->
                    if (i > 0) Spacer(Modifier.height(GapSection))
                    var pin by remember(creds.brand, creds.pin) { mutableStateOf(creds.pin) }
                    // Was a single un-confirmed tap that signed the account out
                    // immediately -- same "tap again to confirm" + 4s
                    // auto-reset pattern used for the climate preset/palette
                    // deletes above, so every destructive action in the app
                    // now asks for the same second tap instead of some firing
                    // instantly and others not.
                    var confirmSignOut by remember(creds.brand) { mutableStateOf(false) }
                    LaunchedEffect(confirmSignOut) {
                        if (confirmSignOut) {
                            delay(4000)
                            confirmSignOut = false
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                        TitleSmallText(creds.brand.label)
                        StatusRow("Email", creds.email)
                        SecretRow("Password", creds.password)
                        // Kia US has no service PIN; commands are session-keyed.
                        if (creds.brand.requiresPin) {
                            OutlinedTextField(
                                value = pin,
                                onValueChange = { pin = it },
                                label = { Text("Service PIN") },
                                singleLine = true,
                                shape = FieldShape,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        ExpressiveButtonRow(spacing = GapRow) {
                            if (creds.brand.requiresPin) {
                                SafeMorphTextButton(
                                    "Update PIN",
                                    onClick = { vm.updatePin(creds.brand, pin) },
                                    enabled = pin.isNotBlank() && pin != creds.pin,
                                )
                            }
                            SafeMorphTextButton(
                                if (confirmSignOut) "Tap again to confirm" else "Sign out",
                                onClick = {
                                    if (confirmSignOut) { vm.logout(creds.brand); confirmSignOut = false }
                                    else confirmSignOut = true
                                },
                                emphasis = ButtonEmphasis.Destructive,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(GapGroup))
                val addAccountSource = remember { MutableInteractionSource() }
                SafeExpansiveButton(
                    interactionSource = addAccountSource,
                    enabled = true,
                ) {
                    // Content-width, not fillMaxWidth() -- a single CTA reads as oversized
                    // stretched edge-to-edge, the same fix already made for Backup & sync's and
                    // Updates' own primary buttons this session; this one was missed then.
                    MorphTextButton(
                        "Add another account",
                        onClick = { vm.beginAddAccount() },
                        interactionSource = addAccountSource,
                    )
                }
                BodySmallText(
                    "Wrong-PIN attempts lock the Service PIN for a few minutes. Fix it above if commands fail.",
                    modifier = Modifier.padding(top = GapRow),
                )
            }
}

/** "AI" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun AiCardContent(state: UiState, advanced: Boolean, vm: AppViewModel) {
                SettingsCard(
                    "AI",
                    AppIcons.AutoAwesome,
                    vm,
                    // Collapsed right-side read; ignored in simple mode where the toggle
                    // itself is already on the row (inlineSetting below).
                    status = if (state.aiEnabled) "On" else "Off",
                    // The card is inline whenever the CURRENT mode leaves it holding one
                    // setting. In simple mode the auto-summarize toggle below is hidden, so
                    // everything this card can do is the one switch -- and a chevron that opens
                    // a card to reveal a single switch is the disclosure this treatment exists
                    // to remove. Advanced mode has two, so it stays a real card there.
                    //
                    // Gated on what is VISIBLE, not on what the card contains. That distinction
                    // is the bug: counting everything the card could ever show says "two
                    // settings" in both modes, and the card never collapses in either.
                    inlineSetting = if (advanced) {
                        null
                    } else {
                        { InlineToggle(state.aiEnabled) { vm.setAiEnabled(it) } }
                    },
                ) {
                    // Same icon-badge + status-line header as the rest of this pass.
                    val aiTint = if (state.aiEnabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    StatusHeaderRow(
                        icon = AppIcons.AutoAwesome,
                        tint = aiTint,
                        title = "On-device AI",
                        status = if (state.aiEnabled) "On" else "Off",
                    )
                    Spacer(Modifier.height(GapGroup))
                    // Names the ENGINE. The card is titled "AI" and the header right above
                    // says "On-device AI" with its own on/off status, so a third "On-device
                    // AI" here said the same thing a third time and told you nothing new.
                    ToggleRow("Gemini Nano", state.aiEnabled) { vm.setAiEnabled(it) }
                    BodySmallText(
                        "Adds an AI summary pebble and lets you ask the search box plain questions. Everything runs privately on your device.",
                    )
                }
}

/** "App shortcuts" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun AppShortcutsCardContent(state: UiState, vm: AppViewModel) {
                SettingsCard("App shortcuts", AppIcons.Bolt, vm) {
                    // No inner MorphExpandButton any more -- this used to have its
                    // own second chevron gating the per-vehicle toggles below,
                    // stacked directly under the card's own PebbleShell chevron
                    // (which didn't exist yet when this was written; SettingsCard
                    // was a static, always-open Card back then, and the inner
                    // toggle was the ONLY way to fold this away). Now that the
                    // card itself opens and closes, a second tap just to see the
                    // toggles it opened FOR was two controls doing one job.
                    BodySmallText(
                        "Quick-access shortcuts from the launcher icon",
                    )
                    Spacer(Modifier.height(GapRow))
                    state.vehicles.forEach { v ->
                        Spacer(Modifier.height(GapHairline))
                        TitleSmallText(v.name)
                        com.bloo.bluelink.Shortcuts.ACTIONS.forEach { cmd ->
                            ToggleRow(
                                com.bloo.bluelink.Shortcuts.actionLabel(cmd),
                                state.isShortcutEnabled(v.vin, cmd),
                            ) { vm.setShortcutEnabled(v.vin, cmd, it) }
                        }
                    }
                }
}

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
                            spacing = 8.dp,
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

/** "Debug" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun DebugCardContent(vm: AppViewModel, clipboardScope: CoroutineScope, clipboard: Clipboard) {
            SettingsCard("Debug", Icons.Filled.BugReport, vm) {
                DebugSettingsPanel(
                    onCopyToClipboard = { text ->
                        clipboardScope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("bloo debug", text)))
                        }
                    },
                )
            }
}

/** "Display" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun DisplayCardContent(appearance: SettingsStore.Appearance, advanced: Boolean, vm: AppViewModel) {
            SettingsCard(
                "Display",
                Icons.Filled.Straighten,
                vm,
                status = if (appearance.unitSystem == "metric") "Metric" else "Imperial",
            ) {
                // Advanced-only: a power-user knob, unlike the Units picker
                // below it which every user needs regardless of mode. PopVisible,
                // not a bare `if` -- was snapping in/out with the mode switch.
                PopVisible(visible = advanced) {
                  Column {
                    UiScaleSlider(appearance, vm)
                    Spacer(Modifier.height(GapGroup))
                  }
                }
                // SIMPLE, not advanced: this changes what is on the car screen
                // every time you open the app, which is the test for whether a
                // switch belongs in the small set. The text-scale slider above
                // it is advanced by the same test -- it is a knob you set once.
                ToggleRow(
                    "Search on the car screen",
                    appearance.showSearch,
                    description = "The search bubble on the car and cover screens. Ask about the car, run a command, or jump to a setting.",
                ) { vm.setShowSearch(it) }
                // Unit system: controls temperature, distance, and speed display.
                SettingsSegmentedRow(
                    label = "Units",
                    options = listOf(
                        SegmentOption("imperial", "Imperial", null),
                        SegmentOption("metric", "Metric", null),
                    ),
                    selectedKey = appearance.unitSystem,
                    onSelect = { vm.setUnitSystem(it) },
                )
            }
}

/** "Font" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun FontCardContent(appearance: SettingsStore.Appearance, vm: AppViewModel) {
            SettingsCard(
                "Font",
                Icons.Filled.TextFields,
                vm,
                // Only cards WITHOUT a StatusHeaderRow of their own get a title-row status --
                // on the ones that have one it would be the same fact twice, ten dp apart,
                // which is exactly the duplication the cover tiles were just cured of.
                status = when (appearance.fontChoice) {
                    FontChoice.ATKINSON -> "Atkinson"
                    FontChoice.GOOGLE_SANS -> "Google Sans"
                    else -> "System"
                },
            ) {
                val labels = mapOf(
                    FontChoice.SYSTEM to "System default",
                    FontChoice.ATKINSON to "Atkinson Hyperlegible",
                    FontChoice.GOOGLE_SANS to "Google Sans",
                )
                Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                    FontChoice.entries.forEach { choice ->
                        ChoiceRow(labels.getValue(choice), appearance.fontChoice == choice) { vm.setFontChoice(choice) }
                    }
                }
            }
}
