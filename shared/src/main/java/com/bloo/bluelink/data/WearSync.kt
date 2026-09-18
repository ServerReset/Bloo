package com.bloo.bluelink.data

import kotlinx.serialization.Serializable

/** Stable command verbs understood by every command source (natural-language
 *  search, quick actions, notification action buttons, AutoLock). These are
 *  plain string constants (not a Kotlin enum) so a serialized [WearCommand]
 *  never needs matching enum ordinals/names across independently-updated call
 *  sites; an unrecognized action string is just ignored by whichever consumer
 *  receives it instead of failing to decode entirely. TOGGLE_* actions ask the
 *  receiver to flip whatever the current state is; the explicit _ON / _OFF /
 *  LOCK / UNLOCK variants force a specific state regardless of the current one.
 *
 *  Named "Wear" for historical reasons (this vocabulary and [WearCommand]
 *  originally doubled as the wire protocol to a Wear OS companion app); it is
 *  now purely the phone's own internal command layer -- see [WearCommandRunner]. */
object WearAction {
    const val TOGGLE_LOCK = "toggle_lock"
    const val LOCK = "lock"
    const val UNLOCK = "unlock"
    const val TOGGLE_CLIMATE = "toggle_climate"
    const val CLIMATE_ON = "climate_on"
    const val CLIMATE_OFF = "climate_off"
    const val TOGGLE_CHARGE = "toggle_charge"
    const val CHARGE_ON = "charge_on"
    const val CHARGE_OFF = "charge_off"

    /** Flash the hazard lights, or flash + sound the horn. Hyundai/Genesis
     *  only (see Vehicle.supportsHornLights) -- Kia's US API has neither. */
    const val FLASH_LIGHTS = "flash_lights"
    const val HORN_AND_LIGHTS = "horn_and_lights"

    /** Apply the AC/DC charge-limit targets in [WearCommand.acLimit]/[WearCommand.dcLimit]. */
    const val SET_CHARGE_LIMITS = "set_charge_limits"

    /** Re-fetch a single car's status (or all, when [WearCommand.vin] is blank). */
    const val REFRESH = "refresh"
}

/** A command for one car, run through [WearCommandRunner]. */
@Serializable
data class WearCommand(
    val vin: String,
    val action: String,
    /** Climate settings to use for [WearAction.CLIMATE_ON]/[WearAction.TOGGLE_CLIMATE].
     *  Seats are [SeatLevel.apiValue] ints (0 = off) so the format stays flat.
     *  [steeringWheelHeat] is likewise [WheelHeatLevel.apiValue]. */
    val tempF: Int = DEFAULT_CLIMATE_TEMP_F,
    val durationMinutes: Int = DEFAULT_CLIMATE_DURATION_MIN,
    val defrost: Boolean = false,
    val steeringWheelHeat: Int = 0,
    val seatFrontLeft: Int = 0,
    val seatFrontRight: Int = 0,
    val seatRearLeft: Int = 0,
    val seatRearRight: Int = 0,
    /** Targets for [WearAction.SET_CHARGE_LIMITS]. */
    val acLimit: Int = DEFAULT_AC_CHARGE_LIMIT_PCT,
    val dcLimit: Int = DEFAULT_DC_CHARGE_LIMIT_PCT,
)

/** The result of attempting a [WearCommand]. */
@Serializable
data class WearCommandResult(
    val vin: String,
    val action: String,
    val ok: Boolean,
    val message: String? = null,
)

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
