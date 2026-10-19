package com.bloo.bluelink.wear

import com.bloo.bluelink.data.VehicleSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the details page shows, and that it says nothing the car did not report. */
class WearCarFactsTest {
    private fun car(
        isEv: Boolean = true, percent: Int? = null, range: Int? = null, locked: Boolean? = null, speed: Double? = null,
        odometer: String? = null, lastService: Int? = null, interval: Int? = null, engineOn: Boolean? = null,
    ) = VehicleSnapshot(
        vin = "V", name = "Car", model = "M", isEv = isEv, hasBattery = isEv, percent = percent, rangeMi = range,
        locked = locked, speedMph = speed, odometer = odometer, lastServiceMiles = lastService, serviceIntervalMiles = interval,
        engineOn = engineOn,
    )

    @Test fun `an empty reading says nothing`() {
        assertEquals(emptyList(), WearCarFacts.status(car()))
        assertEquals(emptyList(), WearCarFacts.care(car()))
    }

    @Test fun `an ev shows its battery and range, a gas car its engine`() {
        assertEquals(listOf("Battery" to "77%", "Range" to "248 mi"), WearCarFacts.status(car(percent = 77, range = 248)))
        assertEquals(listOf("Engine" to "Running"), WearCarFacts.status(car(isEv = false, engineOn = true)))
    }

    @Test fun `moving is only said when the car reported a speed`() {
        assertEquals(emptyList(), WearCarFacts.status(car()))
        assertEquals(listOf("Moving" to "Parked"), WearCarFacts.status(car(speed = 0.0)))
        assertEquals(listOf("Moving" to "Driving"), WearCarFacts.status(car(speed = 31.0)))
    }

    @Test fun `service counts down from the last service`() {
        assertEquals("Due in 2,500 mi", WearCarFacts.serviceLine(car(odometer = "12,500", lastService = 10_000, interval = 5_000)))
        assertEquals("Overdue by 300 mi", WearCarFacts.serviceLine(car(odometer = "15300", lastService = 10_000, interval = 5_000)))
    }

    @Test fun `service needs all three numbers`() {
        assertNull(WearCarFacts.serviceLine(car(odometer = "12,500", lastService = 10_000)))
        assertNull(WearCarFacts.serviceLine(car(lastService = 10_000, interval = 5_000)))
        assertNull(WearCarFacts.serviceLine(car(odometer = "n/a", lastService = 10_000, interval = 5_000)))
    }
}
