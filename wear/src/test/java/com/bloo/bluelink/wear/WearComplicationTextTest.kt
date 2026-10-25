package com.bloo.bluelink.wear

import com.bloo.bluelink.data.VehicleSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

/** The long complication line: what a face with room for a sentence says, and what it leaves out. */
class WearComplicationTextTest {
    private fun car(
        percent: Int? = null,
        rangeMi: Int? = null,
        locked: Boolean? = null,
        charging: Boolean? = null,
        chargeMinutes: Int? = null,
    ) = VehicleSnapshot(
        vin = "V", name = "Car", model = "M", isEv = true,
        percent = percent, rangeMi = rangeMi, locked = locked, charging = charging, chargeMinutes = chargeMinutes,
    )

    @Test fun aParkedCarSaysChargeRangeAndLock() {
        assertEquals("80% · 248 mi · Locked", complicationLongLine(car(percent = 80, rangeMi = 248, locked = true)))
        assertEquals("80% · 248 mi · Unlocked", complicationLongLine(car(percent = 80, rangeMi = 248, locked = false)))
    }

    @Test fun aChargingCarSaysTimeLeftInPlaceOfRange() {
        assertEquals(
            "65% · 1h 55m left · Locked",
            complicationLongLine(car(percent = 65, rangeMi = 200, locked = true, charging = true, chargeMinutes = 115)),
        )
    }

    @Test fun whatTheCarDoesNotReportIsLeftOut() {
        assertEquals("80%", complicationLongLine(car(percent = 80)))
        assertEquals("80% · 248 mi", complicationLongLine(car(percent = 80, rangeMi = 248)))
        // A charging car that reports no time still shows its range.
        assertEquals("65% · 200 mi", complicationLongLine(car(percent = 65, rangeMi = 200, charging = true)))
        assertEquals("Locked", complicationLongLine(car(locked = true)).substringBefore(" ·"))
    }
}
