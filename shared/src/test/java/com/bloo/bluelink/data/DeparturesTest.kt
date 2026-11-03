package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeparturesTest {
    private val eight = DepartureSlot(enabled = true, minuteOfDay = 8 * 60, days = DAYS_WEEKDAYS)
    private val off = DepartureSlot(enabled = false)

    @Test fun slotRoundTrips() {
        val s = DepartureSlot(enabled = true, minuteOfDay = 7 * 60 + 15, days = DAYS_EVERY, smart = false, targetF = 68, seats = true, wheel = true, defrost = true)
        assertEquals(s, DepartureSlot.decode(s.encode()))
    }

    @Test fun scheduleRoundTrips() {
        val sched = DepartureSchedule(listOf(eight, off))
        assertEquals(sched, DepartureSchedule.decode(sched.encode()))
    }

    @Test fun dueInTheWindowBeforeTheDeparture() {
        val sched = DepartureSchedule(listOf(eight, off))
        // Monday (0), 7:35: inside the window that starts 30 minutes before 8:00.
        assertEquals(0, sched.dueSlot(7 * 60 + 35, 0, 100L, listOf(-1L, -1L)))
        // Monday 7:10: too early.
        assertNull(sched.dueSlot(7 * 60 + 10, 0, 100L, listOf(-1L, -1L)))
    }

    @Test fun notOnAnOffDayOrTwiceInADay() {
        val sched = DepartureSchedule(listOf(eight, off))
        // Saturday (5) is not a weekday.
        assertNull(sched.dueSlot(7 * 60 + 35, 5, 100L, listOf(-1L, -1L)))
        // Already started today.
        assertNull(sched.dueSlot(7 * 60 + 35, 0, 100L, listOf(100L, -1L)))
    }

    @Test fun windowWrapsMidnight() {
        val early = DepartureSlot(enabled = true, minuteOfDay = 10, days = DAYS_EVERY)
        val sched = DepartureSchedule(listOf(early, off))
        // Start is at 23:40 the day before: 23:45 is in the window.
        assertEquals(0, sched.dueSlot(23 * 60 + 45, 3, 100L, listOf(-1L, -1L)))
    }

    @Test fun suggestsTheUsualTimeWhenStartsAreTight() {
        val recs = (0 until 6).map { StartRecord(it.toLong(), 1, 7 * 60 + 20 + it, 50) } // Tuesdays, around 7:20
        // Typical start 7:22, plus 15 minutes to leave, rounded to a quarter: 7:45.
        assertEquals(7 * 60 + 45, suggestDeparture(recs, 1))
    }

    @Test fun noSuggestionForFewOrScatteredStarts() {
        val few = (0 until 3).map { StartRecord(it.toLong(), 1, 7 * 60, 50) }
        assertNull(suggestDeparture(few, 1))
        val scattered = listOf(6 * 60, 7 * 60, 9 * 60, 12 * 60, 15 * 60, 18 * 60).mapIndexed { i, m -> StartRecord(i.toLong(), 2, m, 50) }
        assertNull(suggestDeparture(scattered, 2))
    }

    @Test fun historyRoundTripsAndKeepsRecent() {
        val recs = (0 until 70).map { StartRecord(it.toLong(), it % 7, 480, 50) }
        val back = StartRecord.decodeAll(StartRecord.encodeAll(recs))
        assertEquals(StartRecord.KEPT, back.size)
        assertEquals(recs.last(), back.last())
    }
}
