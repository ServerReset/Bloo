package com.bloo.wear.complication

import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.WearAction
import com.bloo.wear.R

/**
 * Watch-face complication for the selected car's lock state. Shows a closed or
 * open padlock reflecting the live state, and toggles lock/unlock on tap.
 */
class LockComplication : ToggleStateComplication() {
    override val dataSourceName = "LockComplication"
    override val title = "Lock"
    override val action = WearAction.TOGGLE_LOCK
    override fun stateOf(snap: VehicleSnapshot) = snap.locked
    override fun iconRes(on: Boolean) = if (on) R.drawable.ic_shortcut_lock else R.drawable.ic_shortcut_unlock
    override fun text(on: Boolean) = if (on) "Locked" else "Unlocked"
    override fun description(on: Boolean) = if (on) "Locked" else "Unlocked"
}
