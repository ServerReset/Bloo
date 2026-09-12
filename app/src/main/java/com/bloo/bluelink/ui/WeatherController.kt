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

    /**
     * Silently re-syncs the weather location to the device's CURRENT position, but only
     * when [SettingsStore.Appearance.weatherFollowsDevice] is set -- i.e. it was last put
     * into this state by [useDeviceLocationForWeather], not a typed place. That call used
     * to be the only time this location was ever fetched: fine for a one-shot "set my
     * location" action, but the reported bug was that it then stayed frozen at that one
     * fix forever, never moving even as the app refreshed everything else. Meant to be
     * called alongside [AppViewModel]'s own [UiState.deviceLocation] refresh -- cold
     * start, pull-to-refresh, and "Locate" -- so both device-position readings stay in
     * step on the same schedule. Fails soft and silently (no `state.message`) on a
     * missing fix: this is a background refresh nobody explicitly asked for right now,
     * not a user-initiated action that deserves an error toast.
     *
     * [preloaded] is that same-schedule guarantee made literal: AppViewModel passes the
     * exact fused-location fix it just used for [UiState.deviceLocation] (the one drawn
     * as the dot on the car map), so this ends up storing THE SAME reading as the
     * weather location instead of a second, independently-fetched one that could
     * legitimately disagree with it -- reported directly as the map dot, the home
     * weather card and "distance to car" not agreeing on where "here" is.
     */
    fun refreshDeviceLocationForWeather(preloaded: android.location.Location? = null) = scope.launch {
        if (!settingsStore.appearance.first().weatherFollowsDevice) return@launch
        if (withContext(Dispatchers.IO) { settingsStore.setWeatherFromDeviceLocation(preloaded) }) {
            loadHomeWeather(force = true)
        }
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
        // withinWindow, not `now - fetchedAt < TTL`: the latter is also true for a
        // fetchedAt in the FUTURE, which a backwards clock correction produces, and
        // that froze the weather until real time caught up. See withinWindow.
        if (!force && cached != null &&
            com.bloo.bluelink.data.withinWindow(System.currentTimeMillis(), cached.fetchedAt, WEATHER_TTL_MS)
        ) return@launch
        WeatherApi.fetch(lat, lon)?.let { w -> state.update { it.copy(homeWeather = w) } }
    }

    /** Fetch weather at a car's last-known location, if any. */
    fun loadCarWeather(v: Vehicle, force: Boolean = false) = scope.launch {
        val loc = state.value.locations[v.vin] ?: return@launch
        val cached = state.value.carWeather[v.vin]
        // Same guard as loadHomeWeather above.
        if (!force && cached != null &&
            com.bloo.bluelink.data.withinWindow(System.currentTimeMillis(), cached.fetchedAt, WEATHER_TTL_MS)
        ) return@launch
        WeatherApi.fetch(loc.latitude, loc.longitude)?.let { w ->
            state.update { it.copy(carWeather = it.carWeather + (v.vin to w)) }
        }
    }
}
