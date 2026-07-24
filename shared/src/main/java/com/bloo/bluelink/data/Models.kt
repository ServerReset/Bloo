package com.bloo.bluelink.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Data models for the Hyundai Blue Link US telematics API.
 *
 * Endpoint paths, header names and field names are derived from the
 * community reverse-engineering work in:
 *  - Hacksore/bluelinky
 *  - schmidtwmark/BetterBlueKit
 *  - andyfase/egmp-bluelink-scriptable
 *
 * Nothing here is simulated: every request hits the real
 * api.telematics.hyundaiusa.com servers.
 */

/** Response body from the Blue Link oauth token endpoint (login and refresh
 *  share this same shape). [refreshToken] can be null on a refresh response
 *  that reuses the existing refresh token rather than issuing a new one. */
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
)

// --- Enrollment / vehicle list -------------------------------------------

/** Response body from the enrollment/details endpoint: every car the signed-in
 *  account has registered with Blue Link. */
@Serializable
data class EnrollmentResponse(
    val enrolledVehicleDetails: List<EnrolledVehicle> = emptyList(),
)

/** One wrapper layer the API adds around each vehicle's details for no
 *  apparent reason beyond matching the real response shape; unwrapped by
 *  [toVehicle] into the flatter [Vehicle] the rest of the app uses. */
@Serializable
data class EnrolledVehicle(
    val vehicleDetails: VehicleDetails,
)

@Serializable
data class VehicleDetails(
    val vin: String,
    val regid: String,
    val nickName: String? = null,
    val modelName: String? = null,
    val modelYear: String? = null,
    val vehicleGeneration: String? = null,
    val brandIndicator: String? = null,
    val enrollmentDate: String? = null,
    val evStatus: String? = null,
    val odometer: String? = null,
)

/** Flattened, UI-friendly representation of a single enrolled car. */
data class Vehicle(
    val vin: String,
    val regId: String,
    val name: String,
    val model: String,
    val generation: String,
    val brandIndicator: String,
    val isEv: Boolean,
    val odometer: String? = null,
)

// --- Vehicle status -------------------------------------------------------

/** Thin wrapper the vehicleStatus endpoint puts around the actual payload;
 *  null when the car hasn't reported a status at all yet. */
@Serializable
data class VehicleStatusResponse(
    val vehicleStatus: VehicleStatus? = null,
)

@Serializable
data class VehicleStatus(
    val doorLock: Boolean? = null,
    val airCtrlOn: Boolean? = null,
    val engine: Boolean? = null,
    val acc: Boolean? = null,
    val trunkOpen: Boolean? = null,
    val hoodOpen: Boolean? = null,
    val defrost: Boolean? = null,
    val doorOpen: DoorOpen? = null,
    val windowOpen: WindowOpen? = null,
    val tirePressureLamp: TirePressureLamp? = null,
    val dte: Dte? = null,
    val airTemp: TempValue? = null,
    val battery: Battery12V? = null,
    val evStatus: EvStatus? = null,
    val dateTime: String? = null,
    // Last-known GPS, included free with the status payload (no rate-limited
    // findMyCar call needed). This is how the official app shows location.
    val vehicleLocation: VehicleLocation? = null,
    // Comfort / climate sub-features
    val steerWheelHeat: Int? = null,
    val sideBackWindowHeat: Int? = null,
    val sideMirrorHeat: Int? = null,
    val seatHeaterVentState: SeatHeaterVentState? = null,
    // Diagnostics / warnings
    val lowFuelLight: Boolean? = null,
    val washerFluidStatus: Boolean? = null,
    val breakOilStatus: Boolean? = null,
    val smartKeyBatteryWarning: Boolean? = null,
    val fuelLevel: Int? = null,
    val tirePressure: TirePressure? = null,
) {
    /** True when the embedded location's last-known speed says the car is
     *  moving. The main phone UI's AppViewModel.isDriving() layers a live GPS
     *  reading on top of this same check; this bare version is what the
     *  watch's own standalone command path (no separate GPS tracking of its
     *  own) uses to apply the same "car rejects climate commands while
     *  driving" gate before starting climate. */
    val isDriving: Boolean get() = (vehicleLocation?.speed?.value ?: 0.0) > 0.0
}

/** Per-window open state (0 closed, 1 open), like [DoorOpen]. */
@Serializable
data class WindowOpen(
    val frontLeft: Int? = null,
    val frontRight: Int? = null,
    val backLeft: Int? = null,
    val backRight: Int? = null,
) {
    val anyOpen: Boolean
        get() = listOf(frontLeft, frontRight, backLeft, backRight).any { it == 1 }
}

/** Current per-seat heater/vent state, used to infer which seats the car has. */
@Serializable
data class SeatHeaterVentState(
    val flSeatHeatState: Int? = null,
    val frSeatHeatState: Int? = null,
    val rlSeatHeatState: Int? = null,
    val rrSeatHeatState: Int? = null,
)

/** Coarse tire-pressure reading; [all] is a single combined status (not
 *  broken out per wheel — that's [TirePressureLamp] instead). */
@Serializable
data class TirePressure(
    val all: Int? = null,
)

/** Per-door open state. The API encodes each door as 0 (closed) or 1 (open). */
@Serializable
data class DoorOpen(
    val frontLeft: Int? = null,
    val frontRight: Int? = null,
    val backLeft: Int? = null,
    val backRight: Int? = null,
) {
    val anyOpen: Boolean
        get() = listOf(frontLeft, frontRight, backLeft, backRight).any { it == 1 }
}

/**
 * Tire-pressure warning lamp. Different vehicle generations use different key
 * names for the same data, so both variants are captured and merged.
 */
@Serializable
data class TirePressureLamp(
    val tirePressureLampAll: Int? = null,
    val tirePressureWarningLampAll: Int? = null,
    val tirePressureLampFL: Int? = null,
    val tirePressureLampFR: Int? = null,
    val tirePressureLampRL: Int? = null,
    val tirePressureLampRR: Int? = null,
    val tirePressureWarningLampFrontLeft: Int? = null,
    val tirePressureWarningLampFrontRight: Int? = null,
    val tirePressureWarningLampRearLeft: Int? = null,
    val tirePressureWarningLampRearRight: Int? = null,
) {
    val all: Int? get() = tirePressureLampAll ?: tirePressureWarningLampAll
    val frontLeft: Int? get() = tirePressureLampFL ?: tirePressureWarningLampFrontLeft
    val frontRight: Int? get() = tirePressureLampFR ?: tirePressureWarningLampFrontRight
    val rearLeft: Int? get() = tirePressureLampRL ?: tirePressureWarningLampRearLeft
    val rearRight: Int? get() = tirePressureLampRR ?: tirePressureWarningLampRearRight

    /** True if any captured warning value is set. */
    val hasWarning: Boolean
        get() = listOf(all, frontLeft, frontRight, rearLeft, rearRight).any { it != null && it != 0 }
}

/** Distance-to-empty: a numeric [value] plus a [unit] code (the API's own
 *  unit enum, not resolved here — callers that care about miles vs km read
 *  this in conjunction with the user's own unit preference). */
@Serializable
data class Dte(
    val value: Double? = null,
    val unit: Int? = null,
)

/** A climate setpoint as the API reports it: [value] is a numeric string
 *  (not a Double) because the API itself sends it quoted; [unit] again is
 *  the API's own unit code. */
@Serializable
data class TempValue(
    val value: String? = null,
    val unit: Int? = null,
)

@Serializable
data class Battery12V(
    val batSoc: Int? = null,
    val batState: Int? = null,
    // NB: batSignalReferenceValue is intentionally omitted — some vehicles
    // (e.g. newer CCNC head units) return it as an object like
    // {"batWarning":65} rather than a number, which would break parsing. It's
    // unused, so we let ignoreUnknownKeys skip it whatever its shape.
) {
    /** Coarse 12V battery health from state of charge / state flag. */
    val health: String?
        get() = when {
            batSoc == null -> null
            batState == 0 -> "Needs attention"
            batSoc >= 75 -> "Good"
            batSoc >= 50 -> "Fair"
            else -> "Low"
        }
}

@Serializable
data class EvStatus(
    val batteryCharge: Boolean? = null,
    val batteryStatus: Int? = null,
    val batteryPlugin: Int? = null,
    val drvDistance: List<DrvDistance> = emptyList(),
    val remainTime2: RemainTime2? = null,
    val reservChargeInfos: ReservChargeInfos? = null,
) {
    /** 0 = unplugged, 1 = fast (DC), 2 = portable/AC. */
    val pluggedInLabel: String?
        get() = when (batteryPlugin) {
            0 -> "Not plugged in"
            1 -> "Plugged in (DC fast)"
            2 -> "Plugged in (AC)"
            else -> null
        }
}

/** Remaining-charge-time estimates, in whatever unit [TimeValue.unit] encodes
 *  (typically minutes). [atc] is the estimate for whichever charger is
 *  currently connected; [etc1]/[etc3] are separate AC/DC estimates reported
 *  regardless of what's plugged in. */
@Serializable
data class RemainTime2(
    val atc: TimeValue? = null,
    val etc1: TimeValue? = null,
    val etc3: TimeValue? = null,
)

/** A {value, unit} pair for time-based fields (charge time estimates), same
 *  shape convention as [Dte]/[TempValue]/[Speed]. */
@Serializable
data class TimeValue(
    val value: Double? = null,
    val unit: Int? = null,
)

/** Charge-limit targets for both charger types on this car. */
@Serializable
data class ReservChargeInfos(
    val targetSOClist: List<TargetSOC> = emptyList(),
) {
    /** Look up the target for a specific [plugType] (0 = DC fast, 1 = AC) by
     *  scanning the flat list the API returns — there's no guaranteed order
     *  or fixed index, so this is a linear search by the plug-type key rather
     *  than direct indexing. Returns null if that plug type wasn't reported. */
    fun level(plugType: Int): Int? =
        targetSOClist.firstOrNull { it.plugType == plugType }?.targetSOClevel
}

/** One entry in the charge-limit list: which plug ([plugType], 0 = DC fast,
 *  1 = AC — matching the same encoding used elsewhere for battery plug type)
 *  and its configured target state-of-charge percentage. */
@Serializable
data class TargetSOC(
    val plugType: Int? = null,
    val targetSOClevel: Int? = null,
)

/** Wraps the range figure for one fuel/energy source; the API models this as
 *  a list (see [EvStatus.drvDistance]) even though in practice only the first
 *  entry (the car's primary energy source) is ever read. */
@Serializable
data class DrvDistance(
    val rangeByFuel: RangeByFuel? = null,
)

/** The actual range value, one level deeper than [DrvDistance] — the API
 *  nests it this way to allow (unused here) per-fuel-type breakdowns. */
@Serializable
data class RangeByFuel(
    val totalAvailableRange: Dte? = null,
)

/**
 * Seat heater/ventilation levels for the US Blue Link climate command.
 * Values follow the community-documented encoding.
 */
@Serializable
enum class SeatLevel(val apiValue: Int, val label: String) {
    HIGH_COOL(5, "High cool"),
    MED_COOL(4, "Med cool"),
    LOW_COOL(3, "Low cool"),
    OFF(0, "Off"),
    LOW_HEAT(6, "Low heat"),
    MED_HEAT(7, "Med heat"),
    HIGH_HEAT(8, "High heat");

    val isCool: Boolean get() = apiValue in 3..5
    val isHeat: Boolean get() = apiValue in 6..8

    companion object {
        /** Build the slider range for a seat given what it supports. */
        fun rangeFor(canCool: Boolean, canHeat: Boolean): List<SeatLevel> = buildList {
            if (canCool) addAll(listOf(HIGH_COOL, MED_COOL, LOW_COOL))
            add(OFF)
            if (canHeat) addAll(listOf(LOW_HEAT, MED_HEAT, HIGH_HEAT))
        }

        fun fromApi(value: Int?): SeatLevel = entries.firstOrNull { it.apiValue == value } ?: OFF
    }
}

/** Which seats this car exposes a heater/vent for, inferred from the live status. */
data class SeatCapability(
    val frontLeft: Boolean = false,
    val frontRight: Boolean = false,
    val rearLeft: Boolean = false,
    val rearRight: Boolean = false,
) {
    val any: Boolean get() = frontLeft || frontRight || rearLeft || rearRight
}

/** A full climate-start request assembled by the UI. */
@Serializable
data class ClimateRequest(
    val tempF: Int,
    val defrost: Boolean,
    val durationMinutes: Int,
    val steeringWheelHeat: Boolean = false,
    val seatFrontLeft: SeatLevel = SeatLevel.OFF,
    val seatFrontRight: SeatLevel = SeatLevel.OFF,
    val seatRearLeft: SeatLevel = SeatLevel.OFF,
    val seatRearRight: SeatLevel = SeatLevel.OFF,
)

/** A user-named, saved climate configuration for one car. */
@Serializable
data class ClimatePreset(
    val id: String,
    val name: String,
    val request: ClimateRequest,
)

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

/** True when any charger is connected (any non-zero [EvStatus.batteryPlugin],
 *  per the 0=unplugged/1=DC/2=AC encoding documented on
 *  [EvStatus.pluggedInLabel]); a missing plug value is treated as unplugged. */
val EvStatus.isPluggedIn: Boolean get() = (batteryPlugin ?: 0) != 0

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

/** "lat, lon" formatted to [decimals] places. */
fun GeoLocation.coordString(decimals: Int = 5): String =
    "%.${decimals}f, %.${decimals}f".format(latitude, longitude)

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
