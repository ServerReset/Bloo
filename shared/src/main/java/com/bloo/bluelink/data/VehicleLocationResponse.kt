package com.bloo.bluelink.data

import kotlinx.serialization.Serializable

// --- Location -------------------------------------------------------------

/** Response body from the dedicated (rate-limited) findMyCar location
 *  endpoint — a superset of the free [VehicleLocation] embedded in the status
 *  payload, adding [head] (compass heading in degrees). */
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


/** Raw GPS coordinate; [alt] (altitude) is captured but not currently
 *  surfaced anywhere in the UI. */
@Serializable
data class Coord(
    val lat: Double? = null,
    val lon: Double? = null,
    val alt: Double? = null,
)


/** A {value, unit} pair for the car's reported speed — same shape convention
 *  used across this file ([Dte], [TempValue], [TimeValue]) for API fields
 *  that come bundled with their own unit code. */
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
    /** Speed at the time of the fix, if reported. >0 implies the car is moving. */
    val speed: Double? = null,
)


/**
 * Great-circle distance to [other], in miles. Haversine, not
 * `android.location.Location.distanceBetween` -- this is pure Kotlin/JVM math with
 * no Android platform dependency, so it stays testable from a plain unit test the
 * way the rest of this file's format/parse helpers are, and the couple of meters
 * Haversine's spherical-Earth approximation loses against WGS84 don't matter for a
 * "how far is the car" readout.
 */
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

/** The headline charge/fuel percentage for this car. Takes [hasBattery]
 *  (the user's manual powertrain override, not the raw isEv flag) as a
 *  parameter rather than reading it off the status itself, since the status
 *  payload has no notion of "this PHEV is actually being tracked as electric" —
 *  that decision lives with the caller (see VehicleSnapshot.hasBattery). */
fun VehicleStatus.percentFor(hasBattery: Boolean): Int? =

    if (hasBattery) evStatus?.batteryStatus else fuelLevel


/** The headline range in miles (battery range for EVs, else distance-to-empty).
 *  Mechanism: only reads the EV battery range when [hasBattery] is true;
 *  otherwise (or if the EV range field itself is missing) it falls back to
 *  the plain [dte] distance-to-empty field, so a car with an unexpectedly
 *  empty EV range still shows *something* rather than a blank range. */
fun VehicleStatus.rangeMiFor(hasBattery: Boolean): Int? {
    val batteryRange = evStatus?.drvDistance?.firstOrNull()?.rangeByFuel?.totalAvailableRange?.value
    return ((if (hasBattery) batteryRange else null) ?: dte?.value)?.toInt()
}


/**
 * This status's reported GPS fix as a [GeoLocation], or null when it carries no usable position.
 *
 * All-or-nothing on the coordinate pair: a fix is returned ONLY when BOTH lat and lon are present,
 * so a status with one but not the other never yields half a position (the phone's snapshot path
 * relied on that to avoid combining a fresh lat with a stale cached lon). Speed comes from the same
 * status whose coord was read, so it always matches the fix.
 *
 * Nullable receiver: three phone call sites (loadStatus, snapshotOf, locate) had this exact block
 * inline -- two on a nullable status, one on a non-null one -- so the receiver is nullable and a
 * null status simply returns null.
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


/** The charge-limit target for the *currently connected* charger, or null if unplugged.
 *  Mechanism: [EvStatus.batteryPlugin] tells us which charger is plugged in
 *  right now (1 = DC fast, 2 = AC, per the encoding documented on
 *  [EvStatus.pluggedInLabel]); this maps that to the matching plugType index
 *  ([ReservChargeInfos] uses 0 for DC / 1 for AC, a *different* numbering
 *  from batteryPlugin's own 1/2) and looks up just that one target, since
 *  showing "your charge limit" should reflect whichever plug is actually
 *  connected, not both AC and DC limits at once. */
fun EvStatus.targetForCurrentPlug(): Int? = when (batteryPlugin) {
    1 -> reservChargeInfos?.level(0) // DC fast
    2 -> reservChargeInfos?.level(1) // AC
    else -> null
}


/**
 * The charge limit to SHOW at a glance, on any surface that draws the charge bar
 * whether or not the car happens to be plugged in right now: [targetForCurrentPlug]
 * when there's an active plug to report, else the car's AC limit as the sensible
 * default the rest of the time.
 *
 * [targetForCurrentPlug] answers "which plug is connected and what's ITS target" --
 * exactly right for a live charging session, but null the instant the car is
 * unplugged, which used to mean a parked car's hero card silently lost its whole
 * limit-aware bar (falling back to a plain, un-split track with no blue "topped up"
 * state) even though the car's own configured limit hadn't gone anywhere. AC over DC
 * as the fallback: it's the everyday/overnight charging scenario every car has a
 * target for, where DC fast-charge limits are the exception most cars only report
 * once actually connected to one.
 *
 * Reported from a real device: a parked, unplugged car at 77% correctly has no
 * CURRENT plug to report a limit for, but the bar still needs ONE to draw the
 * three-segment shape (or the blue stuck-at-limit fill) users asked to see "always,"
 * not just mid-session -- see ChargeReadout.stuckAtLimit's own doc for that same
 * "always" requirement, which this same gap was quietly breaking for parked cars.
 */
fun EvStatus.displayChargeLimit(): Int? = targetForCurrentPlug() ?: reservChargeInfos?.level(1)


/** True when any charger is connected (any non-zero [EvStatus.batteryPlugin],
 *  per the 0=unplugged/1=DC/2=AC encoding documented on
 *  [EvStatus.pluggedInLabel]); a missing plug value is treated as unplugged. */
val EvStatus.isPluggedIn: Boolean get() = (batteryPlugin ?: 0) != 0


/**
 * True when a charger is connected OR the car is actively charging.
 *
 * The `|| batteryCharge` half is not redundant with [isPluggedIn]: some cars report
 * charging while `batteryPlugin` still reads 0 (a plug value that has not caught up, or a
 * DC session the field does not describe), and "can I start/stop a charge" must say yes in
 * that state. That is why every caller wrote the OR -- and wrote it separately, three times
 * in one file: CoverActionBar, ChargePebble and the charge status row each derived
 * `ev?.isPluggedIn == true || charging` inline.
 *
 * Shared because a duplicated PREDICATE is this codebase's most repeated defect: the pebble
 * visibility check existed in four copies and two of them had silently lost a clause. These
 * three still agreed; they are unified before they stop agreeing, not after.
 *
 * Nullable receiver so a null EvStatus (non-EV car, or no status fetched yet) answers false
 * at the call site without each one repeating a `?:` or an `== true`.
 */
val EvStatus?.isPluggedOrCharging: Boolean

    get() = this != null && (isPluggedIn || batteryCharge == true)


/** Shared mechanism behind [DoorOpen.openLabels]/[WindowOpen.openLabels]:
 *  both door and window state use the identical 0=closed/1=open per-position
 *  encoding, so this one helper turns the four raw Int? flags into a list of
 *  only the human-readable position names that are actually open (closed or
 *  unknown/null positions are simply omitted via listOfNotNull, not included
 *  as e.g. "front-left: closed"). */
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
 * "lat, lon" formatted to [decimals] places, in [java.util.Locale.ROOT].
 *
 * ROOT and not the default locale, which is what `"%.4f, %.4f".format(...)` gives you.
 * A single localized number is fine and desirable -- "12,5 km" is correct in German --
 * but a PAIR joined by ", " is not, because the delimiter then collides with the
 * decimal separator: 48.8566, 2.3522 rendered as "48,8566, 2,3522", where there is no
 * way to tell which commas separate the two values. Coordinates are also the one figure
 * in this app a user is likely to copy out and paste into a map, which wants a dot.
 *
 * Every caller was going through the default locale before this, including the watch's
 * own hand-rolled copy of the format string.
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
 * One recent drive from /ac/v2/ts/alerts/maintenance/evTripDetails (EVs only).
 * Energy figures are watt-hours; times are seconds; speeds are mph.
 * Field paths follow the community hyundai_kia_connect_api.
 */
@Serializable
data class EvTrip(
    val startdate: String? = null,
    val distance: Double? = null,
    val totalused: Double? = null,
    val drivetrain: Double? = null,
    val climate: Double? = null,
    val accessories: Double? = null,
    val batterycare: Double? = null,
    val regen: Double? = null,
    val odometer: TripMeasure? = null,
    val mileagetime: TripMeasure? = null,
    val duration: TripMeasure? = null,
    val avgspeed: TripMeasure? = null,
    val maxspeed: TripMeasure? = null,
) {
    /** Minutes actually driving (the API reports seconds); truncates via
     *  integer division after converting, so a partial minute of driving is
     *  dropped rather than rounded up. */
    val driveMinutes: Int? get() = mileagetime?.value?.let { (it / 60).toInt() }

    /** Minutes stopped-but-on: total duration minus driving time. Returns
     *  null (rather than a bogus figure) if either the total duration or the
     *  driving time is missing, since the subtraction is meaningless without
     *  both; the result is clamped to never go negative in case the two
     *  fields disagree slightly due to independent rounding on the server side. */
    val idleMinutes: Int?
        get() {
            val total = duration?.value ?: return null
            val driving = mileagetime?.value ?: return null
            return ((total - driving) / 60).toInt().coerceAtLeast(0)
        }

    /** Net consumption in kWh (one decimal), if the car reported energy data.
     *  Mechanism: the raw value is watt-hours; dividing by 100 then rounding
     *  to the nearest whole number, then dividing by 10, is a roundabout way
     *  of rounding the final kWh figure to one decimal place while working
     *  entirely in Long arithmetic via Math.round (avoids accumulating
     *  floating-point rounding error from repeated Double division). */
    val usedKwh: Double? get() = totalused?.let { Math.round(it / 100.0) / 10.0 }

    /** Regenerated energy in kWh (one decimal). Same round-to-one-decimal
     *  mechanism as [usedKwh] above. */
    val regenKwh: Double? get() = regen?.let { Math.round(it / 100.0) / 10.0 }
}


@Serializable
data class EvTripDetailsResponse(
    val tripdetails: List<EvTrip> = emptyList(),
)
