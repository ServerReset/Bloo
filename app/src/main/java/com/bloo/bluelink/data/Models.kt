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

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
)

// --- Enrollment / vehicle list -------------------------------------------

@Serializable
data class EnrollmentResponse(
    val enrolledVehicleDetails: List<EnrolledVehicle> = emptyList(),
)

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

@Serializable
data class VehicleStatusResponse(
    val vehicleStatus: VehicleStatus? = null,
)

@Serializable
data class VehicleStatus(
    val doorLock: Boolean? = null,
    val airCtrlOn: Boolean? = null,
    val engine: Boolean? = null,
    val trunkOpen: Boolean? = null,
    val hoodOpen: Boolean? = null,
    val defrost: Boolean? = null,
    val doorOpen: DoorOpen? = null,
    val tirePressureLamp: TirePressureLamp? = null,
    val dte: Dte? = null,
    val airTemp: TempValue? = null,
    val battery: Battery12V? = null,
    val evStatus: EvStatus? = null,
    val dateTime: String? = null,
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
)

/** Current per-seat heater/vent state, used to infer which seats the car has. */
@Serializable
data class SeatHeaterVentState(
    val flSeatHeatState: Int? = null,
    val frSeatHeatState: Int? = null,
    val rlSeatHeatState: Int? = null,
    val rrSeatHeatState: Int? = null,
)

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

@Serializable
data class Dte(
    val value: Double? = null,
    val unit: Int? = null,
)

@Serializable
data class TempValue(
    val value: String? = null,
    val unit: Int? = null,
)

@Serializable
data class Battery12V(
    val batSoc: Int? = null,
)

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

@Serializable
data class RemainTime2(
    val atc: TimeValue? = null,
    val etc1: TimeValue? = null,
    val etc3: TimeValue? = null,
)

@Serializable
data class TimeValue(
    val value: Double? = null,
    val unit: Int? = null,
)

@Serializable
data class ReservChargeInfos(
    val targetSOClist: List<TargetSOC> = emptyList(),
) {
    fun level(plugType: Int): Int? =
        targetSOClist.firstOrNull { it.plugType == plugType }?.targetSOClevel
}

@Serializable
data class TargetSOC(
    val plugType: Int? = null,
    val targetSOClevel: Int? = null,
)

@Serializable
data class DrvDistance(
    val rangeByFuel: RangeByFuel? = null,
)

@Serializable
data class RangeByFuel(
    val totalAvailableRange: Dte? = null,
)

/**
 * Seat heater/ventilation levels for the US Blue Link climate command.
 * Values follow the community-documented encoding.
 */
enum class SeatLevel(val apiValue: Int, val label: String) {
    HIGH_COOL(5, "High cool"),
    MED_COOL(4, "Med cool"),
    LOW_COOL(3, "Low cool"),
    OFF(0, "Off"),
    LOW_HEAT(6, "Low heat"),
    MED_HEAT(7, "Med heat"),
    HIGH_HEAT(8, "High heat");

    companion object {
        /** Range offered when the seat can both heat and cool (ventilated). */
        val ventilatedRange = listOf(HIGH_COOL, MED_COOL, LOW_COOL, OFF, LOW_HEAT, MED_HEAT, HIGH_HEAT)

        /** Range offered when the seat is heat-only. */
        val heatOnlyRange = listOf(OFF, LOW_HEAT, MED_HEAT, HIGH_HEAT)

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

// --- Location -------------------------------------------------------------

@Serializable
data class VehicleLocationResponse(
    val coord: Coord? = null,
    val head: Double? = null,
    val speed: Speed? = null,
)

@Serializable
data class Coord(
    val lat: Double? = null,
    val lon: Double? = null,
    val alt: Double? = null,
)

@Serializable
data class Speed(
    val value: Double? = null,
    val unit: Int? = null,
)

/** UI-facing location result. */
data class GeoLocation(
    val latitude: Double,
    val longitude: Double,
)
