package com.bloo.bluelink.data

import kotlinx.serialization.Serializable

// --- Location -------------------------------------------------------------

/**
 * Response body from the dedicated (rate-limited) findMyCar location endpoint — a superset of the
 * free [VehicleLocation] embedded in the status payload, adding [head] (compass heading in
 * degrees).
 */
@Serializable
data class VehicleLocationResponse(
    val coord: Coord? = null,
    val head: Double? = null,
    val speed: Speed? = null,
)

/** Location embedded in the vehicleStatus payload (free, not rate-limited). */
@Serializable
data class VehicleLocation(
    val coord: Coord? = null,
    val time: String? = null,
    val speed: Speed? = null,
)

/**
 * Raw GPS coordinate.
 */
@Serializable
data class Coord(
    val lat: Double? = null,
    val lon: Double? = null,
)

@Serializable
data class Speed(
    val value: Double? = null,
    val unit: Int? = null,
)

/** UI-facing location result. */
@Serializable
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
    val speed: Double? = null,
)

/** Great-circle distance to [other], in miles. */
fun GeoLocation.distanceMilesTo(other: GeoLocation): Double {
    val earthRadiusMi = 3958.8
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val dLat = Math.toRadians(other.latitude - latitude)
    val dLon = Math.toRadians(other.longitude - longitude)
    val a = kotlin.math.sin(dLat / 2).let { it * it } +
        kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * kotlin.math.sin(dLon / 2).let { it * it }
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return earthRadiusMi * c
}

// --- Shared status helpers (used across UI, snapshots, cache, AI) ---------

/** The headline charge/fuel percentage for this car. */
fun VehicleStatus.percentFor(hasBattery: Boolean): Int? =

    if (hasBattery) evStatus?.batteryStatus else fuelLevel

/** The headline range in miles (battery range for EVs, else distance-to-empty). */
fun VehicleStatus.rangeMiFor(hasBattery: Boolean): Int? {
    val batteryRange = evStatus?.drvDistance?.firstOrNull()?.rangeByFuel?.totalAvailableRange?.value
    return ((if (hasBattery) batteryRange else null) ?: dte?.value)?.toInt()
}

/**
 * All-or-nothing on the coordinate pair: a fix is returned ONLY when BOTH lat and lon are present,
 * so a status with one but not the other never yields half a position (the phone's snapshot path
 * relied on that to avoid combining a fresh lat with a stale cached lon).
 */
fun VehicleStatus?.toGeoLocation(): GeoLocation? {
    val c = this?.vehicleLocation?.coord ?: return null
    val lat = c.lat
    val lon = c.lon
    return if (lat != null && lon != null) {
        GeoLocation(lat, lon, this.vehicleLocation?.speed?.value)
    } else {
        null
    }
}

/** The charge-limit target for the *currently connected* charger, or null if unplugged. */
fun EvStatus.targetForCurrentPlug(): Int? = when (batteryPlugin) {
    1 -> reservChargeInfos?.level(0) // DC fast
    2 -> reservChargeInfos?.level(1) // AC
    else -> null
}

/**
 * AC over DC as the fallback: it's the everyday/overnight charging scenario every car has a target
 * for, where DC fast-charge limits are the exception most cars only report once actually connected
 * to one.
 */
fun EvStatus.displayChargeLimit(): Int? = targetForCurrentPlug() ?: reservChargeInfos?.level(1)

/**
 * True when any charger is connected (any non-zero [EvStatus.batteryPlugin], per the
 * 0=unplugged/1=DC/2=AC encoding documented on [EvStatus.pluggedInLabel]); a missing plug value is
 * treated as unplugged.
 */
val EvStatus.isPluggedIn: Boolean get() = (batteryPlugin ?: 0) != 0

/** True when a charger is connected OR the car is actively charging. */
val EvStatus?.isPluggedOrCharging: Boolean
    get() = this != null && (isPluggedIn || batteryCharge == true)

/**
 * Shared mechanism behind [DoorOpen.openLabels]/[WindowOpen.openLabels]: both door and window state
 * use the identical 0=closed/1=open per-position encoding, so this one helper turns the four raw
 * Int? flags into a list of only the human-readable position names that are actually open (closed
 * or unknown/null positions are simply omitted via listOfNotNull, not included as e.g.
 */
private fun openPositions(fl: Int?, fr: Int?, bl: Int?, br: Int?): List<String> = listOfNotNull(
    if (fl == 1) "front-left" else null,
    if (fr == 1) "front-right" else null,
    if (bl == 1) "rear-left" else null,
    if (br == 1) "rear-right" else null,
)

/** Human-readable list of which doors are currently open (empty if all closed/unknown). */
fun DoorOpen.openLabels(): List<String> = openPositions(frontLeft, frontRight, backLeft, backRight)

/** Human-readable list of which windows are currently open (empty if all closed/unknown). */
fun WindowOpen.openLabels(): List<String> = openPositions(frontLeft, frontRight, backLeft, backRight)

/**
 * "lat, lon" formatted to [decimals] places, in [java.util.Locale.ROOT]. ROOT and not the default
 * locale, which is what `"%.4f, %.4f".format(...)` gives you.
 */
fun coordString(lat: Double, lon: Double, decimals: Int = 5): String =

    String.format(java.util.Locale.ROOT, "%.${decimals}f, %.${decimals}f", lat, lon)

/** [coordString] for a [GeoLocation]. */
fun GeoLocation.coordString(decimals: Int = 5): String = coordString(latitude, longitude, decimals)

// --- EV trip history (Hyundai/Genesis US evTripDetails) --------------------

/** A {value, unit} pair used throughout the trip-details payload. */
@Serializable
data class TripMeasure(
    val value: Double? = null,
    val unit: Int? = null,
)

/**
 * One recent drive from /ac/v2/ts/alerts/maintenance/evTripDetails (EVs only). Energy figures are
 * watt-hours; times are seconds; speeds are mph. Field paths follow the community
 * hyundai_kia_connect_api.
 */
@Serializable
data class EvTrip(
    val startdate: String? = null,
    val distance: Double? = null,
    val totalused: Double? = null,
    val drivetrain: Double? = null,
    val climate: Double? = null,
    val regen: Double? = null,
    val odometer: TripMeasure? = null,
    val mileagetime: TripMeasure? = null,
    val duration: TripMeasure? = null,
    val avgspeed: TripMeasure? = null,
    val maxspeed: TripMeasure? = null,
) {
    /**
     * Minutes actually driving (the API reports seconds); truncates via integer division after
     * converting, so a partial minute of driving is dropped rather than rounded up.
     */
    val driveMinutes: Int? get() = mileagetime?.value?.let { (it / 60).toInt() }

    /** Minutes stopped-but-on: total duration minus driving time. */
    val idleMinutes: Int?
        get() {
            val total = duration?.value ?: return null
            val driving = mileagetime?.value ?: return null
            return ((total - driving) / 60).toInt().coerceAtLeast(0)
        }

    /**
     * Mechanism: the raw value is watt-hours; dividing by 100 then rounding to the nearest whole
     * number, then dividing by 10, is a roundabout way of rounding the final kWh figure to one
     * decimal place while working entirely in Long arithmetic via Math.round (avoids accumulating
     * floating-point rounding error from repeated Double division).
     */
    val usedKwh: Double? get() = totalused?.let { Math.round(it / 100.0) / 10.0 }

    /**
     * Regenerated energy in kWh (one decimal). Same round-to-one-decimal mechanism as [usedKwh]
     * above.
     */
    val regenKwh: Double? get() = regen?.let { Math.round(it / 100.0) / 10.0 }
}

@Serializable
data class EvTripDetailsResponse(
    val tripdetails: List<EvTrip> = emptyList(),
)
