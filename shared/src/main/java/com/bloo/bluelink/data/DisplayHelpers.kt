package com.bloo.bluelink.data

import kotlin.math.roundToInt

/**
 * The name to show for a car: nickname, else model, else the tail of its identifier. Each step
 * guards against BLANK (a nickname of "" passes the JSON accessor), and the last resort is never blank.
 */
fun vehicleDisplayName(nickName: String?, modelName: String?, id: String): String =

    nickName?.takeIf { it.isNotBlank() }
        ?: modelName?.takeIf { it.isNotBlank() }
        ?: id.takeLast(6).ifBlank { "Car" }

/** Label for a WMO weather code; each branch lists a whole group of codes, unknown codes give "-". */
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
    else -> "-"
}

/** Formats a Celsius temperature as °F or °C per the user preference; weather data is always Celsius.
 *
 *  Rounds in both branches (truncating made readings up to a degree cold). Not routed through
 *  [degValue]: the °C -> °F -> °C float round trip lands on the wrong side of some half-degree ties
 *  (24.5 -> 24.499999999999996). Metric rounds the Celsius it was given. */
/** °C as °F: the one conversion every temperature in the app goes through. */
fun cToF(c: Double): Double = c * 9.0 / 5.0 + 32.0

/** °F as °C. */
fun fToC(f: Double): Double = (f - 32.0) * 5.0 / 9.0

fun weatherTemp(tempC: Double, fahrenheit: Boolean): String =

    if (fahrenheit) "${cToF(tempC).roundToInt()}°F" else "${tempC.roundToInt()}°C"

/**
 * The one mile/kilometre conversion factor, exact. Those are not reciprocals -- 1 / 0.621371 =
 * 1.609344 -- so a Canadian metric user's value round-tripped lossily through the API boundary and
 * back to the screen: 263 km arrived as 163.42 mi, rendered as 262 km.
 */
const val KM_PER_MI = 1.609344

/** The door-lock word for the car: "Locked" or "Unlocked", or null while the car has not said. Shared so the phone and the watch word it alike. */
fun doorLockLabel(locked: Boolean?): String? = when (locked) {
    true -> "Locked"
    false -> "Unlocked"
    null -> null
}

/** The engine word for the car: "Running" or "Parked", or null while the car has not said. Shared so the phone and the watch word it alike. */
fun engineLabel(engineOn: Boolean?): String? = when (engineOn) {
    true -> "Running"
    false -> "Parked"
    null -> null
}

/** The word for a car that is charging, shared by every surface that names the state. */
const val CHARGING_WORD = "Charging"

/** The charging word for the car's widget and preview: "Charging" or "Not charging". */
fun chargingLabel(charging: Boolean): String = if (charging) CHARGING_WORD else "Not charging"

/**
 * Format a distance in miles as "mi" or "km" based on the unit system. The API's distance figures
 * are always miles, so metric users get a multiply by [KM_PER_MI] and a re-labelled unit; imperial
 * users get the raw value. Metric ROUNDS rather than truncates.
 */
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
 * The service countdown in words -- "Due in 2,500 mi" or "Overdue by 300 mi" -- for [remaining] miles (negative once overdue),
 * with the distance formatted by [distance] in the reader's own unit. Shared so the phone and the watch word a service alike.
 */
fun serviceCountdown(remaining: Int, distance: (Int) -> String): String =
    if (remaining >= 0) "Due in ${distance(remaining)}" else "Overdue by ${distance(-remaining)}"

/**
 * The ABSOLUTE odometer reading a service falls due at. Trivial arithmetic, and shared anyway for
 * one reason: [serviceDue] is defined as `nextServiceMiles - odometer`, so the relative countdown
 * ("in N mi") and the absolute figure ("at N mi") are two views of ONE number and must never
 * disagree.
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
