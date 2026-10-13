package com.bloo.bluelink.ui

import com.bloo.bluelink.data.settingsMode

/**
 * Helpers to determine which settings are available in the current mode (simple vs. advanced) and
 * should appear in search results. When in simple mode, advanced-only settings are completely
 * hidden from search and cannot be accessed via any search/command interface.
 */

/**
 * Settings that are only visible in advanced mode. These are completely
 * hidden when state.settingsMode == "simple".
 *
 * Per-car settings have car names appended (e.g., "Licence plate · Ioniq"),
 * so we match by prefix for those.
 */
internal val ADVANCED_ONLY_SETTINGS = setOf(
    // Per-car settings in Advanced mode only
    "Default climate start", // Car settings card - advanced-only per-car setting
    "Temperature unit", // Units group - per-measurement override
    "Distance unit", // Units group - per-measurement override
    "Car-running alerts", // Notifications card - advanced-only
    "Car-started alerts", // Notifications card - advanced-only
    "Left-unlocked alert delay", // Notifications card - advanced-only minutes field
    "Door-open alert delay", // Notifications card - advanced-only minutes field
    "Car-running alert delay", // Notifications card - advanced-only minutes field
    "Aurora motion", // Look card - advanced-only background group

    // App-wide settings in Advanced mode only
    "Palette override", // Appearance card - custom palette override
    "Custom palette", // Appearance card - custom palette creation

    // Identity paperwork (Advanced only) - these have car names appended. The SERVICE fields are
    // not here: the car card shows them in Simple mode too (they drive the "service due" alert), so
    // search must find them there as well, not hide what the card shows.
    "Licence plate · ", // Prefix for "Licence plate · <car name>"
    "VIN · ", // Prefix for "VIN · <car name>"
)

/**
 * Check if a setting should be visible in the current mode. Returns true if the setting is
 * available, false if hidden in current mode.
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
