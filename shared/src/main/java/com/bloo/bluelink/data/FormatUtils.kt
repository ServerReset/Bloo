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

/** "1h 20m" / "45 min" duration formatter, shared across phone and watch. */
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
    return when {
        ambientF >= 90 -> min // Really hot: max cold.
        ambientF >= 70 -> (ambientF - 10).coerceIn(min, max)
        ambientF <= 40 -> max // Really cold: max hot.
        else -> (ambientF + 10).coerceIn(min, max)
    }
}

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
    for (pattern in arrayOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss")) {
        val parsed = runCatching {
            java.text.SimpleDateFormat(pattern, java.util.Locale.US).parse(trimmed)
        }.getOrNull()
        if (parsed != null) return outFormat.format(parsed)
    }
    return trimmed.take(16).replace('T', ' ')
}

/** Mask an email for diagnostics (AppLog is in-memory/copyable in the app's
 *  own log viewer, not a place account addresses should appear in full) --
 *  "j***@gmail.com" instead of "jane.doe@gmail.com". */
fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    if (at <= 0) return "***"
    return "${email.first()}***${email.substring(at)}"
}

/** Current GMT offset in whole hours (e.g. -5 EST, -4 EDT) -- both brand API
 *  clients send this as an auth header; kept in one place so a future fix
 *  (e.g. rounding for negative sub-hour offsets) can't apply to one and not
 *  the other. */
fun gmtOffsetHours(): String {
    val offsetMs = TimeZone.getDefault().getOffset(System.currentTimeMillis())
    return (offsetMs / 3_600_000).toString()
}

/** "just now" / "x min ago" / "x hr ago" for a wall-clock timestamp in ms. */
fun relativeLabel(ms: Long?): String {
    if (ms == null || ms <= 0) return ""
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000 -> "just now"
        d < 3_600_000 -> "${d / 60_000} min ago"
        d < 86_400_000 -> "${d / 3_600_000} hr ago"
        else -> "${d / 86_400_000} day ago"
    }
}

/**
 * A climate setpoint (the API reports it as a °F string) rendered in the user's
 * chosen unit. Non-numeric values pass through with a bare degree sign.
 */
fun degLabel(valueF: String, fahrenheit: Boolean): String {
    val f = valueF.toDoubleOrNull() ?: return "$valueF°"
    return if (fahrenheit) "${f.toLong()}°F" else "${((f - 32) * 5 / 9.0).toLong()}°C"
}

/** Human-readable label for a WMO weather code integer. */
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

/** Formats a Celsius temperature as °F or °C based on the user preference. */
fun weatherTemp(tempC: Double, fahrenheit: Boolean): String =
    if (fahrenheit) "${(tempC * 9 / 5 + 32).toInt()}°F" else "${tempC.toInt()}°C"

/** Format a distance in miles as "mi" or "km" based on the unit system. */
fun formatDistance(mi: Number, metric: Boolean): String =
    if (metric) "${(mi.toDouble() * 1.609).toInt()} km" else "${mi.toInt()} mi"

/** Format speed in km/h based on the unit system. */
fun formatSpeed(kph: Double, metric: Boolean): String =
    if (metric) "${kph.toInt()} km/h" else "${(kph / 1.609).toInt()} mph"

/** Format trip distance in miles to the user's preferred unit. */
fun formatTripDistance(mi: Double, metric: Boolean): String =
    if (metric) "%.1f km".format(mi * 1.609) else "%.1f mi".format(mi)

/** Format efficiency (miles per kWh or km per kWh). */
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
    engineOn == true  -> "Driving"
    charging == true  -> "Charging"
    climateOn == true -> "Climate on"
    locked == true    -> "Locked"
    locked == false   -> "Unlocked"
    else              -> "—"
}
