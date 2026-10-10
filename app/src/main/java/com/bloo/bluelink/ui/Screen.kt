package com.bloo.bluelink.ui

import com.bloo.bluelink.data.Vehicle

sealed interface Screen {
    /**
     * The bootstrapping state, and ONLY the bootstrapping state: shown for the brief window between
     * process start and the cold-start auto-login coroutine (see AppViewModel's init block)
     * determining whether this is a genuinely logged-out device (-> Login) or a returning signed-in
     * one (-> whatever loadGarage resolves once its network call returns).
     */
    data object Loading : Screen
    data object Login : Screen
    /** First-run deck of welcome cards: restore, setup, look and feel, and a card per car. */
    data object Onboarding : Screen
    /** The same deck, with just a card per newly detected car (post-first-run). */
    data class CarSetup(val vins: List<String>) : Screen
    /**
     * Main screen: the car carousel/grid. Zero-vehicle accounts land here too, via GarageStatusCard
     * (Guard.kt) as a pager page.
     */
    data object Garage : Screen
}

/**
 * Reserved pseudo-VIN for Settings cards so they share car pebbles' collapse state. No real VIN
 * collides.
 */
internal const val SETTINGS_CARD_VIN = "__settings__"

/**
 * Placeholder [Vehicle] so [SettingsCard] can reuse [AppViewModel.togglePebble] keyed on `v.vin`.
 * Only `vin` is read; the other fields are empty placeholders and never shown.
 */
internal val SettingsPseudoVehicle = Vehicle(
    vin = SETTINGS_CARD_VIN,
    regId = "",
    name = "",
    model = "",
    generation = "",
    brandIndicator = "",
    isEv = false,
)
