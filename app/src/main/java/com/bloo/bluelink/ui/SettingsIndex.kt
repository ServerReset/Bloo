package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import com.bloo.bluelink.data.ambientFahrenheit
import com.bloo.bluelink.data.CLIMATE_TEMP_RANGE_F
import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.VehicleCommandRunner
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.aiEnabled
import com.bloo.bluelink.data.setAiEnabled
import com.bloo.bluelink.data.setAuroraBackground
import com.bloo.bluelink.data.setDynamicColor
import com.bloo.bluelink.data.setHapticsEnabled
import com.bloo.bluelink.data.setNotifyCharging
import com.bloo.bluelink.data.setNotifyDoor
import com.bloo.bluelink.data.setNotifyRunning
import com.bloo.bluelink.data.setNotifyService
import com.bloo.bluelink.data.setNotifyUnlocked
import com.bloo.bluelink.data.setPebbleOutline
import com.bloo.bluelink.data.setSeamlessInstallShizuku
import com.bloo.bluelink.data.setSeatFlag

internal val SearchStopwords = setOf(
    "for", "the", "of", "show", "me", "what", "whats", "is", "a", "an", "to",
    "car", "cars", "my", "s", "setting", "settings", "get", "in",
)


/**
 * Words people use for things this app calls something else.
 *
 * The index is written in the app's own vocabulary, which is the vocabulary of
 * someone who already knows where everything is. A search box is used by
 * someone who does not: they type "vibrate", not "haptic feedback"; "dark
 * mode", not "theme"; "gps", not "location". Rather than stuff every synonym
 * into every entry's keyword string -- which has to be remembered at each of
 * the ~60 call sites, and silently is not -- each query token expands to
 * itself plus its synonyms, and an entry matching ANY of them counts as
 * matching the token.
 *
 * Written token -> app vocabulary, not the reverse: this maps what a person
 * types onto what the index contains.
 */
internal val SearchSynonyms: Map<String, List<String>> = mapOf(
    "vibrate" to listOf("haptic"),
    "vibration" to listOf("haptic"),
    "buzz" to listOf("haptic"),
    "dark" to listOf("theme", "night"),
    "light" to listOf("theme"),
    "night" to listOf("theme", "dark"),
    "colour" to listOf("color", "palette"),
    "colours" to listOf("color", "palette"),
    "gps" to listOf("location"),
    "map" to listOf("location"),
    "where" to listOf("location"),
    "parked" to listOf("location"),
    "font" to listOf("text", "typeface"),
    "size" to listOf("scale", "text"),
    "bigger" to listOf("scale", "text"),
    "smaller" to listOf("scale", "text"),
    "mileage" to listOf("odometer", "miles"),
    "miles" to listOf("odometer"),
    "km" to listOf("odometer", "kilometres"),
    "range" to listOf("battery", "fuel"),
    "charge" to listOf("battery", "charging"),
    "percent" to listOf("battery", "charge"),
    "battery" to listOf("charge"),
    "plug" to listOf("charge", "charging"),
    "ac" to listOf("climate"),
    "heat" to listOf("climate"),
    "heater" to listOf("climate"),
    "cool" to listOf("climate"),
    "aircon" to listOf("climate"),
    "defrost" to listOf("climate", "defog"),
    "warm" to listOf("climate"),
    "preheat" to listOf("climate"),
    "seats" to listOf("seat"),
    "doors" to listOf("lock", "door"),
    "alarm" to listOf("horn"),
    "beep" to listOf("horn"),
    "flash" to listOf("lights"),
    "headlights" to listOf("lights"),
    "backup" to listOf("sync", "drive", "google"),
    "cloud" to listOf("sync", "drive"),
    "notify" to listOf("notification", "alert"),
    "notifications" to listOf("notification", "alert"),
    "password" to listOf("pin", "credentials", "login"),
    "signout" to listOf("logout", "sign"),
    "plate" to listOf("license", "registration"),
    "service" to listOf("maintenance"),
    "tyre" to listOf("tire"),
    "update" to listOf("version", "upgrade"),
    "language" to listOf("locale"),
    "units" to listOf("unit", "metric", "imperial"),
    "celsius" to listOf("metric", "unit"),
    "fahrenheit" to listOf("imperial", "unit"),
    // Natural words for the glass / look settings.
    "transparent" to listOf("glass", "clarity"),
    "transparency" to listOf("glass", "clarity"),
    "blur" to listOf("glass"),
    "blurry" to listOf("glass"),
    "frosted" to listOf("glass"),
    "liquid" to listOf("glass"),
    "bend" to listOf("glass", "ultra"),
    "edge" to listOf("glass", "ultra"),
    "refraction" to listOf("glass"),
    "animation" to listOf("aurora", "motion"),
    "animations" to listOf("aurora", "motion"),
    "moving" to listOf("aurora", "motion"),
    "background" to listOf("aurora"),
    "wallpaper" to listOf("aurora", "dynamic"),
    "accent" to listOf("color", "palette"),
    "hue" to listOf("color", "palette"),
    "palette" to listOf("color"),
    "tiny" to listOf("scale", "text"),
    "huge" to listOf("scale", "text"),
    "zoom" to listOf("scale", "text"),
    "readable" to listOf("scale", "text", "font"),
    "accessibility" to listOf("font", "scale", "text"),
    "saturation" to listOf("vibrancy"),
    "vivid" to listOf("vibrancy"),
    "fingerprint" to listOf("biometric"),
    "biometrics" to listOf("biometric"),
    "privacy" to listOf("biometric", "ai"),
    "gemini" to listOf("ai"),
    "assistant" to listOf("ai"),
    "reminder" to listOf("notification", "service"),
    "reminders" to listOf("notification", "service"),
    "alerts" to listOf("notification"),
    "alert" to listOf("notification"),
    "ping" to listOf("notification"),
    "sound" to listOf("haptic"),
    "annoying" to listOf("haptic", "notification"),
    "temp" to listOf("temperature", "climate"),
    "temperature" to listOf("climate", "unit"),
    "kilometers" to listOf("odometer", "kilometres"),
    "bluetooth" to listOf("autolock"),
    "grace" to listOf("autolock", "lock"),
    "delay" to listOf("autolock", "lock", "minutes"),
    "minutes" to listOf("alert", "notification"),
    "watch" to listOf("wear", "notification"),
    "smartwatch" to listOf("watch"),
    "shizuku" to listOf("install", "update"),
    "install" to listOf("update", "shizuku"),
    "shortcut" to listOf("shortcuts", "launcher"),
    "launcher" to listOf("shortcuts"),
)


/** A token and every form of it worth matching. */
internal fun expandToken(t: String): List<String> {
    val extra = SearchSynonyms[t] ?: return listOf(t)
    return buildList { add(t); addAll(extra) }
}


/** A runner command id as a sentence fragment, for the confirm card. */
internal fun aiCommandLabel(cmd: String): String = when (cmd) {
    "lock" -> "Lock"
    "unlock" -> "Unlock"
    "charge_on" -> "Start charging"
    "charge_off" -> "Stop charging"
    "lights" -> "Flash the lights on"
    "horn" -> "Sound the horn on"
    "climate_on" -> "Start climate on"
    "climate_off" -> "Stop climate on"
    else -> "Run on"
}


internal class SearchEntry(val title: String, val haystack: String, val content: @Composable () -> Unit) {
    // Memoized lowercase title to avoid recomputing on every search score call
    val titleLowercase: String = title.lowercase()
}


/**
 * Declarative description of one plain on/off setting, so a new simple toggle
 * needs exactly one entry here to become searchable -- not a hand-written
 * [SearchEntry] closure duplicating the same `ToggleRow(label, checked) { onToggle }`
 * shape every other toggle already uses. This is the "dynamic index" for the
 * subset of settings that fit it: anything that is genuinely just a checked
 * state and a setter reads its search row from this single list instead of a
 * bespoke `add(...)` call. Settings whose search behaviour has to be more than
 * a toggle -- a segmented picker, a confirm-gated biometric prompt, a slider,
 * anything per-vehicle -- still declare themselves explicitly below; forcing
 * those through this shape would be the same regression the biometric entry's
 * own comment warns about (a search shortcut skipping a step the real row
 * enforces).
 */
internal class ToggleSpec(
    val title: String,
    val keywords: String,
    /** Shown on the row itself; defaults to [title] since most toggles read
     *  identically in both places. Only a few (e.g. "Dynamic color (Material
     *  You)") spell the row out more fully than the search title. */
    val label: String = title,
    /** How a person would ask for it in a sentence; matched like keywords. */
    val phrases: String = "",
    val visible: (UiState) -> Boolean = { true },
    val checked: (SettingsStore.Appearance, SettingsStore.NotificationPrefs, UiState) -> Boolean,
    val onToggle: (AppViewModel, Boolean) -> Unit,
)


/** Every plain app-wide toggle, in the order it should appear when searched.
 *  Add a new one here -- not a new `add(...)` call in [SettingsSearchResults]
 *  -- and it is searchable with no other change. */
internal val ToggleSettings = listOf(
    ToggleSpec(
        title = "Haptic feedback", keywords = "vibration vibrate buzz sound",
        checked = { a, _, _ -> a.hapticsEnabled }, onToggle = { vm, v -> vm.setHapticsEnabled(v) },
    ),
    ToggleSpec(
        title = "Live charging updates", keywords = "notification charging live progress ongoing bar ev limit",
        checked = { _, n, _ -> n.charging }, onToggle = { vm, v -> vm.setNotifyCharging(v) },
    ),
    ToggleSpec(
        title = "Service due alerts", keywords = "notification reminder service",
        checked = { _, n, _ -> n.service }, onToggle = { vm, v -> vm.setNotifyService(v) },
    ),
    ToggleSpec(
        title = "Door-left-open alerts", keywords = "notification door open",
        checked = { _, n, _ -> n.doorOpen }, onToggle = { vm, v -> vm.setNotifyDoor(v) },
    ),
    ToggleSpec(
        title = "Car-running alerts", keywords = "notification engine climate running left on",
        checked = { _, n, _ -> n.running }, onToggle = { vm, v -> vm.setNotifyRunning(v) },
    ),
    ToggleSpec(
        title = "Charge-complete alerts", keywords = "notification charging finished full done battery",
        phrases = "tell me when the car is done charging",
        checked = { _, n, _ -> n.chargeComplete }, onToggle = { vm, v -> vm.setNotifyChargeComplete(v) },
    ),
    ToggleSpec(
        title = "Car-started alerts", keywords = "notification engine started remote start someone",
        phrases = "tell me when somebody starts my car",
        checked = { _, n, _ -> n.carStarted }, onToggle = { vm, v -> vm.setNotifyCarStarted(v) },
    ),
    ToggleSpec(
        title = "Watch low-battery alerts", keywords = "watch wear notification low battery under 20 percent",
        phrases = "warn me on my watch when the battery is low",
        checked = { _, n, _ -> n.watchLowBattery }, onToggle = { vm, v -> vm.setNotifyWatchLowBattery(v) },
    ),
    ToggleSpec(
        title = "AutoLock alerts", keywords = "autolock notification locked dry run test",
        phrases = "tell me when autolock locks the car",
        checked = { _, n, _ -> n.autoLockAlerts }, onToggle = { vm, v -> vm.setNotifyAutoLock(v) },
    ),
    ToggleSpec(
        title = "Left-unlocked alerts", keywords = "notification unlocked lock left open",
        checked = { _, n, _ -> n.unlocked }, onToggle = { vm, v -> vm.setNotifyUnlocked(v) },
    ),
    ToggleSpec(
        title = "Aurora background", keywords = "gradient animated theme background glow",
        checked = { a, _, _ -> a.auroraBackground }, onToggle = { vm, v -> vm.setAuroraBackground(v) },
    ),
    ToggleSpec(
        title = "Dynamic color", label = "Dynamic color (Material You)", keywords = "material you wallpaper theme color",
        checked = { a, _, _ -> a.dynamicColor }, onToggle = { vm, v -> vm.setDynamicColor(v) },
    ),
    ToggleSpec(
        title = "Pebble outline", keywords = "border rim card theme appearance",
        checked = { a, _, _ -> a.pebbleOutline }, onToggle = { vm, v -> vm.setPebbleOutline(v) },
    ),
    // Same top-level gate the AI card itself uses -- these two only mean
    // anything on a device Gemini Nano actually supports, same reason the
    // card is hidden entirely rather than shown disabled.
    ToggleSpec(
        title = "On-device AI", label = "On-device AI (Gemini Nano)", keywords = "gemini nano ai summary assistant privacy on-device",
        visible = { it.aiSupported }, checked = { _, _, s -> s.aiEnabled }, onToggle = { vm, v -> vm.setAiEnabled(v) },
    ),
    // Same gate as the row itself (Backup & sync): only meaningful with
    // Shizuku actually installed and running.
    ToggleSpec(
        title = "Install updates seamlessly", label = "Install updates seamlessly (Shizuku)", keywords = "shizuku silent install update",
        visible = { it.shizukuAvailable }, checked = { a, _, _ -> a.seamlessInstallShizuku }, onToggle = { vm, v -> vm.setSeamlessInstallShizuku(v) },
    ),
)


/**
 * The per-vehicle counterpart of [ToggleSpec]: a plain on/off setting that
 * exists once PER CAR -- a seat's heat/cool flag, the heated-steering-wheel
 * flag, whether a dashboard section shows for that car -- rather than once
 * for the whole app. [CarSettingsCard] (the real "Cars" settings card) is
 * the source of truth for all of these; this list is what makes them
 * searchable without a hand-written [SearchEntry] closure per car per
 * toggle, the same duplication [ToggleSettings] already removed on the
 * app-wide side.
 */
internal class VehicleToggleSpec(
    val title: (Vehicle) -> String,
    val keywords: (Vehicle) -> String,
    val label: String,
    val visible: (Vehicle, UiState) -> Boolean = { _, _ -> true },
    val checked: (Vehicle, UiState) -> Boolean,
    val onToggle: (AppViewModel, Vehicle, Boolean) -> Unit,
)


/** Every plain per-car toggle: the four seat positions' heat and cool flags, and the
 *  heated steering wheel flag -- generated once per position here instead of needing
 *  its own [SearchEntry] written out by hand. Reuses [SeatPositions] (Screens.kt), the
 *  same list [CarSettingsCard] itself builds its seat rows from, so the two can't drift
 *  out of sync with each other on label or key. */
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


/** True if any WORD in [hay] starts with [prefix] -- "lim" hits "charge limit"
 *  but not "unlimited". Scanning for the boundary beats splitting the string,
 *  which would allocate a list per entry per keystroke. */
internal fun hasWordStarting(hay: String, prefix: String): Boolean {
    var i = hay.indexOf(prefix)
    while (i >= 0) {
        if (i == 0 || !hay[i - 1].isLetterOrDigit()) return true
        i = hay.indexOf(prefix, i + 1)
    }
    return false
}


/** Within one insertion, deletion or substitution. Deliberately not a full
 *  Levenshtein: one typo is what people actually make, and bounding it at one
 *  keeps this O(n) and keeps "haptic" from matching "static". */
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
 * How well one entry answers the query, or null for "not at all".
 *
 * The old engine was `tokens.all { it in haystack }` and then showed whatever
 * survived IN DECLARATION ORDER. Two problems, and the second is the one you
 * feel: a bare substring test makes "car" hit "carbon", and with no ranking at
 * all the best match for "charge" was whichever charge-related setting happened
 * to be added to the list first. Ranking is most of what makes a search feel
 * like it understands the question.
 *
 * Every token must still match something ([tokens] are ANDed) -- narrowing by
 * adding a word is the one behaviour people rely on. What changed is WHERE a
 * token matched now counts: the title outranks the keywords, the start of a
 * word outranks the middle of one, and shorter titles win ties, so "charge
 * limit" beats "charge limit notification threshold" for the query "charge
 * limit".
 */
internal fun searchScore(tokens: List<String>, e: SearchEntry, fuzzy: Boolean): Int? {
    val title = e.titleLowercase  // Use memoized lowercase title instead of recomputing
    var total = 0
    for (t in tokens) {
        // Best hit across the token and its synonyms. A synonym that lands is
        // worth less than the literal word: someone who typed "haptic" meant
        // the haptics entry more certainly than someone who typed "vibrate".
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
    // Tie-break on brevity: among equally-matched entries the shortest title is
    // the most specific answer, not the least.
    return total * 100 - title.length
}


/** A vehicle command recognised in a free-form search query. [cmd]/[climateTarget]
 *  map directly onto [com.bloo.bluelink.data.VehicleCommandRunner]'s own command
 *  vocabulary, so search runs commands through the exact same path the Quick
 *  Settings tiles use. */
internal class ParsedVehicleCommand(val cmd: String, val climateTarget: String = "default", val label: String)


/** Recognises a small, deliberately-conservative set of command phrasings --
 *  lock/unlock, start/stop/smart climate, start/stop charging -- rather than
 *  attempting general natural-language command parsing. Order matters:
 *  "unlock" is checked before the bare "lock" pattern so "unlock" doesn't
 *  also match as "lock".
 *
 *  Direction is encoded IN the command itself, never left for the runner to
 *  re-derive from the last-known snapshot. When the phrasing says start / stop
 *  / turn on / turn off / begin, we emit the explicit directional token
 *  (`climate_on`/`climate_off`, `charge_on`/`charge_off`) so the runner forces
 *  that direction. Before this, both "start climate" and "stop climate"
 *  collapsed to the bare `"climate"` toggle and the runner flipped against the
 *  snapshot -- so "stop the climate" while climate was already off would
 *  *start* it on the real car. The bare toggle tokens ("climate"/"charge") are
 *  reserved for genuinely ambiguous phrasing (none currently produced here). */
/**
 * The temperature asked for, in Fahrenheit, or null if the query names none.
 *
 * Superlatives resolve to the ends of [CLIMATE_TEMP_RANGE_F], which is the
 * honest reading of "coldest" -- it means the coldest the car will accept, not
 * absolute zero, and the range is the same one the climate slider offers.
 *
 * A BARE number is deliberately not a temperature. "Ioniq 5", "Model 3" and
 * "EV6 GT" all put digits in a query that is naming a car, so a number only
 * counts when a preposition introduces it ("at 64", "to 64") or a unit follows
 * it ("64 degrees", "64F"). Getting this wrong would start climate at 5 degrees
 * because the car is called an Ioniq 5.
 */
internal fun parseClimateTemperature(q: String, metric: Boolean): Int? {
    if (RxColdest.containsMatchIn(q)) {
        return CLIMATE_TEMP_RANGE_F.first
    }
    if (RxWarmest.containsMatchIn(q)) {
        return CLIMATE_TEMP_RANGE_F.last
    }
    val m = RxTempAtTo.find(q)
        ?: RxTempDegrees.find(q)
        ?: return null
    val n = m.groupValues[1].toIntOrNull() ?: return null
    val unit = m.groupValues.drop(2).firstOrNull { it.isNotBlank() }
    val f = when {
        unit == "c" -> ambientFahrenheit(n.toDouble())
        unit == "f" -> n
        // No unit given: believe the user's own setting rather than assuming
        // Fahrenheit. "start climate at 20" from someone on metric means 20C.
        metric -> ambientFahrenheit(n.toDouble())
        else -> n
    }
    return f.coerceIn(CLIMATE_TEMP_RANGE_F.first, CLIMATE_TEMP_RANGE_F.last)
}
