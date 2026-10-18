package com.bloo.bluelink.wear

import com.bloo.bluelink.data.VehicleSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The Tile's only real decision: which buttons a car gets, and what they say.
 *
 * It is worth a test because it is invisible until someone is wearing the watch. A Kia offered a Flash
 * button it cannot perform, or an EV with no Charge button, would only ever be found by a tester -- and
 * the tile is the one surface a user reaches for without opening the app.
 */
class WearTileActionsTest {

    private fun car(
        isEv: Boolean = true,
        percent: Int? = 50,
        locked: Boolean? = null,
        charging: Boolean? = null,
        climateOn: Boolean? = null,
        brandIndicator: String = "H",
        generation: String = "5",
    ) = VehicleSnapshot(
        vin = "V",
        name = "Car",
        model = "M",
        isEv = isEv,
        hasBattery = isEv,
        brandIndicator = brandIndicator,
        generation = generation,
        percent = percent,
        locked = locked,
        charging = charging,
        climateOn = climateOn,
    )

    @Test
    fun `an EV gets the four everyday controls`() {
        val actions = WearTileActions.forCar(car(locked = false))
        assertEquals(listOf("Lock", "Climate", "Charge", "Flash"), actions.map { it.label })
        assertEquals(listOf("lock", "climate", "charge", "flash"), actions.map { it.key })
    }

    @Test
    fun `the label says what a tap will do, and the fill says what is true`() {
        val actions = WearTileActions.forCar(car(locked = true, charging = true, climateOn = true))
        assertEquals(listOf("Unlock", "Climate", "Stop", "Flash"), actions.map { it.label })
        // Locked, charging and climate-on are lit; Flash never is. A lit "Unlock" means the car IS locked --
        // if the fill followed the button's verb instead, it would read as "already unlocked".
        assertEquals(listOf(true, true, true, false), actions.map { it.active })
    }

    @Test
    fun `nothing is lit on a car that is unlocked, idle and off`() {
        val actions = WearTileActions.forCar(car(locked = false, charging = false, climateOn = false))
        assertEquals(listOf(false, false, false, false), actions.map { it.active })
    }

    @Test
    fun `a car with no battery gets no charge button`() {
        val actions = WearTileActions.forCar(car(isEv = false, percent = null))
        assertEquals(listOf("Lock", "Climate", "Flash"), actions.map { it.label })
        assertFalse(actions.any { it.key == "charge" })
    }

    @Test
    fun `a Kia gets no flash, because its US API has no horn or lights`() {
        val actions = WearTileActions.forCar(car(isEv = false, percent = null, brandIndicator = "K"))
        assertEquals(listOf("Lock", "Climate"), actions.map { it.label })
        assertFalse(actions.any { it.key == "flash" })
    }

    @Test
    fun `the tile follows the controls chosen for the face`() {
        val actions = WearTileActions.forCar(car(locked = false), listOf("refresh", "lock", "none", "horn"))
        assertEquals(listOf("Refresh", "Lock", "Horn"), actions.map { it.label })
    }

    @Test
    fun `a long label keeps its first word on the tile`() {
        assertEquals("Climate", WearTileActions.tileLabel("Climate off"))
        assertEquals("Stop", WearTileActions.tileLabel("Stop charge"))
        assertEquals("Unlock", WearTileActions.tileLabel("Unlock"))
    }
}
