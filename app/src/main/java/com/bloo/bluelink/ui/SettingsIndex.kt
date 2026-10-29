package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import com.bloo.bluelink.data.AlertKeys
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import com.bloo.bluelink.data.PrefKeys
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SettingsCatalog
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.aiEnabled
import com.bloo.bluelink.data.ambientFahrenheit
import com.bloo.bluelink.data.setAiEnabled
import com.bloo.bluelink.data.setAuroraBackground
import com.bloo.bluelink.data.setColumnsFlipped
import com.bloo.bluelink.data.setDynamicColor
import com.bloo.bluelink.data.setHapticsEnabled
import com.bloo.bluelink.data.setNotifyAutoLock
import com.bloo.bluelink.data.setNotifyCarStarted
import com.bloo.bluelink.data.setNotifyCharging
import com.bloo.bluelink.data.setNotifyDoor
import com.bloo.bluelink.data.setNotifyRunning
import com.bloo.bluelink.data.setNotifyService
import com.bloo.bluelink.data.setNotifyUnlocked
import com.bloo.bluelink.data.setSeamlessInstallShizuku
import com.bloo.bluelink.data.setSeatFlag
import com.bloo.uicommon.content

internal class SearchEntry(val title: String, val haystack: String, val content: @Composable () -> Unit) {
    // Memoized lowercase title; avoids recomputing per score call.
    val titleLowercase: String = title.lowercase()
}

/**
 * Declarative description of one plain on/off setting: one entry here makes it searchable. The
 * display title and the words that find it come from [SettingsCatalog] by [key] -- declared once,
 * there, never copied here. Everything this file adds is the BEHAVIOUR (how to read and write it).
 */
internal class ToggleSpec(
    val key: String,
    /**
     * The row label, when it spells the setting out more fully than its title ("Dynamic colour
     * (Material You)"). Null uses the title.
     */
    val label: String? = null,
    /** How a person would ask for it in a sentence; matched like keywords. */
    val phrases: String = "",
    val visible: (UiState) -> Boolean = { true },
    val checked: (SettingsStore.Appearance, SettingsStore.NotificationPrefs, UiState) -> Boolean,
    val onToggle: (AppViewModel, Boolean) -> Unit,
) {
    /** The title, from the catalog (the one source of truth). */
    val title: String = SettingsCatalog.titleOf(key) ?: key
    /** The words that find it, from the catalog. */
    val keywords: String = SettingsCatalog.keywordsOf(key)
    /** The label the row draws; see [label]. */
    val rowLabel: String get() = label ?: title
}

/**
 * Every plain app-wide toggle, in the order it should appear when searched. Add a new one here --
 * not a new `add(...)` call in [SettingsSearchResults] -- and it is searchable with no other
 * change.
 */
internal val ToggleSettings = listOf(
    ToggleSpec(
        key = PrefKeys.HAPTICS_ENABLED,
        checked = { a, _, _ -> a.hapticsEnabled }, onToggle = { vm, v -> vm.persist { setHapticsEnabled(v) } },
    ),
    ToggleSpec(
        key = AlertKeys.CHARGING,
        checked = { _, n, _ -> n.charging }, onToggle = { vm, v -> vm.setNotifyCharging(v) },
    ),
    ToggleSpec(
        key = AlertKeys.SERVICE,
        checked = { _, n, _ -> n.service }, onToggle = { vm, v -> vm.persist { setNotifyService(v) } },
    ),
    ToggleSpec(
        key = AlertKeys.DOOR,
        checked = { _, n, _ -> n.doorOpen }, onToggle = { vm, v -> vm.persist { setNotifyDoor(v) } },
    ),
    ToggleSpec(
        key = AlertKeys.RUNNING,
        checked = { _, n, _ -> n.running }, onToggle = { vm, v -> vm.persist { setNotifyRunning(v) } },
    ),
    ToggleSpec(
        key = AlertKeys.CHARGE_COMPLETE,
        phrases = "tell me when the car is done charging",
        checked = { _, n, _ -> n.chargeComplete }, onToggle = { vm, v -> vm.setNotifyChargeComplete(v) },
    ),
    ToggleSpec(
        key = AlertKeys.CAR_STARTED,
        phrases = "tell me when somebody starts my car",
        checked = { _, n, _ -> n.carStarted }, onToggle = { vm, v -> vm.persist { setNotifyCarStarted(v) } },
    ),
    ToggleSpec(
        key = AlertKeys.WATCH_LOW_BATTERY,
        phrases = "warn me on my watch when the battery is low",
        checked = { _, n, _ -> n.watchLowBattery }, onToggle = { vm, v -> vm.setNotifyWatchLowBattery(v) },
    ),
    ToggleSpec(
        key = AlertKeys.AUTOLOCK,
        phrases = "tell me when autolock locks the car",
        checked = { _, n, _ -> n.autoLockAlerts }, onToggle = { vm, v -> vm.persist { setNotifyAutoLock(v) } },
    ),
    ToggleSpec(
        key = AlertKeys.UNLOCKED,
        checked = { _, n, _ -> n.unlocked }, onToggle = { vm, v -> vm.persist { setNotifyUnlocked(v) } },
    ),
    ToggleSpec(
        key = PrefKeys.AURORA_BACKGROUND,
        checked = { a, _, _ -> a.auroraBackground }, onToggle = { vm, v -> vm.persist { setAuroraBackground(v) } },
    ),
    ToggleSpec(
        key = PrefKeys.DYNAMIC_COLOR, label = "Dynamic colour (Material You)",
        checked = { a, _, _ -> a.dynamicColor }, onToggle = { vm, v -> vm.setDynamicColor(v) },
    ),
    ToggleSpec(
        key = PrefKeys.COLUMNS_FLIPPED,
        phrases = "put the controls on the other side swap the two columns",
        checked = { a, _, _ -> a.columnsFlipped }, onToggle = { vm, v -> vm.persist { setColumnsFlipped(v) } },
    ),
    // Same top-level gate the AI card itself uses -- these two only mean anything on a device
    // Gemini Nano actually supports, same reason the card is hidden entirely rather than shown
    // disabled.
    ToggleSpec(
        key = PrefKeys.AI_ENABLED, label = "On-device AI (Gemini Nano)",
        visible = { it.aiSupported }, checked = { _, _, s -> s.aiEnabled }, onToggle = { vm, v -> vm.setAiEnabled(v) },
    ),
    ToggleSpec(
        key = PrefKeys.AI_AUTO_SUMMARIZE,
        phrases = "summarize my car automatically every time i open the app",
        visible = { it.aiActive }, checked = { _, _, s -> s.aiAutoSummarize }, onToggle = { vm, v -> vm.setAiAutoSummarize(v) },
    ),
    // Gated like the Backup & sync row: needs Shizuku installed and running.
    ToggleSpec(
        key = PrefKeys.SEAMLESS_INSTALL_SHIZUKU, label = "Install updates seamlessly (Shizuku)",
        visible = { it.shizukuAvailable }, checked = { a, _, _ -> a.seamlessInstallShizuku }, onToggle = { vm, v -> vm.setSeamlessInstallShizuku(v) },
    ),
    ToggleSpec(
        key = PrefKeys.MOTION_BLUR, label = "Motion blur",
        phrases = "turn off the blur on moving things make things sharper stop the smearing battery saver",
        checked = { a, _, _ -> a.motionBlur }, onToggle = { vm, v -> vm.setMotionBlur(v) },
    ),
    ToggleSpec(
        key = PrefKeys.CLASSIC_HEADINGS, label = "Classic headings",
        phrases = "serif font classical titles headings elegant old style letters",
        checked = { a, _, _ -> a.classicHeadings }, onToggle = { vm, v -> vm.setClassicHeadings(v) },
    ),
    ToggleSpec(
        key = PrefKeys.REDUCE_MOTION, label = "Reduce motion",
        phrases = "less movement stop the bouncing calm things down motion sickness accessibility battery saver",
        checked = { a, _, _ -> a.reduceMotion }, onToggle = { vm, v -> vm.setReduceMotion(v) },
    ),
    ToggleSpec(
        key = PrefKeys.KEYBOARD_SHORTCUTS, label = "Keyboard shortcuts",
        phrases = "hotkeys ctrl keys desktop keyboard navigate without touch",
        checked = { a, _, _ -> a.keyboardShortcuts }, onToggle = { vm, v -> vm.setKeyboardShortcuts(v) },
    ),
)

/**
 * The per-vehicle counterpart of [ToggleSpec]: a plain on/off setting that exists once per car.
 * [CarSettingsCard] is the source of truth; this list makes them searchable.
 */
internal class VehicleToggleSpec(
    val title: (Vehicle) -> String,
    val keywords: (Vehicle) -> String,
    val label: String,
    val visible: (Vehicle, UiState) -> Boolean = { _, _ -> true },
    val checked: (Vehicle, UiState) -> Boolean,
    val onToggle: (AppViewModel, Vehicle, Boolean) -> Unit,
)

/**
 * Every plain per-car toggle: each seat position's heat and cool flags plus the heated steering
 * wheel. Built from [SeatPositions] (Screens.kt), the same list [CarSettingsCard] uses.
 */
internal val VehicleToggleSettings: List<VehicleToggleSpec> = buildList {
    SeatPositions.forEach { pos ->
        add(
            VehicleToggleSpec(
                title = { v -> "${pos.label} seat heat · ${v.name}" },
                keywords = { v -> "seat heat warm climate ${v.name}" },
                label = "${pos.label} seat heat",
                checked = { v, s -> pos.heat(s.seatConfigs[v.vin] ?: SeatConfig()) },
                onToggle = { vm, v, value -> vm.setSeatFlag(v, pos.heatKey, value) },
            ),
        )
        add(
            VehicleToggleSpec(
                title = { v -> "${pos.label} seat cool · ${v.name}" },
                keywords = { v -> "seat cool ventilated climate ${v.name}" },
                label = "${pos.label} seat cool",
                checked = { v, s -> pos.cool(s.seatConfigs[v.vin] ?: SeatConfig()) },
                onToggle = { vm, v, value -> vm.setSeatFlag(v, pos.coolKey, value) },
            ),
        )
    }
    add(
        VehicleToggleSpec(
            title = { v -> "Heated steering wheel · ${v.name}" },
            keywords = { v -> "steering wheel heat climate ${v.name}" },
            label = "Heated steering wheel",
            checked = { v, s -> (s.seatConfigs[v.vin] ?: SeatConfig()).steeringWheel },
            onToggle = { vm, v, value -> vm.setSeatFlag(v, "sw", value) },
        ),
    )
}

/**
 * True if any word in [hay] starts with [prefix] ("lim" hits "charge limit", not "unlimited").
 * Scans for the boundary instead of splitting, to avoid allocating per entry per keystroke.
 */
internal fun hasWordStarting(hay: String, prefix: String): Boolean {
    var i = hay.indexOf(prefix)
    while (i >= 0) {
        if (i == 0 || !hay[i - 1].isLetterOrDigit()) return true
        i = hay.indexOf(prefix, i + 1)
    }
    return false
}

/**
 * Within one insertion, deletion or substitution. Not full Levenshtein: O(n), and keeps "haptic"
 * from matching "static".
 */
internal fun withinOneEdit(a: String, b: String): Boolean {
    if (a == b) return true
    val (short, long) = if (a.length <= b.length) a to b else b to a
    if (long.length - short.length > 1) return false
    var i = 0
    var j = 0
    var slack = 1
    while (i < short.length && j < long.length) {
        if (short[i] == long[j]) { i++; j++; continue }
        if (slack == 0) return false
        slack = 0
        if (short.length == long.length) { i++; j++ } else j++
    }
    return true
}

/** True if any word of [hay] is within one edit of [token]. */
internal fun hasFuzzyWord(hay: String, token: String): Boolean {
    var start = 0
    while (start <= hay.length) {
        var end = start
        while (end < hay.length && hay[end].isLetterOrDigit()) end++
        if (end > start && withinOneEdit(hay.substring(start, end), token)) return true
        start = if (end == start) start + 1 else end + 1
    }
    return false
}

/**
 * How well one entry answers the query, or null for "not at all". Every token must match something
 * ([tokens] are ANDed). Title outranks keywords, a word start outranks the middle of one, and
 * shorter titles win ties.
 */
internal fun searchScore(tokens: List<String>, e: SearchEntry, fuzzy: Boolean): Int? {
    val title = e.titleLowercase
    var total = 0
    for (t in tokens) {
        // Best hit across the token and its synonyms; a synonym is worth less than the literal
        // word.
        var hit = 0
        for ((i, form) in expandToken(t).withIndex()) {
            val penalty = if (i == 0) 0 else 30
            val score = when {
                title == form -> 1000
                title.startsWith(form) -> 500
                hasWordStarting(title, form) -> 320
                form in title -> 160
                hasWordStarting(e.haystack, form) -> 90
                form in e.haystack -> 40
                fuzzy && form.length >= 4 && hasFuzzyWord(e.haystack, form) -> 10
                else -> 0
            }
            if (score > 0) hit = maxOf(hit, score - penalty)
        }
        if (hit == 0) return null
        total += hit
    }
    // Tie-break on brevity: the shortest title is the most specific answer.
    return total * 100 - title.length
}

/**
 * A vehicle command recognised in a free-form search query. [cmd]/[climateTarget] use
 * [com.bloo.bluelink.data.VehicleCommandRunner]'s command vocabulary.
 */
internal class ParsedVehicleCommand(val cmd: String, val climateTarget: String = "default", val label: String)

/**
 * The temperature asked for, in Fahrenheit, or null if the query names none. Superlatives resolve
 * to the ends of [CLIMATE_TEMP_RANGE_F].
 */
internal fun parseClimateTemperature(q: String, metric: Boolean): Int? {
    if (RxColdest.containsMatchIn(q)) return CLIMATE_TEMP_RANGE_F.first
    if (RxWarmest.containsMatchIn(q)) return CLIMATE_TEMP_RANGE_F.last
    val m = RxTempAtTo.find(q)
        ?: RxTempDegrees.find(q)
        ?: return null
    val n = m.groupValues[1].toIntOrNull() ?: return null
    val unit = m.groupValues.drop(2).firstOrNull { it.isNotBlank() }
    val f = when {
        unit == "c" -> ambientFahrenheit(n.toDouble())
        unit == "f" -> n
        // No unit: follow the user's own metric setting.
        metric -> ambientFahrenheit(n.toDouble())
        else -> n
    }
    return f.coerceIn(CLIMATE_TEMP_RANGE_F.first, CLIMATE_TEMP_RANGE_F.last)
}
