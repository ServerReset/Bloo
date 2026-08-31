package com.bloo.bluelink.ui

/**
 * Owns all weather logic for the app: the home/car weather fetch through
 * [WeatherApi], the TTL-based cache guard ([WEATHER_TTL_MS]), and the
 * weather-location persistence in [SettingsStore]. Extracted verbatim from
 * [AppViewModel], which keeps thin forwarders so every existing call site
 * (settings screens, home/car weather pebbles) is untouched.
 */
import android.app.Application
import android.location.Geocoder
import com.bloo.bluelink.data.SettingsStore
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.WeatherApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

internal class WeatherController(
    private val app: Application,
    private val settingsStore: SettingsStore,
    private val state: MutableStateFlow<UiState>,
    private val scope: CoroutineScope,
) {
    /** Un-set the "home" weather location: clears the saved lat/lon/label and
     *  drops any already-fetched reading so the weather pebble hides itself. */
    fun clearWeatherLocation() = scope.launch {
        settingsStore.setWeatherLocation(null, null, null)
        state.update { it.copy(homeWeather = null) }
    }

    /** Forward-geocode a place name and save it as the weather location. */
    fun setWeatherPlace(query: String) = scope.launch {
        val q = query.trim()
        if (q.isBlank()) return@launch
        val hit = withContext(Dispatchers.IO) {
            runCatching {
                Geocoder(app, Locale.getDefault()).getFromLocationName(q, 1)?.firstOrNull()
            }.getOrNull()
        }
        if (hit == null) {
            state.update { it.copy(message = "Couldn't find \"$q\"") }
            return@launch
        }
        val label = listOfNotNull(hit.locality ?: hit.subAdminArea, hit.adminArea)
            .distinct().joinToString(", ").ifBlank { q }
        settingsStore.setWeatherLocation(hit.latitude, hit.longitude, label)
        loadHomeWeather(force = true)
    }

    /** Use the device's last-known location as the weather location (needs permission). */
    fun useDeviceLocationForWeather() = scope.launch {
        val ok = withContext(Dispatchers.IO) { settingsStore.setWeatherFromDeviceLocation() }
        if (!ok) {
            state.update { it.copy(message = "No device location available. Try setting a place instead") }
            return@launch
        }
        loadHomeWeather(force = true)
    }

    /** Fetch weather for the configured home location. Skips if a recent reading exists. */
    fun loadHomeWeather(force: Boolean = false) = scope.launch {
        val appearance = settingsStore.appearance.first()
        val lat = appearance.weatherLat
        val lon = appearance.weatherLon
        if (lat == null || lon == null) {
            state.update { it.copy(homeWeather = null) }
            return@launch
        }
        val cached = state.value.homeWeather
        if (!force && cached != null && System.currentTimeMillis() - cached.fetchedAt < WEATHER_TTL_MS) return@launch
        WeatherApi.fetch(lat, lon)?.let { w -> state.update { it.copy(homeWeather = w) } }
    }

    /** Fetch weather at a car's last-known location, if any. */
    fun loadCarWeather(v: Vehicle, force: Boolean = false) = scope.launch {
        val loc = state.value.locations[v.vin] ?: return@launch
        val cached = state.value.carWeather[v.vin]
        if (!force && cached != null && System.currentTimeMillis() - cached.fetchedAt < WEATHER_TTL_MS) return@launch
        WeatherApi.fetch(loc.latitude, loc.longitude)?.let { w ->
            state.update { it.copy(carWeather = it.carWeather + (v.vin to w)) }
        }
    }
}
