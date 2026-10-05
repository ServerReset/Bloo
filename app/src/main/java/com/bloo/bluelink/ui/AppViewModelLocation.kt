package com.bloo.bluelink.ui

import android.location.Geocoder
import androidx.lifecycle.viewModelScope
import com.bloo.bluelink.data.GeoLocation
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- Device location, reverse geocoding, and weather place (extracted from AppViewModel) --

/**
 * Refreshes [UiState.deviceLocation] -- the PHONE's own last-known position, not any
 * car's. Best-effort via the same fused-location helper
 * ([com.bloo.bluelink.autolock.LocationHelper]) used elsewhere; fails soft (leaves whatever value
 * was already there) with no permission or no fix, the same way [locate] already
 * treats a failed car GPS fix. Fire-and-forget: callers do not await this, since a
 * missing/slow device fix should never hold up whatever ELSE they were doing (loading
 * the garage, refreshing a car's status, locating the car).
 *
 * Called on cold start/app open ([loadGarageInner]), on every pull-to-refresh
 * ([refreshStatus]), and again by [locate] itself -- reported directly: tapping
 * "Locate" only ever refreshed the CAR's position, leaving this one stale until the
 * next unrelated refresh happened to touch it.
 */
internal fun AppViewModel.refreshDeviceLocation() {
    viewModelScope.launch {
        val loc = com.bloo.bluelink.autolock.LocationHelper.currentLocation(getApplication()) ?: return@launch
        _state.update { it.copy(deviceLocation = loc.toDeviceGeoLocation()) }
        // Reverse-geocode the phone's fix so the Location & Weather pebble can label the
        // "your location" block with a place name, not just coordinates. Best-effort: a
        // failure leaves the prior name (or null) and is never surfaced as an error.
        reverseGeocode(loc.toDeviceGeoLocation())?.let { place ->
            _state.update { it.copy(devicePlace = place.full) }
        }
        // Hands this SAME fused-location fix to the weather-follows-device path
        // (its own persisted flag, checked inside) rather than letting it do its
        // own separate LocationManager fetch -- see
        // WeatherController.refreshDeviceLocationForWeather's own doc for why:
        // reported directly as the map's device dot, the home weather card and
        // "distance to car" not agreeing on where "here" is.
        weather.refreshDeviceLocationForWeather(loc)
        // And the live device-position weather readout for the Location & Weather
        // pebble's "where you are" half -- same fix, same schedule as the dot.
        weather.loadPhoneWeather()
    }
}

/**
 * Turn a lat/lon into a short human-readable place name (a neighbourhood or city).
 * Null means "just don't show a place name" -- geocoding fails routinely (no
 * network, unsupported locale, nothing at those coordinates) and no caller treats
 * that as an error worth surfacing.
 *
 * Delegates to the shared [com.bloo.bluelink.data.reverseGeocode]. This was its own
 * copy until now, using only the deprecated blocking Geocoder overload on every
 * device, with no isPresent() check.
 *
 * The KDoc this replaces argued for its withTimeoutOrNull on the grounds that "the
 * async listener API needs API 33+, and this needs to work below that" -- true of
 * the fallback, but it meant the phone never took the 33+ path at all, and the
 * timeout it was defending could not fire around a blocking call anyway. Both
 * points are addressed where the implementation now lives.
 */
internal suspend fun AppViewModel.reverseGeocode(loc: GeoLocation): com.bloo.bluelink.data.GeocodedPlace? =
    com.bloo.bluelink.data.reverseGeocode(getApplication(), loc.latitude, loc.longitude)

/** Kept in sync by GarageScreen's own `LaunchedEffect(expandedMap.vin)` -- see
 *  [UiState.mapExpanded]'s own doc. Guarded the same way [setOnSettingsPageSlot]
 *  is, so panning/zooming the expanded map (which doesn't change `vin`) never
 *  re-emits this. */
fun AppViewModel.setMapExpanded(value: Boolean) {
    if (_state.value.mapExpanded != value) _state.update { it.copy(mapExpanded = value) }
}

fun AppViewModel.setWeatherPlace(query: String) = weather.setWeatherPlace(query)

fun AppViewModel.useDeviceLocationForWeather() = weather.useDeviceLocationForWeather()

/**
 * A device GPS fix as a [GeoLocation]: lat/lon plus speed when the fix carries one.
 *
 * Both places that publish the phone's own location -- [refreshDeviceLocation] and the
 * live-tracking collector in AppViewModelCommands -- built this identical three-line
 * `GeoLocation(...)` inline, including the same `hasSpeed()` guard. One mapping so the two
 * can't drift on whether a fix has a speed (which drives "is the DEVICE moving").
 */
internal fun android.location.Location.toDeviceGeoLocation(): GeoLocation =
    GeoLocation(
        latitude,
        longitude,
        if (hasSpeed()) speed.toDouble() else null,
    )
