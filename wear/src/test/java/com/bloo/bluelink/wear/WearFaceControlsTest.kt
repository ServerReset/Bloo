package com.bloo.bluelink.wear

import com.bloo.bluelink.data.CarAction
import com.bloo.bluelink.data.VehicleSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The face's control registry: what a wearer can put in a quarter, and when a car cannot do it. Worth testing
 * because a stored choice is a key that must never change meaning, and a control offered to a car that cannot
 * do it only fails on the wrist.
 */
class WearFaceControlsTest {
    private fun car(isEv: Boolean = true, locked: Boolean? = null, charging: Boolean? = null, climateOn: Boolean? = null, brandIndicator: String = "H") =
        VehicleSnapshot(
            vin = "V", name = "Car", model = "M", isEv = isEv, hasBattery = isEv, brandIndicator = brandIndicator, generation = "5",
            percent = 50, locked = locked, charging = charging, climateOn = climateOn,
        )

    @Test fun `there are at least ten controls to choose from`() {
        assertTrue(WearFaceControls.all.size >= 10, "only ${WearFaceControls.all.size}")
    }

    @Test fun `keys are unique and never the empty marker`() {
        val keys = WearFaceControls.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertFalse(WearFaceControls.NONE in keys)
    }

    @Test fun `the defaults are all real controls`() {
        assertEquals(4, WearFaceControls.defaults.size)
        WearFaceControls.defaults.forEach { assertNotNull(WearFaceControls.byKey(it), it) }
    }

    @Test fun `the lock label says what a tap does`() {
        val lock = WearFaceControls.byKey("lock")!!
        assertEquals("Unlock", lock.label(car(locked = true)))
        assertEquals("Lock", lock.label(car(locked = false)))
        assertTrue(lock.confirm(car(locked = true)), "unlocking asks first")
        assertFalse(lock.confirm(car(locked = false)))
    }

    @Test fun `unlock only always asks first`() {
        assertTrue(WearFaceControls.byKey("unlock_only")!!.confirm(car(locked = true)))
        assertEquals(CarAction.UNLOCK, WearFaceControls.byKey("unlock_only")!!.action)
    }

    @Test fun `a car with no battery is not offered the charge controls`() {
        val gas = car(isEv = false)
        listOf("charge", "charge_on", "charge_off").forEach { assertFalse(WearFaceControls.byKey(it)!!.available(gas), it) }
        listOf("charge", "charge_on", "charge_off").forEach { assertTrue(WearFaceControls.byKey(it)!!.available(car()), it) }
    }

    @Test fun `controls are lit by the state they control`() {
        assertTrue(WearFaceControls.byKey("climate")!!.active(car(climateOn = true)))
        assertFalse(WearFaceControls.byKey("climate")!!.active(car(climateOn = false)))
        assertTrue(WearFaceControls.byKey("charge")!!.active(car(charging = true)))
    }

    @Test fun `an unknown stored choice names nothing`() {
        assertEquals("Empty", WearFaceControls.choiceName("nonsense"))
        assertEquals(null, WearFaceControls.byKey("nonsense"))
    }
}
