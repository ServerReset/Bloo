package com.bloo.bluelink.data

import kotlinx.serialization.Serializable

/** Stable command verbs understood by every command source (natural-language
 *  search, quick actions, notification action buttons, AutoLock). These are
 *  plain string constants (not a Kotlin enum) so a serialized [CarCommand]
 *  never needs matching enum ordinals/names across independently-updated call
 *  sites; an unrecognized action string is just ignored by whichever consumer
 *  receives it instead of failing to decode entirely. TOGGLE_* actions ask the
 *  receiver to flip whatever the current state is; the explicit _ON / _OFF /
 *  LOCK / UNLOCK variants force a specific state regardless of the current one.
 *
 *  Formerly named "Wear*" because this vocabulary doubled as the wire protocol
 *  to a now-removed Wear OS companion app; it is purely the phone's own
 *  internal command layer -- see [CarCommandRunner]. */
object CarAction {
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

    /** Apply the AC/DC charge-limit targets in [CarCommand.acLimit]/[CarCommand.dcLimit]. */
    const val SET_CHARGE_LIMITS = "set_charge_limits"

    /** Re-fetch a single car's status (or all, when [CarCommand.vin] is blank). */
    const val REFRESH = "refresh"
}

/** A command for one car, run through [CarCommandRunner]. */
@Serializable
data class CarCommand(
    val vin: String,
    val action: String,
    /** Climate settings to use for [CarAction.CLIMATE_ON]/[CarAction.TOGGLE_CLIMATE].
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
    /** Targets for [CarAction.SET_CHARGE_LIMITS]. */
    val acLimit: Int = DEFAULT_AC_CHARGE_LIMIT_PCT,
    val dcLimit: Int = DEFAULT_DC_CHARGE_LIMIT_PCT,
)

/** The result of attempting a [CarCommand]. */
@Serializable
data class CarCommandResult(
    val vin: String,
    val action: String,
    val ok: Boolean,
    val message: String? = null,
)
