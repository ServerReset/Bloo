package com.bloo.bluelink.data

/** What this car can heat and cool, from the person's seat setup. Smart comfort only ever uses what the car has. */
data class ComfortCaps(
    val driverHeat: Boolean = false,
    val driverCool: Boolean = false,
    val passHeat: Boolean = false,
    val passCool: Boolean = false,
    val wheel: Boolean = false,
)

/** The seat, wheel and defrost settings smart climate picks for the weather, with each choice named in [reasons]. */
data class SmartComfort(
    val seatDriver: SeatLevel,
    val seatPassenger: SeatLevel,
    val wheel: WheelHeatLevel,
    val defrost: Boolean,
    val reasons: List<String>,
)

/** At or below this feels-like °F, the seats and wheel warm up. */
private const val COLD_F = 45.0

/** At or above this feels-like °F, the seats cool down. */
private const val HOT_F = 82.0

/** At or below this feels-like °F the glass may frost, so defrost runs. */
private const val FROST_F = 33.0

/** Damp air on a cold day fogs the glass: defrost runs at this humidity or above. */
private const val DAMP_PERCENT = 70

/**
 * Smart comfort for the weather where the car is parked: seats warm on a cold day and cool on a hot one, the wheel warms on a cold day
 * (if the car has one), and defrost runs when the glass would frost or fog. It only switches on what [caps] says the car has.
 */
fun smartComfort(w: Weather, caps: ComfortCaps): SmartComfort {
    val feelsF = cToF(w.feelsLikeC)
    val cold = feelsF <= COLD_F
    val hot = feelsF >= HOT_F
    val reasons = mutableListOf<String>()
    fun seat(heat: Boolean, cool: Boolean): SeatLevel = when {
        cold && heat -> SeatLevel.MED_HEAT
        hot && cool -> SeatLevel.MED_COOL
        else -> SeatLevel.OFF
    }
    val driver = seat(caps.driverHeat, caps.driverCool)
    val passenger = seat(caps.passHeat, caps.passCool)
    if (driver != SeatLevel.OFF || passenger != SeatLevel.OFF) {
        reasons += if (cold) "Seats warmed: it feels cold" else "Seats cooled: it feels hot"
    }
    val wheel = if (cold && caps.wheel) WheelHeatLevel.HIGH else WheelHeatLevel.OFF
    if (wheel.isOn) reasons += "Steering wheel warmed: it feels cold"
    val damp = (w.humidity ?: 0) >= DAMP_PERCENT
    val defrost = feelsF <= FROST_F || (cold && damp)
    if (defrost) reasons += if (feelsF <= FROST_F) "Defrost on: the glass may frost" else "Defrost on: damp air fogs the glass"
    return SmartComfort(driver, passenger, wheel, defrost, reasons)
}
