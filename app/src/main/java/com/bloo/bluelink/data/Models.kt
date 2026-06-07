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

@Serializable
data class TirePressureLamp(
    val tirePressureLampAll: Int? = null,
)

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
    val drvDistance: List<DrvDistance> = emptyList(),
)

@Serializable
data class DrvDistance(
    val rangeByFuel: RangeByFuel? = null,
)

@Serializable
data class RangeByFuel(
    val totalAvailableRange: Dte? = null,
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
