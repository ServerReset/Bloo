@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.LockTiming
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.launch
import com.bloo.bluelink.data.deleteCustomPalette
import com.bloo.bluelink.data.saveCustomPalette
import com.bloo.bluelink.data.setActiveCustomPaletteId
import com.bloo.bluelink.data.setAuroraBackground
import com.bloo.bluelink.data.setAuroraMotion
import com.bloo.bluelink.data.setBiometricLock
import com.bloo.bluelink.data.setColorPalette
import com.bloo.bluelink.data.setDynamicColor
import com.bloo.bluelink.data.setHapticsEnabled
import com.bloo.bluelink.data.setLockTiming
import com.bloo.bluelink.data.setPebbleOutline
import com.bloo.bluelink.data.setThemeMode

/** "Security" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun SecurityCardContent(
    state: UiState,
    vm: AppViewModel,
    canBio: Boolean,
    context: Context,
    appearance: SettingsStore.Appearance,
) {
            // Hoisted so the collapsed row's right side shows the lock state (see
            // SettingsCard's `status`).
            val locked = canBio && appearance.biometricLock
            val securityTint = if (locked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
            val securityStatus = when {
                !canBio -> "No biometrics enrolled"
                locked -> "Locked · ${appearance.lockTiming.label}"
                else -> "Not locked"
            }
            SettingsCard("Security", AppIcons.Lock, vm, status = securityStatus) {
                // Same icon-badge + status-line header Notifications/Backup & sync use --
                // this card used to open straight into a segmented row with no glanceable
                // read of whether the app lock is actually on.
                StatusHeaderRow(
                    icon = if (locked) AppIcons.Lock else AppIcons.LockOpen,
                    tint = securityTint,
                    title = "App lock",
                    status = securityStatus,
                )
                Spacer(Modifier.height(GapGroup))
                if (canBio) {
                    // One control, three states. This used to be a "require biometric"
                    // on/off toggle plus a separate lock-timing row, which let people save
                    // contradictory combinations (lock ON with "never re-lock", or a lock
                    // timing shown while the lock itself was OFF). A single group keeps the
                    // flag and its timing as one decision:
                    //   Off        -> no lock at all
                    //   Screen off -> lock on launch, re-lock when the screen turns off
                    //   Immediate  -> lock on launch, re-lock the moment it's backgrounded
                    val timingOn = appearance.biometricLock
                    SettingsSegmentedRow(
                        label = "App lock",
                        options = listOf(
                            SegmentOption("off", LockTiming.OFF.label, null),
                            SegmentOption("screen_off", LockTiming.SCREEN_OFF.label, null),
                            SegmentOption("immediate", LockTiming.IMMEDIATE.label, null),
                        ),
                        selectedKey = when {
                            !timingOn -> "off"
                            appearance.lockTiming == LockTiming.IMMEDIATE -> "immediate"
                            else -> "screen_off"
                        },
                        onSelect = { key ->
                            when (key) {
                                "off" -> {
                                    // Turning the lock OFF needs the same authentication
                                    // turning it on does, otherwise reaching Settings from an
                                    // already-unlocked app lets a single unauthenticated tap
                                    // permanently remove the lock on future cold launches --
                                    // turning momentary physical access into standing access
                                    // to unlocking the car. Failing to authenticate keeps the
                                    // lock on.
                                    val activity = context.findFragmentActivity()
                                    // The cold-start lock decision is (biometricLock && canBio)
                                    // OR a PIN being set (see AppViewModel's own doc) -- clearing
                                    // ONLY biometricLock here used to leave the app locking on
                                    // every launch anyway whenever a PIN was also set, reported
                                    // directly as "turn off locking, it still asks every time".
                                    // "App lock: Off" is this control's own promise that the app
                                    // stops asking at all, so it has to clear BOTH mechanisms --
                                    // the same biometric confirmation already required to get
                                    // here is treated as proof of identity everywhere else in
                                    // this screen (enabling/disabling the lock itself), so
                                    // reusing it to also drop the PIN isn't a weaker gate than
                                    // the PIN removal dialog's own "enter the current PIN" check,
                                    // just a different, already-trusted proof of the same thing.
                                    val pinAlsoSet = state.appPinSet
                                    if (activity == null) {
                                        // Fail closed -- keep the lock -- but say so,
                                        // rather than leaving the control looking stuck.
                                        vm.reportInfo("Couldn't verify it's you. The lock is still on.")
                                    } else {
                                        showBiometricPrompt(
                                            activity = activity,
                                            title = "Turn off app lock",
                                            subtitle = if (pinAlsoSet) {
                                                "Confirm to stop requiring it. This also removes your PIN."
                                            } else {
                                                "Confirm to stop requiring it"
                                            },
                                            onSuccess = {
                                                vm.setBiometricLock(false)
                                                if (pinAlsoSet) vm.removeAppPin()
                                            },
                                            onError = { },
                                        )
                                    }
                                }
                                else -> {
                                    val timing = if (key == "immediate") LockTiming.IMMEDIATE else LockTiming.SCREEN_OFF
                                    if (timingOn) {
                                        // Already locked: changing *when* it re-locks needs no
                                        // extra proof -- the user just proved who they are to
                                        // be in here, and tightening the timing is harmless.
                                        vm.setLockTiming(timing)
                                    } else {
                                        // Turning the lock ON proves who you are first, then
                                        // both arms it and sets when it re-locks.
                                        val activity = context.findFragmentActivity()
                                        if (activity == null) {
                                            vm.reportInfo("Couldn't verify it's you. The lock wasn't turned on.")
                                        } else {
                                            showBiometricPrompt(
                                                activity = activity,
                                                title = "Enable biometric lock",
                                                subtitle = "Confirm to require it on launch",
                                                onSuccess = {
                                                    vm.setBiometricLock(true)
                                                    vm.setLockTiming(timing)
                                                },
                                                onError = { },
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                } else {
                    BodyMediumText(
                        "No biometrics are enrolled on this device.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // --- App PIN ---
                // The device unlock PIN: the required mechanism on devices with
                // no biometrics, an optional backup on those that have them.
                // Separate from the biometric rows above because it is a second,
                // independent mechanism, not a mode of the first one.
                Spacer(Modifier.height(GapGroup))
                SectionDivider(alpha = 0.4f)
                Spacer(Modifier.height(GapHairline))
                var pinDialog by remember { mutableStateOf<String?>(null) }
                val pinSet = state.appPinSet
                StatusHeaderRow(
                    icon = if (pinSet) AppIcons.Lock else Icons.Filled.Pin,
                    tint = if (pinSet) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                    title = "App PIN",
                    status = if (pinSet) "On · 4-8 digits" else "Off",
                )
                Spacer(Modifier.height(GapHairline))
                BodySmallText(
                    if (canBio)
                        "A 4-8 digit PIN that works as a backup when biometrics aren't available."
                    else
                        "This device has no biometrics, so the app unlocks with this PIN.",
                )
                Spacer(Modifier.height(GapGroup))
                ExpressiveButtonRow(spacing = 8.dp) {
                    val pinSource = remember { MutableInteractionSource() }
                    SafeExpansiveButton(
                        interactionSource = pinSource,
                        enabled = true,
                    ) {
                        // The shared action button. "Remove" beside it keeps its errorContainer
                        // tone on purpose -- red is the one difference in this row that carries
                        // meaning, so the OTHER half is what had to move onto the standard look.
                        MorphActionButton(
                            label = if (pinSet) "Change PIN" else "Set up PIN",
                            icon = if (pinSet) Icons.Filled.LockReset else AppIcons.Lock,
                            onClick = { pinDialog = "set" },
                            interactionSource = pinSource,
                        )
                    }
                    if (pinSet) {
                        SafeMorphTextButton(
                            "Remove",
                            onClick = { pinDialog = "remove" },
                            emphasis = ButtonEmphasis.Destructive,
                        )
                    }
                }
                PinDialogs(
                    mode = pinDialog,
                    onDismiss = { pinDialog = null },
                    vm = vm,
                    state = state,
                    canBio = canBio,
                )
            }
}

/** "Sounds & vibration" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun SoundsVibrationCardContent(appearance: SettingsStore.Appearance, vm: AppViewModel) {
            SettingsCard(
                "Sounds & vibration",
                Icons.Filled.Vibration,
                vm,
                inlineSetting = { InlineToggle(appearance.hapticsEnabled) { vm.setHapticsEnabled(it) } },
            ) {}
}

/** "Theme" card content -- see the call site in [SettingsScreen] for context. */
@Composable
internal fun ThemeCardContent(appearance: SettingsStore.Appearance, advanced: Boolean, vm: AppViewModel) {
            // Hoisted so the collapsed row's right side shows the display mode (see
            // SettingsCard's `status`).
            val themeLabel = when (appearance.themeMode) {
                ThemeMode.SYSTEM -> "System"
                ThemeMode.LIGHT -> "Light"
                ThemeMode.DARK -> "Dark"
            }
            val themeStatus = if (appearance.auroraBackground) "$themeLabel · Aurora" else themeLabel
            SettingsCard("Theme", Icons.Filled.Palette, vm, status = themeStatus) {
                // Same icon-badge + status-line header as the rest of this pass.
                val themeTint = MaterialTheme.colorScheme.tertiary
                StatusHeaderRow(
                    icon = Icons.Filled.Palette,
                    tint = themeTint,
                    title = "Display mode",
                    status = themeStatus,
                )
                Spacer(Modifier.height(GapGroup))
                // Dark is always true black (OLED-friendly) now -- see blooColorScheme's
                // own doc. This used to also offer separate "AMOLED"/"+AMOLED" options
                // alongside plain Dark/System; there was no reason to make dark mode
                // dimmer than it needs to be by default, so that's just what Dark/System
                // (while dark) do now, with no extra choice to make.
                SettingsSegmentedRow(
                    label = "Appearance",
                    options = listOf(
                        SegmentOption(ThemeMode.SYSTEM.name, "System", null),
                        SegmentOption(ThemeMode.LIGHT.name, "Light", null),
                        SegmentOption(ThemeMode.DARK.name, "Dark", null),
                    ),
                    selectedKey = appearance.themeMode.name,
                    onSelect = { vm.setThemeMode(ThemeMode.valueOf(it)) },
                )
                // Advanced-only, same tier as the dynamic-color block below --
                // Aurora's motion sub-option is power-user territory, not
                // something a simple-mode user needs (the built-in solid-surface
                // background covers everyone else).
                AnimatedVisibility(visible = staggeredAdvancedVisible(advanced, 5), enter = expandEnterSized(), exit = expandExitSized()) {
                  Column {
                    Spacer(Modifier.height(GapRow))
                    ToggleRow("Aurora background", appearance.auroraBackground) { vm.setAuroraBackground(it) }
                    BodySmallText(
                        "Show a gradient aurora behind the content instead of a solid surface.",
                    )
                    // Same AnimatedVisibility-wraps-a-Column idiom as the dynamic-
                    // color section below, instead of a bare `if` -- this whole
                    // Motion block otherwise just materialized the instant the
                    // toggle above flipped on.
                    AnimatedVisibility(
                        visible = appearance.auroraBackground,
                        enter = expandEnterSized(),
                        exit = expandExitSized(),
                    ) {
                        Column {
                            Spacer(Modifier.height(GapRow))
                            Text("Motion", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(GapHairline))
                            MorphSegmented(
                                options = listOf(
                                    SegmentOption("static", "Static", null),
                                    SegmentOption("motion", "Motion", null),
                                ),
                                selectedKey = appearance.auroraMotion,
                                onSelect = { vm.setAuroraMotion(it) },
                            )
                        }
                    }
                  }
                }
                AnimatedVisibility(visible = staggeredAdvancedVisible(advanced, 6), enter = expandEnterSized(), exit = expandExitSized()) {
                  // AnimatedVisibility lays out a single child, not an implicit
                  // Column of its content lambda's composables -- without this
                  // wrapper the Spacer/Divider/Toggle/Slider siblings below would
                  // all stack on top of each other instead of flowing vertically.
                  Column {
                    Spacer(Modifier.height(GapGroup))
                    SectionDivider()
                    Spacer(Modifier.height(GapRow))
                    ToggleRow("Dynamic color (Material You)", appearance.dynamicColor) { vm.setDynamicColor(it) }
                    BodySmallText(
                        "Use your wallpaper palette (Android 12+) instead of a built-in one.",
                    )
                    AnimatedVisibility(
                        visible = !appearance.dynamicColor,
                        enter = expandEnterSized(),
                        exit = expandExitSized(),
                    ) {
                        Column {
                            Spacer(Modifier.height(GapRow))
                            LabelText("Built-in palettes")
                            Spacer(Modifier.height(GapHairline))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(GapRow)) {
                                ColorPalette.entries.forEach { palette ->
                                    PaletteSwatch(
                                        palette = palette,
                                        selected = appearance.activeCustomPaletteId == null && appearance.colorPalette == palette,
                                        onClick = { vm.setColorPalette(palette); vm.setActiveCustomPaletteId(null) },
                                    )
                                }
                            }
                            // Custom palettes: the create/edit dialog and per-palette
                            // selection existed (SettingsStore + AppViewModel) but had
                            // no entry point anywhere in the UI after the old Color
                            // card was merged into this Theme card -- restore it here.
                            Spacer(Modifier.height(GapRow))
                            LabelText("Custom palettes")
                            Spacer(Modifier.height(GapHairline))
                            var editingPalette by remember { mutableStateOf<CustomPaletteData?>(null) }
                            var showPaletteEditor by remember { mutableStateOf(false) }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(GapRow)) {
                                appearance.customPalettes.forEach { palette ->
                                    CustomPaletteSwatch(
                                        palette = palette,
                                        selected = appearance.activeCustomPaletteId == palette.id,
                                        onClick = { vm.setActiveCustomPaletteId(palette.id) },
                                        onEdit = { editingPalette = palette; showPaletteEditor = true },
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    MorphIconButton(
                                        onClick = { editingPalette = null; showPaletteEditor = true },
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = "New custom palette")
                                    }
                                    Spacer(Modifier.height(GapHairline))
                                    LabelSmallText("New")
                                }
                            }
                            if (showPaletteEditor) {
                                PaletteEditorDialog(
                                    editing = editingPalette,
                                    onSave = { vm.saveCustomPalette(it); vm.setActiveCustomPaletteId(it.id); showPaletteEditor = false },
                                    onDelete = { vm.deleteCustomPalette(it); showPaletteEditor = false },
                                    onDismiss = { showPaletteEditor = false },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(GapRow))
                    VibrancySlider(appearance, vm)
                    Spacer(Modifier.height(GapRow))
                    ToggleRow("Pebble outline", appearance.pebbleOutline) { vm.setPebbleOutline(it) }
                  }
                }
            }
}
