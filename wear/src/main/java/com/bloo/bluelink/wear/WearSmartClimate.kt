package com.bloo.bluelink.wear

import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WeatherApi
import com.bloo.bluelink.data.ambientFahrenheit
import com.bloo.bluelink.data.smartClimateHint
import com.bloo.bluelink.data.smartClimateIsCooling
import com.bloo.bluelink.data.smartClimateTargetF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * What smart climate WILL set for a car, so its row can say so before it is pressed: the weather where the car is parked, run
 * through the same rule the phone and the command use ([smartClimateTargetF]). Looked up by the watch itself (its position is
 * the car's, from the snapshot) and kept a quarter of an hour; with no location or no connection there is simply no hint and
 * the row says only "Smart climate".
 */
internal object WearSmartClimate {
    /** The target for a car: [targetF] is what it will set, [ambientF] the outside temperature it is answering. */
    data class Hint(val targetF: Int, val ambientF: Int, val cooling: Boolean, val fetchedAt: Long)

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
        val ambient = ambientFahrenheit(weather.tempC)
        val hint = Hint(smartClimateTargetF(ambient), ambient, smartClimateIsCooling(ambient), System.currentTimeMillis())
        _hints.update { it + (car.vin to hint) }
    }

    /** "Cool to 62°" or "Heat to 78°", with the outside temperature, in the phone's unit (the phone words the first part the same way). */
    fun describe(hint: Hint): String = smartClimateHint(hint.ambientF, WearPresets.fahrenheit)

    private val failedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private const val TTL_MS = 15 * 60_000L
    private const val RETRY_MS = 90_000L
}
