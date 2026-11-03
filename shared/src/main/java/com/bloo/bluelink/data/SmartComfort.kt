package com.bloo.bluelink.data

import kotlin.math.roundToInt

/** What this car can heat and cool, from the person's seat setup. Smart comfort only ever uses what the car has. */
data class ComfortCaps(
    val driverHeat: Boolean = false,
    val driverCool: Boolean = false,
    val passHeat: Boolean = false,
    val passCool: Boolean = false,
    val rearLeftHeat: Boolean = false,
    val rearLeftCool: Boolean = false,
    val rearRightHeat: Boolean = false,
    val rearRightCool: Boolean = false,
    val wheel: Boolean = false,
) {
    /** Nine digits, one per flag in declaration order. Carried in the snapshot and the watch sync. */
    fun encode(): String = listOf(
        driverHeat, driverCool, passHeat, passCool, rearLeftHeat, rearLeftCool, rearRightHeat, rearRightCool, wheel,
    ).joinToString("") { if (it) "1" else "0" }

    companion object {
        fun decode(text: String?): ComfortCaps {
            if (text == null || text.length != 9 || text.any { it != '0' && it != '1' }) return ComfortCaps()
            val b = text.map { it == '1' }
            return ComfortCaps(b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8])
        }
    }
}

/** The six things smart comfort sets, in the order the learned model keeps them. */
enum class ComfortItem { DRIVER, PASSENGER, REAR_LEFT, REAR_RIGHT, WHEEL, DEFROST }

/**
 * The comfort settings smart climate picks for the weather, with each choice named in [reasons]. The levels are as the car takes them.
 */
data class SmartComfort(
    val seatDriver: SeatLevel,
    val seatPassenger: SeatLevel,
    val seatRearLeft: SeatLevel,
    val seatRearRight: SeatLevel,
    val wheel: WheelHeatLevel,
    val defrost: Boolean,
    val reasons: List<String>,
)

/** How many drivers a car keeps comfort for. Each has their own learned model. */
const val DRIVER_COUNT = 2

/** The key a driver's learned comfort is kept under: the car's VIN for the first driver, so existing settings still apply to them. */
fun comfortKey(vin: String, driver: Int): String = if (driver <= 0) vin else "$vin#$driver"

/** At or below this feels-like °F, the seats and wheel warm up. */
private const val COLD_F = 45.0

/** At or above this feels-like °F, the seats cool down. */
private const val HOT_F = 82.0

/** At or below this feels-like °F the glass may frost, so defrost runs. */
private const val FROST_F = 33.0

/** Damp air on a cold day fogs the glass: defrost runs at this humidity or above. */
private const val DAMP_PERCENT = 70

/** How far one manual setting moves what the model has learned for a kind of day. */
private const val COMFORT_LEARN_RATE = 0.3

/** The highest seat or wheel level in the model's scale (three steps). */
private const val TOP_LEVEL = 3.0

/**
 * What smart comfort has learned from the person's own settings: for each item, the level they choose on a cold day (heat) and on a hot
 * day (cool), on a scale of 0 (off) to 3 (high). NaN means no opinion yet, so the weather rule decides. [seen] counts the manual settings
 * that taught it. Stored as one short string, so Drive sync carries it to every device.
 */
data class ComfortModel(
    val cold: DoubleArray = DoubleArray(ComfortItem.entries.size) { Double.NaN },
    val hot: DoubleArray = DoubleArray(ComfortItem.entries.size) { Double.NaN },
    val seen: Int = 0,
) {
    /** The learned level for [item] on the kind of day [mode] names, or null while there is no opinion. */
    fun levelFor(item: ComfortItem, mode: SmartClimateMode): Double? {
        val v = when (mode) {
            SmartClimateMode.HEAT -> cold[item.ordinal]
            SmartClimateMode.COOL -> hot[item.ordinal]
            SmartClimateMode.HOLD -> Double.NaN
        }
        return if (v.isNaN()) null else v
    }

    /** One manual setting, as the person made it, learned on the kind of day [mode] names. Holding a comfortable day teaches nothing. */
    fun learn(mode: SmartClimateMode, levels: IntArray): ComfortModel {
        if (mode == SmartClimateMode.HOLD) return this
        val target = if (mode == SmartClimateMode.HEAT) cold.copyOf() else hot.copyOf()
        ComfortItem.entries.forEach { item ->
            val i = item.ordinal
            val user = levels[i].toDouble()
            target[i] = if (target[i].isNaN()) user else (target[i] + COMFORT_LEARN_RATE * (user - target[i])).coerceIn(0.0, TOP_LEVEL)
        }
        return if (mode == SmartClimateMode.HEAT) copy(cold = target, seen = seen + 1) else copy(hot = target, seen = seen + 1)
    }

    /** Text form: the cold values, then the hot values (hundredths, "x" for no opinion), then the count. */
    fun encode(): String {
        fun part(a: DoubleArray) = a.joinToString(",") { if (it.isNaN()) "x" else (it * 100).roundToInt().toString() }
        return part(cold) + "|" + part(hot) + "|" + seen
    }

    companion object {
        fun decode(text: String): ComfortModel {
            val p = text.split("|")
            if (p.size != 3) return ComfortModel()
            val n = ComfortItem.entries.size
            val cold = readPart(p[0], n) ?: return ComfortModel()
            val hot = readPart(p[1], n) ?: return ComfortModel()
            return ComfortModel(cold, hot, p[2].toIntOrNull() ?: 0)
        }

        /** One part of the text: [n] values, "x" for no opinion, hundredths otherwise. Null when it does not read. */
        private fun readPart(s: String, n: Int): DoubleArray? {
            val values = s.split(",")
            if (values.size != n) return null
            val out = DoubleArray(n)
            for (i in 0 until n) {
                val t = values[i]
                out[i] = if (t == "x") Double.NaN else ((t.toIntOrNull() ?: return null) / 100.0)
            }
            return out
        }
    }
}

/**
 * The levels a request asks for, as smart comfort's scale: seat heat or cool 0 to 3 (for the mode, heat on a cold day and cool on a hot
 * one), the wheel 0 to 2, defrost 0 or 1. Index order is [ComfortItem].
 */
fun comfortLevelsOf(req: ClimateRequest, mode: SmartClimateMode): IntArray {
    fun seat(l: SeatLevel): Int = when {
        mode == SmartClimateMode.HEAT && l.isHeat -> l.apiValue - 5
        mode == SmartClimateMode.COOL && l.isCool -> l.apiValue - 2
        else -> 0
    }
    return intArrayOf(
        seat(req.seatFrontLeft), seat(req.seatFrontRight), seat(req.seatRearLeft), seat(req.seatRearRight),
        req.steeringWheelHeat.apiValue.coerceAtMost(2), if (req.defrost) 1 else 0,
    )
}

/**
 * Smart comfort for the weather where the car is parked. Seats warm on a cold day and cool on a hot one, the wheel warms on a cold day,
 * and defrost runs when the glass would frost or fog. Only what [caps] says the car has is switched on. Where [model] has learned the
 * person's own level for an item, that level is used in place of the rule's default.
 */
fun smartComfort(w: Weather, caps: ComfortCaps, model: ComfortModel? = null): SmartComfort {
    val feelsF = cToF(w.feelsLikeC)
    val mode = smartClimateMode(feelsF.roundToInt())
    val cold = mode == SmartClimateMode.HEAT
    val hot = mode == SmartClimateMode.COOL
    val reasons = mutableListOf<String>()

    // The seat's level on this kind of day: the learned one if there is one, else medium when the car has that seat's heat or cool.
    fun seat(item: ComfortItem, heat: Boolean, cool: Boolean): SeatLevel {
        val learned = model?.levelFor(item, mode)
        return when {
            cold && heat -> heatLevel(learned ?: 2.0)
            hot && cool -> coolLevel(learned ?: 2.0)
            else -> SeatLevel.OFF
        }
    }
    val driver = seat(ComfortItem.DRIVER, caps.driverHeat, caps.driverCool)
    val passenger = seat(ComfortItem.PASSENGER, caps.passHeat, caps.passCool)
    val rearLeft = seat(ComfortItem.REAR_LEFT, caps.rearLeftHeat, caps.rearLeftCool)
    val rearRight = seat(ComfortItem.REAR_RIGHT, caps.rearRightHeat, caps.rearRightCool)
    if (listOf(driver, passenger, rearLeft, rearRight).any { it != SeatLevel.OFF }) {
        reasons += if (cold) "Seats warmed: it feels cold" else "Seats cooled: it feels hot"
    }

    val wheelLevel = model?.levelFor(ComfortItem.WHEEL, mode)
    val wheel = if (cold && caps.wheel) wheelLevelOf(wheelLevelValue(wheelLevel ?: 2.0)) else WheelHeatLevel.OFF
    if (wheel.isOn) reasons += "Steering wheel warmed: it feels cold"

    val damp = (w.humidity ?: 0) >= DAMP_PERCENT
    val frost = feelsF <= FROST_F
    val ruleDefrost = cold && (frost || damp)
    val learnedDefrost = model?.levelFor(ComfortItem.DEFROST, mode)
    val defrost = cold && (if (learnedDefrost != null) learnedDefrost >= 0.5 else ruleDefrost)
    if (defrost) reasons += if (frost) "Defrost on: the glass may frost" else "Defrost on: damp air fogs the glass"
    return SmartComfort(driver, passenger, rearLeft, rearRight, wheel, defrost, reasons)
}

private fun heatLevel(v: Double): SeatLevel = when (v.roundToInt().coerceIn(0, 3)) {
    1 -> SeatLevel.LOW_HEAT
    2 -> SeatLevel.MED_HEAT
    3 -> SeatLevel.HIGH_HEAT
    else -> SeatLevel.OFF
}

private fun coolLevel(v: Double): SeatLevel = when (v.roundToInt().coerceIn(0, 3)) {
    1 -> SeatLevel.LOW_COOL
    2 -> SeatLevel.MED_COOL
    3 -> SeatLevel.HIGH_COOL
    else -> SeatLevel.OFF
}

/** The wheel's learned level on the 0 to 2 scale, rounded and kept in range. */
private fun wheelLevelValue(v: Double): Int = v.roundToInt().coerceIn(0, 2)

private fun wheelLevelOf(v: Int): WheelHeatLevel = WheelHeatLevel.entries.first { it.apiValue == v }
