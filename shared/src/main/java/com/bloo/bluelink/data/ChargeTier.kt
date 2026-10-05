package com.bloo.bluelink.data

import kotlin.math.roundToInt

/** How urgent a charge or fuel level is, independent of any surface's palette (see [chargeTier]). */
enum class ChargeTier { UNKNOWN, CHARGING, CRITICAL, LOW, NORMAL }


/** At or below this percentage a level is [ChargeTier.CRITICAL]. */
const val CHARGE_CRITICAL_PCT = 15


/** At or below this percentage (and above [CHARGE_CRITICAL_PCT]) a level is [ChargeTier.LOW]. */
const val CHARGE_LOW_PCT = 30


/**
 * Which band a charge/fuel level falls in. Charging outranks the level, and a null [percent] is
 * [ChargeTier.UNKNOWN]. Bands are inclusive ("15% or less"); each surface maps tiers to its own colours.
 */
fun chargeTier(percent: Int?, charging: Boolean): ChargeTier = when {
    charging -> ChargeTier.CHARGING
    percent == null -> ChargeTier.UNKNOWN
    percent <= CHARGE_CRITICAL_PCT -> ChargeTier.CRITICAL
    percent <= CHARGE_LOW_PCT -> ChargeTier.LOW
    else -> ChargeTier.NORMAL
}


/**
 * The name to show for a car: nickname, else model, else the tail of its identifier. Each step
 * guards against BLANK (a nickname of "" passes the JSON accessor), and the last resort is never blank.
 */
fun vehicleDisplayName(nickName: String?, modelName: String?, id: String): String =

    nickName?.takeIf { it.isNotBlank() }
        ?: modelName?.takeIf { it.isNotBlank() }
        ?: id.takeLast(6).ifBlank { "Car" }


/** Label for a WMO weather code; each branch lists a whole group of codes, unknown codes give "—". */
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


/** Formats a Celsius temperature as °F or °C per the user preference; weather data is always Celsius.
 *
 *  Rounds in both branches (truncating made readings up to a degree cold). Not routed through
 *  [degValue]: the °C -> °F -> °C float round trip lands on the wrong side of some half-degree ties
 *  (24.5 -> 24.499999999999996). Metric rounds the Celsius it was given. */
fun weatherTemp(tempC: Double, fahrenheit: Boolean): String =

    if (fahrenheit) "${(tempC * 9 / 5 + 32).roundToInt()}°F" else "${tempC.roundToInt()}°C"


/** The one exact mile/kilometre factor; dividing by it keeps both directions exact inverses. */
const val KM_PER_MI = 1.609344


/** Format a distance in miles as "mi" or "km" per the unit system. API distances are always miles;
 *  metric multiplies by [KM_PER_MI] and rounds. */
fun formatDistance(mi: Number, metric: Boolean): String =

    if (metric) "${(mi.toDouble() * KM_PER_MI).roundToInt()} km" else "${mi.toInt()} mi"


/** Format speed from km/h (unlike [formatDistance]'s miles input): imperial converts to mph, metric passes through. */
fun formatSpeed(kph: Double, metric: Boolean): String =

    if (metric) "${kph.toInt()} km/h" else "${(kph / KM_PER_MI).toInt()} mph"


/**
 * Format a speed whose input is MILES per hour, unlike [formatSpeed]'s km/h (EvTrip's avgspeed/maxspeed
 * are mph). Named for its input unit so a call site cannot pick the wrong conversion.
 */
fun formatSpeedMph(mph: Double, metric: Boolean): String =

    if (metric) "${(mph * KM_PER_MI).roundToInt()} km/h" else "${mph.toInt()} mph"


/** Format trip distance in miles to the preferred unit, keeping one decimal (trips are short). */
fun formatTripDistance(mi: Double, metric: Boolean): String =

    if (metric) "%.1f km".format(mi * KM_PER_MI) else "%.1f mi".format(mi)



/** Raw signed miles until the next service: [lastServiceMiles] + [intervalMiles] - [odometerMiles].
 *  Null if any input is null; negative once overdue (callers clamp as needed). */
fun serviceDue(odometerMiles: Int?, lastServiceMiles: Int?, intervalMiles: Int?): Int? {
    if (odometerMiles == null || lastServiceMiles == null || intervalMiles == null) return null
    return nextServiceMiles(lastServiceMiles, intervalMiles) - odometerMiles
}


/**
 * The ABSOLUTE odometer reading a service falls due at. [serviceDue] is defined as
 * `nextServiceMiles - odometer`, so the countdown and the absolute figure share one formula.
 */
fun nextServiceMiles(lastServiceMiles: Int, intervalMiles: Int): Int =

    lastServiceMiles + intervalMiles


/** Parse the API's odometer string ("12,345.6") into whole miles (grouping commas stripped,
 *  fraction truncated), or null if blank/unparseable. */
fun parseOdometerMiles(odometer: String?): Int? =

    odometer?.trim()?.takeIf { it.isNotBlank() }?.replace(",", "")?.toDoubleOrNull()?.toInt()


/**
 * Whether an app-lock should re-engage after [elapsedMs] in the background, given the lock-timing
 * wire key ("off" / "immediate", via [LockTiming.wireKey]). "1min"/"5min"/"10min" are legacy values
 * from older builds; an unrecognised key re-locks.
 */
fun shouldRelockAfter(elapsedMs: Long, timingKey: String, screenTurnedOff: Boolean = false): Boolean = when (timingKey) {
    "off" -> false
    "immediate" -> true
    "screen_off" -> screenTurnedOff
    "1min" -> elapsedMs >= 60_000L
    "5min" -> elapsedMs >= 300_000L
    "10min" -> elapsedMs >= 600_000L
    else -> true
}
