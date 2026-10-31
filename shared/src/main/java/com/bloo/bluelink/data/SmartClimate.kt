package com.bloo.bluelink.data

import android.content.Context
import kotlin.math.abs
import kotlin.math.roundToInt

/** One climate start the person set by hand: the outside temperature when they set it, and the target they chose. */
data class ClimateChoice(val ambientF: Int, val targetF: Int)

/** The target smart climate sets, and the things that moved it, in words, so the person can see why it chose what it did. */
data class SmartClimatePlan(val targetF: Int, val reasons: List<String>)

/** How many hand-set choices are kept per car. Older ones fall away. */
const val CLIMATE_CHOICES_KEPT = 40

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
fun smartClimatePlan(w: Weather, choices: List<ClimateChoice> = emptyList(), fahrenheit: Boolean = true): SmartClimatePlan {
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

    fun list(context: Context, vin: String): List<ClimateChoice> =
        decodeClimateChoices(prefs(context).getString(vin, "").orEmpty())

    /** Keeps a hand-set start, the newest last, trimmed to [CLIMATE_CHOICES_KEPT]. */
    fun record(context: Context, vin: String, ambientF: Int, targetF: Int) {
        val kept = (list(context, vin) + ClimateChoice(ambientF, targetF)).takeLast(CLIMATE_CHOICES_KEPT)
        prefs(context).edit().putString(vin, encodeClimateChoices(kept)).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
