package com.bloo.bluelink.ui

import com.bloo.uicommon.segmentsOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.bloo.bluelink.data.SettingsCatalog
import com.bloo.bluelink.data.SettingsStore
import com.bloo.uicommon.GapRow
import com.bloo.uicommon.content

/**
 * The searchable settings that are not plain on/off switches -- the ranges (sliders and minute
 * fields) and the pickers (units, font, mode). Like [ToggleSpec], the title and keywords come from
 * [SettingsCatalog] by [key]; this file adds only the control to draw.
 */
internal class SettingSpec(
    val key: String,
    val kind: SettingKind,
    val phrases: String = "",
    val visible: (UiState) -> Boolean = { true },
    val content: @Composable (SettingsStore.Appearance, SettingsStore.NotificationPrefs, UiState, AppViewModel) -> Unit,
) {
    val title: String = SettingsCatalog.titleOf(key) ?: key
    val keywords: String = SettingsCatalog.keywordsOf(key)
}

internal enum class SettingKind { Range, Choice }

internal val RangeSettings: List<SettingSpec> = listOf(
    SettingSpec(
        key = "ui_scale", kind = SettingKind.Range,
        phrases = "make the text bigger make everything smaller text is too small can't read",
    ) { a, _, _, vm -> UiScaleSlider(a, vm, label = "Scale") },
    SettingSpec(
        key = "glass_clarity", kind = SettingKind.Range,
        phrases = "make it see through make the glass clearer more transparent less blurry stronger glass edge",
    ) { a, _, _, vm -> GlassClaritySlider(a, vm) },
    SettingSpec(
        key = "vibrancy", kind = SettingKind.Range,
        phrases = "make the colors brighter less colorful more vivid washed out",
    ) { a, _, _, vm -> VibrancySlider(a, vm) },
    SettingSpec(
        key = "notify_unlocked_min", kind = SettingKind.Range,
        phrases = "how long before it warns me the car is unlocked",
    ) { _, n, _, vm -> MinutesField(n.unlockedMinutes, "Left unlocked: minutes before alerting", vm::setUnlockedMinutes) },
    SettingSpec(
        key = "notify_door_min", kind = SettingKind.Range,
        phrases = "how long before it warns me a door is open",
    ) { _, n, _, vm -> MinutesField(n.doorOpenMinutes, "Door left open: minutes before alerting", vm::setDoorOpenMinutes) },
    SettingSpec(
        key = "notify_running_min", kind = SettingKind.Range,
        phrases = "how long before it warns me the car is still running",
    ) { _, n, _, vm -> MinutesField(n.runningMinutes, "Car left running: minutes before alerting", vm::setRunningMinutes) },
)

/** Every app-wide picker: one choice out of a few. */
internal val ChoiceSettings: List<SettingSpec> = listOf(
    SettingSpec(
        key = "theme_mode", kind = SettingKind.Choice,
        phrases = "turn on dark mode switch to light mode black background follow the phone",
    ) { a, _, _, vm -> ThemeModeSegmentedRow(a) { vm.setThemeMode(it) } },
    SettingSpec(
        key = "unit_system", kind = SettingKind.Choice,
        phrases = "use kilometres use miles celsius or fahrenheit",
    ) { a, _, _, vm -> UnitSystemRow(a, vm) },
    SettingSpec(
        key = "temp_unit", kind = SettingKind.Choice,
        phrases = "show temperatures in celsius show fahrenheit",
    ) { a, _, _, vm -> TempUnitRow(a, vm) },
    SettingSpec(
        key = "distance_unit", kind = SettingKind.Choice,
        phrases = "show distances in miles show kilometres",
    ) { a, _, _, vm -> DistanceUnitRow(a, vm) },
    SettingSpec(
        key = "watch_lock_timing", kind = SettingKind.Choice,
        phrases = "make my watch ask for the pin when i send a command",
    ) { _, _, s, vm ->
        Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
            com.bloo.bluelink.data.WatchLockTiming.entries.forEach { t ->
                ChoiceRow(t.label, s.watchLockTiming == t) { vm.setWatchLockTiming(t) }
            }
        }
    },
    SettingSpec(
        key = "settings_mode", kind = SettingKind.Choice,
        phrases = "show me more settings hide the advanced options keep it simple",
    ) { _, _, s, vm ->
        SettingsSegmentedRow(
            label = "Settings",
            options = segmentsOf("simple" to "Simple", "advanced" to "Advanced"),
            selectedKey = s.settingsMode,
            onSelect = { vm.setSettingsMode(it) },
        )
    },
    SettingSpec(
        key = "color_palette", kind = SettingKind.Choice,
        phrases = "change the accent colour make the app green use a different color",
    ) { a, _, _, vm ->
        Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
            ColorPalette.entries.forEach { palette ->
                ChoiceRow(palette.label, a.colorPalette == palette) { vm.setColorPalette(palette) }
            }
        }
    },
    SettingSpec(
        key = "font_choice", kind = SettingKind.Choice,
        phrases = "change the font easier to read dyslexia friendly",
    ) { a, _, _, vm ->
        Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
            FontChoice.entries.forEach { choice ->
                ChoiceRow(choice.label, a.fontChoice == choice) { vm.setFontChoice(choice) }
            }
        }
    },
)
