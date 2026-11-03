package com.bloo.bluelink.wear

import com.bloo.bluelink.data.SmartClimateMode
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.Weather
import com.bloo.bluelink.data.WeatherApi
import com.bloo.bluelink.data.ambientFahrenheit
import com.bloo.bluelink.data.cToF
import com.bloo.bluelink.data.degLabel
import com.bloo.bluelink.data.smartClimateMode
import com.bloo.bluelink.data.smartClimatePlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

/**
 * What smart climate WILL set for a car, so its row can say so before it is pressed: the weather where the car is parked, run
 * through the same rule the phone and the command use ([smartClimateTargetF]). Looked up by the watch itself (its position is
 * the car's, from the snapshot) and kept a quarter of an hour; with no location or no connection there is simply no hint and
 * the row says only "Smart climate".
 */
internal object WearSmartClimate {
    /** The target for a car: [targetF] is what it will set, [ambientF] the outside temperature it is answering, [mode] which way it moves. */
    data class Hint(val targetF: Int, val ambientF: Int, val mode: SmartClimateMode, val fetchedAt: Long)

    /** The last reading each car was planned from, so the plan can be made again when the person's own history arrives. */
    private val readings = java.util.concurrent.ConcurrentHashMap<String, Weather>()

    private val _hints = MutableStateFlow<Map<String, Hint>>(emptyMap())
    val hints: StateFlow<Map<String, Hint>> = _hints

    /** Looks the weather up for [car] unless a reading under [TTL_MS] old is already held. */
    suspend fun refresh(car: VehicleSnapshot) {
        val lat = car.lat ?: return
        val lon = car.lon ?: return
        val held = _hints.value[car.vin]
        val now = System.currentTimeMillis()
        if (held != null && now - held.fetchedAt < TTL_MS) return
        if (now - (failedAt[car.vin] ?: 0L) < RETRY_MS) return
        val weather = runCatching { WeatherApi.fetch(lat, lon) }.getOrNull()
        if (weather == null) {
            // Offline or unavailable: do not ask again on every scroll past the row.
            failedAt[car.vin] = now
            return
        }
        readings[car.vin] = weather
        _hints.update { it + (car.vin to hintFor(car.vin, weather)) }
    }

    /** Plans every car held again, with the person's current history: called when the phone's history arrives. */
    fun replan() {
        _hints.update { held -> held.mapValues { (vin, old) -> readings[vin]?.let { hintFor(vin, it) } ?: old } }
    }

    /** The plan for one car, weighing the person's own past starts for it. */
    private fun hintFor(vin: String, weather: Weather): Hint {
        val plan = smartClimatePlan(weather, WearClimateChoices.choicesFor(vin), feedback = WearClimateChoices.feedbackFor(vin))
        val feelsF = cToF(weather.feelsLikeC).roundToInt()
        return Hint(plan.targetF, ambientFahrenheit(weather.tempC), smartClimateMode(feelsF), System.currentTimeMillis())
    }

    /** "Cool to 62°" or "Heat to 78°", with the outside temperature, in the phone's unit (the phone words the first part the same way). */
    fun describe(hint: Hint): String {
        val fahrenheit = WearPresets.fahrenheit
        // The target is the plan's, so the hint shows the number that is actually sent.
        val verb = when (hint.mode) {
            SmartClimateMode.HEAT -> "Heat to "
            SmartClimateMode.COOL -> "Cool to "
            SmartClimateMode.HOLD -> "Hold at "
        }
        return verb + degLabel(hint.targetF.toString(), fahrenheit) + " · " + degLabel(hint.ambientF.toString(), fahrenheit) + " outside"
    }

    private val failedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private const val TTL_MS = 15 * 60_000L
    private const val RETRY_MS = 90_000L
}
