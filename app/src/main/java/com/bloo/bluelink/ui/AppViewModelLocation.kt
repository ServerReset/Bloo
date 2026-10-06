package com.bloo.bluelink.ui

import android.location.Geocoder
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.Vehicle
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- Device location, reverse geocoding, and weather place (extracted from AppViewModel) --

/**
 * Refreshes [UiState.deviceLocation], the PHONE's own last-known position, via
 * [com.bloo.bluelink.autolock.LocationHelper]. Fails soft (keeps the existing value) without
 * permission or a fix.
 */
internal fun AppViewModel.refreshDeviceLocation() {
    viewModelScope.launch {
        val loc = com.bloo.bluelink.autolock.LocationHelper.currentLocation(getApplication()) ?: return@launch
        _state.update { it.copy(deviceLocation = loc.toDeviceGeoLocation()) }
        // Reverse-geocode the phone's fix so the Location & Weather pebble can label the "your
        // location" block with a place name, not just coordinates. Best-effort: a failure leaves
        // the prior name (or null) and is never surfaced as an error.
        reverseGeocode(loc.toDeviceGeoLocation())?.let { place ->
            _state.update { it.copy(devicePlace = place.full) }
        }
        // Hands this same fix to the weather-follows-device path instead of a separate
        // LocationManager fetch, so the map dot, home weather and "distance to car" agree on where
        // "here" is (see WeatherController.refreshDeviceLocationForWeather).
        weather.refreshDeviceLocationForWeather(loc)
        // And the device-position weather for the pebble's "where you are" half.
        weather.loadPhoneWeather()
    }
}

/**
 * Turn a lat/lon into a short place name (neighbourhood or city). Null means show none; geocoding
 * fails routinely and is not an error. Delegates to the shared
 * [com.bloo.bluelink.data.reverseGeocode].
 */
internal suspend fun AppViewModel.reverseGeocode(loc: GeoLocation): com.bloo.bluelink.data.GeocodedPlace? =
    com.bloo.bluelink.data.reverseGeocode(getApplication(), loc.latitude, loc.longitude)

/**
 * Reverse-geocode [loc] and store the resulting place name/zip for [v]. Best-effort: a failed
 * geocode leaves the maps untouched and returns false. The ONE place the two callers (Locate, and a
 * status refresh that carried GPS) apply a reverse-geocoded place, so they cannot drift on which
 * fields they set.
 */
internal suspend fun AppViewModel.applyPlace(v: Vehicle, loc: GeoLocation): Boolean {
    val place = reverseGeocode(loc) ?: return false
    _state.update {
        it.copy(
            placeNames = it.placeNames + (v.vin to place.full),
            placeZips = it.placeZips + (v.vin to place.compact),
        )
    }
    return true
}

/**
 * Kept in sync by GarageScreen's `LaunchedEffect(expandedMap.vin)` (see [UiState.mapExpanded]).
 * Guarded like [setOnSettingsPageSlot] so panning/zooming the map never re-emits.
 */
fun AppViewModel.setMapExpanded(value: Boolean) {
    if (_state.value.mapExpanded != value) _state.update { it.copy(mapExpanded = value) }
}

fun AppViewModel.setWeatherPlace(query: String) = weather.setWeatherPlace(query)

fun AppViewModel.useDeviceLocationForWeather() = weather.useDeviceLocationForWeather()

/**
 * A device GPS fix as a [GeoLocation] (lat/lon plus speed when present). The one mapping shared
 * with the live-tracking collector so both agree on whether a fix has speed.
 */
internal fun android.location.Location.toDeviceGeoLocation(): GeoLocation =
    GeoLocation(
        latitude,
        longitude,
        if (hasSpeed()) speed.toDouble() else null,
    )
