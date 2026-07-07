package com.bloo.wear.complication

import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WearAction
import com.bloo.wear.R

/**
 * Watch-face complication for the selected car's climate state. Shows a snowflake
 * with on/off text reflecting the live state, and starts/stops climate on tap.
 */
class ClimateComplication : ToggleStateComplication() {
    override val dataSourceName = "ClimateComplication"
    override val title = "Climate"
    override val action = WearAction.TOGGLE_CLIMATE
    override fun stateOf(snap: VehicleSnapshot) = snap.climateOn
    override fun iconRes(on: Boolean) = R.drawable.ic_shortcut_climate
    override fun text(on: Boolean) = if (on) "On" else "Off"
    override fun description(on: Boolean) = if (on) "Climate on" else "Climate off"
}
