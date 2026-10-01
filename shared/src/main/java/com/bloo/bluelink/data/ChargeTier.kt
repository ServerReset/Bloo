package com.bloo.bluelink.data

import kotlin.math.roundToInt

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
 * packed ARGB ints on the phone -- which is the part that legitimately differs,
 * including what UNKNOWN should look like.
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
 * sibling `distance` in the same payload is treated as miles by the phone,
 * so `formatSpeed(62.0, metric = false)` rendered 62 mph as "38 mph"
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
 * lock-timing setting as its wire key ("off" / "immediate", via [LockTiming.wireKey]).
 *
 * The "1min"/"5min"/"10min" branches are legacy: LockTiming no longer offers those grace
 * periods as a choice, but a value stored by an older build still resolves correctly here
 * instead of falling through to the `else` fail-safe. `else` maps to "lock" (an unrecognised
 * key means re-lock rather than silently stay open).
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
