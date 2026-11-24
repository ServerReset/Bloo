package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Distances and ranges: rounded where they are shown, in the person's unit, from the car's own figure. */
class DistanceMathTest {
    private fun car(rangeMi: Int? = null, exact: Double? = null) =
        VehicleSnapshot(vin = "V", name = "Car", model = "M", isEv = true, rangeMi = rangeMi, rangeMiExact = exact)

    @Test fun bothUnitsRound() {
        assertEquals("1 mi", formatDistance(0.9, metric = false), "0.9 mi used to read 0 mi")
        assertEquals("10 mi", formatDistance(9.97, metric = false))
        assertEquals("1 km", formatDistance(0.9 / KM_PER_MI, metric = true))
        assertEquals("263 km", formatDistance(263.0 / KM_PER_MI, metric = true))
    }

    @Test fun kilometresComeFromTheCarsOwnFigure() {
        // A Canadian car reports 263 km, stored as 163.42 mi. Rounded to 163 first, it came back as 262 km.
        val exact = 263.0 / KM_PER_MI
        assertEquals("263 km", car(rangeMi = 163, exact = exact).rangeLabel(metric = true))
        assertEquals("300 km", car(rangeMi = 186, exact = 300.0 / KM_PER_MI).rangeLabel(metric = true))
        assertEquals("163 mi", car(rangeMi = 163, exact = exact).rangeLabel(metric = false))
    }

    @Test fun anOlderSnapshotWithoutTheExactFigureStillShows() {
        assertEquals("270 mi", car(rangeMi = 270).rangeLabel(metric = false))
        assertEquals("435 km", car(rangeMi = 270).rangeLabel(metric = true))
        assertNull(car().rangeLabel(metric = true))
    }

    @Test fun theGlanceLineFollowsTheUnit() {
        assertEquals("435 km", car(rangeMi = 270).stateLine(metric = true))
        assertEquals("270 mi", car(rangeMi = 270).stateLine())
    }

    @Test fun theHaversineDistanceIsRight() {
        val a = GeoLocation(0.0, 0.0)
        assertEquals(0.0, a.distanceMilesTo(a), 1e-9)
        // One degree of longitude on the equator: 69.09 mi.
        assertEquals(69.09, a.distanceMilesTo(GeoLocation(0.0, 1.0)), 0.05)
        // Across the antimeridian it is still one degree, not most of the world.
        assertEquals(69.09, GeoLocation(0.0, 179.5).distanceMilesTo(GeoLocation(0.0, -179.5)), 0.05)
        // The far side of the world: half the circumference, and a number (not NaN).
        val far = a.distanceMilesTo(GeoLocation(0.0, 180.0))
        assertTrue(far in 12_430.0..12_440.0, "antipodes $far")
    }

    @Test fun theRangeKeepsTheCarsFraction() {
        val status = VehicleStatus(dte = Dte(value = 163.42))
        assertEquals(163.42, status.rangeMiFor(hasBattery = false)!!, 1e-9)
    }
}

/** Charge times, as they are shown. */
class ChargeTimeMathTest {
    @Test fun durationsReadNaturally() {
        assertEquals("0 min", fmtMinutes(0))
        assertEquals("59 min", fmtMinutes(59))
        assertEquals("1h", fmtMinutes(60))
        assertEquals("1h 1m", fmtMinutes(61))
        assertEquals("2h", fmtMinutes(120), "it read 2h 0m")
        assertEquals("24h", fmtMinutes(1440))
    }

    @Test fun aFewSecondsLeftIsStillAnEstimate() {
        fun ev(minutes: Double?) = EvStatus(remainTime2 = RemainTime2(atc = TimeValue(minutes, 1)))
        assertEquals(1, ev(0.6).minutesToFull, "0.6 min used to truncate to 0, which reads as no estimate")
        assertEquals(115, ev(115.0).minutesToFull)
        assertNull(ev(0.0).minutesToFull)
        assertNull(ev(-3.0).minutesToFull)
        assertNull(ev(null).minutesToFull)
    }
}
