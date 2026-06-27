package com.bloo.bluelink.data

/** "1h 20m" / "45 min" duration formatter, shared across phone and watch. */
fun fmtMinutes(min: Int): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "$min min"

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
