package com.bloo.bluelink.data

/** "1h 20m" / "45 min" duration formatter, shared across phone and watch. */
fun fmtMinutes(min: Int): String = if (min >= 60) "${min / 60}h ${min % 60}m" else "$min min"

/** "just now" / "x min ago" / "x hr ago" for a wall-clock timestamp in ms. */
fun relativeLabel(ms: Long?): String {
    if (ms == null || ms <= 0) return ""
    val d = System.currentTimeMillis() - ms
    return when {
        d < 60_000 -> "just now"
        d < 3_600_000 -> "${d / 60_000} min ago"
        d < 86_400_000 -> "${d / 3_600_000} hr ago"
        else -> "${d / 86_400_000} d ago"
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
