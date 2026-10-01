package com.bloo.bluelink.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.bloo.bluelink.ui.ColorPalette
import com.bloo.bluelink.ui.CustomPaletteData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

suspend fun SettingsStore.setColorPalette(palette: ColorPalette) {
    editTracked { it[SettingsStore.Keys.PALETTE] = palette.name }
}


/** Shared decode-or-default for the repeated
 *  `runCatching { json.decodeFromString(serializer, raw) }.getOrElse { default }`
 *  pattern used to read JSON-encoded prefs — a corrupt/foreign/renamed stored
 *  value falls back to [default] rather than throwing. The [json] instance is
 *  passed in (climateJson vs paletteJson, both ignoreUnknownKeys=true but kept
 *  explicit per section) rather than hardcoded here. */
internal fun <T> SettingsStore.decodeJsonOr(json: Json, serializer: DeserializationStrategy<T>, raw: String, default: T): T =

    runCatching { json.decodeFromString(serializer, raw) }.getOrElse { default }


/** Last-used climate settings for a car, restored when the pebble reopens. */
suspend fun SettingsStore.savedClimate(vin: String): ClimateRequest? {
    val raw = context.settingsDataStore.data.first()[stringPreferencesKey("climate_$vin")] ?: return null
    return runCatching { climateJson.decodeFromString(ClimateRequest.serializer(), raw) }.getOrNull()
}


suspend fun SettingsStore.saveClimate(vin: String, req: ClimateRequest) {
    editTracked {
        it[stringPreferencesKey("climate_$vin")] = climateJson.encodeToString(ClimateRequest.serializer(), req)
    }
}


/** User-named climate presets for a car. */
suspend fun SettingsStore.climatePresets(vin: String): List<ClimatePreset> =

    climatePresets(vin, context.settingsDataStore.data.first())


fun SettingsStore.climatePresets(vin: String, p: Preferences): List<ClimatePreset> {
    val raw = p[stringPreferencesKey("climate_presets_$vin")] ?: return emptyList()
    return decodeJsonOr(climateJson, presetListSerializer, raw, emptyList())
}


/** Insert-or-replace by id: the whole preset list is re-read, decoded, the
 *  matching entry (by [ClimatePreset.id]) is replaced in place if found or
 *  appended if not, then the entire list is re-encoded and written back as
 *  one JSON string — there's no partial-update of a single preset within
 *  the stored JSON, the whole array is always rewritten. */
suspend fun SettingsStore.saveClimatePreset(vin: String, preset: ClimatePreset) {
    val existing = climatePresets(vin).toMutableList()
    val idx = existing.indexOfFirst { it.id == preset.id }
    if (idx >= 0) existing[idx] = preset else existing.add(preset)
    editTracked {
        it[stringPreferencesKey("climate_presets_$vin")] = climateJson.encodeToString(presetListSerializer, existing)
    }
}


suspend fun SettingsStore.deleteClimatePreset(vin: String, id: String) {
    val updated = climatePresets(vin).filter { it.id != id }
    editTracked {
        it[stringPreferencesKey("climate_presets_$vin")] = climateJson.encodeToString(presetListSerializer, updated)
    }
}


/** Persist a full, reordered preset list for a car. */
suspend fun SettingsStore.setClimatePresets(vin: String, presets: List<ClimatePreset>) {
    editTracked {
        it[stringPreferencesKey("climate_presets_$vin")] = climateJson.encodeToString(presetListSerializer, presets)
    }
}


private suspend fun SettingsStore.readCustomPalettes(): List<CustomPaletteData> {
    val raw = context.settingsDataStore.data.first()[SettingsStore.Keys.CUSTOM_PALETTES] ?: return emptyList()
    return decodeJsonOr(paletteJson, paletteListSerializer, raw, emptyList())
}


/** Insert or replace a custom palette by id. */
suspend fun SettingsStore.saveCustomPalette(palette: CustomPaletteData) {
    val updated = readCustomPalettes().filter { it.id != palette.id } + palette
    editTracked {
        it[SettingsStore.Keys.CUSTOM_PALETTES] = paletteJson.encodeToString(paletteListSerializer, updated)
    }
}


/** Remove a custom palette; clears the active id if it matches. */
suspend fun SettingsStore.deleteCustomPalette(id: String) {
    val updated = readCustomPalettes().filter { it.id != id }
    editTracked { prefs ->
        prefs[SettingsStore.Keys.CUSTOM_PALETTES] = paletteJson.encodeToString(paletteListSerializer, updated)
        if (prefs[SettingsStore.Keys.ACTIVE_CUSTOM_PALETTE_ID] == id) prefs.remove(SettingsStore.Keys.ACTIVE_CUSTOM_PALETTE_ID)
    }
}


/** Set which custom palette is active (null = use a built-in palette). */
suspend fun SettingsStore.setActiveCustomPaletteId(id: String?) {
    editTracked {
        if (id == null) it.remove(SettingsStore.Keys.ACTIVE_CUSTOM_PALETTE_ID)
        else it[SettingsStore.Keys.ACTIVE_CUSTOM_PALETTE_ID] = id
    }
}


/** Sets or clears (blank/null) the user's own Open Charge Map API key -- see
 *  [SettingsStore.Appearance.chargerApiKey]'s own doc. */
suspend fun SettingsStore.setChargerApiKey(key: String?) {
    editTracked {
        if (key.isNullOrBlank()) it.remove(SettingsStore.Keys.CHARGER_API_KEY) else it[SettingsStore.Keys.CHARGER_API_KEY] = key.trim()
    }
}


/** Set or clear the weather location. Passing null lat/lon clears it. Always
 *  resets [SettingsStore.Appearance.weatherFollowsDevice] to false -- every caller of this
 *  EXCEPT [setWeatherFromDeviceLocation] is setting an explicit, static
 *  location (a typed place, or clearing it entirely), and that one turns the
 *  flag back on itself, right after calling this. */
suspend fun SettingsStore.setWeatherLocation(lat: Double?, lon: Double?, label: String?) {
    editTracked {
        if (lat == null || lon == null) {
            it.remove(SettingsStore.Keys.WEATHER_LAT)
            it.remove(SettingsStore.Keys.WEATHER_LON)
            it.remove(SettingsStore.Keys.WEATHER_LABEL)
        } else {
            it[SettingsStore.Keys.WEATHER_LAT] = lat.toString()
            it[SettingsStore.Keys.WEATHER_LON] = lon.toString()
            if (label.isNullOrBlank()) it.remove(SettingsStore.Keys.WEATHER_LABEL) else it[SettingsStore.Keys.WEATHER_LABEL] = label
        }
        it.remove(SettingsStore.Keys.WEATHER_FOLLOWS_DEVICE)
    }
}


/** Set the home weather location from this device's own last-known GPS
 *  fix, reverse-geocoded to a place label -- the phone Settings screen's
 *  "My location" action. Returns false when no location is available
 *  (e.g. permission never granted on this device) so the caller can
 *  report that clearly.
 *
 *  [preloaded], when given, is used AS-IS instead of this function doing
 *  its own LocationManager fetch -- specifically so AppViewModel's periodic
 *  device-location refresh (fused location, the same fix that becomes
 *  [com.bloo.bluelink.ui.UiState.deviceLocation] and the dot drawn on the
 *  car map) and the weather-follows-device location are the SAME reading,
 *  not two independently-fetched ones that can legitimately disagree by
 *  city blocks. Reported directly: the map's device dot, the home weather
 *  card and "how far is the car from me" could each show a different spot
 *  for "here". A caller with no pre-fetched location (the Settings screen's
 *  manual "My location" button) still gets the original LocationManager-based
 *  fetch below. */
suspend fun SettingsStore.setWeatherFromDeviceLocation(preloaded: android.location.Location? = null): Boolean {
    val loc = preloaded ?: run {
        // GetLastKnownLocation requires an active location grant; fail fast and
        // explicitly instead of relying on the SecurityException throw inside
        // the runCatching below to do the same thing. Keep the runCatching
        // anyway -- TIME is revoked mid-call by the user sometimes, and a
        // missed weather label must never crash a settings click.
        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_COARSE_LOCATION,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        runCatching {
            val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
            listOf(
                android.location.LocationManager.GPS_PROVIDER,
                android.location.LocationManager.NETWORK_PROVIDER,
                android.location.LocationManager.PASSIVE_PROVIDER,
            ).firstNotNullOfOrNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
        }.getOrNull() ?: return false
    }
    // @Suppress("DEPRECATION"): the sync Geocoder is Java-deprecated in favour of
    // the API-33+ listener overload, but the sync form is the only one that
    // exists on every supported API level (minSdk 26) without a second,
    // listener-shaped implementation. The whole read is runCatching-wrapped.
    @Suppress("DEPRECATION")
    val label = runCatching {
        android.location.Geocoder(context, java.util.Locale.getDefault())
            .getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.let { a ->
                listOfNotNull(a.locality ?: a.subAdminArea, a.adminArea).distinct()
                    .joinToString(", ")
                    // .ifBlank, because the `?: "My location"` below only catches a NULL
                    // geocode. An Address whose locality, subAdminArea AND adminArea are
                    // all null -- offshore, or a sparse country -- makes joinToString
                    // return "", which is non-null, so it sailed past the fallback and
                    // setWeatherLocation stored a label of no label at all. The
                    // forward-geocode path in AppViewModel already guards this way.
                    .ifBlank { "My location" }
            }
    }.getOrNull() ?: "My location"
    setWeatherLocation(loc.latitude, loc.longitude, label)
    // Re-set AFTER setWeatherLocation, which unconditionally clears this flag
    // (see its own doc) -- this is the one call site that's allowed to turn it
    // back on, marking the location as "following the device" so a later
    // refresh (WeatherController.refreshDeviceLocationForWeather) knows to
    // re-run this same fetch instead of leaving it frozen at this one fix.
    editTracked { it[SettingsStore.Keys.WEATHER_FOLLOWS_DEVICE] = "true" }
    return true
}
