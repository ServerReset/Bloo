package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The drive-side inference and, more importantly, the seat mapping it feeds.
 *
 * The mapping is the part worth pinning. Bloo names seats by SIDE and Hyundai's
 * CCS2 climate payload names the front pair by ROLE, so the translation between
 * them depends on which side the driver sits -- and getting it backwards is a
 * silent failure: the command succeeds, the car heats a seat, and it is the
 * wrong one. Nothing errors and nothing looks broken.
 */
class DriveSideTest {

    @Test
    fun `the right-hand-drive markets in the Europe region are recognised`() {
        assertEquals(DriveSide.RIGHT, driveSideFor("GB"))
        assertEquals(DriveSide.RIGHT, driveSideFor("IE"))
        assertEquals(DriveSide.RIGHT, driveSideFor("MT"))
        assertEquals(DriveSide.RIGHT, driveSideFor("CY"))
    }

    @Test
    fun `mainland Europe and the Americas are left-hand drive`() {
        for (c in listOf("DE", "FR", "ES", "IT", "NL", "PL", "SE", "NO", "US", "CA")) {
            assertEquals(DriveSide.LEFT, driveSideFor(c), "$c should be left-hand drive")
        }
    }

    /** An unknown or absent country keeps the value the payload always carried,
     *  so a device Bloo cannot place behaves exactly as it did before. */
    @Test
    fun `an unknown or missing country falls back to left`() {
        assertEquals(DriveSide.LEFT, driveSideFor(null))
        assertEquals(DriveSide.LEFT, driveSideFor(""))
        assertEquals(DriveSide.LEFT, driveSideFor("ZZ"))
    }

    /** Locale country codes are conventionally upper case, but nothing forces
     *  it, and a lower-case "gb" silently reading as left-hand drive is exactly
     *  the kind of near-miss this whole file exists to prevent. */
    @Test
    fun `country codes match regardless of case`() {
        assertEquals(DriveSide.RIGHT, driveSideFor("gb"))
        assertEquals(DriveSide.RIGHT, driveSideFor("Ie"))
    }

    @Test
    fun `the payload letter matches the side`() {
        assertEquals("L", DriveSide.LEFT.ccs2Code)
        assertEquals("R", DriveSide.RIGHT.ccs2Code)
    }

    /**
     * THE invariant: the seat that gets the driver's setting is the one the
     * driver is sitting in.
     *
     * Mirrors the mapping in EuApi.startClimate. On a left-hand-drive car the
     * driver's seat is the front left; on a right-hand-drive car it is the front
     * right, and the front pair swaps while the rear pair -- named by side in
     * the payload as well -- does not.
     */
    @Test
    fun `the front seats swap with the drive side and the rear seats never do`() {
        val req = ClimateRequest(
            tempF = 72,
            defrost = false,
            durationMinutes = 10,
            seatFrontLeft = SeatLevel.HIGH_HEAT,
            seatFrontRight = SeatLevel.LOW_COOL,
            seatRearLeft = SeatLevel.MED_HEAT,
            seatRearRight = SeatLevel.HIGH_COOL,
        )
        for (side in DriveSide.entries) {
            val driver = if (side == DriveSide.RIGHT) req.seatFrontRight else req.seatFrontLeft
            val passenger = if (side == DriveSide.RIGHT) req.seatFrontLeft else req.seatFrontRight
            if (side == DriveSide.LEFT) {
                assertEquals(SeatLevel.HIGH_HEAT, driver, "LHD driver sits front left")
                assertEquals(SeatLevel.LOW_COOL, passenger, "LHD passenger sits front right")
            } else {
                assertEquals(SeatLevel.LOW_COOL, driver, "RHD driver sits front right")
                assertEquals(SeatLevel.HIGH_HEAT, passenger, "RHD passenger sits front left")
            }
            // The rear pair is addressed by side at both ends, so it is identical
            // whichever way the front swaps.
            assertEquals(SeatLevel.MED_HEAT, req.seatRearLeft)
            assertEquals(SeatLevel.HIGH_COOL, req.seatRearRight)
        }
    }

    // ---- EU sign-in country ----

    /** A user in a market the region serves sends their OWN country, not
     *  Germany's -- which is the whole point of the change. */
    @Test
    fun `a served European country is sent as itself`() {
        assertEquals("fr", euLoginCountry("FR"))
        assertEquals("gb", euLoginCountry("GB"))
        assertEquals("it", euLoginCountry("it"))
        assertEquals("no", euLoginCountry("NO"))
        assertEquals("de", euLoginCountry("DE"))
    }

    /**
     * THE safety property. Anything the region does not serve falls back to the
     * value every user sent before, so this cannot break a sign-in that works
     * today -- a German owner whose phone is set to US English keeps sending
     * "de" rather than a country the European IDP has never heard of.
     */
    @Test
    fun `anything outside the region falls back to the previous value`() {
        for (c in listOf("US", "CA", "AU", "JP", "KR", "ZZ", "", null)) {
            assertEquals("de", euLoginCountry(c), "$c is not a European market")
        }
    }
}
