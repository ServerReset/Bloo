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

class ComfortModelTest {
    private val levelsHigh = intArrayOf(3, 0, 0, 0, 2, 1)

    @Test fun roundTripsThroughItsText() {
        val m = ComfortModel().learn(SmartClimateMode.HEAT, levelsHigh)
        val back = ComfortModel.decode(m.encode())
        assertEquals(m.seen, back.seen)
        assertEquals(m.levelFor(ComfortItem.DRIVER, SmartClimateMode.HEAT), back.levelFor(ComfortItem.DRIVER, SmartClimateMode.HEAT))
        assertEquals(null, back.levelFor(ComfortItem.DRIVER, SmartClimateMode.COOL))
    }

    @Test fun learnsThePersonsLevel() {
        val m = ComfortModel().learn(SmartClimateMode.HEAT, intArrayOf(1, 1, 1, 1, 1, 0))
        assertEquals(1.0, m.levelFor(ComfortItem.DRIVER, SmartClimateMode.HEAT))
        val after = m.learn(SmartClimateMode.HEAT, intArrayOf(3, 3, 3, 3, 3, 1))
        assertTrue(after.levelFor(ComfortItem.DRIVER, SmartClimateMode.HEAT)!! > 1.0)
    }

    @Test fun holdingAComfortableDayTeachesNothing() {
        val m = ComfortModel()
        assertEquals(0, m.learn(SmartClimateMode.HOLD, levelsHigh).seen)
    }

    @Test fun learnedLevelReplacesTheRuleDefault() {
        val cold = at(35.0)
        val learned = ComfortModel().learn(SmartClimateMode.HEAT, intArrayOf(0, 0, 0, 0, 0, 0))
        assertEquals(SeatLevel.OFF, smartComfort(cold, all, learned).seatDriver)
    }

    @Test fun comfortLevelsReadAsTheModelScale() {
        val req = ClimateRequest(tempF = 70, defrost = true, durationMinutes = 10, steeringWheelHeat = WheelHeatLevel.HIGH,
            seatFrontLeft = SeatLevel.MED_HEAT, seatFrontRight = SeatLevel.OFF)
        assertEquals(listOf(2, 0, 0, 0, 2, 1), comfortLevelsOf(req, SmartClimateMode.HEAT).toList())
    }

    private val all = ComfortCaps(driverHeat = true, driverCool = true, passHeat = true, passCool = true, wheel = true)
    private fun at(feelsF: Double) = Weather(
        tempC = (feelsF - 32) * 5 / 9, feelsLikeC = (feelsF - 32) * 5 / 9, highC = null, lowC = null,
        windKph = 0.0, humidity = 40, isDay = true, code = 0,
    )
}

class ComfortRequestTest {
    private val base = ClimateRequest(tempF = 70, defrost = false, durationMinutes = 10)
    private val caps = ComfortCaps(driverHeat = true, passHeat = true, rearLeftHeat = true, wheel = true)
    private val coldWeather = Weather(tempC = 0.0, feelsLikeC = 0.0, highC = null, lowC = null, windKph = 0.0, humidity = 40, isDay = true, code = 0)

    @Test fun switchesOffGiveNoComfort() {
        val auto = smartComfort(coldWeather, caps)
        val req = comfortRequest(base, 70, SmartClimateMode.HEAT, auto, caps, ComfortSwitches(seats = false, wheel = false, defrost = false))
        assertEquals(SeatLevel.OFF, req.seatFrontLeft)
        assertEquals(WheelHeatLevel.OFF, req.steeringWheelHeat)
        assertEquals(false, req.defrost)
    }

    @Test fun matchingTheSmartChoiceChangesNothing() {
        val auto = smartComfort(coldWeather, caps)
        val req = comfortRequest(base, 70, SmartClimateMode.HEAT, auto, caps, ComfortSwitches(seats = true, wheel = true, defrost = auto.defrost))
        assertEquals(autoComfortRequest(base, 70, auto).seatFrontLeft, req.seatFrontLeft)
        assertEquals(autoComfortRequest(base, 70, auto).steeringWheelHeat, req.steeringWheelHeat)
    }

    @Test fun onWithoutAnAutoChoiceUsesMediumWhereTheSeatHasIt() {
        val req = comfortRequest(base, 70, SmartClimateMode.HEAT, null, caps, ComfortSwitches(seats = true, wheel = false, defrost = false))
        assertEquals(SeatLevel.MED_HEAT, req.seatFrontLeft)
        assertEquals(SeatLevel.OFF, req.seatRearRight) // the car has no rear-right seat heat
    }
}
