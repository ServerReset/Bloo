package com.bloo.bluelink.data

import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * A resolved reverse-geocode result, in two lengths:
 *  - [full] -- "123 Main St, San Jose", the existing human-readable form every phone/watch
 *    surface already shows.
 *  - [compact] -- "123 Main St, 95112" (street + ZIP instead of city), for the cover screen's
 *    own space-constrained surfaces, where [full] (especially with a car-name suffix appended
 *    beside it) reliably wrapped onto two lines inside a narrow pill -- a real reported "looks
 *    bad, it's two layers" bug. A ZIP is usually shorter than a city name and never itself
 *    contains a space, so it is far less likely to push the line over.
 */
data class GeocodedPlace(val full: String, val compact: String)

/** "123 Main St, San Jose" -- a real street address, not just the city --
 *  built from a reverse-geocode result's house number + street name
 *  (subThoroughfare + thoroughfare) plus locality, falling back to
 *  "Springfield, IL" (locality/subAdminArea + adminArea) when the geocoder
 *  didn't return street-level detail for this fix, and to the raw first
 *  address line if even that's blank.
 *
 *  Was "Springfield, IL"-only for every caller (this is what the phone's
 *  Location/Info pebbles and the watch's Location card showed even when a
 *  full street address was available), while the widget separately built
 *  its own street-address string inline instead of sharing this function --
 *  the same "identical logic drifts when duplicated" trap this file's other
 *  helpers already got extracted to fix elsewhere. One implementation now,
 *  and it's the more useful of the two.
 *
 *  Was also separately defined on phone and watch before that; the watch's
 *  copy was missing the `.distinct()` the phone's had, so it could render
 *  "Springfield, Springfield" when locality == adminArea. */
fun formatPlaceName(a: android.location.Address): GeocodedPlace? {
    val street = listOfNotNull(
        a.subThoroughfare?.takeIf { it.isNotBlank() },
        a.thoroughfare?.takeIf { it.isNotBlank() },
    ).joinToString(" ").takeIf { it.isNotBlank() }
    val locality = a.locality ?: a.subAdminArea
    val parts = if (street != null) listOfNotNull(street, locality) else listOfNotNull(locality, a.adminArea)
    val full = parts.distinct().joinToString(", ").ifBlank { a.getAddressLine(0) } ?: return null
    // Street + ZIP when both exist; otherwise just reuse `full` -- a geocode result with no
    // street-level detail or no postal code has nothing more compact to offer than the long
    // form already is.
    val zip = a.postalCode?.takeIf { it.isNotBlank() }
    val compact = if (street != null && zip != null) "$street, $zip" else full
    return GeocodedPlace(full, compact)
}

/** "1h 20m" / "45 min" duration formatter, shared across phone and watch.
 *  Mechanism: integer-divides by 60 to get whole hours and takes the
 *  remainder as leftover minutes; below 60 it skips the hours part entirely
 *  and just prints "X min". */
fun fmtMinutes(min: Int): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "$min min"

/** °C -> whole-degree °F, for turning a live weather reading into the
 *  [ambientF] input smart-climate calculations branch on. Was reimplemented
 *  inline at 7 call sites (phone's ClimatePebble x4, TileCommandRunner, the
 *  watch's smartClimate() and its HomeScreen preview) -- 3 of them truncated
 *  (`.toInt()`) while the other 4 rounded (`.roundToInt()`), so the exact
 *  same live weather reading near a whole-degree boundary could resolve to a
 *  different ambientF, and therefore a different smart-climate target and a
 *  different "Cool"/"Heat" label, depending on which of these entry points
 *  you happened to use. */
fun ambientFahrenheit(tempC: Double): Int = (tempC * 9.0 / 5.0 + 32.0).roundToInt()

/** The Fahrenheit range every supported car's climate target temperature can
 *  actually be set to. Was hardcoded as (60, 85) in six different places
 *  (phone's ClimatePebble x4, TileCommandRunner, the watch's smart-climate
 *  calc and its HomeScreen preview) while the temperature slider itself --
 *  the one thing actually driven by what the car will accept -- used 62..82.
 *  A "smart" target clamped to the wrong, wider range could still ask the
 *  car for something outside what it supports. */
val CLIMATE_TEMP_RANGE_F = 62..82

/**
 * A one-tap "smart" target temperature for the given outside [ambientF],
 * always clamped into [CLIMATE_TEMP_RANGE_F]. Moderate weather (70-89F warm,
 * 41-69F cool) runs a gentle 10F off ambient, same as before; genuinely
 * extreme weather (90F+ or 40F and below) goes straight for the most
 * aggressive setting the car allows instead of a flat offset -- clamping a
 * flat "ambient - 10" into the range on a truly hot day lands at the
 * range's WARM end (e.g. 100F - 10 = 90, clamped up to 82, the LEAST
 * aggressive cooling setting available), the opposite of what "smart"
 * cooling should do on an extreme day.
 */
fun smartClimateTargetF(ambientF: Int): Int {
    val min = CLIMATE_TEMP_RANGE_F.first
    val max = CLIMATE_TEMP_RANGE_F.last
    // Mechanism: reads the range's own min/max rather than hardcoding them, so
    // this stays correct automatically if CLIMATE_TEMP_RANGE_F above is ever
    // changed. The four branches are checked top-to-bottom in order of
    // "how extreme is it", so a truly hot or cold reading short-circuits
    // straight to the most aggressive setting before the milder +/-10 offset
    // branches below even get a chance to run.
    return when {
        ambientF >= 90 -> min // Really hot: max cold.
        ambientF >= 70 -> (ambientF - 10).coerceIn(min, max)
        ambientF <= 40 -> max // Really cold: max hot.
        else -> (ambientF + 10).coerceIn(min, max)
    }
}

/** Whether [smartClimateTargetF] would be cooling (rather than heating) for the
 *  given outside [ambientF] — used by callers to pick the "Cool"/"Heat" label
 *  without recomputing the target. Matches the cool/heat partition in
 *  [smartClimateTargetF]: 70F and up is cooling, below is heating. */
fun smartClimateIsCooling(ambientF: Int): Boolean = ambientF >= 70

/** The valid range (in minutes) for a SINGLE remote-start climate command's
 *  duration -- the vendor API itself rejects/clamps anything past 10 minutes
 *  per command. The default within this range stays its own constant,
 *  [DEFAULT_CLIMATE_DURATION_MIN]. */
val CLIMATE_DURATION_RANGE: IntRange = 1..10

/** The UI-facing range for the "Run time" picker. Wider than
 *  [CLIMATE_DURATION_RANGE] on purpose: a request past the single-command cap
 *  is auto-chained into multiple commands (see [climateChunks] and
 *  ClimateExtendWorker on the phone) -- the first chunk fires immediately and
 *  each following chunk is scheduled to fire the moment the previous one's
 *  duration elapses, so the car's climate never actually turns off in
 *  between. 20 min is a practical ceiling, not an API limit: a real remote
 *  climate run this long is already unusual, and each extra chunk is another
 *  scheduled background command that can fail/drift, so this doesn't try to
 *  support arbitrarily long runs. */
val CLIMATE_EXTENDED_DURATION_RANGE: IntRange = 1..20

/**
 * Splits a requested total climate-run [minutes] into the sequence of
 * per-command durations needed to cover it, each within
 * [CLIMATE_DURATION_RANGE] -- e.g. 13 -> [10, 3], 25 -> [10, 10, 5],
 * 7 -> [7]. The first element is the chunk to send immediately; every
 * element after it is a follow-up to schedule once the chunk before it
 * elapses. [minutes] is clamped to at least 1 (a 0- or negative-minute
 * request would otherwise produce an empty list with nothing to send at
 * all).
 */
fun climateChunks(minutes: Int): List<Int> {
    val max = CLIMATE_DURATION_RANGE.last
    var remaining = minutes.coerceAtLeast(1)
    val chunks = mutableListOf<Int>()
    while (remaining > 0) {
        val chunk = remaining.coerceAtMost(max)
        chunks += chunk
        remaining -= chunk
    }
    return chunks
}

/** The valid range for a car's AC/DC charge-limit percentage sliders. Was
 *  duplicated as a literal `50..100` at 5 call sites (phone's ChargePebble
 *  slider, the watch's setAcLimit/setDcLimit clamps and its two SliderRows)
 *  -- still consistent everywhere today, but the exact same shape as the
 *  climate-range bug: nothing forced them to stay that way. */
val CHARGE_LIMIT_RANGE = 50..100

/** How long a vehicle's cached status is trusted before it's treated as
 *  stale (worth nudging the user to pull-to-refresh). Three different
 *  hardcoded values for this same concept had accumulated: AppViewModel's
 *  own auto-refresh check used 10 minutes, the phone UI's "pull to refresh"
 *  hint used 15, and the watch's home screen used 30 -- with no indication
 *  any of the differences were deliberate. Picked 15 minutes (the phone UI's
 *  existing value, the one actually user-facing as copy) as the one
 *  reasonable default for all three. */
val STALE_STATUS_MS = 15L * 60 * 1000L

/** How long an available update is snoozed for after "Remind me" / "Not
 *  now" -- was defined identically (byte-for-byte the same math) in
 *  UpdateChecker.kt (phone) and WearViewModel.kt (watch) instead of once
 *  here. */
val UPDATE_SNOOZE_MS = 3L * 24 * 60 * 60 * 1000L

/** The climate request used when nothing else is configured -- no saved
 *  preset, no smart-climate weather data, just "turn it on." Was
 *  independently typed in at 6 call sites (TileCommandRunner, AppViewModel's
 *  shortcut handling, the phone UI's slider initial state, WearCommand's and
 *  WearViewModel's ClimateDraft's wire/default values) -- still consistent
 *  everywhere today, but exactly the same "hand-copied constant" shape as
 *  the climate-range bug. */
const val DEFAULT_CLIMATE_TEMP_F = 72
const val DEFAULT_CLIMATE_DURATION_MIN = 10

/** The charge-limit targets used until a car's real targets load in: 80% for AC
 *  (a daily home/level-2 ceiling), 90% for DC (fast-charging past that is
 *  inefficient). One conceptual pair, previously typed as bare 80/90 literals at
 *  four sites -- WearCommand's wire defaults, applyChargeLimits' and LimitsCard's
 *  `?: 80/90` fallbacks on the watch, and ChargePebble's seed on the phone -- the
 *  same hand-copied-constant shape that already caused a real drift bug (the phone
 *  once defaulted BOTH to 80%, so a "Set" before the DC target loaded pushed it
 *  low). Since both pills send both values together, the two halves must agree. */
const val DEFAULT_AC_CHARGE_LIMIT_PCT = 80
const val DEFAULT_DC_CHARGE_LIMIT_PCT = 90

/** Charger-plug type label for [EvStatus.batteryPlugin]. Was defined
 *  separately on phone and watch and had already drifted ("AC (level 2)" vs
 *  "AC") despite mapping the exact same wire value. */
fun chargerLabel(plugin: Int?): String? = when (plugin) {
    1 -> "DC fast"
    2 -> "AC (level 2)"
    else -> null
}

/** "2026-06-01 18:22:31.0" / "2026-06-01T18:22:31" -> "Mon Jun 1 · 6:22 PM"
 *  (falls back to a trimmed raw string). Was defined separately on phone and
 *  watch; the watch's version was fixed to try both a 'T' and a plain-space
 *  separator (the feed has been observed with both) after the phone's
 *  space-only version silently fell back to raw text on a 'T'-separated
 *  timestamp -- consolidated on the more robust dual-pattern parse.
 *  [includeWeekday] false drops the leading "EEE " for narrower displays. */
fun tripDate(raw: String?, includeWeekday: Boolean = true): String {
    if (raw.isNullOrBlank()) return "Trip"
    // Drop any fractional seconds - the feed's precision varies (".0" vs ".000000").
    val trimmed = raw.substringBefore('.').trim()
    val outFormat = java.text.SimpleDateFormat(
        if (includeWeekday) "EEE MMM d · h:mm a" else "MMM d · h:mm a",
        java.util.Locale.US,
    )
    // Mechanism: tries each known raw-timestamp shape in turn (ISO-8601 with a
    // 'T' separator, then the plain-space variant) and returns as soon as one
    // successfully parses; runCatching swallows the ParseException from a
    // pattern that doesn't match so the loop can just move on to the next one.
    for (pattern in arrayOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss")) {
        val parsed = runCatching {
            java.text.SimpleDateFormat(pattern, java.util.Locale.US).parse(trimmed)
        }.getOrNull()
        if (parsed != null) return outFormat.format(parsed)
    }
    // Neither pattern matched (unexpected feed shape): fall back to a
    // best-effort raw rendering — truncate to a sane length and swap any 'T'
    // for a space so it at least reads like a normal date/time instead of raw ISO.
    return trimmed.take(16).replace('T', ' ')
}

/** Mask an email for diagnostics (AppLog is in-memory/copyable in the app's
 *  own log viewer, not a place account addresses should appear in full) --
 *  "j***@gmail.com" instead of "jane.doe@gmail.com". */
fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    // indexOf returns -1 if there's no '@' at all, and 0 would mean the address
    // starts with '@' (no local part to keep) — both are malformed input, so
    // both get the same fully-redacted fallback rather than crashing on
    // substring math with a bad index.
    if (at <= 0) return "***"
    // Keep just the first character of the local part plus the whole domain
    // (everything from '@' onward), replacing the rest of the local part with
    // a fixed "***" regardless of its original length.
    return "${email.first()}***${email.substring(at)}"
}

/** Current GMT offset in whole hours (e.g. -5 EST, -4 EDT) -- both brand API
 *  clients send this as an auth header; kept in one place so a future fix
 *  (e.g. rounding for negative sub-hour offsets) can't apply to one and not
 *  the other. */
fun gmtOffsetHours(): String {
    // getOffset(now) accounts for DST automatically (returns the raw offset
    // plus any active DST adjustment for the current instant), then dividing
    // the millisecond offset by the number of ms in an hour truncates to a
    // whole number of hours (integer division), which is the granularity both
    // brands' auth headers expect.
    val offsetMs = TimeZone.getDefault().getOffset(System.currentTimeMillis())
    return (offsetMs / 3_600_000).toString()
}

/** "just now" / "x min ago" / "x hr ago" for a wall-clock timestamp in ms. */
fun relativeLabel(ms: Long?): String {
    // null or <= 0 means "no timestamp recorded yet" (e.g. a snapshot that's
    // never been fetched) -- there's nothing meaningful to show, so return
    // blank rather than a nonsensical "just now"/negative duration.
    if (ms == null || ms <= 0) return ""
    val d = System.currentTimeMillis() - ms
    // Each branch's divisor converts the elapsed-ms delta into the largest
    // whole unit that still fits (minutes under an hour, hours under a day,
    // otherwise days); integer division truncates rather than rounds.
    return when {
        d < 60_000 -> "just now"
        d < 3_600_000 -> "${d / 60_000} min ago"
        d < 86_400_000 -> "${d / 3_600_000} hr ago"
        else -> "${d / 86_400_000} day${if (d / 86_400_000 != 1L) "s" else ""} ago"
    }
}

/**
 * A climate setpoint (the API reports it as a °F string) rendered in the user's
 * chosen unit. Non-numeric values pass through with a bare degree sign.
 */
fun degLabel(valueF: String, fahrenheit: Boolean, sourceUnit: Int? = null): String {
    // A non-numeric string (unexpected/blank value) passes through unconverted with
    // just a bare degree sign appended, rather than crashing or hiding the raw value.
    val raw = valueF.toDoubleOrNull() ?: return "$valueF°"

    // [sourceUnit] is the API's own unit code for THIS value, and reading it is what
    // stops a Celsius car being converted as if it were Fahrenheit. This used to
    // assume every reading was °F, which is true of the US backends but not of
    // Canada: a car sitting at 22.5°C was run through the °F formula and displayed
    // as (22.5 - 32) * 5/9 = -5°C. Reported from a real device.
    //
    // 0 = Celsius, 1 = Fahrenheit, matching every send path in this app --
    // BlueLinkApi and KiaUsaApi both post unit 1 alongside a °F value, CanadaApi
    // posts unit 0 alongside a Celsius index. A null or unrecognised code keeps the
    // old assumption, so nothing that was reading correctly changes.
    val sourceIsCelsius = sourceUnit == 0
    val valueIsAlreadyTarget = sourceIsCelsius != fahrenheit

    // A Celsius value shown in Celsius keeps its fraction, and ONLY that case does.
    // Canada's setpoint table is in half degrees, so 22.5 is the common reading
    // there and rounding it to "23" throws away precision the car actually sent.
    //
    // Fahrenheit stays whole, deliberately: [degValue]'s own doc records that a
    // fractional °F reading displaying as "71°F" was a bug worth fixing, the UI
    // sets °F in whole degrees everywhere, and a first cut of this that preserved
    // fractions on BOTH axes turned "71.6" into "71.6°F" -- caught by the existing
    // test, which is exactly what it was there for.
    if (valueIsAlreadyTarget) {
        if (fahrenheit) return "${raw.roundToInt()}°F"
        return "${trimTrailingZero(raw)}°C"
    }
    val converted = if (fahrenheit) raw * 9 / 5.0 + 32 else (raw - 32) * 5 / 9.0
    return "${converted.roundToInt()}°${if (fahrenheit) "F" else "C"}"
}

/** "22.5" stays "22.5"; "22.0" becomes "22". Keeps a half-degree setpoint honest
 *  without printing a pointless ".0" on every whole one. */
private fun trimTrailingZero(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

/**
 * A °F reading as a whole number in the user's chosen unit, rounded rather than
 * truncated.
 *
 * The rounding is the point of having this. Every conversion written inline
 * around the app already rounds -- the climate slider both ways, the preset
 * summary, the widget config preview, the watch's swing label -- and the two
 * SHARED helpers every surface routes through, [degLabel] and [weatherTemp], were
 * the only two that truncated. So the functions one place could fix were the ones
 * getting it wrong, while the scattered copies were right.
 *
 * Truncating biases every label downward by up to a full degree, and it bit
 * hardest exactly where it is least visible: °F values rarely land on whole °C, so
 * a metric user saw almost every setpoint a degree cold. 75°F is 23.9°C, which
 * truncated to "23°C".
 *
 * Takes Double rather than Int because the API sends these as strings and some
 * regions send fractions -- Canada reports fractional °F, which truncated in the
 * Fahrenheit branch too, so "71.6" displayed as "71°F".
 */
fun degValue(valueF: Double, fahrenheit: Boolean): Int =
    if (fahrenheit) valueF.roundToInt() else ((valueF - 32) * 5 / 9.0).roundToInt()

/**
 * Whether temperatures render in Fahrenheit, from a unit-system string. Imperial is
 * the default for a null or unrecognised value, matching every other reader of this
 * setting.
 *
 * One rule, because there were two. The phone derived it as
 * `unitSystem != "metric"`. The watch derived it as
 * `localUnitSystem != "metric" || phonePayload?.useFahrenheit != false` -- an OR, so
 * Celsius required the watch to be metric AND the phone to have pushed
 * `useFahrenheit == false`. Two consequences, both visible on one screen:
 *
 *  - A watch set to Metric while the phone stayed imperial showed distances in km
 *    (those read the watch's own unit system) and temperatures in °F. Same screen,
 *    two measurement systems.
 *  - A watch that had never been paired had no payload at all, so
 *    `null != false` was true and it showed °F however the user had set it.
 *
 * The watch now derives temperature from the same watch-local unit system its seven
 * distance and speed readouts already use, which is also what its own Units setting
 * writes. That makes units a per-device choice consistently, rather than per-device
 * for distance and jointly-negotiated for temperature.
 */
fun useFahrenheit(unitSystem: String?): Boolean = (unitSystem ?: "imperial") != "metric"

/** How urgent a charge or fuel level is, independent of any surface's palette.
 *  See [chargeTier]. */
enum class ChargeTier { UNKNOWN, CHARGING, CRITICAL, LOW, NORMAL }

/** At or below this percentage a level is [ChargeTier.CRITICAL]. */
const val CHARGE_CRITICAL_PCT = 15

/** At or below this percentage (and above [CHARGE_CRITICAL_PCT]) a level is
 *  [ChargeTier.LOW]. */
const val CHARGE_LOW_PCT = 30

/**
 * Which band a charge/fuel level falls in. Charging outranks the level itself, and a
 * null [percent] is [ChargeTier.UNKNOWN] rather than being folded into a band.
 *
 * The three gauges in this app each had their own copy of this and no two agreed:
 *
 *  - the widget's ring used `<= 0.15` / `<= 0.30`
 *  - the watch's tile used `< 15` / `< 30`
 *  - the watch's home ring had only `< 15` and NO amber band at all
 *
 * So a car at exactly 15% was red on the widget and not on either watch surface, and
 * a car at 20% was amber on the watch's tile while its home screen showed the same
 * car in the ordinary accent colour. The bands are inclusive here, which is the
 * widget's reading and the safer one: "15% or less" flags at the number a user would
 * expect it to.
 *
 * Only the bands are shared. Each surface maps them to its own colour system --
 * Compose theme roles on the watch, ProtoLayout roles on its tile, packed ARGB ints
 * in the widget -- which is the part that legitimately differs, including what
 * UNKNOWN should look like.
 */
fun chargeTier(percent: Int?, charging: Boolean): ChargeTier = when {
    charging -> ChargeTier.CHARGING
    percent == null -> ChargeTier.UNKNOWN
    percent <= CHARGE_CRITICAL_PCT -> ChargeTier.CRITICAL
    percent <= CHARGE_LOW_PCT -> ChargeTier.LOW
    else -> ChargeTier.NORMAL
}

/**
 * The name to show for a car: its nickname, else its model, else the tail of its
 * identifier -- with every step guarding against BLANK, not just null.
 *
 * That guard is the reason this exists. All three API parsers had their own copy of
 * this fallback chain and only BlueLinkApi's checked for blankness. The other two
 * used `?:` alone, and their JSON accessor filters the literal string "null" but
 * passes an empty string straight through -- so a Kia US or Canada account whose car
 * has a nickname set to "" got a car named "", on every surface at once: the phone
 * header, the widget, the tile, the complication and its notifications. Hyundai and
 * Genesis US were fine, which is why it could sit there.
 *
 * The last resort also can't return blank, unlike the copies it replaces: an empty
 * identifier used to fall through to `"".takeLast(6)`, i.e. nothing at all.
 */
fun vehicleDisplayName(nickName: String?, modelName: String?, id: String): String =
    nickName?.takeIf { it.isNotBlank() }
        ?: modelName?.takeIf { it.isNotBlank() }
        ?: id.takeLast(6).ifBlank { "Car" }

/** Human-readable label for a WMO weather code integer. Mechanism: WMO codes
 *  group many numerically-adjacent values under one user-facing label (e.g.
 *  71/73/75/77/85/86 are all "Snow" of varying intensity/type), so each branch
 *  lists every code in that group; anything not covered by a listed group
 *  falls through to the "—" placeholder rather than guessing. */
fun weatherLabel(code: Int): String = when (code) {
    0 -> "Clear"
    1, 2 -> "Partly cloudy"
    3 -> "Cloudy"
    45, 48 -> "Fog"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 65, 66, 67 -> "Rain"
    71, 73, 75, 77, 85, 86 -> "Snow"
    80, 81, 82 -> "Showers"
    95, 96, 99 -> "Thunderstorm"
    else -> "—"
}

/** Formats a Celsius temperature as °F or °C based on the user preference.
 *  Weather data (unlike car climate data) always arrives as Celsius, so this
 *  is the one conversion point for it.
 *
 *  Rounds, in both branches. It used to truncate, and the KDoc here recorded that
 *  as though it were a decision -- "the result is truncated (`.toInt()`, not
 *  rounded)" -- without ever saying why, while every other temperature conversion
 *  in the app rounded. Open-Meteo reports decimals, so truncating made every
 *  reading on the watch (then its only caller -- the phone now reaches this same
 *  helper too, via WeatherApi.Weather.tempLabel) up to a degree cold: 22.8°C showed
 *  as "22°C". Same fix and same reasoning as [degValue], which the car-side
 *  [degLabel] now shares.
 *
 *  Deliberately NOT routed through [degValue] by converting Celsius to Fahrenheit
 *  first. That would be algebraically identical and numerically not, which I
 *  checked rather than assumed: over -40..50°C there are nine half-degree inputs
 *  where the round trip lands on the wrong side of the tie, because °C -> °F -> °C
 *  is not exact in binary floating point. 24.5 comes back as 24.499999999999996 and
 *  rounds to 24 instead of 25; -4.5 comes back as -4.500000000000001 and rounds to
 *  -5 instead of -4. The metric branch rounds the Celsius it was actually given. */
fun weatherTemp(tempC: Double, fahrenheit: Boolean): String =
    if (fahrenheit) "${(tempC * 9 / 5 + 32).roundToInt()}°F" else "${tempC.roundToInt()}°C"

/**
 * The one mile/kilometre conversion factor, exact.
 *
 * There were two: this file used `* 1.609` in four places while CanadaApi's kmToMi
 * used `* 0.621371`. Those are not reciprocals -- 1 / 0.621371 = 1.609344 -- so a
 * Canadian metric user's value round-tripped lossily through the API boundary and
 * back to the screen: 263 km arrived as 163.42 mi, rendered as 262 km. Dividing by
 * this instead of multiplying by a second constant makes the two directions exact
 * inverses by construction rather than by whoever typed the most digits.
 */
const val KM_PER_MI = 1.609344

/** Format a distance in miles as "mi" or "km" based on the unit system. The
 *  API's distance figures are always miles, so metric users get a multiply
 *  by [KM_PER_MI] and a re-labelled unit; imperial users get the raw value.
 *
 *  Metric ROUNDS rather than truncates. Truncating compounded with the constant
 *  mismatch above to turn a 263 km range into "262 km"; with an exact factor a
 *  half-kilometre of truncation is still the difference between 262.94 and 263. */
fun formatDistance(mi: Number, metric: Boolean): String =
    if (metric) "${(mi.toDouble() * KM_PER_MI).roundToInt()} km" else "${mi.toInt()} mi"

/** Format speed in km/h based on the unit system. Note the input unit here is
 *  km/h (unlike [formatDistance]'s miles input) — imperial mode divides by
 *  1.609 to convert back down to mph, metric mode passes the value straight
 *  through with just a re-labelled unit. */
fun formatSpeed(kph: Double, metric: Boolean): String =
    if (metric) "${kph.toInt()} km/h" else "${(kph / KM_PER_MI).toInt()} mph"

/**
 * Format a speed whose input is MILES per hour, unlike [formatSpeed]'s km/h.
 *
 * Both exist because the two speed sources genuinely differ in unit, and having
 * only the km/h one meant the mph source was silently run through the wrong
 * conversion: EvTrip's avgspeed/maxspeed are mph (its own KDoc says so, and its
 * sibling `distance` in the same payload is treated as miles by both the phone and
 * the watch), so `formatSpeed(62.0, metric = false)` rendered 62 mph as "38 mph"
 * and metric rendered it as "62 km/h" instead of ~100. Wrong in both modes.
 *
 * Named for its input unit rather than overloading, so a call site cannot pick the
 * wrong one by accident the way an overload set invites.
 */
fun formatSpeedMph(mph: Double, metric: Boolean): String =
    if (metric) "${(mph * KM_PER_MI).roundToInt()} km/h" else "${mph.toInt()} mph"

/** Format trip distance in miles to the user's preferred unit. Same
 *  mi-to-km conversion factor as [formatDistance], but keeps one decimal
 *  place (`%.1f`) instead of truncating to a whole number — trip distances
 *  are often short enough that whole-number rounding would lose useful
 *  precision. */
fun formatTripDistance(mi: Double, metric: Boolean): String =
    if (metric) "%.1f km".format(mi * KM_PER_MI) else "%.1f mi".format(mi)

/** Format efficiency (miles per kWh or km per kWh). Converts the distance
 *  component to km first when metric (same 1.609 factor as the other
 *  distance formatters here) before dividing by the energy used, so the
 *  ratio itself — not just the label — is correct for the selected unit
 *  system, not just a relabelled imperial figure. */
fun formatEfficiency(mi: Double, kwh: Double, metric: Boolean): String {
    val eff = if (metric) (mi * KM_PER_MI) / kwh else mi / kwh
    return "${"%.1f".format(eff)} ${if (metric) "km" else "mi"}/kWh"
}

/**
 * The canonical "what's this car doing right now" label, in priority order
 * (driving beats charging beats climate beats lock state). Every surface that
 * shows a one-line vehicle state — the phone widget, phone Quick Settings
 * tiles, the wear tile, and wear complications — used to reimplement this same
 * priority chain independently, and they'd drifted slightly out of sync with
 * each other. Colors stay local to each surface since phone/wear use
 * different color systems (a Compose theme vs. Wear ProtoLayout roles), but
 * the label — and the priority order that decides which state "wins" when
 * several are true at once — is exactly the kind of logic that should only
 * exist in one place.
 */
fun vehicleStateLabel(
    engineOn: Boolean?,
    charging: Boolean?,
    climateOn: Boolean?,
    locked: Boolean?,
): String = when {
    // `when` evaluates branches top-to-bottom and stops at the first true
    // condition, which is what actually encodes the priority order described
    // above: a car that is both driving and charging (e.g. towing while
    // plugged in) reports "Driving" because that branch is checked first.
    // Every `== true` check treats a null (unknown/not-yet-fetched) state the
    // same as false, so an unknown value never wins a branch by accident.
    engineOn == true  -> "Driving"
    charging == true  -> "Charging"
    climateOn == true -> "Climate on"
    locked == true    -> "Locked"
    locked == false   -> "Unlocked"
    else              -> "—" // locked itself unknown (null) and nothing else applies.
}

/** Raw signed miles remaining until the next scheduled service: the next-due
 *  odometer reading ([lastServiceMiles] + [intervalMiles]) minus the current
 *  [odometerMiles]. Returns null if any input is null. The value is intentionally
 *  left signed (negative once service is overdue) — callers apply their own
 *  coerceAtLeast(0) / >= comparisons depending on how they present it. */
fun serviceDue(odometerMiles: Int?, lastServiceMiles: Int?, intervalMiles: Int?): Int? {
    if (odometerMiles == null || lastServiceMiles == null || intervalMiles == null) return null
    return nextServiceMiles(lastServiceMiles, intervalMiles) - odometerMiles
}

/**
 * The ABSOLUTE odometer reading a service falls due at.
 *
 * Trivial arithmetic, and shared anyway for one reason: [serviceDue] is defined as
 * `nextServiceMiles - odometer`, so the relative countdown ("in N mi") and the absolute
 * figure ("at N mi") are two views of ONE number and must never disagree. The phone and the
 * watch each recomputed `last + interval` inline for the absolute view while calling the
 * shared helper for the relative one -- so the formula lived in three places and only two of
 * them were the shared one. [serviceDue] now routes through this, which is what makes the
 * agreement structural instead of coincidental.
 *
 * This file already records what happens otherwise: the widget's service field re-inlined
 * this arithmetic, and its own comment concludes "re-inlining a shared formatter is exactly
 * how that bug got in".
 */
fun nextServiceMiles(lastServiceMiles: Int, intervalMiles: Int): Int =
    lastServiceMiles + intervalMiles

/** Parse the API's odometer field (a possibly-comma-grouped, possibly-decimal
 *  string like "12,345.6") into whole miles, or null if blank/unparseable.
 *  Strips grouping commas and truncates any fractional part via toInt(). */
fun parseOdometerMiles(odometer: String?): Int? =
    odometer?.trim()?.takeIf { it.isNotBlank() }?.replace(",", "")?.toDoubleOrNull()?.toInt()

/**
 * Whether an app-lock should re-engage after [elapsedMs] in the background, given the user's
 * lock-timing setting as its wire key ("off" / "immediate" / "1min" / "5min" / "10min").
 *
 * The phone (biometric) and watch (PIN) each ran this exact rule with the same 60_000 /
 * 300_000 / 600_000 thresholds -- the phone off its LockTiming enum, the watch off the string
 * key it stores. One home keeps those magic numbers from drifting between the two lock flows.
 * The phone maps its enum via LockTiming.wireKey; the watch already holds the key. `else` maps
 * to "lock" as a fail-safe (an unrecognised key means re-lock rather than silently stay open),
 * matching the watch's prior branch -- though both callers only ever pass one of the five keys.
 */
fun shouldRelockAfter(elapsedMs: Long, timingKey: String): Boolean = when (timingKey) {
    "off" -> false
    "immediate" -> true
    "1min" -> elapsedMs >= 60_000L
    "5min" -> elapsedMs >= 300_000L
    "10min" -> elapsedMs >= 600_000L
    else -> true
}

/**
 * Which of [shown] page-indicator dots to light for item [index] out of [count] total.
 *
 * When there are no more items than dots the mapping is 1:1 (just clamped). When there are MORE
 * items than dots, [index]'s position in `[0, count-1]` is rescaled onto `[0, shown-1]` and
 * rounded to the nearest dot -- so the first item always lights the first dot and the last the
 * last, with the middle items sharing the dots between. Extracted from the watch's CurvedDots
 * so this off-by-one-prone integer mapping (the `count-1` divisor, the endpoint behaviour) can
 * be unit-tested; the composable owns only the drawing. Returns 0 for a degenerate
 * count/shown <= 1 (the caller draws nothing then anyway).
 */
fun activeDotIndex(count: Int, shown: Int, index: Int): Int {
    if (shown <= 1 || count <= 1) return 0
    if (count <= shown) return index.coerceIn(0, shown - 1)
    return ((index.toFloat() / (count - 1)) * (shown - 1)).roundToInt().coerceIn(0, shown - 1)
}
