package com.bloo.wear.ui

import com.bloo.wear.CarView
import com.bloo.wear.WearPebbles
import com.bloo.wear.WearTiles
import com.bloo.wear.WearUi

// Which tiles a given car shows, and why -- the POLICY behind the watch's carousel,
// deliberately kept out of HomeScreen.kt, which renders it.
//
// Two reasons to separate them. It is the contract "standalone the watch has every
// feature; linked to a phone it simplifies to quick controls", which is worth being able
// to read in one screenful instead of hunting through 2,700 lines of Compose. And it is
// pure -- lists, sets and booleans, no Compose -- so it can be unit-tested, which it now
// is (WearTileVisibilityTest). Loading it from a test does not drag in HomeScreenKt.

/** Synthetic tile key for the alerts card (not part of the user-orderable set). */
internal const val TILE_ALERTS = "alerts"

/** Number of active warnings (open doors/windows/trunk/hood + tire/fluid/key alerts). */
internal val CarView.alertCount: Int
    get() = doorsOpen.size + windowsOpen.size +
        (if (trunkOpen) 1 else 0) + (if (hoodOpen) 1 else 0) +
        (if (tireWarning) 1 else 0) + (if (lowFuel) 1 else 0) +
        (if (washerLow) 1 else 0) + (if (brakeLow) 1 else 0) + (if (keyFobLow) 1 else 0)

/**
 * Tiles that duplicate a DEEPER phone feature rather than offering a quick glance/control:
 * seat-by-seat heat (Comfort), saved climate presets (Presets), AC/DC charge-limit sliders
 * (Limits), full diagnostics (Diagnostics), car facts like VIN/plate/odometer (Info), and
 * roadside/service links (Assist). None of these are things you reach for mid-walk the way
 * "is it locked" or "start the climate" are -- they're the kind of editing you sit down and
 * do once in a while, which the phone is already better suited for (a real keyboard, a real
 * screen, no bezel to fight).
 *
 * Hidden only while [WearUi.phoneConnected] -- with no phone reachable the watch IS the only
 * way to reach the car at all, so every one of these becomes the opposite of optional and the
 * full tile set returns. This is a deliberate simplification, not a capability gate: unlike
 * [WearTiles.AI] (which is hidden standalone because it genuinely cannot run without the
 * phone's on-device model), every tile named here works perfectly well standalone -- it's
 * just that reaching for a phone already in your pocket beats a temperature slider you have
 * to drag with a fingertip on a 1.4" face.
 */
internal val PHONE_AVAILABLE_DEEP_TILES = setOf(
    WearTiles.COMFORT, WearTiles.PRESETS, WearTiles.LIMITS,
    WearTiles.DIAGNOSTICS, WearTiles.INFO, WearTiles.ASSIST,
)

internal fun visibleTiles(ui: WearUi, car: CarView): List<String> {
    val hasAlerts = car.alertCount > 0
    val out = ArrayList<String>()
    if (hasAlerts) out.add(TILE_ALERTS)
    // Tile order is derived from this car's pebble order, kept in sync with the
    // phone (one pebble can expand into several tiles); pebbles the user hid on
    // the phone are dropped so a hidden section doesn't still show up here.
    val hidden = ui.settings?.hiddenSections?.get(car.vin).orEmpty()
    for (key in WearPebbles.tilesFor(ui.pebbleOrderFor(car.vin), hidden)) {
        if (ui.phoneConnected && key in PHONE_AVAILABLE_DEEP_TILES) continue
        val show = when (key) {
            // Shown whenever it reaches here, so you can save the first preset from the
            // watch with none stored yet. Note this is only reached STANDALONE: Presets
            // is in PHONE_AVAILABLE_DEEP_TILES, so a connected phone skips it above.
            WearTiles.PRESETS -> true
            WearTiles.CHARGE -> car.hasBattery
            // Charge-LIMIT editing needs a brand that reports the targets. Canada never does
            // (reservChargeInfos is always null), so LimitsCard's Apply is permanently dead
            // and its "hasn't reported yet" hint could never clear -- hide the tile there.
            // Charging Start/Stop (CHARGE, above) still works. See Brand.supportsChargeLimits.
            WearTiles.LIMITS -> car.hasBattery && car.brand.supportsChargeLimits
            WearTiles.LOCATION -> car.lat != null && car.lon != null
            // Weather + Smart Climate: shown when we HAVE weather (phone-pushed or
            // watch-fetched), OR when standalone and the car has a known location so
            // the watch can fetch it itself (see WearViewModel.fetchWeatherStandalone)
            // — the card renders its own "loading/no data" state until the fetch lands.
            WearTiles.WEATHER, WearTiles.SMART_CLIMATE ->
                ui.extras.carWeather[car.vin] != null || ui.extras.homeWeather != null ||
                    (!ui.phoneConnected && car.lat != null && car.lon != null)
            WearTiles.DIAGNOSTICS -> car.hasLiveStatus
            // AI runs on the phone's on-device model and can't run on the watch, so
            // it's a phone-only feature: shown only when enabled AND a phone is
            // connected. Standalone, it's hidden entirely (no dead "Summarize" button).
            WearTiles.AI -> ui.settings?.aiEnabled == true && ui.phoneConnected
            else -> true // summary, lock, climate, comfort, info, assist, more
        }
        if (show) out.add(key)
    }
    return out
}
