package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins for the brand/region rule family in [Brand.kt].
 *
 * These rules have a documented bug history: a Horn/Lights capability rule
 * hand-copied into the WATCH missed the Canada half, and every Canadian user
 * got Flash and Horn buttons that silently did nothing on every tap (see
 * [Brand.supportsHornLights]'s own doc). Every consumer (phone screens, watch
 * buttons, widget actions) now routes through the shared accessors; these
 * tests are the tripwire that keeps any future copy from diverging again,
 * by pinning the rules in the ONE place a copy would have to be written from.
 */
class BrandRulesTest {

    private fun vehicle(vin: String, indicator: String, gen: String, isEv: Boolean = true) =
        Vehicle(
            vin = vin, regId = "reg-$vin", name = "$vin", model = "M",
            generation = gen, brandIndicator = indicator, isEv = isEv,
        )

    // --- supportsHornLights (the Canadian flash/horn regression) -------------

    @Test
    fun hornLights_usHyundaiAndGenesis() {
        assertTrue(Brand.HYUNDAI.supportsHornLights)
        assertTrue(Brand.GENESIS.supportsHornLights)
    }

    @Test
    fun hornLights_kiaIsOut_everywhere() {
        // Kia US has no rcs/rhl endpoint; Kia Canada neither.
        assertFalse(Brand.KIA.supportsHornLights)
        assertFalse(Brand.KIA_CA.supportsHornLights)
    }

    @Test
    fun hornLights_canadaAndEuropeExcluded() {
        // The half the watch originally forgot: HYUNDAI_CA, GENESIS_CA,
        // HYUNDAI_EU are all OUT even though they're Hyundai-shaped brands.
        assertFalse(Brand.HYUNDAI_CA.supportsHornLights)
        assertFalse(Brand.GENESIS_CA.supportsHornLights)
        assertFalse(Brand.HYUNDAI_EU.supportsHornLights)
    }

    @Test
    fun hornLights_vehicleSpellingMatchesBrand() {
        assertTrue(vehicle("V1", "H", "5").supportsHornLights)
        assertFalse(vehicle("V2", "K", "5").supportsHornLights)
        assertFalse(vehicle("V3", Brand.HYUNDAI_CA.code, "5").supportsHornLights)
        assertFalse(vehicle("V4", Brand.HYUNDAI_EU.code, "5").supportsHornLights)
    }

    // --- isGen5W (OLD pre-ccNC units only; generation < 3) -------------------

    @Test
    fun gen5w_oldUnitBelowThree() {
        assertTrue(vehicle("V1", "H", "2").isGen5W)
        assertFalse(vehicle("V2", "H", "3").isGen5W)
        assertFalse(vehicle("V3", "G", "4").isGen5W)
    }

    @Test
    fun gen5w_kiaNeverReportsGeneration() {
        assertFalse(vehicle("V4", "K", "2").isGen5W)
        assertFalse(vehicle("V5", "K", "5").isGen5W)
    }

    @Test
    fun gen5w_canadaEuropeExcluded() {
        assertFalse(vehicle("V6", Brand.HYUNDAI_CA.code, "2").isGen5W)
        assertFalse(vehicle("V7", Brand.HYUNDAI_EU.code, "1").isGen5W)
    }

    @Test
    fun gen5w_unparseableGenerationAssumesModern() {
        // The fallback is 3 = NOT Gen5W: an unknown value means modern, because
        // the Gen5W population is by definition legacy and shrinking.
        assertFalse(vehicle("V8", "H", "abc").isGen5W)
        assertFalse(vehicle("V9", "H", "").isGen5W)
    }

    // --- supportsConnectedStore ---------------------------------------------

    @Test
    fun connectedStore_kiaAlways() {
        // The rule lives on Vehicle (brand spelling exists; pin the Vehicle one).
        assertTrue(vehicle("V0", "K", "2").supportsConnectedStore)
        assertTrue(vehicle("V1", "K", "2").supportsConnectedStore)
    }

    @Test
    fun connectedStore_usNonKiaModernOnly() {
        assertTrue(vehicle("V2", "H", "5").supportsConnectedStore)
        assertFalse(vehicle("V3", "H", "2").supportsConnectedStore)
    }

    @Test
    fun connectedStore_canadaEuropeOut() {
        assertFalse(vehicle("V4", Brand.KIA_CA.code, "5").supportsConnectedStore)
        assertFalse(vehicle("V5", Brand.HYUNDAI_CA.code, "5").supportsConnectedStore)
        assertFalse(vehicle("V6", Brand.HYUNDAI_EU.code, "5").supportsConnectedStore)
    }

    // --- platformOverridable --------------------------------------------------

    @Test
    fun platformOverridable_onlyWhereGenerationVaries() {
        assertTrue(vehicle("V1", "H", "5").platformOverridable)
        assertTrue(vehicle("V2", "G", "5").platformOverridable)
        assertFalse(vehicle("V3", "K", "5").platformOverridable)
        assertFalse(vehicle("V4", Brand.KIA_CA.code, "5").platformOverridable)
        assertFalse(vehicle("V5", Brand.HYUNDAI_CA.code, "5").platformOverridable)
        assertFalse(vehicle("V6", Brand.HYUNDAI_EU.code, "5").platformOverridable)
    }

    @Test
    fun fromIndicator_roundTripsEverySpecialBrand() {
        assertEquals(Brand.HYUNDAI, Brand.fromIndicator("H"))
        assertEquals(Brand.GENESIS, Brand.fromIndicator("G"))
        assertEquals(Brand.KIA, Brand.fromIndicator("K"))
        assertEquals(Brand.HYUNDAI_CA, Brand.fromIndicator(Brand.HYUNDAI_CA.code))
        assertEquals(Brand.GENESIS_CA, Brand.fromIndicator(Brand.GENESIS_CA.code))
        assertEquals(Brand.KIA_CA, Brand.fromIndicator(Brand.KIA_CA.code))
        assertEquals(Brand.HYUNDAI_EU, Brand.fromIndicator(Brand.HYUNDAI_EU.code))
    }
}
