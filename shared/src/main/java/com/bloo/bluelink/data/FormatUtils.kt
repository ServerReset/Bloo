package com.bloo.bluelink.data

import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * A resolved reverse-geocode result, in two lengths:
 *  - [full] -- "123 Main St, San Jose", the existing human-readable form every phone
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


/** "1h 20m" / "45 min" duration formatter, shared across the phone UI.
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


/**
 * Whether [stamp] is within [windowMs] before [now] -- the one correct way to ask
 * "did this happen recently enough to skip doing it again?".
 *
 * The obvious spelling, `now - stamp < windowMs`, is wrong in a way that does not
 * show up in testing: it is also true for every NEGATIVE difference, i.e. whenever
 * the stamp is in the future. And these stamps go into the future routinely. They
 * are wall-clock readings taken with System.currentTimeMillis() and then persisted,
 * so any backwards correction of the clock -- a watch that has drifted and re-synced
 * from its phone, a device whose date was set wrong and later fixed -- leaves stored
 * stamps ahead of the current time. Every throttle written the obvious way then
 * suppresses its own work for exactly as long as the skew lasts, silently, with
 * nothing on screen to explain it. Real instances found in this codebase: update
 * checks (so no update ever appeared) and weather refresh.
 *
 * Requiring the difference to be non-negative reads a future stamp as what it
 * actually is -- not a recent event but a broken one -- and the safe reading of a
 * broken throttle is to do the work. Half-open on purpose: a stamp exactly
 * [windowMs] old is due, matching how every caller's interval is documented.
 *
 * Note this is for "skip if recent" throttles. A staleness test spelled
 * `now - stamp > windowMs` already fails safe, since a future stamp reads as fresh
 * rather than hiding data, so those are deliberately left alone.
 */
fun withinWindow(now: Long, stamp: Long, windowMs: Long): Boolean =

    (now - stamp) in 0 until windowMs


/** The climate request used when nothing else is configured -- no saved
 *  preset, no smart-climate weather data, just "turn it on." Was
 *  independently typed in at 6 call sites (TileCommandRunner, AppViewModel's
 *  shortcut handling, the phone UI's slider initial state, CarCommand's and
 *  WearViewModel's ClimateDraft's wire/default values) -- still consistent
 *  everywhere today, but exactly the same "hand-copied constant" shape as
 *  the climate-range bug. */
const val DEFAULT_CLIMATE_TEMP_F = 72

const val DEFAULT_CLIMATE_DURATION_MIN = 10


/** The charge-limit targets used until a car's real targets load in: 80% for AC
 *  (a daily home/level-2 ceiling), 90% for DC (fast-charging past that is
 *  inefficient). One conceptual pair, previously typed as bare 80/90 literals at
 *  four sites -- CarCommand's wire defaults, applyChargeLimits' and LimitsCard's
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


// The three formatters tripDate needs, built once per thread instead of three
// times per call. tripDate renders the title of every row in the phone's trips
// list, so on a scroll it was constructing a SimpleDateFormat -- which parses its
// pattern and loads a full set of locale DateFormatSymbols -- several times for
// every item that came into view, on the device least able to afford it.
//
// ThreadLocal rather than a plain val because SimpleDateFormat is not thread-safe,
// and deliberately NOT java.time: DateTimeFormatter would be immutable and need no
// ThreadLocal, but it also parses strictly where SimpleDateFormat is lenient, and
// this parses a vendor feed whose exact shape is only known by observation (the
// two patterns below were themselves discovered that way). Swapping in a stricter
// parser to save a wrapper would risk silently falling back to raw text on real
// timestamps. Same pattern, and the same reasoning, as KiaUsaApi's rfc1123Format.
private val tripOutWithWeekday = ThreadLocal.withInitial {
    java.text.SimpleDateFormat("EEE MMM d · h:mm a", java.util.Locale.US)
}

private val tripOutNoWeekday = ThreadLocal.withInitial {
    java.text.SimpleDateFormat("MMM d · h:mm a", java.util.Locale.US)
}

private val tripParsers = ThreadLocal.withInitial {
    arrayOf(
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US),
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US),
    )
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
    val outFormat = if (includeWeekday) tripOutWithWeekday.get()!! else tripOutNoWeekday.get()!!
    // Mechanism: tries each known raw-timestamp shape in turn (ISO-8601 with a
    // 'T' separator, then the plain-space variant) and returns as soon as one
    // successfully parses; runCatching swallows the ParseException from a
    // pattern that doesn't match so the loop can just move on to the next one.
    for (parser in tripParsers.get()!!) {
        val parsed = runCatching { parser.parse(trimmed) }.getOrNull()
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
 * summary -- and the two
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
 * `unitSystem != "metric"`. The watch (since removed) derived it as
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
 * The watch derived temperature from the same watch-local unit system its seven
 * distance and speed readouts already used, which was also what its own Units setting
 * wrote -- a per-device choice consistently, rather than per-device
 * for distance and jointly-negotiated for temperature.
 */
fun useFahrenheit(unitSystem: String?): Boolean = (unitSystem ?: "imperial") != "metric"

/**
 * Temperature unit with its own override. [tempUnit] is "f", "c", or anything else (normally
 * "auto") to follow the main Units setting -- so someone can read temperatures in Celsius and
 * distances in miles, or the other way round, while everyone else still sets one switch.
 */
fun resolveFahrenheit(unitSystem: String?, tempUnit: String?): Boolean = when (tempUnit) {
    "f" -> true
    "c" -> false
    else -> useFahrenheit(unitSystem)
}

/** Distance (mileage, range, trips, speed) unit with its own override: "km", "mi", or follow Units. */
fun resolveMetricDistance(unitSystem: String?, distanceUnit: String?): Boolean = when (distanceUnit) {
    "km" -> true
    "mi" -> false
    else -> (unitSystem ?: "imperial") == "metric"
}
