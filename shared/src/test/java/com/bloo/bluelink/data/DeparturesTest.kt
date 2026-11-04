package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeparturesTest {
    private val eight = DepartureSlot(enabled = true, minuteOfDay = 8 * 60)
    private val off = DepartureSlot(enabled = false)

    @Test fun slotRoundTrips() {
        val s = DepartureSlot(enabled = true, minuteOfDay = 7 * 60 + 15)
        assertEquals(s, DepartureSlot.decode(s.encode()))
    }

    @Test fun scheduleRoundTrips() {
        val sched = DepartureSchedule(listOf(eight, off))
        assertEquals(sched, DepartureSchedule.decode(sched.encode()))
    }

    @Test fun dueInTheWindowBeforeTheDeparture() {
        val sched = DepartureSchedule(listOf(eight, off))
        // 7:35 is inside the window that starts 30 minutes before 8:00.
        assertEquals(0, sched.dueSlot(7 * 60 + 35, 100L, listOf(-1L, -1L)))
        // 7:10 is too early.
        assertNull(sched.dueSlot(7 * 60 + 10, 100L, listOf(-1L, -1L)))
    }

    @Test fun notTwiceInADayOrWhenOff() {
        val sched = DepartureSchedule(listOf(eight, off))
        assertNull(sched.dueSlot(7 * 60 + 35, 100L, listOf(100L, -1L)))
        // Switched off, a departure never fires.
        assertNull(DepartureSchedule(listOf(off, off)).dueSlot(7 * 60 + 35, 100L, listOf(-1L, -1L)))
    }

    @Test fun windowWrapsMidnight() {
        val early = DepartureSlot(enabled = true, minuteOfDay = 10)
        val sched = DepartureSchedule(listOf(early, off))
        // The start is 23:40 the day before, so 23:45 is in the window.
        assertEquals(0, sched.dueSlot(23 * 60 + 45, 100L, listOf(-1L, -1L)))
    }

    @Test fun suggestsTheUsualTimeWhenStartsAreTight() {
        val recs = (0 until 6).map { StartRecord(it.toLong(), 1, 7 * 60 + 20 + it, 50) }
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
