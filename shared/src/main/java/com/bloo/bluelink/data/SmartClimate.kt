package com.bloo.bluelink.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

/** One climate start the person set by hand: the outside temperature when they set it, and the target they chose. */
@Serializable
data class ClimateChoice(val ambientF: Int, val targetF: Int)

/**
 * How the person felt about the plan at a given outside temperature: [deltaF] degrees warmer (positive) or cooler (negative) than the plan
 * was, or 0 for "that was right". Taken from the feel slider when a start is submitted, and from a plain start (which counts as right).
 */
@Serializable
data class ClimateFeedback(val ambientF: Int, val deltaF: Int)

/**
 * What smart climate has learned about one car, in a few numbers: how many °F the person wants it warmer (+) or cooler (-) than the plan,
 * for each kind of day (heating, cooling, comfortable), and how many answers went in. Learning keys on the outside temperature each answer
 * was given at, which every device records the same way, so the numbers mean the same wherever they are read. Kept as one short string in
 * the synced settings, so every device has the same model.
 */
data class ClimateModel(val heat: Double = 0.0, val cool: Double = 0.0, val hold: Double = 0.0, val seen: Int = 0)

/** How much each new answer moves the learned number for its kind of day. */
private const val LEARN_RATE = 0.3

/** Learns one more answer: the number for the kind of day at [ambientF] moves toward what the person wanted, and stays within the limit. */
fun ClimateModel.learn(ambientF: Int, deltaF: Int): ClimateModel {
    fun move(bias: Double) = (bias + LEARN_RATE * (deltaF - bias)).coerceIn(-FEEL_LIMIT_F.toDouble(), FEEL_LIMIT_F.toDouble())
    return when (smartClimateMode(ambientF)) {
        SmartClimateMode.HEAT -> copy(heat = move(heat), seen = seen + 1)
        SmartClimateMode.COOL -> copy(cool = move(cool), seen = seen + 1)
        SmartClimateMode.HOLD -> copy(hold = move(hold), seen = seen + 1)
    }
}

/** The learned shift, in whole °F, for the kind of day at [ambientF]. */
fun ClimateModel.biasFor(ambientF: Int): Int = when (smartClimateMode(ambientF)) {
    SmartClimateMode.HEAT -> heat
    SmartClimateMode.COOL -> cool
    SmartClimateMode.HOLD -> hold
}.roundToInt().coerceIn(-FEEL_LIMIT_F, FEEL_LIMIT_F)

/** Four numbers, comma-separated: the three shifts in hundredths of a degree, then how many answers went in. */
fun encodeClimateModel(m: ClimateModel): String =
    listOf((m.heat * 100).roundToInt(), (m.cool * 100).roundToInt(), (m.hold * 100).roundToInt(), m.seen).joinToString(",")

/** Reads what [encodeClimateModel] wrote. Anything unreadable is a fresh model, so a bad value never breaks the plan. */
fun decodeClimateModel(text: String): ClimateModel {
    val p = text.split(",").mapNotNull { it.trim().toIntOrNull() }
    if (p.size != 4) return ClimateModel()
    return ClimateModel(p[0] / 100.0, p[1] / 100.0, p[2] / 100.0, p[3])
}

/** The target smart climate sets, and the things that moved it, in words, so the person can see why it chose what it did. */
data class SmartClimatePlan(val targetF: Int, val reasons: List<String>)

/** How many hand-set choices are kept per car. Older ones fall away. */
const val CLIMATE_CHOICES_KEPT = 40

/** How many feel answers are kept per car. */
const val CLIMATE_FEEDBACK_KEPT = 40

/** The most the feel answers may move the plan, in °F, either way. */
private const val FEEL_LIMIT_F = 4

/** How far one feel step moves the setting from the plan, in °F: colder, normal or hotter is one step either way. */
const val FEEL_STEP_F = 2

/** The feel slider's three answers: -1 colder, 0 normal, 1 hotter. */
fun feelLabel(step: Int): String = when {
    step < 0 -> "Colder"
    step > 0 -> "Hotter"
    else -> "Normal"
}

/** How close, in outside °F, a past choice must be to count as "at a similar temperature". */
private const val SIMILAR_AMBIENT_F = 6

/** Humidity at or above this, on a cooling day, asks for a little more cooling: humid air keeps a cabin clammy. */
private const val HUMID_PERCENT = 70

/** Wind at or above this (km/h), on a heating day, takes some of the warmth away, so the target goes up a little. */
private const val WINDY_KPH = 25.0

/** How far the next few hours must move, in °F, before the trend changes the target. */
private const val TREND_F = 5

/**
 * Smart climate's target from the weather where the car is parked. The base is the comfortable cabin temperature for the feels-like
 * outside ([smartClimateTargetF]); the weather then nudges it the way a person would adjust: a humid hot day wants a little more cooling,
 * wind takes warmth away on a cold day, night on a hot day wants a little cooler for sleep, and the next few hours get ahead of a rise or a
 * fall. The person's own past choices at a similar outside temperature pull it toward what they usually set. Every nudge is named in
 * [SmartClimatePlan.reasons], so the target is never a mystery.
 */
fun smartClimatePlan(
    w: Weather,
    choices: List<ClimateChoice> = emptyList(),
    fahrenheit: Boolean = true,
    feedback: List<ClimateFeedback> = emptyList(),
    /** What smart climate has learned for this car. When it has learned anything it is used in place of the raw answers. */
    model: ClimateModel? = null,
): SmartClimatePlan {
    val reasons = mutableListOf<String>()
    val feelsF = cToF(w.feelsLikeC).roundToInt()
    val nowF = cToF(w.tempC)
    val mode = smartClimateMode(feelsF)
    var model = smartClimateTargetF(feelsF)
    val feels = degLabel(feelsF.toString(), fahrenheit)
    val toward = degLabel(model.toString(), fahrenheit)
    reasons += when (mode) {
        SmartClimateMode.HEAT -> "Feels like $feels, so heat toward $toward to make up the cold"
        SmartClimateMode.COOL -> "Feels like $feels, so cool toward $toward: a car in the heat gains warmth quickly"
        SmartClimateMode.HOLD -> "Feels like $feels, so hold a comfortable $toward"
    }

    val humidity = w.humidity ?: 0
    if (mode == SmartClimateMode.COOL && humidity >= HUMID_PERCENT) {
        model -= 2
        reasons += "Humid ($humidity%): 2° cooler"
    }
    if (mode == SmartClimateMode.HEAT && w.windKph >= WINDY_KPH) {
        model += 2
        reasons += "Windy: 2° warmer, the wind takes warmth"
    }
    if (mode == SmartClimateMode.COOL && !w.isDay) {
        model -= 1
        reasons += "Night: 1° cooler for sleep"
    }

    // The next three hours: get ahead of a rise on a hot day, or of a fall on a cold one.
    val next = w.hourly.take(3).map { cToF(it.tempC) }
    if (next.isNotEmpty()) {
        val peak = next.max()
        val low = next.min()
        if (mode == SmartClimateMode.COOL && peak - nowF >= TREND_F) {
            model -= 2
            reasons += "Warming to ${degLabel(peak.roundToInt().toString(), fahrenheit)} soon: starting cooler"
        }
        if (mode == SmartClimateMode.HEAT && nowF - low >= TREND_F) {
            model += 2
            reasons += "Cooling to ${degLabel(low.roundToInt().toString(), fahrenheit)} soon: starting warmer"
        }
    }

    // Past choices at a similar outside temperature: the median of what was set, weighted with the model.
    var target = model
    val similar = choices.filter { abs(it.ambientF - feelsF) <= SIMILAR_AMBIENT_F }
    if (similar.isNotEmpty()) {
        val learned = similar.map { it.targetF }.sorted()[similar.size / 2]
        target = (model * 0.4 + learned * 0.6).roundToInt()
        reasons += "You usually set ${degLabel(learned.toString(), fahrenheit)} around here"
    }
    // What the person said about the plan: the learned numbers for this kind of day when there are any, otherwise the average of the
    // recent answers at a similar outside temperature.
    val ambient = ambientFahrenheit(w.tempC)
    val shift: Int? = if (model != null && model.seen > 0) {
        model.biasFor(ambient)
    } else {
        feedback.filter { abs(it.ambientF - feelsF) <= SIMILAR_AMBIENT_F }.takeIf { it.isNotEmpty() }
            ?.let { felt -> (felt.sumOf { it.deltaF }.toDouble() / felt.size).roundToInt().coerceIn(-FEEL_LIMIT_F, FEEL_LIMIT_F) }
    }
    if (shift != null && shift != 0) {
        target += shift
        val word = if (shift > 0) "warmer" else "cooler"
        reasons += "You wanted it ${abs(shift)}° $word around here"
    }
    val range = CLIMATE_TEMP_RANGE_F
    return SmartClimatePlan(target.coerceIn(range.first, range.last), reasons)
}

/** Encodes choices as "ambient:target" pairs, comma-separated. */
fun encodeClimateChoices(choices: List<ClimateChoice>): String = choices.joinToString(",") { "${it.ambientF}:${it.targetF}" }

/** Reads what [encodeClimateChoices] wrote. A malformed pair is skipped rather than failing the whole list. */
fun decodeClimateChoices(text: String): List<ClimateChoice> =
    text.split(",").mapNotNull { pair ->
        val parts = pair.split(":")
        if (parts.size != 2) return@mapNotNull null
        val ambient = parts[0].toIntOrNull() ?: return@mapNotNull null
        val target = parts[1].toIntOrNull() ?: return@mapNotNull null
        ClimateChoice(ambient, target)
    }

/**
 * The hand-set climate choices kept for each car, on the device. Plain shared preferences, so the phone can read them where it draws
 * the smart row without waiting on a coroutine.
 */
object ClimateChoices {
    private const val PREFS = "climate_choices"
    private const val FEEL_PREFS = "climate_feedback"

    /** The feel answers for [vin], the newest last. */
    fun feedback(context: Context, vin: String): List<ClimateFeedback> =
        decodeClimateFeedback(context.getSharedPreferences(FEEL_PREFS, Context.MODE_PRIVATE).getString(vin, "").orEmpty())

    /** Keeps a feel answer, the newest last, trimmed to [CLIMATE_FEEDBACK_KEPT]. */
    fun recordFeedback(context: Context, vin: String, ambientF: Int, deltaF: Int) {
        val kept = (feedback(context, vin) + ClimateFeedback(ambientF, deltaF)).takeLast(CLIMATE_FEEDBACK_KEPT)
        context.getSharedPreferences(FEEL_PREFS, Context.MODE_PRIVATE).edit()
            .putString(vin, kept.joinToString(",") { "${it.ambientF}:${it.deltaF}" }).apply()
    }

    private fun decodeClimateFeedback(text: String): List<ClimateFeedback> =
        text.split(",").mapNotNull { pair ->
            val parts = pair.split(":")
            if (parts.size != 2) return@mapNotNull null
            ClimateFeedback(parts[0].toIntOrNull() ?: return@mapNotNull null, parts[1].toIntOrNull() ?: return@mapNotNull null)
        }

    fun list(context: Context, vin: String): List<ClimateChoice> =
        decodeClimateChoices(prefs(context).getString(vin, "").orEmpty())

    /** Keeps a hand-set start, the newest last, trimmed to [CLIMATE_CHOICES_KEPT]. */
    fun record(context: Context, vin: String, ambientF: Int, targetF: Int) {
        val kept = (list(context, vin) + ClimateChoice(ambientF, targetF)).takeLast(CLIMATE_CHOICES_KEPT)
        prefs(context).edit().putString(vin, encodeClimateChoices(kept)).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
