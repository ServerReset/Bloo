package com.bloo.bluelink.widget

import com.bloo.bluelink.R
import com.bloo.bluelink.data.WearAction

/**
 * An action that can be assigned to one of a widget's four buttons. [requiresAuth]
 * gates the tap behind a biometric / device-credential prompt before it runs.
 */
enum class WidgetAction(
    val key: String,
    val label: String,
    val icon: Int,
    val requiresAuth: Boolean,
    val kind: Kind,
    val wearAction: String? = null,
) {
    DOORS("doors", "Doors", R.drawable.ic_shortcut_lock, true, Kind.COMMAND, WearAction.TOGGLE_LOCK),
    LOCK("lock", "Lock", R.drawable.ic_shortcut_lock, true, Kind.COMMAND, WearAction.LOCK),
    UNLOCK("unlock", "Unlock", R.drawable.ic_shortcut_unlock, true, Kind.COMMAND, WearAction.UNLOCK),
    CLIMATE("climate", "Climate", R.drawable.ic_shortcut_climate, true, Kind.COMMAND, WearAction.TOGGLE_CLIMATE),
    CLIMATE_ON("climate_on", "Climate on", R.drawable.ic_shortcut_climate, true, Kind.COMMAND, WearAction.CLIMATE_ON),
    CLIMATE_OFF("climate_off", "Climate off", R.drawable.ic_shortcut_climate, true, Kind.COMMAND, WearAction.CLIMATE_OFF),
    CHARGE("charge", "Charge", R.drawable.ic_widget_bolt, true, Kind.COMMAND, WearAction.TOGGLE_CHARGE),
    REFRESH("refresh", "Refresh", R.drawable.ic_widget_refresh, false, Kind.REFRESH),
    LOCATION("location", "Location", R.drawable.ic_widget_location, true, Kind.LOCATION),
    OPEN("open", "Open app", R.drawable.ic_shortcut_car, false, Kind.OPEN);

    /**
     * Groups actions by how the widget/worker must actually handle a tap:
     * - [COMMAND]: sends a BlueLink/wear command (lock, unlock, climate, charge) and needs a response.
     * - [REFRESH]: just re-fetches and redraws current vehicle status, no command sent.
     * - [LOCATION]: fetches/display the vehicle's last known location.
     * - [OPEN]: simply launches the main app, bypassing any auth or worker dispatch.
     * Callers (e.g. the widget's click handler) switch on this to decide which code path to run.
     */
    enum class Kind { COMMAND, REFRESH, LOCATION, OPEN }

    companion object {
        /** All defined actions, in declaration order -- used to populate the widget-config picker UI. */
        val ALL = entries

        /** Looks up an action by its persisted [key] string (as stored in widget prefs); returns
         *  null if the key is missing/unrecognized so callers can fall back to a default. */
        fun fromKey(key: String?): WidgetAction? = entries.firstOrNull { it.key == key }

        /** Sensible out-of-the-box button set for a fresh widget. */
        val DEFAULTS = listOf(DOORS, CLIMATE, REFRESH, LOCATION)
    }
}
