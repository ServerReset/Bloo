package com.bloo.bluelink.data

import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * A resolved reverse-geocode result in two lengths:
 *  - [full] -- "123 Main St, San Jose".
 *  - [compact] -- "123 Main St, 95112" (street + ZIP), short enough for narrow pills.
 */
data class GeocodedPlace(val full: String, val compact: String)

/** "123 Main St, San Jose" from a reverse-geocode result (house number + street + locality).
 *  Falls back to "Springfield, IL" without street detail, then to the raw first address line. */
fun formatPlaceName(a: android.location.Address): GeocodedPlace? {
    val street = listOfNotNull(
        a.subThoroughfare?.takeIf { it.isNotBlank() },
        a.thoroughfare?.takeIf { it.isNotBlank() },
    ).joinToString(" ").takeIf { it.isNotBlank() }
    val locality = a.locality ?: a.subAdminArea
    val parts = if (street != null) listOfNotNull(street, locality) else listOfNotNull(locality, a.adminArea)
    val full = parts.distinct().joinToString(", ").ifBlank { a.getAddressLine(0) } ?: return null
    // Street + ZIP when both exist; otherwise just reuse `full` -- a geocode result with no
    // street-level detail or no postal code has nothing more compact to offer than the long form
    // already is.
    val zip = a.postalCode?.takeIf { it.isNotBlank() }
    val compact = if (street != null && zip != null) "$street, $zip" else full
    return GeocodedPlace(full, compact)
}

/** "1h 20m" / "45 min" duration formatter. */
fun fmtMinutes(min: Int): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "$min min"

/** °C -> whole-degree °F (rounded), the [ambientF] input for smart-climate calculations. */
fun ambientFahrenheit(tempC: Double): Int = cToF(tempC).roundToInt()

/** The Fahrenheit range every supported car's climate target can be set to. */
val CLIMATE_TEMP_RANGE_F = 62..82

/** The cabin temperature a person is comfortable at: the middle of the band smart climate holds without a change. */
const val COMFORT_F = 70

/** Which way smart climate moves the cabin for an outside temperature: heat it, cool it, or hold it at [COMFORT_F]. */
enum class SmartClimateMode { HEAT, HOLD, COOL }

/** Below this (feels-like °F) the car heats; above [COOL_ABOVE_F] it cools; between them it holds the comfortable temperature. */
private const val HEAT_BELOW_F = 64
private const val COOL_ABOVE_F = 75

fun smartClimateMode(ambientF: Int): SmartClimateMode = when {
    ambientF < HEAT_BELOW_F -> SmartClimateMode.HEAT
    ambientF > COOL_ABOVE_F -> SmartClimateMode.COOL
    else -> SmartClimateMode.HOLD
}

/**
 * The cabin temperature smart climate aims for, from the feels-like outside temperature, clamped into [CLIMATE_TEMP_RANGE_F].
 *
 * The target is a comfortable cabin, not a copy of the weather. In the comfortable band it holds [COMFORT_F]. Colder, it heats further the
 * colder it gets, because the cabin has further to go. Hotter, it cools further, since a parked car in heat gains warmth quickly from the
 * sun and needs a cooler setpoint to get back to comfort.
 */
fun smartClimateTargetF(ambientF: Int): Int {
    val target = when {
        ambientF >= 96 -> 64.0
        ambientF > COOL_ABOVE_F -> COMFORT_F - (ambientF - COOL_ABOVE_F) * 6.0 / 21
        ambientF >= HEAT_BELOW_F -> COMFORT_F.toDouble()
        ambientF > 40 -> COMFORT_F + (HEAT_BELOW_F - ambientF) * 8.0 / 24
        else -> 78 + (40 - ambientF).coerceAtMost(20) * 0.2
    }
    return target.roundToInt().coerceIn(CLIMATE_TEMP_RANGE_F)
}

/** Whether [smartClimateTargetF] cools for [ambientF] (above the comfortable band), as opposed to heating or holding. */
fun smartClimateIsCooling(ambientF: Int): Boolean = smartClimateMode(ambientF) == SmartClimateMode.COOL

/** What smart climate says it will do for [ambientF], in the person's unit: "Cool to 69°", "Heat to 75°" or "Hold at 70°". */
fun smartClimateLabel(ambientF: Int, fahrenheit: Boolean): String {
    val target = degLabel(smartClimateTargetF(ambientF).toString(), fahrenheit)
    return when (smartClimateMode(ambientF)) {
        SmartClimateMode.HEAT -> "Heat to "
        SmartClimateMode.COOL -> "Cool to "
        SmartClimateMode.HOLD -> "Hold at "
    } + target
}

/** The compact smart-climate hint the watch shows under its row: "Cool to 62° · 88° outside". */
fun smartClimateHint(ambientF: Int, fahrenheit: Boolean): String =
    smartClimateLabel(ambientF, fahrenheit) + " · " + degLabel(ambientF.toString(), fahrenheit) + " outside"

/** The sentence under the smart-climate row on the phone: the ambient reading, then the target it will set. */
fun smartClimateTargetLine(ambientF: Int, fahrenheit: Boolean): String {
    val ambient = degLabel(ambientF.toString(), fahrenheit)
    val target = degLabel(smartClimateTargetF(ambientF).toString(), fahrenheit)
    return "It's $ambient by your car. Smart climate targets $target."
}

/** Just the temperature a saved climate preset sets, as "79°" (the unit is established by context). */
fun climatePresetTemp(req: ClimateRequest, fahrenheit: Boolean): String = "${degValue(req.tempF.toDouble(), fahrenheit)}°"

/** A compact "79° · Defrost · Heat" summary of what a saved climate preset will set; the phone and the watch word it alike. */
fun climatePresetDetail(req: ClimateRequest, fahrenheit: Boolean): String {
    // Bare "°" rather than degLabel's "°F"/"°C": the unit is established by context.
    val parts = mutableListOf(climatePresetTemp(req, fahrenheit))
    if (req.defrost) parts += "Defrost"
    val seats = listOf(req.seatFrontLeft, req.seatFrontRight, req.seatRearLeft, req.seatRearRight)
    if (seats.any { it.isHeat }) parts += "Heat"
    if (seats.any { it.isCool }) parts += "Cool"
    if (req.steeringWheelHeat.isOn) parts += "Wheel"
    return parts.joinToString(" · ")
}

/**
 * The range (in minutes) for a remote-start climate run, sent as ONE command: the car runs for the whole of it.
 * The default within this range stays its own constant, [DEFAULT_CLIMATE_DURATION_MIN].
 */
val CLIMATE_DURATION_RANGE: IntRange = 1..20

/** Valid range for a car's AC/DC charge-limit percentage sliders. */
val CHARGE_LIMIT_RANGE = 50..100

/**
 * How long a vehicle's cached status is trusted before it's treated as stale (worth nudging the
 * user to pull-to-refresh). Picked 15 minutes (the phone UI's existing value, the one actually
 * user-facing as copy) as the one reasonable default for all three.
 */
val STALE_STATUS_MS = 15L * 60 * 1000L

/** How long an available update is snoozed after "Remind me" / "Not now". */
val UPDATE_SNOOZE_MS = 3L * 24 * 60 * 60 * 1000L

/**
 * Whether [stamp] is within [windowMs] before [now] -- use for "skip if recent" throttles.
 * Requires a non-negative difference: persisted wall-clock stamps can be in the future after a
 * clock correction, and `now - stamp < windowMs` would then suppress the work indefinitely.
 * Half-open: a stamp exactly [windowMs] old is due.
 */
fun withinWindow(now: Long, stamp: Long, windowMs: Long): Boolean =

    (now - stamp) in 0 until windowMs

/** The climate request used when nothing else is configured: just "turn it on". */
const val DEFAULT_CLIMATE_TEMP_F = 72

const val DEFAULT_CLIMATE_DURATION_MIN = 10

/** Charge-limit targets used until a car's real targets load: 80% AC, 90% DC. Both are sent together. */
const val DEFAULT_AC_CHARGE_LIMIT_PCT = 80

const val DEFAULT_DC_CHARGE_LIMIT_PCT = 90

/** Charger-plug type label for [EvStatus.batteryPlugin]. */
fun chargerLabel(plugin: Int?): String? = when (plugin) {
    1 -> "DC fast"
    2 -> "AC (level 2)"
    else -> null
}

// Per-thread formatters for tripDate (rendered per list row; SimpleDateFormat is costly to build).
// ThreadLocal because SimpleDateFormat is not thread-safe; not java.time because its strict
// parsing could fall back to raw text on the vendor feed's observed timestamp shapes.
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

/** "2026-06-01 18:22:31.0" / "2026-06-01T18:22:31" -> "Mon Jun 1 · 6:22 PM" (falls back to trimmed raw text).
 *  The feed uses both 'T' and space separators. */
fun tripDate(raw: String?, includeWeekday: Boolean = true): String {
    if (raw.isNullOrBlank()) return "Trip"
    // Drop any fractional seconds - the feed's precision varies (".0" vs ".000000").
    val trimmed = raw.substringBefore('.').trim()
    val outFormat = if (includeWeekday) tripOutWithWeekday.get()!! else tripOutNoWeekday.get()!!
    // Try each known timestamp shape; runCatching swallows the ParseException of a non-match.
    for (parser in tripParsers.get()!!) {
        val parsed = runCatching { parser.parse(trimmed) }.getOrNull()
        if (parsed != null) return outFormat.format(parsed)
    }
    // Unexpected shape: truncate and swap 'T' for a space.
    return trimmed.take(16).replace('T', ' ')
}

/** Mask an email for diagnostics: "j***@gmail.com". */
fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    // No '@' (-1) or no local part (0): fully redacted.
    if (at <= 0) return "***"
    // First character of the local part plus the domain.
    return "${email.first()}***${email.substring(at)}"
}

/**
 * Current GMT offset in whole hours (e.g. -5 EST, -4 EDT) -- both brand API clients send this as an
 * auth header; kept in one place so a future fix (e.g. rounding for negative sub-hour offsets)
 * can't apply to one and not the other.
 */
fun gmtOffsetHours(): String {
    // getOffset(now) includes DST; integer division truncates to whole hours.
    val offsetMs = TimeZone.getDefault().getOffset(System.currentTimeMillis())
    return (offsetMs / 3_600_000).toString()
}

/** "just now" / "x min ago" / "x hr ago" for a wall-clock timestamp in ms. */
fun relativeLabel(ms: Long?): String {
    // No timestamp recorded yet: blank rather than a nonsensical duration.
    if (ms == null || ms <= 0) return ""
    val d = System.currentTimeMillis() - ms
    // Each branch's divisor converts the elapsed-ms delta into the largest whole unit that still
    // fits (minutes under an hour, hours under a day, otherwise days); integer division truncates
    // rather than rounds.
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
    // Non-numeric values normally pass through with a bare degree sign (they are temperature
    // indicators, e.g. "LO"). The one exception is a switched-off climate setpoint, which the API
    // reports as "OFF" -- a degree sign there reads as a temperature, so render it as the same
    // "Off" the Climate pebble uses.
    val raw = valueF.toDoubleOrNull()
        ?: return if (valueF.equals("OFF", ignoreCase = true)) "Off" else "$valueF°"

    // [sourceUnit] is the API's unit code for THIS value (0 = Celsius, 1 = Fahrenheit); reading it
    // stops a Celsius car being converted as °F. Null or unrecognised codes assume °F.
    val sourceIsCelsius = sourceUnit == 0
    val valueIsAlreadyTarget = sourceIsCelsius != fahrenheit

    // A Celsius value shown in Celsius keeps its fraction, and ONLY that case does. Canada's
    // setpoint table is in half degrees, so 22.5 is the common reading there and rounding it to
    // "23" throws away precision the car actually sent.
    if (valueIsAlreadyTarget) {
        if (fahrenheit) return "${raw.roundToInt()}°F"
        return "${trimTrailingZero(raw)}°C"
    }
    val converted = if (fahrenheit) cToF(raw) else fToC(raw)
    return "${converted.roundToInt()}°${if (fahrenheit) "F" else "C"}"
}

/** "22.5" stays "22.5"; "22.0" becomes "22". */
private fun trimTrailingZero(v: Double): String =

    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

/**
 * A °F reading as a whole number in the user's chosen unit, rounded (not truncated, which
 * biases every label down a degree). Takes Double because some regions send fractional °F.
 */
fun degValue(valueF: Double, fahrenheit: Boolean): Int =

    if (fahrenheit) valueF.roundToInt() else fToC(valueF).roundToInt()

/**
 * Whether temperatures render in Fahrenheit, from a unit-system string. Imperial is the
 * default for null or unrecognised values. One rule shared by every surface.
 */
fun useFahrenheit(unitSystem: String?): Boolean = (unitSystem ?: "imperial") != "metric"

/**
 * Temperature unit with its own override. [tempUnit] is "f", "c", or anything else (normally
 * "auto") to follow the main Units setting.
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
