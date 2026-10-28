package com.bloo.bluelink.ui

import kotlin.test.Test
import kotlin.test.assertEquals

/** The climate stepper moves one whole degree in the unit the car is shown in, and never past the car's range. */
class ClimateStepTest {
    @Test fun fahrenheitStepsByOneDegree() {
        assertEquals(73, stepTempF(72, +1, fahrenheit = true))
        assertEquals(71, stepTempF(72, -1, fahrenheit = true))
    }

    @Test fun fahrenheitStopsAtTheEnds() {
        assertEquals(82, stepTempF(82, +1, fahrenheit = true))
        assertEquals(62, stepTempF(62, -1, fahrenheit = true))
    }

    @Test fun celsiusStepsByOneWholeDegreeOfCelsius() {
        // 72°F is 22°C; one degree up is 23°C, which is 73.4°F, shown as 73.
        assertEquals(73, stepTempF(72, +1, fahrenheit = false))
        // One degree down is 21°C, which is 69.8°F, shown as 70.
        assertEquals(70, stepTempF(72, -1, fahrenheit = false))
    }

    @Test fun celsiusStopsAtTheEnds() {
        assertEquals(82, stepTempF(82, +1, fahrenheit = false))
        assertEquals(62, stepTempF(62, -1, fahrenheit = false))
    }
}
