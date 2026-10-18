package com.bloo.bluelink.wear

import com.bloo.bluelink.data.VehicleSnapshot

/**
 * The watch Tile's action protocol, and the tile's only real decision.
 *
 * A Tile's click can only be an Action -- it cannot run code -- so a button on the tile launches the
 * app with one of these keys in its extras, and the app runs it through the same PIN gate and command
 * layer as a tap on the watch face. The key is the control's own key from [WearFaceControls], so the tile
 * and the face are one list and cannot disagree about what a button does.
 *
 * [forCar] is a pure function and deliberately not inside [BlooTileService]: a Service cannot be unit
 * tested without a device, and "which buttons does this car get, and what do they say" is worth
 * testing -- it is the difference between a tile that can charge an EV and one that offers Flash to a
 * car with no lights to flash.
 */
object WearTileActions {
    /**
     * The tile's buttons: the controls the wearer chose for the face ([slots], in the face's order), each only if
     * this car can do it. A Kia is not offered Flash, and a car with no battery is not offered Charge, so the
     * tile never carries a button that would fail.
     */
    fun forCar(car: VehicleSnapshot, slots: List<String> = WearFaceControls.defaults): List<WearTileAction> =
        slots.mapNotNull { key -> WearFaceControls.byKey(key)?.takeIf { it.available(car) } }.map { control ->
            WearTileAction(
                label = tileLabel(control.label(car)),
                key = control.key,
                // The fill tracks the CAR, not the button's verb: lit means the car is locked, exactly as lit on
                // Climate means the climate is running. The label says what a tap will do, the fill says what is
                // true -- and these two must not be the same thing, or a lit "Unlock" would read as "already
                // unlocked".
                active = control.active(car) || (control.key == "lock" && car.locked == true),
            )
        }

    /** A tile button is narrow: a long label ("Climate off", "Stop charge") keeps its first word. */
    internal fun tileLabel(label: String) = if (label.length > 8) label.substringBefore(' ') else label
}

/** One action on the watch's Tile: what it says, which command the app runs, and whether it is on. */
data class WearTileAction(val label: String, val key: String, val active: Boolean)
