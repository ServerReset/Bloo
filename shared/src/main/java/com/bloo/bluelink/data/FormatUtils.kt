package com.bloo.bluelink.data

import java.util.TimeZone
import kotlin.math.roundToInt

/** "Springfield, IL" (locality/subAdminArea + adminArea) from a reverse-
 *  geocode result, falling back to the raw first address line. Was defined
 *  separately on phone and watch; the watch's copy was missing the
 *  `.distinct()` phone's had, so it could render "Springfield, Springfield"
 *  when locality == adminArea. */
fun formatPlaceName(a: android.location.Address): String? =
    listOfNotNull(a.locality ?: a.subAdminArea, a.adminArea)
        .distinct()
        .joinToString(", ")
        .ifBlank { a.getAddressLine(0) }

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
fun degLabel(valueF: String, fahrenheit: Boolean): String {
    // The API always reports temperatures as °F strings (even for cars whose
    // owner prefers metric), so this is the one conversion point: a
    // non-numeric string (unexpected/blank value) passes through unconverted
    // with just a bare degree sign appended, rather than crashing or hiding
    // the raw value entirely.
    val f = valueF.toDoubleOrNull() ?: return "$valueF°"
    return if (fahrenheit) "${f.toLong()}°F" else "${((f - 32) * 5 / 9.0).toLong()}°C"
}

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
 *  is the one conversion point for it; the result is truncated (`.toInt()`,
 *  not rounded) in both branches. */
fun weatherTemp(tempC: Double, fahrenheit: Boolean): String =
    if (fahrenheit) "${(tempC * 9 / 5 + 32).toInt()}°F" else "${tempC.toInt()}°C"

/** Format a distance in miles as "mi" or "km" based on the unit system. The
 *  API's distance figures are always miles, so metric users get a multiply
 *  by the fixed 1 mi = 1.609 km conversion factor and a re-labelled unit;
 *  imperial users get the raw value straight through. Both branches truncate
 *  to a whole number via `.toInt()`. */
fun formatDistance(mi: Number, metric: Boolean): String =
    if (metric) "${(mi.toDouble() * 1.609).toInt()} km" else "${mi.toInt()} mi"

/** Format speed in km/h based on the unit system. Note the input unit here is
 *  km/h (unlike [formatDistance]'s miles input) — imperial mode divides by
 *  1.609 to convert back down to mph, metric mode passes the value straight
 *  through with just a re-labelled unit. */
fun formatSpeed(kph: Double, metric: Boolean): String =
    if (metric) "${kph.toInt()} km/h" else "${(kph / 1.609).toInt()} mph"

/** Format trip distance in miles to the user's preferred unit. Same
 *  mi-to-km conversion factor as [formatDistance], but keeps one decimal
 *  place (`%.1f`) instead of truncating to a whole number — trip distances
 *  are often short enough that whole-number rounding would lose useful
 *  precision. */
fun formatTripDistance(mi: Double, metric: Boolean): String =
    if (metric) "%.1f km".format(mi * 1.609) else "%.1f mi".format(mi)

/** Format efficiency (miles per kWh or km per kWh). Converts the distance
 *  component to km first when metric (same 1.609 factor as the other
 *  distance formatters here) before dividing by the energy used, so the
 *  ratio itself — not just the label — is correct for the selected unit
 *  system, not just a relabelled imperial figure. */
fun formatEfficiency(mi: Double, kwh: Double, metric: Boolean): String {
    val eff = if (metric) (mi * 1.609) / kwh else mi / kwh
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
