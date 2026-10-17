package com.bloo.bluelink.wear

import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.supportsHornLights

/**
 * The watch Tile's action protocol, and the tile's only real decision.
 *
 * A Tile's click can only be an Action -- it cannot run code -- so a button on the tile launches the
 * app with one of these keys in its extras, and the app runs it through the same PIN gate and command
 * layer as a tap on the watch face. The keys therefore live here, with the actions, rather than on the
 * Activity that happens to receive them.
 *
 * [forCar] is a pure function and deliberately not inside [BlooTileService]: a Service cannot be unit
 * tested without a device, and "which buttons does this car get, and what do they say" is worth
 * testing -- it is the difference between a tile that can charge an EV and one that offers Flash to a
 * car with no lights to flash.
 */
object WearTileActions {
    const val KEY_LOCK = "lock"
    const val KEY_CLIMATE = "climate"
    const val KEY_CHARGE = "charge"
    const val KEY_FLASH = "flash"

    /**
     * The actions worth a tap from the carousel, in the order they are reached for.
     *
     * Lock and Climate are always there. The third is whichever of Charge or Flash this car can
     * actually do: an EV gets Charge (or Stop, while it is charging), and a car with no battery gets
     * Flash only if its brand has horn/lights at all -- Kia's US API has neither, so a Kia gets two
     * buttons rather than a third that would fail.
     */
    fun forCar(car: VehicleSnapshot): List<WearTileAction> = buildList {
        add(
            WearTileAction(
                label = if (car.locked == true) "Unlock" else "Lock",
                key = KEY_LOCK,
                // The fill tracks the CAR, not the button's verb: lit means the car is locked, exactly
                // as lit on Climate means the climate is running. The label says what a tap will do,
                // the fill says what is true -- and these two must not be the same thing, or a lit
                // "Unlock" would read as "already unlocked".
                active = car.locked == true,
            ),
        )
        add(
            WearTileAction(
                label = "Climate",
                key = KEY_CLIMATE,
                active = car.climateOn == true,
            ),
        )
        if (car.hasBattery) {
            add(
                WearTileAction(
                    label = if (car.charging == true) "Stop" else "Charge",
                    key = KEY_CHARGE,
                    active = car.charging == true,
                ),
            )
        } else if (car.toVehicle().supportsHornLights) {
            add(WearTileAction(label = "Flash", key = KEY_FLASH, active = false))
        }
    }
}

/** One action on the watch's Tile: what it says, which command the app runs, and whether it is on. */
data class WearTileAction(val label: String, val key: String, val active: Boolean)
