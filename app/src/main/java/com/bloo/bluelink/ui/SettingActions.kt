package com.bloo.bluelink.ui

import com.bloo.bluelink.data.AiSettingResolver
import com.bloo.bluelink.data.AiSettingResolver.Option
import com.bloo.bluelink.data.AlertKeys
import com.bloo.bluelink.data.SettingsCatalog
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.setDistanceUnit
import com.bloo.bluelink.data.setDoorOpenMinutes
import com.bloo.bluelink.data.setFontChoice
import com.bloo.bluelink.data.setRunningMinutes
import com.bloo.bluelink.data.setTempUnit
import com.bloo.bluelink.data.setUnitSystem
import com.bloo.bluelink.data.setUnlockedMinutes

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
    /** A free-text value (a place name) rather than a switch, option or number. */
    val text: Boolean = false,
    /** A one-shot action (reset, clear, collapse all): a picker with a single "run" option. */
    val isAction: Boolean = false,
    /** The action needs the location permission first; the card asks for it, then runs [write]. */
    val needsLocationPermission: Boolean = false,
    val read: (SettingContext) -> String,
    val write: (AppViewModel, String) -> Unit,
) {
    fun candidate(ctx: SettingContext) =
        AiSettingResolver.Candidate(id, title, read(ctx), hint, options, min, max, text)

    /** How a resolved value reads to a person ("on", "Metric", "1.2"). */
    fun describe(value: String): String = options.firstOrNull { it.key == value }?.label ?: value
}

/**
 * One setting's title and the words that find it both come from [SettingsCatalog] by [key]; the
 * [phrases] here are the sentence forms that are only useful to the resolver.
 */
private fun hintOf(key: String, phrases: String): String = "${SettingsCatalog.keywordsOf(key)} $phrases".trim()

private fun choice(
    id: String,
    key: String,
    phrases: String,
    options: List<Option>,
    read: (SettingContext) -> String,
    write: (AppViewModel, String) -> Unit,
) = SettingAction(id, SettingsCatalog.titleOf(key) ?: key, hintOf(key, phrases), options = options, read = read, write = write)

/** A one-shot action the request can run: it has no value, only "run". */
private fun action(id: String, title: String, hint: String, run: (AppViewModel) -> Unit) = SettingAction(
    id, title, hint, options = listOf(Option("run", title)), isAction = true,
    read = { "" }, write = { vm, _ -> run(vm) },
)

private fun range(
    id: String,
    key: String,
    phrases: String,
    min: Double,
    max: Double,
    read: (SettingContext) -> String,
    write: (AppViewModel, String) -> Unit,
) = SettingAction(id, SettingsCatalog.titleOf(key) ?: key, hintOf(key, phrases), min = min, max = max, read = read, write = write)

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
            "c_theme", "theme_mode", "night black background follow the phone",
            listOf(Option("system", "Follow the phone"), Option("light", "Light"), Option("dark", "Dark")),
            { it.appearance.themeMode.name.lowercase() },
            { vm, v -> vm.setThemeMode(ThemeMode.valueOf(v.uppercase())) },
        ),
    )
    add(
        choice(
            "c_units", "unit_system", "kilometres miles celsius fahrenheit",
            listOf(Option("imperial", "Imperial (miles, °F)"), Option("metric", "Metric (km, °C)")),
            { it.appearance.unitSystem },
            { vm, v -> vm.persist { setUnitSystem(v) } },
        ),
    )
    add(
        choice(
            "c_temp", "temp_unit", "degrees",
            listOf(Option("auto", "Follow units"), Option("f", "Fahrenheit"), Option("c", "Celsius")),
            { it.appearance.tempUnit },
            { vm, v -> vm.persist { setTempUnit(v) } },
        ),
    )
    add(
        choice(
            "c_distance", "distance_unit", "mileage odometer range",
            listOf(Option("auto", "Follow units"), Option("mi", "Miles"), Option("km", "Kilometres")),
            { it.appearance.distanceUnit },
            { vm, v -> vm.persist { setDistanceUnit(v) } },
        ),
    )
    add(
        choice(
            "c_font", "font_choice", "atkinson hyperlegible google sans dyslexia",
            FontChoice.entries.map { Option(it.name.lowercase(), it.label) },
            { it.appearance.fontChoice.name.lowercase() },
            { vm, v -> vm.persist { setFontChoice(FontChoice.valueOf(v.uppercase())) } },
        ),
    )
    add(
        choice(
            "c_palette", "color_palette", "",
            ColorPalette.entries.map { Option(it.name.lowercase(), it.label) },
            { it.appearance.colorPalette.name.lowercase() },
            { vm, v -> vm.setColorPalette(ColorPalette.valueOf(v.uppercase())) },
        ),
    )
    add(
        choice(
            "c_watchlock", "watch_lock_timing", "",
            com.bloo.bluelink.data.WatchLockTiming.entries.map { Option(it.wireKey, it.label) },
            { it.state.watchLockTiming.wireKey },
            { vm, v -> vm.setWatchLockTiming(com.bloo.bluelink.data.WatchLockTiming.fromWire(v)) },
        ),
    )
    add(
        choice(
            "c_mode", "settings_mode", "power user",
            listOf(Option("simple", "Simple"), Option("advanced", "Advanced")),
            { it.state.settingsMode },
            { vm, v -> vm.setSettingsMode(v) },
        ),
    )
    add(
        choice(
            "c_glass", "glass_clarity", "",
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
            "r_scale", "ui_scale", "", 0.85, 1.3,
            { it.appearance.uiScale.toString() }, { vm, v -> vm.setUiScaleSoon(v.toFloat()) },
        ),
    )
    add(
        range(
            "r_vibrancy", "vibrancy", "", 0.5, 1.6,
            { it.appearance.vibrancy.toString() }, { vm, v -> vm.setVibrancySoon(v.toFloat()) },
        ),
    )
    add(
        range(
            "r_unlocked", AlertKeys.UNLOCKED_MINUTES, "", 1.0, 120.0,
            { it.notif.unlockedMinutes.toString() }, { vm, v -> vm.persist { setUnlockedMinutes(v.toDouble().toInt()) } },
        ),
    )
    add(
        range(
            "r_door", AlertKeys.DOOR_MINUTES, "", 1.0, 120.0,
            { it.notif.doorOpenMinutes.toString() }, { vm, v -> vm.persist { setDoorOpenMinutes(v.toDouble().toInt()) } },
        ),
    )
    add(
        range(
            "r_running", AlertKeys.RUNNING_MINUTES, "", 1.0, 120.0,
            { it.notif.runningMinutes.toString() }, { vm, v -> vm.persist { setRunningMinutes(v.toDouble().toInt()) } },
        ),
    )
}

/**
 * The settings that depend on what this person has: each car's pinned pebble, and their saved
 * palettes. Built from live state, so a request can name "the Ioniq's pinned pebble" or a palette
 * they made.
 */
private fun dynamicSettingActions(state: UiState, appearance: SettingsStore.Appearance): List<SettingAction> = buildList {
    state.vehicles.forEachIndexed { i, v ->
        val pinnable = pinnablePebbles(v, state)
        if (pinnable.isEmpty()) return@forEachIndexed
        add(
            choice(
                "p$i", "Pinned pebble · ${v.name}", "pin pebble hot seat full screen beside controls ${v.name}",
                listOf(Option("none", "Nothing pinned")) + pinnable.map { Option(it, sectionLabel(it)) },
                { c -> c.state.hotspotSections[v.vin] ?: "none" },
                { vm, value ->
                    val current = vm._state.value.hotspotSections[v.vin]
                    // setHotspot toggles: unpin by naming the current one, pin by naming a different one.
                    if (value == "none") current?.let { vm.setHotspot(v, it) } else if (current != value) vm.setHotspot(v, value)
                },
            ),
        )
    }
    if (state.vehicles.size > 1) {
        add(
            choice(
                "o_first", "Car order: put this car first", "car order reorder sort first garage move",
                state.vehicles.map { Option(it.vin, it.name) },
                { c -> c.state.vehicles.firstOrNull()?.vin.orEmpty() },
                { vm, vin ->
                    val cars = vm._state.value.vehicles
                    cars.firstOrNull { it.vin == vin }?.let { first -> vm.reorderVehicles(listOf(first) + cars.filter { it.vin != vin }) }
                },
            ),
        )
    }
    state.vehicles.forEachIndexed { i, v ->
        add(
            action("a_order$i", "Reset pebble order · ${v.name}", "pebbles order reset default ${v.name}") {
                it.setSectionOrder(v, com.bloo.bluelink.data.DEFAULT_SECTIONS)
            },
        )
        val sections = state.sectionsFor(v).filter { it != "summary" && state.isSectionAvailable(v, it) }
        add(
            action("a_collapse$i", "Collapse all pebbles · ${v.name}", "pebbles collapse close fold ${v.name}") { vm ->
                sections.filter { vm._state.value.isPebbleExpanded(v.vin, it) }.forEach { vm.togglePebble(v, it) }
            },
        )
        add(
            action("a_expand$i", "Expand all pebbles · ${v.name}", "pebbles expand open unfold ${v.name}") { vm ->
                sections.filter { !vm._state.value.isPebbleExpanded(v.vin, it) }.forEach { vm.togglePebble(v, it) }
            },
        )
        add(
            action("a_photopick$i", "Choose a new photo · ${v.name}", "photo picture image change choose set pick new ${v.name}") {
                it.requestPhotoPick(v.vin)
            },
        )
        if (!state.imageUrls[v.vin].isNullOrBlank()) {
            add(
                action("a_photo$i", "Clear photo · ${v.name}", "photo picture image remove clear default ${v.name}") {
                    it.setVehicleImage(v.vin, "")
                },
            )
        }
    }
    add(
        SettingAction(
            "x_weather", SettingsCatalog.titleOf("weather_label") ?: "Weather location", "weather place city location forecast",
            text = true,
            read = { c -> c.appearance.weatherLabel ?: "none" },
            write = { vm, place -> vm.setWeatherPlace(place) },
        ),
    )
    add(
        SettingAction(
            "a_weatherdevice", "Use my location for weather", "weather follow device location gps my location",
            options = listOf(Option("run", "Use my location for weather")), isAction = true, needsLocationPermission = true,
            read = { "" }, write = { vm, _ -> vm.useDeviceLocationForWeather() },
        ),
    )
    if (appearance.weatherLabel != null) {
        add(action("a_weatherclear", "Clear weather location", "weather place city location clear follow device") { it.clearWeatherLocation() })
    }
    if (appearance.customPalettes.isNotEmpty()) {
        add(
            choice(
                "c_custom", "custom_palettes", "saved",
                listOf(Option("builtin", "Built-in palette")) + appearance.customPalettes.map { Option(it.id, it.name) },
                { c -> c.appearance.activeCustomPaletteId ?: "builtin" },
                { vm, value -> vm.setActiveCustomPaletteId(value.takeIf { it != "builtin" }) },
            ),
        )
    }
}

/** The settings a request may change right now: visible in this mode and on this device. */
internal fun availableSettingActions(state: UiState, appearance: SettingsStore.Appearance): List<SettingAction> =
    (SettingActions + dynamicSettingActions(state, appearance))
        .filter { it.visible(state) && isSettingAvailableInMode(it.title, state.settingsMode) }
