package com.bloo.wear.complication

import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WearAction
import com.bloo.wear.R

/**
 * Watch-face complication for the selected car's climate state. Renders a snowflake
 * with on/off text reflecting the live state (SHORT_TEXT or MONOCHROMATIC_IMAGE)
 * and starts/stops climate on tap. All shared behaviour lives in
 * [ToggleStateComplication]; this only supplies what differs.
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
