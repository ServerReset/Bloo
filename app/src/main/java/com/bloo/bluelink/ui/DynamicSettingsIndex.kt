package com.bloo.bluelink.ui

import com.bloo.bluelink.data.SettingsStore

/**
 * Helpers to determine which settings are available in the current mode
 * (simple vs. advanced) and should appear in search results.
 *
 * When in simple mode, advanced-only settings are completely hidden from
 * search and cannot be accessed via any search/command interface.
 */

/**
 * Settings that are only visible in advanced mode. These are completely
 * hidden when state.settingsMode == "simple".
 *
 * Per-car settings have car names appended (e.g., "License plate · Ioniq"),
 * so we match by prefix for those.
 */
internal val ADVANCED_ONLY_SETTINGS = setOf(
    // Per-car settings in Advanced mode only
    "Default climate start", // Car settings card - advanced-only per-car setting

    // App-wide settings in Advanced mode only
    "Palette override", // Appearance card - custom palette override
    "Custom palette", // Appearance card - custom palette creation

    // Identity & service group (Advanced only) - these have car names appended
    "License plate · ", // Prefix for "License plate · <car name>"
    "VIN · ", // Prefix for "VIN · <car name>"
    "Last service · ", // Prefix for "Last service · <car name>"
    "Service interval · ", // Prefix for "Service interval · <car name>"
)

/**
 * Check if a setting should be visible in the current mode.
 * Returns true if the setting is available, false if hidden in current mode.
 */
internal fun isSettingAvailableInMode(
    settingTitle: String,
    settingsMode: String,
): Boolean {
    if (settingsMode == "advanced") return true
    // Check for exact match or prefix match (for per-car settings with car names)
    return !ADVANCED_ONLY_SETTINGS.any { prefix ->
        settingTitle == prefix || settingTitle.startsWith(prefix)
    }
}

/**
 * Settings that show per-car data that should always be searchable
 * regardless of mode.
 */
internal val PER_CAR_DATA_SETTINGS = setOf(
    "License plate",
    "Odometer",
    "VIN",
    "Range",
    "Battery",
    "Fuel",
    "Last refreshed",
    "Location",
    "Powertrain",
    "Head-unit generation",
    "Last service",
    "Service interval",
)

/**
 * Settings that are advanced-only and should be filtered out in simple mode.
 * These are typically climate defaults, customization options, etc.
 */
internal val ADVANCED_FEATURES = setOf(
    "Default climate start",
    "Custom palette",
)
