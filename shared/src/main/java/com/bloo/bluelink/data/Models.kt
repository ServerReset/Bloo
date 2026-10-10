package com.bloo.bluelink.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Data models for the Hyundai Blue Link US telematics API. */

/**
 * Response body from the Blue Link oauth token endpoint (login and refresh share this same shape).
 * [refreshToken] can be null on a refresh response that reuses the existing refresh token rather
 * than issuing a new one.
 */
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
)

// --- Enrollment / vehicle list -------------------------------------------

/**
 * Response body from the enrollment/details endpoint: every car the signed-in account has
 * registered with Blue Link.
 */
@Serializable
data class EnrollmentResponse(
    val enrolledVehicleDetails: List<EnrolledVehicle> = emptyList(),
)

/**
 * One wrapper layer the API adds around each vehicle's details for no apparent reason beyond
 * matching the real response shape; unwrapped by [toVehicle] into the flatter [Vehicle] the rest of
 * the app uses.
 */
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
    // Last-known GPS, included free with the status payload (no rate-limited findMyCar call
    // needed). This is how the official app shows location.
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
    /** True when the embedded location's last-known speed says the car is moving. */
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

@Serializable
data class SeatHeaterVentState(
    val flSeatHeatState: Int? = null,
    val frSeatHeatState: Int? = null,
    val rlSeatHeatState: Int? = null,
    val rrSeatHeatState: Int? = null,
)

/**
 * Coarse tire-pressure reading; [all] is a single combined status (not broken out per wheel —
 * that's [TirePressureLamp] instead).
 */
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
 * Tire-pressure warning lamp. Different vehicle generations use different key names for the same
 * data, so both variants are captured and merged.
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

/**
 * Distance-to-empty: a numeric [value] plus a [unit] code (the API's own unit enum, not resolved
 * here — callers that care about miles vs km read this in conjunction with the user's own unit
 * preference).
 */
@Serializable
data class Dte(
    val value: Double? = null,
    val unit: Int? = null,
)

/**
 * A climate setpoint as the API reports it: [value] is a numeric string (not a Double) because the
 * API itself sends it quoted; [unit] again is the API's own unit code.
 */
@Serializable
data class TempValue(
    val value: String? = null,
    val unit: Int? = null,
)

@Serializable
data class Battery12V(
    val batSoc: Int? = null,
    val batState: Int? = null,
    // NB: batSignalReferenceValue is intentionally omitted — some vehicles (e.g. newer CCNC head
    // units) return it as an object like {"batWarning":65} rather than a number, which would break
    // parsing.
) {
    /** Every display goes through this. */
    val level: Int?
        get() = batSoc?.takeIf { it in 0..100 }

    /** Coarse 12V battery health from state of charge / state flag. */
    val health: String?
        get() = when {
            level == null -> null
            batState == 0 -> "Needs attention"
            level!! >= 75 -> "Good"
            level!! >= 50 -> "Fair"
            else -> "Low"
        }

    /**
     * Whether this 12V reading is one the user should act on -- i.e. exactly the readings [health]
     * already calls "Low" or "Needs attention". Defined in terms of [health] rather than repeating
     * a number, because the number was the bug.
     */
    val needsAttention: Boolean
        get() = health == "Low" || health == "Needs attention"
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

    /**
     * Minutes until the battery is full, or null when the car isn't reporting a usable estimate.
     * Non-positive means "no estimate", not "zero minutes".
     */
    val minutesToFull: Int?
        get() = remainTime2?.atc?.value?.toInt()?.takeIf { it > 0 }
}

@Serializable
data class RemainTime2(
    val atc: TimeValue? = null,
    val etc1: TimeValue? = null,
    val etc3: TimeValue? = null,
)

/** A remaining time reported by the API: [value] in minutes, [unit] the API's own unit code. */
@Serializable
data class TimeValue(
    val value: Double? = null,
    val unit: Int? = null,
)

/**
 * The reserved/charge-target info from the EV status: the AC/DC target state-of-charge entries,
 * looked up by plug type via [level].
 */
@Serializable
data class ReservChargeInfos(
    val targetSOClist: List<TargetSOC> = emptyList(),
) {
    /**
     * Look up the target for a specific [plugType] (0 = DC fast, 1 = AC) by scanning the flat list
     * the API returns — there's no guaranteed order or fixed index, so this is a linear search by
     * the plug-type key rather than direct indexing.
     */
    fun level(plugType: Int): Int? =
        targetSOClist.firstOrNull { it.plugType == plugType }?.targetSOClevel
}

/**
 * One entry in the charge-limit list: which plug ([plugType], 0 = DC fast, 1 = AC — matching the
 * same encoding used elsewhere for battery plug type) and its configured target state-of-charge
 * percentage.
 */
@Serializable
data class TargetSOC(
    val plugType: Int? = null,
    val targetSOClevel: Int? = null,
)

/**
 * Wraps the range figure for one fuel/energy source; the API models this as a list (see
 * [EvStatus.drvDistance]) even though in practice only the first entry (the car's primary energy
 * source) is ever read.
 */
@Serializable
data class DrvDistance(
    val rangeByFuel: RangeByFuel? = null,
)

/**
 * The actual range value, one level deeper than [DrvDistance] — the API nests it this way to allow
 * (unused here) per-fuel-type breakdowns.
 */
@Serializable
data class RangeByFuel(
    val totalAvailableRange: Dte? = null,
)

/**
 * Seat heater/ventilation levels for the US Blue Link climate command. Values follow the
 * community-documented encoding.
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

/** Steering-wheel heater level for the US Blue Link climate command (Off / Low / High). */
@Serializable
enum class WheelHeatLevel(val apiValue: Int, val label: String) {
    OFF(0, "Off"),
    LOW(1, "Low"),
    HIGH(2, "High");

    val isOn: Boolean get() = this != OFF

    companion object {
        fun fromApi(value: Int?): WheelHeatLevel = entries.firstOrNull { it.apiValue == value } ?: OFF
    }
}

/** The US Blue Link climate command: temperature, defrost, duration, and the seat/wheel heat levels. */
@Serializable
data class ClimateRequest(
    val tempF: Int,
    val defrost: Boolean,
    val durationMinutes: Int,
    val steeringWheelHeat: WheelHeatLevel = WheelHeatLevel.OFF,
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
