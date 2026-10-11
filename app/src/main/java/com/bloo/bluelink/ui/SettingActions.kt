package com.bloo.bluelink.ui

import com.bloo.bluelink.data.AiSettingResolver
import com.bloo.bluelink.data.AiSettingResolver.Option
import com.bloo.bluelink.data.SettingsStore

/** Everything a setting's current value is read from: the same three sources search draws its controls from. */
internal class SettingContext(
    val appearance: SettingsStore.Appearance,
    val notif: SettingsStore.NotificationPrefs,
    val state: UiState,
)

/**
 * One app-wide setting in the form natural-language search needs: how to read it, what values are
 * legal for it, and how to write one. A switch has no [options] and no bounds; a picker has
 * [options]; a range has [min] and [max]. Values travel as text ("on"/"off", an option key, a
 * number) and are validated by [AiSettingResolver] before [write] is ever called.
 */
internal class SettingAction(
    val id: String,
    val title: String,
    val hint: String,
    val visible: (UiState) -> Boolean = { true },
    val options: List<Option> = emptyList(),
    val min: Double? = null,
    val max: Double? = null,
    val read: (SettingContext) -> String,
    val write: (AppViewModel, String) -> Unit,
) {
    fun candidate(ctx: SettingContext) =
        AiSettingResolver.Candidate(id, title, read(ctx), hint, options, min, max)

    /** How a resolved value reads to a person ("on", "Metric", "1.2"). */
    fun describe(value: String): String = options.firstOrNull { it.key == value }?.label ?: value
}

private fun choice(
    id: String,
    title: String,
    hint: String,
    options: List<Option>,
    read: (SettingContext) -> String,
    write: (AppViewModel, String) -> Unit,
) = SettingAction(id, title, hint, options = options, read = read, write = write)

private fun range(
    id: String,
    title: String,
    hint: String,
    min: Double,
    max: Double,
    read: (SettingContext) -> String,
    write: (AppViewModel, String) -> Unit,
) = SettingAction(id, title, hint, min = min, max = max, read = read, write = write)

/**
 * Every app-wide switch, picker and range a plain-language request can change. The switches are
 * [ToggleSettings] itself (so a new toggle is reachable by voice with no extra code); the pickers and
 * ranges mirror [ChoiceSettings] and [RangeSettings], which draw the controls for the same settings.
 */
internal val SettingActions: List<SettingAction> = buildList {
    ToggleSettings.forEachIndexed { i, spec ->
        add(
            SettingAction(
                id = "t$i", title = spec.title, hint = "${spec.keywords} ${spec.phrases}".trim(),
                visible = spec.visible,
                read = { c -> if (spec.checked(c.appearance, c.notif, c.state)) "on" else "off" },
                write = { vm, v -> spec.onToggle(vm, v == "on") },
            ),
        )
    }
    add(
        choice(
            "c_theme", "Display mode", "theme light dark system appearance night",
            listOf(Option("system", "Follow the phone"), Option("light", "Light"), Option("dark", "Dark")),
            { it.appearance.themeMode.name.lowercase() },
            { vm, v -> vm.setThemeMode(ThemeMode.valueOf(v.uppercase())) },
        ),
    )
    add(
        choice(
            "c_aurora", "Aurora motion", "aurora background animated static motion moving still",
            listOf(Option("static", "Still"), Option("motion", "Moving")),
            { it.appearance.auroraMotion },
            { vm, v -> vm.setAuroraMotion(v) },
        ),
    )
    add(
        choice(
            "c_units", "Units", "metric imperial kilometres miles celsius fahrenheit",
            listOf(Option("imperial", "Imperial (miles, °F)"), Option("metric", "Metric (km, °C)")),
            { it.appearance.unitSystem },
            { vm, v -> vm.setUnitSystem(v) },
        ),
    )
    add(
        choice(
            "c_temp", "Temperature unit", "temperature celsius fahrenheit degrees",
            listOf(Option("auto", "Follow units"), Option("f", "Fahrenheit"), Option("c", "Celsius")),
            { it.appearance.tempUnit },
            { vm, v -> vm.setTempUnit(v) },
        ),
    )
    add(
        choice(
            "c_distance", "Distance unit", "distance mileage odometer range miles kilometres km",
            listOf(Option("auto", "Follow units"), Option("mi", "Miles"), Option("km", "Kilometres")),
            { it.appearance.distanceUnit },
            { vm, v -> vm.setDistanceUnit(v) },
        ),
    )
    add(
        choice(
            "c_font", "Font", "typeface atkinson hyperlegible google sans accessibility dyslexia",
            FontChoice.entries.map { Option(it.name.lowercase(), it.label) },
            { it.appearance.fontChoice.name.lowercase() },
            { vm, v -> vm.setFontChoice(FontChoice.valueOf(v.uppercase())) },
        ),
    )
    add(
        choice(
            "c_palette", "Colour palette", "color theme palette accent",
            ColorPalette.entries.map { Option(it.name.lowercase(), it.label) },
            { it.appearance.colorPalette.name.lowercase() },
            { vm, v -> vm.setColorPalette(ColorPalette.valueOf(v.uppercase())) },
        ),
    )
    add(
        choice(
            "c_mode", "Simple or advanced settings", "simple advanced mode power user",
            listOf(Option("simple", "Simple"), Option("advanced", "Advanced")),
            { it.state.settingsMode },
            { vm, v -> vm.setSettingsMode(v) },
        ),
    )
    add(
        choice(
            "c_glass", "Glass clarity", "liquid glass transparency frosted clear crystal ultra blur",
            GlassStops.map { Option(it.name.lowercase(), it.name) },
            { c -> (if (c.appearance.ultraGlass) GlassStops.last() else GlassStops[nearestGlassStop(c.appearance.glassClarity)]).name.lowercase() },
            { vm, v ->
                val stop = GlassStops.first { it.name.equals(v, ignoreCase = true) }
                vm.setGlassClaritySoon(stop.transparency)
                vm.setUltraGlass(stop.ultra)
            },
        ),
    )
    add(
        range(
            "r_scale", "Text & layout scale", "display size zoom bigger smaller text interface", 0.85, 1.3,
            { it.appearance.uiScale.toString() }, { vm, v -> vm.setUiScaleSoon(v.toFloat()) },
        ),
    )
    add(
        range(
            "r_vibrancy", "Colour vibrancy", "color saturation vivid muted", 0.5, 1.6,
            { it.appearance.vibrancy.toString() }, { vm, v -> vm.setVibrancySoon(v.toFloat()) },
        ),
    )
    add(
        range(
            "r_unlocked", "Left-unlocked alert delay (minutes)", "minutes before alerting unlocked notification delay", 1.0, 120.0,
            { it.notif.unlockedMinutes.toString() }, { vm, v -> vm.setUnlockedMinutes(v.toDouble().toInt()) },
        ),
    )
    add(
        range(
            "r_door", "Door-open alert delay (minutes)", "minutes before alerting door open notification delay", 1.0, 120.0,
            { it.notif.doorOpenMinutes.toString() }, { vm, v -> vm.setDoorOpenMinutes(v.toDouble().toInt()) },
        ),
    )
    add(
        range(
            "r_running", "Car-running alert delay (minutes)", "minutes before alerting engine running notification delay", 1.0, 120.0,
            { it.notif.runningMinutes.toString() }, { vm, v -> vm.setRunningMinutes(v.toDouble().toInt()) },
        ),
    )
}

/** The settings a request may change right now: visible in this mode and on this device. */
internal fun availableSettingActions(state: UiState): List<SettingAction> =
    SettingActions.filter { it.visible(state) && isSettingAvailableInMode(it.title, state.settingsMode) }
