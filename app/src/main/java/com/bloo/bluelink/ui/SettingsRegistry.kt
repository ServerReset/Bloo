package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import com.bloo.bluelink.data.SettingsStore

/**
 * The searchable settings that are not plain on/off switches -- the ranges (sliders and minute
 * fields) and the pickers (units, font, mode).
 */
internal class SettingSpec(
    val title: String,
    val keywords: String,
    val phrases: String = "",
    /**
     * The kind of control, so results can be grouped and a range is never mistaken for a switch.
     */
    val kind: SettingKind,
    val visible: (UiState) -> Boolean = { true },
    val content: @Composable (SettingsStore.Appearance, SettingsStore.NotificationPrefs, UiState, AppViewModel) -> Unit,
)

internal enum class SettingKind { Range, Choice }

internal val RangeSettings: List<SettingSpec> = listOf(
    SettingSpec(
        title = "Text & layout scale", kind = SettingKind.Range,
        keywords = "display size zoom bigger smaller text interface density",
        phrases = "make the text bigger make everything smaller text is too small can't read",
    ) { a, _, _, vm -> UiScaleSlider(a, vm, label = "Scale") },
    SettingSpec(
        title = "Glass clarity", kind = SettingKind.Range,
        keywords = "liquid glass transparency frosted medium soft clear crystal ultra refraction edge blur backing everywhere",
        phrases = "make it see through make the glass clearer more transparent less blurry stronger glass edge",
    ) { a, _, _, vm -> GlassClaritySlider(a, vm) },
    SettingSpec(
        title = "Colour vibrancy", kind = SettingKind.Range,
        keywords = "color saturation vivid material you monochrome best buy tv muted",
        phrases = "make the colors brighter less colorful more vivid washed out",
    ) { a, _, _, vm -> VibrancySlider(a, vm) },
    SettingSpec(
        title = "Left-unlocked alert delay", kind = SettingKind.Range,
        keywords = "minutes before alerting unlocked notification delay wait",
        phrases = "how long before it warns me the car is unlocked",
    ) { _, n, _, vm -> MinutesField(n.unlockedMinutes, "Left unlocked: minutes before alerting", vm::setUnlockedMinutes) },
    SettingSpec(
        title = "Door-open alert delay", kind = SettingKind.Range,
        keywords = "minutes before alerting door open notification delay wait",
        phrases = "how long before it warns me a door is open",
    ) { _, n, _, vm -> MinutesField(n.doorOpenMinutes, "Door left open: minutes before alerting", vm::setDoorOpenMinutes) },
    SettingSpec(
        title = "Car-running alert delay", kind = SettingKind.Range,
        keywords = "minutes before alerting engine running notification delay wait",
        phrases = "how long before it warns me the car is still running",
    ) { _, n, _, vm -> MinutesField(n.runningMinutes, "Car left running: minutes before alerting", vm::setRunningMinutes) },
)

/** Every app-wide picker: one choice out of a few. */
internal val ChoiceSettings: List<SettingSpec> = listOf(
    SettingSpec(
        title = "Display mode", kind = SettingKind.Choice,
        keywords = "theme light dark amoled oled system appearance",
        phrases = "turn on dark mode switch to light mode black background follow the phone",
    ) { a, _, _, vm -> ThemeModeSegmentedRow(a) { vm.setThemeMode(it) } },
    SettingSpec(
        title = "Aurora motion", kind = SettingKind.Choice,
        keywords = "aurora background animated static motion moving still",
        phrases = "stop the background moving make the background still",
    ) { a, _, _, vm ->
        SettingsSegmentedRow(
            label = "Aurora motion",
            options = listOf(SegmentOption("static", "Static", null), SegmentOption("motion", "Motion", null)),
            selectedKey = a.auroraMotion,
            onSelect = { vm.setAuroraMotion(it) },
        )
    },
    SettingSpec(
        title = "Units", kind = SettingKind.Choice,
        keywords = "unit system metric imperial temperature distance speed miles km",
        phrases = "use kilometres use miles celsius or fahrenheit",
    ) { a, _, _, vm -> UnitSystemRow(a, vm) },
    SettingSpec(
        title = "Temperature unit", kind = SettingKind.Choice,
        keywords = "temperature celsius fahrenheit degrees c f units",
        phrases = "show temperatures in celsius show fahrenheit",
    ) { a, _, _, vm -> TempUnitRow(a, vm) },
    SettingSpec(
        title = "Distance unit", kind = SettingKind.Choice,
        keywords = "distance mileage odometer range miles kilometres km units",
        phrases = "show distances in miles show kilometres",
    ) { a, _, _, vm -> DistanceUnitRow(a, vm) },
    SettingSpec(
        title = "Watch lock timing", kind = SettingKind.Choice,
        keywords = "watch wear pin lock ask passcode security open commands",
        phrases = "make my watch ask for the pin when i send a command",
    ) { _, _, s, vm ->
        Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
            com.bloo.bluelink.data.WatchLockTiming.entries.forEach { t ->
                ChoiceRow(t.label, s.watchLockTiming == t) { vm.setWatchLockTiming(t) }
            }
        }
    },
    SettingSpec(
        title = "Simple or advanced settings", kind = SettingKind.Choice,
        keywords = "simple advanced mode power user more options fewer options settings",
        phrases = "show me more settings hide the advanced options keep it simple",
    ) { _, _, s, vm ->
        SettingsSegmentedRow(
            label = "Settings",
            options = listOf(SegmentOption("simple", "Simple", null), SegmentOption("advanced", "Advanced", null)),
            selectedKey = s.settingsMode,
            onSelect = { vm.setSettingsMode(it) },
        )
    },
    SettingSpec(
        title = "Colour palette", kind = SettingKind.Choice,
        keywords = "color theme palette accent blue violet teal forest amber rose bloo",
        phrases = "change the accent colour make the app green use a different color",
    ) { a, _, _, vm ->
        Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
            ColorPalette.entries.forEach { palette ->
                ChoiceRow(palette.label, a.colorPalette == palette) { vm.setColorPalette(palette) }
            }
        }
    },
    SettingSpec(
        title = "Font", kind = SettingKind.Choice,
        keywords = "typeface atkinson hyperlegible google sans accessibility low vision text",
        phrases = "change the font easier to read dyslexia friendly",
    ) { a, _, _, vm ->
        Column(verticalArrangement = Arrangement.spacedBy(GapRow)) {
            FontChoice.entries.forEach { choice ->
                ChoiceRow(choice.label, a.fontChoice == choice) { vm.setFontChoice(choice) }
            }
        }
    },
)
