package com.bloo.bluelink.ui

import com.bloo.bluelink.data.GeoLocation
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// --- EV charger map search (extracted from AppViewModel) -------------------
//
// The expanded map's "Chargers" layer: toggling it on/off, fetching nearby
// Open Charge Map stations, and the speed/network filters applied to the
// already-fetched list. See AppViewModel.chargerJob's own doc for why a
// superseded fetch is cancelled outright rather than just having its result
// ignored.

/** The expanded map's "Chargers" [MapFeature] -- toggles the layer on/off, and on
 *  the way ON, fetches around [around] if nothing's loaded yet (or [force]s a
 *  fresh fetch regardless, for a genuine re-search after panning far away). A
 *  filter-only change (speed/network) never calls this -- see
 *  [setChargerMinKw]/[toggleChargerNetwork], which re-filter the already-fetched
 *  list instead of re-fetching. */
internal fun AppViewModel.toggleChargersVisible(around: GeoLocation, force: Boolean = false) {
    val showing = !_state.value.chargersVisible
    _state.update { it.copy(chargersVisible = showing) }
    if (showing && (force || _state.value.chargers.isEmpty())) loadNearbyChargers(around)
}

/** Fetches Open Charge Map stations around [around] and replaces [UiState.chargers]
 *  wholesale. A genuine failure (see [com.bloo.bluelink.data.ChargerApi.nearby]'s
 *  own doc -- null, not an empty list) surfaces as [UiState.chargersError] instead
 *  of silently looking like "zero chargers nearby": that exact confusion is a real
 *  report, from a search that actually failed for lack of an API key. */
internal fun AppViewModel.loadNearbyChargers(around: GeoLocation) {
    // Cancels a still-running SUPERSEDED fetch outright -- reachable from a quick
    // double-tap on "Chargers" (off/on before the first fetch lands) or two "Retry"
    // taps in a row -- rather than letting it complete and just discarding its
    // result: the same liveLocationJob/pushJob shape already used elsewhere in this
    // class for "a newer call supersedes an in-flight one."
    chargerJob?.cancel()
    _state.update { it.copy(chargersLoading = true, chargersError = null) }
    chargerJob = viewModelScope.launch {
        val stations = com.bloo.bluelink.data.ChargerApi.nearby(
            around.latitude,
            around.longitude,
            apiKey = appearance.value.chargerApiKey,
        )
        _state.update {
            if (stations != null) {
                it.copy(chargers = stations, chargersLoading = false, chargersError = null)
            } else {
                it.copy(chargersLoading = false, chargersError = "Couldn't reach the charger directory")
            }
        }
    }
}

/** Saves the user's own Open Charge Map API key (blank/null clears it) and, when
 *  [around] is known (the map's retry UI has a location; Settings' own "save key"
 *  field doesn't), re-runs the last search with it -- see
 *  [SettingsStore.setChargerApiKey]'s and [SettingsStore.Appearance.chargerApiKey]'s
 *  own docs. */
internal fun AppViewModel.setChargerApiKey(key: String?, around: GeoLocation?) {
    viewModelScope.launch {
        settingsStore.setChargerApiKey(key)
        if (around != null) loadNearbyChargers(around)
    }
}

/** 0 clears the speed filter entirely ("any speed"). */
internal fun AppViewModel.setChargerMinKw(kw: Int) {
    _state.update { it.copy(chargerFilters = it.chargerFilters.copy(minKw = kw)) }
}

/** Toggles one network name in/out of the filter's allow-list -- an empty
 *  resulting set means "any network", not "no networks match", matching
 *  [com.bloo.bluelink.data.ChargerFilters]'s own doc. */
internal fun AppViewModel.toggleChargerNetwork(network: String) {
    _state.update {
        val current = it.chargerFilters.networks
        val next = if (network in current) current - network else current + network
        it.copy(chargerFilters = it.chargerFilters.copy(networks = next))
    }
}
