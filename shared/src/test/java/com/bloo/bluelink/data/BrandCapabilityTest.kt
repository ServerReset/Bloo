package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The per-brand capability flags, pinned.
 *
 * Each of these encodes "this backend cannot do X", and each exists because a
 * surface once offered X anyway: a control that did nothing on every tap, or a
 * state that could never be corrected. They are cheap to get wrong when a new
 * region is added -- the flag is a single `&&` clause in a file nobody edits
 * while writing an API client -- and expensive to notice, because the failure
 * is silence rather than an error.
 */
class BrandCapabilityTest {

    /**
     * Horn/flash exist only on the Hyundai/Genesis US telematics API. Kia US,
     * the three Canada brands and Europe all route to repositories that leave
     * flashLights/hornAndLights on the interface's no-op defaults, so offering
     * the buttons gives the user a control that silently does nothing.
     *
     * This has now shipped twice -- Canada on the watch, and Europe would have
     * been the widget -- both times because a surface re-derived the rule by
     * hand instead of reading it. Hence the flag, and hence this test.
     */
    @Test
    fun `only the US Hyundai and Genesis brands claim horn and flash`() {
        val supported = Brand.entries.filter { it.supportsHornLights }
        assertTrue(Brand.HYUNDAI in supported, "Hyundai US supports horn/flash")
        assertTrue(Brand.GENESIS in supported, "Genesis US supports horn/flash")
        assertFalse(Brand.KIA.supportsHornLights, "Kia US has no horn/flash endpoint")
        for (b in Brand.entries.filter { it.isCanada }) {
            assertFalse(b.supportsHornLights, "$b is Canada: no horn/flash endpoint")
        }
        for (b in Brand.entries.filter { it.isEurope }) {
            assertFalse(b.supportsHornLights, "$b is Europe: no horn/flash endpoint")
        }
    }

    /**
     * Europe cannot report whether climate is RUNNING, so nothing may write an
     * optimistic "on" for it: [SnapshotStore] keeps the old value when a status
     * field is null (so a missing field never wipes a known one), which means an
     * unconfirmable guess would survive forever -- a climate button lit
     * permanently, and a toggle that sends STOP long after the car's own timer
     * ended the session.
     */
    @Test
    fun `only brands whose status reports climate may claim to know it`() {
        assertFalse(Brand.HYUNDAI_EU.reportsClimateState, "EU status has no airCtrlOn field")
        assertTrue(Brand.HYUNDAI.reportsClimateState)
        assertTrue(Brand.KIA.reportsClimateState)
        for (b in Brand.entries.filter { it.isCanada }) {
            assertTrue(b.reportsClimateState, "$b (Canada) does report airCtrlOn")
        }
    }

    /**
     * Charge limits are the mirror case, and worth pinning BECAUSE it differs
     * from the two above: Europe genuinely reads and writes charge targets
     * ([EuApi] parses Green.ChargingInformation.TargetSoC and posts targetSOClist),
     * so unlike horn/flash it must NOT be excluded. Canada stays excluded because
     * it can neither read the real limit nor safely set one.
     */
    @Test
    fun `charge limits are excluded for Canada only`() {
        for (b in Brand.entries.filter { it.isCanada }) {
            assertFalse(b.supportsChargeLimits, "$b cannot read charge targets")
        }
        assertTrue(Brand.HYUNDAI_EU.supportsChargeLimits, "EU reads and writes charge targets")
        assertTrue(Brand.HYUNDAI.supportsChargeLimits)
    }

    /**
     * Trips exist only where a backend actually serves them.
     *
     * BlueLinkRepository is the only one that overrides trips(); Kia US, the
     * Canada brands and Europe inherit the interface's emptyList() and their API
     * clients have no endpoint to call. The pebble already hides for Gen5W head
     * units so it does not sit permanently empty -- this is the same rule for
     * the same reason, one level further out.
     */
    @Test
    fun `only the US Hyundai and Genesis brands claim trip history`() {
        assertTrue(Brand.HYUNDAI.supportsTrips)
        assertTrue(Brand.GENESIS.supportsTrips)
        assertFalse(Brand.KIA.supportsTrips, "Kia US has no trips endpoint")
        for (b in Brand.entries.filter { it.isCanada || it.isEurope }) {
            assertFalse(b.supportsTrips, "$b has no trips endpoint")
        }
    }

    /** Every brand but Kia US gates its commands behind the login PIN. Europe
     *  included -- its commands need a PIN-derived control token. */
    @Test
    fun `every brand except Kia US requires a PIN`() {
        assertFalse(Brand.KIA.requiresPin)
        for (b in Brand.entries.filter { it != Brand.KIA }) {
            assertTrue(b.requiresPin, "$b needs a PIN")
        }
    }
}
