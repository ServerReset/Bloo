package com.bloo.bluelink.data

import kotlinx.serialization.Serializable

/** The live climate draft for one car, shared between simultaneous live
 *  compositions of the same car's climate pebble (see [AppViewModel.
 *  publishClimateState]/[ClimatePebble]'s own "cross-composition sync" doc) --
 *  the dual-column hotspot can pin controls to a secondary slot while the
 *  full pebble list still renders the same pebble too. Seat values are
 *  [SeatLevel.apiValue] ints; [steering] is likewise [WheelHeatLevel.apiValue]. */
@Serializable
data class ClimateSync(
    val activePresetId: String? = null,
    val tempF: Int = DEFAULT_CLIMATE_TEMP_F,
    val durationMinutes: Int = DEFAULT_CLIMATE_DURATION_MIN,
    val defrost: Boolean = false,
    val steering: Int = 0,
    val seatFrontLeft: Int = 0,
    val seatFrontRight: Int = 0,
    val seatRearLeft: Int = 0,
    val seatRearRight: Int = 0,
)

/** Build the live [ClimateSync] draft from this request, expanding the four
 *  [SeatLevel] seats to their [SeatLevel.apiValue] ints (note [ClimateSync.steering]
 *  carries this request's [ClimateRequest.steeringWheelHeat], likewise as
 *  [WheelHeatLevel.apiValue]). */
fun ClimateRequest.toClimateSync(activePresetId: String?): ClimateSync =
    ClimateSync(
        activePresetId = activePresetId,
        tempF = tempF,
        durationMinutes = durationMinutes,
        defrost = defrost,
        steering = steeringWheelHeat.apiValue,
        seatFrontLeft = seatFrontLeft.apiValue,
        seatFrontRight = seatFrontRight.apiValue,
        seatRearLeft = seatRearLeft.apiValue,
        seatRearRight = seatRearRight.apiValue,
    )
