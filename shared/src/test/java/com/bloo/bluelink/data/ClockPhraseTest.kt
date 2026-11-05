package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClockPhraseTest {
    @Test fun meridiemTimes() {
        assertEquals(21 * 60, parseClockMinute("climate off at 9 pm"))
        assertEquals(21 * 60, parseClockMinute("climate off at 9:00 p.m."))
        assertEquals(21 * 60 + 30, parseClockMinute("9:30pm"))
        assertEquals(9 * 60, parseClockMinute("at 9am"))
        assertEquals(0, parseClockMinute("at 12 am"))
        assertEquals(12 * 60, parseClockMinute("at 12 pm"))
    }

    @Test fun twentyFourHourTimes() {
        assertEquals(21 * 60, parseClockMinute("climate off at 21:00"))
        assertEquals(23 * 60 + 15, parseClockMinute("at 23:15"))
    }

    @Test fun bareHourUsesTheNaturalReading() {
        assertEquals(9 * 60, parseClockMinute("lock the car for 9"))
        assertEquals(18 * 60, parseClockMinute("lock the car at 6"))
        assertEquals(13 * 60, parseClockMinute("at 1"))
        assertEquals(12 * 60, parseClockMinute("at 12"))
    }

    @Test fun wordsAndHalfHours() {
        assertEquals(12 * 60, parseClockMinute("at noon"))
        assertEquals(0, parseClockMinute("until midnight"))
        assertEquals(8 * 60 + 15, parseClockMinute("for 8:15"))
    }

    @Test fun temperaturesAreNotTimes() {
        assertNull(parseClockMinute("start climate at 72 degrees"))
        assertNull(parseClockMinute("climate at 70"))
        assertNull(parseClockMinute("72 degrees"))
    }

    @Test fun strayNumbersAreSkipped() {
        assertEquals(9 * 60, parseClockMinute("lock 2 cars at 9"))
    }

    @Test fun noTimeNamed() {
        assertNull(parseClockMinute("start climate"))
        assertNull(parseClockMinute("lock the car"))
    }

    @Test fun minutesUntilWrapsPastMidnight() {
        assertEquals(60, minutesUntil(20 * 60, 21 * 60))
        assertEquals(10 * 60, minutesUntil(23 * 60, 9 * 60))
        assertEquals(0, minutesUntil(9 * 60, 9 * 60))
    }
}
