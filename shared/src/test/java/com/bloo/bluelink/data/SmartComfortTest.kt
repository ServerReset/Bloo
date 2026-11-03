package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmartComfortTest {
    private val all = ComfortCaps(driverHeat = true, driverCool = true, passHeat = true, passCool = true, wheel = true)

    /** A reading at [feelsF] (apparent) with [humidity] percent. */
    private fun at(feelsF: Double, humidity: Int? = 40) = Weather(
        tempC = (feelsF - 32) * 5 / 9, feelsLikeC = (feelsF - 32) * 5 / 9, highC = null, lowC = null,
        windKph = 0.0, humidity = humidity, isDay = true, code = 0,
    )

    @Test fun coldDayWarmsSeatsAndWheel() {
        val c = smartComfort(at(35.0), all)
        assertEquals(SeatLevel.MED_HEAT, c.seatDriver)
        assertEquals(SeatLevel.MED_HEAT, c.seatPassenger)
        assertEquals(WheelHeatLevel.HIGH, c.wheel)
    }

    @Test fun hotDayCoolsSeats() {
        val c = smartComfort(at(90.0), all)
        assertEquals(SeatLevel.MED_COOL, c.seatDriver)
        assertEquals(WheelHeatLevel.OFF, c.wheel)
        assertFalse(c.defrost)
    }

    @Test fun mildDayDoesNothing() {
        val c = smartComfort(at(65.0), all)
        assertEquals(SeatLevel.OFF, c.seatDriver)
        assertTrue(c.reasons.isEmpty())
    }

    @Test fun carWithoutSeatsOrWheelIsLeftAlone() {
        val c = smartComfort(at(35.0), ComfortCaps())
        assertEquals(SeatLevel.OFF, c.seatDriver)
        assertEquals(WheelHeatLevel.OFF, c.wheel)
    }

    @Test fun dampColdDayDefrosts() {
        assertTrue(smartComfort(at(50.0, humidity = 85), all).defrost)
        assertFalse(smartComfort(at(50.0, humidity = 40), all).defrost)
    }

    @Test fun frostingDefrostsRegardlessOfHumidity() {
        assertTrue(smartComfort(at(30.0, humidity = 20), all).defrost)
    }
}
