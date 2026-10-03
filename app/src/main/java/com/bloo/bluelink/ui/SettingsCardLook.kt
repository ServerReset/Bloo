@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Style
import androidx.compose.ui.semantics.onClick
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bloo.bluelink.data.SettingsStore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.bloo.bluelink.data.setFontChoice
import com.bloo.bluelink.data.unitSystem
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.semantics.selected
import com.bloo.bluelink.data.deleteCustomPalette
import com.bloo.bluelink.data.saveCustomPalette
import com.bloo.bluelink.data.setActiveCustomPaletteId
import com.bloo.bluelink.data.setAuroraBackground
import com.bloo.bluelink.data.setAuroraMotion
import com.bloo.bluelink.data.setColorPalette
import com.bloo.bluelink.data.setDynamicColor
import com.bloo.bluelink.data.setHapticsEnabled
import com.bloo.bluelink.data.setPebbleOutline
import com.bloo.bluelink.data.setThemeMode

/** The "Visuals" card: theme and colour, font, units, text scale, the search bubble, and the welcome cards. */
@Composable
internal fun VisualsCardContent(appearance: SettingsStore.Appearance, advanced: Boolean, vm: AppViewModel) {
    SettingsCard(
        "Visuals",
        Icons.Filled.Palette,
        vm,
        status = displayStatus(appearance),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(GapGroup)) {
            ThemeGroups(appearance, advanced, vm)
            // SIMPLE, not advanced: Atkinson Hyperlegible is a typeface for low vision, and an accessibility
            // choice behind "advanced" is the one the people who need it are least likely to find.
            FontGroup(appearance, vm)
            SettingsGroup("Units") {
                UnitSystemRow(appearance, vm)
                PopVisible(visible = advanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
                        TempUnitRow(appearance, vm)
                        DistanceUnitRow(appearance, vm)
                        BodySmallText("Auto follows Units, so you can mix them: Celsius with miles, say.")
                    }
                }
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

/** "Dark · Aurora · Metric": the Display card's one-line summary. */
private fun displayStatus(appearance: SettingsStore.Appearance): String = listOfNotNull(
    when (appearance.themeMode) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    },
    if (appearance.auroraBackground) "Aurora" else null,
    if (appearance.unitSystem == "metric") "Metric" else "Imperial",
).joinToString(" · ")

/** The "Font" group of the Visuals card: which typeface the app is set in. */
@Composable
private fun FontGroup(appearance: SettingsStore.Appearance, vm: AppViewModel) {
    val labels = mapOf(
        FontChoice.SYSTEM to "System default",
        FontChoice.ATKINSON to "Atkinson Hyperlegible",
        FontChoice.GOOGLE_SANS to "Google Sans",
    )
    SettingsGroup("Font") {
        FontChoice.entries.forEach { choice ->
            ChoiceRow(labels.getValue(choice), appearance.fontChoice == choice) { vm.setFontChoice(choice) }
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

/** The theme groups of the Display card: light or dark, the background, colour and style. The finer controls are advanced. */
@Composable
private fun ThemeGroups(appearance: SettingsStore.Appearance, advanced: Boolean, vm: AppViewModel) {
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
                        GlassClaritySlider(appearance, vm)
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GapRow), verticalArrangement = Arrangement.spacedBy(GapRow)) {
            ColorPalette.entries.forEach { palette ->
                PaletteSwatch(
                    palette = palette,
                    selected = appearance.activeCustomPaletteId == null && appearance.colorPalette == palette,
                    onClick = { vm.setColorPalette(palette); vm.setActiveCustomPaletteId(null) },
                )
            }
        }
        LabelText("Custom palettes")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(GapRow), verticalArrangement = Arrangement.spacedBy(GapRow)) {
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
