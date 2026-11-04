package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals

class PreRunNameTest {
    @Test fun nameRoundTrips() {
        val s = DepartureSlot(enabled = true, minuteOfDay = 7 * 60, name = "Morning drive")
        assertEquals(s, DepartureSlot.decode(s.encode()))
    }

    @Test fun oldSavesHaveNoNameAndShowTheDefault() {
        val s = DepartureSlot.decode("1,480")!!
        assertEquals("", s.name)
        assertEquals("Pre-run 1", s.label(0))
        assertEquals("Pre-run 2", s.label(1))
    }

    @Test fun blankNameShowsTheDefault() {
        assertEquals("Pre-run 2", DepartureSlot(name = "  ").label(1))
    }

    @Test fun separatorsAreRemovedAndNameIsCut() {
        val dirty = "Work, home; late " + "x".repeat(40)
        val cleaned = DepartureSlot.cleanName(dirty)
        assertEquals(PRE_RUN_NAME_MAX, cleaned.length)
        assertEquals(false, cleaned.contains(',') || cleaned.contains(';'))
        val s = DepartureSlot(enabled = true, minuteOfDay = 60, name = cleaned)
        assertEquals(cleaned, DepartureSlot.decode(s.encode())!!.name)
    }

    @Test fun scheduleKeepsNamesThroughItsOwnRoundTrip() {
        val schedule = DepartureSchedule(
            listOf(
                DepartureSlot(enabled = true, minuteOfDay = 480, name = "Commute"),
                DepartureSlot(enabled = false, minuteOfDay = 1020, name = "Evening"),
            ),
        )
        assertEquals(schedule, DepartureSchedule.decode(schedule.encode()))
    }
}
