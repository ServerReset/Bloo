@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import com.bloo.uicommon.ReorderColumn
import android.content.ClipData
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.bloo.bluelink.data.aiEnabled
import com.bloo.bluelink.data.setAiEnabled
import com.bloo.bluelink.data.setFontChoice
import com.bloo.bluelink.data.setShowSearch
import com.bloo.bluelink.data.setUnitSystem
import com.bloo.bluelink.data.unitSystem
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.composed
import com.bloo.bluelink.data.links
import kotlin.math.max
import com.bloo.bluelink.data.setChargerApiKey
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.semantics.selected
import com.bloo.bluelink.data.LockTiming
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
import com.bloo.uicommon.rememberConfirmArm

/** The "Display" card: units, text scale, the search bubble, and the welcome cards. */
@Composable
internal fun DisplayCardContent(appearance: SettingsStore.Appearance, advanced: Boolean, vm: AppViewModel) {
    SettingsCard(
        "Display",
        Icons.Filled.Straighten,
        vm,
        status = if (appearance.unitSystem == "metric") "Metric" else "Imperial",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            SettingsGroup("Units") {
                SettingsSegmentedRow(
                    label = "Temperature, distance and speed",
                    options = listOf(SegmentOption("imperial", "Imperial", null), SegmentOption("metric", "Metric", null)),
                    selectedKey = appearance.unitSystem,
                    onSelect = { vm.setUnitSystem(it) },
                )
            }
            SettingsGroup("Car screen") {
                ToggleRow(
                    "Search on the car screen",
                    appearance.showSearch,
                    description = "The search bubble on the car and cover screens. Ask about the car, run a command, or jump to a setting.",
                ) { vm.setShowSearch(it) }
            }
            // A knob you set once, so advanced only.
            PopVisible(visible = advanced) {
                SettingsGroup("Size") { UiScaleSlider(appearance, vm) }
            }
            SettingsGroup("Welcome cards") {
                BodySmallText("The cards you swipe through when you first open Bloo. Bring them back whenever you like.")
                SafeMorphTextButton("Show welcome cards", onClick = { vm.showWelcomeCards() }, icon = Icons.Filled.Style)
            }
        }
    }
}

/** The "Font" card: which typeface the app is set in. */
@Composable
internal fun FontCardContent(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    val labels = mapOf(
        FontChoice.SYSTEM to "System default",
        FontChoice.ATKINSON to "Atkinson Hyperlegible",
        FontChoice.GOOGLE_SANS to "Google Sans",
    )
    SettingsCard(
        "Font",
        Icons.Filled.TextFields,
        vm,
        status = when (appearance.fontChoice) {
            FontChoice.ATKINSON -> "Atkinson"
            FontChoice.GOOGLE_SANS -> "Google Sans"
            else -> "System"
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
            FontChoice.entries.forEach { choice ->
                ChoiceRow(labels.getValue(choice), appearance.fontChoice == choice) { vm.setFontChoice(choice) }
            }
        }
    }
}

/** The "Sounds & vibration" card: one switch, so it sits on the title row. */
@Composable
internal fun SoundsVibrationCardContent(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    SettingsCard(
        "Sounds & vibration",
        Icons.Filled.Vibration,
        vm,
        inlineSetting = { InlineToggle(appearance.hapticsEnabled) { vm.setHapticsEnabled(it) } },
    ) {}
}

/** The "Theme" card: light or dark, the background, and colour. The finer controls are advanced. */
@Composable
internal fun ThemeCardContent(appearance: SettingsStore.Appearance, advanced: Boolean, vm: AppViewModel) {
    val mode = when (appearance.themeMode) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }
    SettingsCard("Theme", Icons.Filled.Palette, vm, status = if (appearance.auroraBackground) "$mode · Aurora" else mode) {
        Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            SettingsGroup("Mode") {
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
            }
            PopVisible(visible = advanced) {
                Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
                    SettingsGroup("Background") {
                        ToggleRow(
                            "Aurora background",
                            appearance.auroraBackground,
                            description = "A gradient aurora behind the content instead of a solid surface.",
                        ) { vm.setAuroraBackground(it) }
                        PopVisible(visible = appearance.auroraBackground) {
                            SettingsSegmentedRow(
                                label = "Motion",
                                options = listOf(SegmentOption("static", "Static", null), SegmentOption("motion", "Motion", null)),
                                selectedKey = appearance.auroraMotion,
                                onSelect = { vm.setAuroraMotion(it) },
                            )
                        }
                    }
                    SettingsGroup("Colour") {
                        ToggleRow(
                            "Dynamic color (Material You)",
                            appearance.dynamicColor,
                            description = "Use your wallpaper palette (Android 12+) instead of a built-in one.",
                        ) { vm.setDynamicColor(it) }
                        PopVisible(visible = !appearance.dynamicColor) { PaletteChooser(appearance, vm) }
                        VibrancySlider(appearance, vm)
                    }
                    SettingsGroup("Style") {
                        ToggleRow("Pebble outline", appearance.pebbleOutline) { vm.setPebbleOutline(it) }
                    }
                }
            }
        }
    }
}

/** Built-in and custom colour palettes, and the editor for the custom ones. */
@Composable
private fun PaletteChooser(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    var editing by remember { mutableStateOf<CustomPaletteData?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
        LabelText("Built-in palettes")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(GapRow)) {
            ColorPalette.entries.forEach { palette ->
                PaletteSwatch(
                    palette = palette,
                    selected = appearance.activeCustomPaletteId == null && appearance.colorPalette == palette,
                    onClick = { vm.setColorPalette(palette); vm.setActiveCustomPaletteId(null) },
                )
            }
        }
        LabelText("Custom palettes")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(GapRow)) {
            appearance.customPalettes.forEach { palette ->
                CustomPaletteSwatch(
                    palette = palette,
                    selected = appearance.activeCustomPaletteId == palette.id,
                    onClick = { vm.setActiveCustomPaletteId(palette.id) },
                    onEdit = { editing = palette; showEditor = true },
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MorphIconButton(onClick = { editing = null; showEditor = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "New custom palette")
                }
                LabelSmallText("New")
            }
        }
    }
    if (showEditor) {
        PaletteEditorDialog(
            editing = editing,
            onSave = { vm.saveCustomPalette(it); vm.setActiveCustomPaletteId(it.id); showEditor = false },
            onDelete = { vm.deleteCustomPalette(it); showEditor = false },
            onDismiss = { showEditor = false },
        )
    }
}
