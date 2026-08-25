package com.bloo.bluelink.ui

import com.bloo.bluelink.data.SeatConfig
import com.bloo.bluelink.data.Powertrain
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.VehiclePlatform
import com.bloo.bluelink.data.isGen5W
import com.bloo.bluelink.data.platformOverridable
import com.bloo.bluelink.data.Brand
import com.bloo.bluelink.data.DEFAULT_SECTIONS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM pins for the pure decision functions on [UiState] (AppViewModel.kt):
 * per-car section ordering, expand/hide/pending keying, and the powertrain /
 * platform inference + override rules every UI gate reads.
 *
 * These are the "where does this data show up" answers for the whole app --
 * they decide which pebbles render, in what order, and what generation/state
 * machinery each car is assumed to have. A wrong answer here is not a crash;
 * it is a user's car silently missing its charge tile, or a Canadian user
 * being offered the Gen5W connected store, or a Kia being shown a battery
 * it does not have. All are pure Kotlin over plain data -- the 150+ fields
 * construct with defaults -- so they pin exactly like PinLock arithmetic.
 */
class UiStateLogicTest {

    private fun vehicle(vin: String, indicator: String, isEv: Boolean = false, gen: String = "5") =
        Vehicle(
            vin = vin, regId = "reg-$vin", name = "$vin Car",
            model = "M", generation = gen, brandIndicator = indicator, isEv = isEv,
        )
    private val usHyundai = vehicle("V1", "H", isEv = true)
    private val usKia = vehicle("V2", "K")
    private val kiaCanada = vehicle("V3", Brand.KIA_CA.code)
    private val usKiaGen3 = vehicle("V4", "K", gen = "3")
    // A GENUINE Gen5W unit: an OLD Hyundai (generation < 3). Modern (>=3) cars
    // are ccNC, the NOT-Gen5W world -- isGen5W is about old head units, not new
    // ones, and the naming is the trap this test pins.
    private val oldHyundai = vehicle("V5", "H", gen = "2")

    // --- sectionsFor --------------------------------------------------------

    @Test
    fun sectionsFor_defaultsToCanonicalOrder() {
        val s = UiState()
        assertEquals(DEFAULT_SECTIONS, s.sectionsFor(usHyundai))
    }

    @Test
    fun sectionsFor_usesPerVinOverride() {
        val custom = listOf("controls", "charge", "summary")
        val s = UiState(sectionOrders = mapOf("V1" to custom))
        assertEquals(custom, s.sectionsFor(usHyundai))
    }

    @Test
    fun sectionsFor_otherVinsUnaffectedByAnOverrides() {
        val s = UiState(sectionOrders = mapOf("V1" to listOf("charge")))
        assertEquals(DEFAULT_SECTIONS, s.sectionsFor(usKia))
    }

    @Test
    fun sectionsFor_overrideIsRestoredAfterEmptySectionOrderStored() {
        // A stored empty list is a real (deliberate) wipe, not "no override".
        val s = UiState(sectionOrders = mapOf("V1" to emptyList()))
        assertEquals(emptyList(), s.sectionsFor(usHyundai))
    }

    // --- expand / hide / pending keying --------------------------------------

    @Test
    fun expandedByDefault_untilKeyedCollapsed() {
        val s = UiState()
        assertTrue(s.isPebbleExpanded("V1", "charge"))
        val hidden = UiState(collapsedPebbles = setOf("V1:charge"))
        assertFalse(hidden.isPebbleExpanded("V1", "charge"))
        // Collapse is per vin:section -- another car's same section stays
        // expanded, and a DIFFERENT section of the same car stays expanded.
        assertTrue(UiState(collapsedPebbles = setOf("V1:charge")).isPebbleExpanded("V2", "charge"))
        assertTrue(UiState(collapsedPebbles = setOf("V1:charge")).isPebbleExpanded("V1", "climate"))
    }

    @Test
    fun hideAndExpandAreIndependentKeyspaces() {
        val s = UiState(collapsedPebbles = setOf("V1:charge"), hiddenPebbles = setOf("V1:ai"))
        assertFalse(s.isPebbleExpanded("V1", "charge")) // collapsed
        assertTrue(s.isPebbleHidden("V1", "ai"))       // the hidden key is honored
        assertTrue(s.isPebbleExpanded("V1", "ai")) // hidden, not collapsed
        // No cross-vehicle leak either way.
        assertFalse(UiState(hiddenPebbles = setOf("V1:ai")).isPebbleHidden("V2", "ai"))
    }

    @Test
    fun pending_keyedByVinAndAction() {
        assertFalse(UiState().isPending("V1", "lock"))
        assertTrue(UiState(pending = setOf("V1:lock")).isPending("V1", "lock"))
        // The "V1:lock" key must not satisfy a different action or vin.
        assertFalse(UiState(pending = setOf("V1:lock")).isPending("V1", "unlock"))
        assertFalse(UiState(pending = setOf("V1:lock")).isPending("V2", "lock"))
    }

    // --- powertrain ----------------------------------------------------------

    @Test
    fun powertrainOf_infersFromIsEvWhenNoOverride() {
        assertEquals(Powertrain.EV, UiState().powertrainOf(usHyundai))
        assertEquals(Powertrain.GAS, UiState().powertrainOf(usKia))
    }

    @Test
    fun powertrainOf_overrideWins() {
        val s = UiState(powertrains = mapOf("V1" to Powertrain.PHEV))
        assertEquals(Powertrain.PHEV, s.powertrainOf(usHyundai))
        // And the override still applies for a non-EV vehicle (a PHEV marked gas).
        val s2 = UiState(powertrains = mapOf("V2" to Powertrain.PHEV))
        assertEquals(Powertrain.PHEV, s2.powertrainOf(usKia))
    }

    @Test
    fun batteryAndFuelFollowPowertrain() {
        val ev = UiState()
        assertTrue(ev.hasBattery(usHyundai))
        assertFalse(ev.hasFuel(usHyundai))
        assertFalse(ev.hasBattery(usKia))
        assertTrue(ev.hasFuel(usKia))
        // PHEV: both.
        val phev = UiState(powertrains = mapOf(usKia.vin to Powertrain.PHEV))
        assertTrue(phev.hasBattery(usKia))
        assertTrue(phev.hasFuel(usKia))
    }

    // --- platform / gen5w ----------------------------------------------------

    @Test
    fun platformOf_defaultsToApiInference() {
        val s = UiState()
        // Kia exposes NO generation number at all, so isGen5W is explicitly
        // false for KIA and every Kia defaults to the modern ccNC platform.
        assertEquals(false, usKia.isGen5W)
        assertEquals(VehiclePlatform.CCNC, s.platformOf(usKia))
        // An old Hyundai/Genesis US report (< 3) is the genuine Gen5W world.
        assertEquals(true, oldHyundai.isGen5W)
        assertEquals(VehiclePlatform.GEN5W, s.platformOf(oldHyundai))
        // A modern Hyundai (>= 3) is ccNC.
        assertEquals(false, usHyundai.isGen5W)
        assertEquals(VehiclePlatform.CCNC, s.platformOf(usHyundai))
    }

    @Test
    fun platformOverrideFlipFlopsEveryNonKiaGateButNotKiaConnectedStore() {
        // For a Kia, the override changes the GENERATION gate, while the
        // connected-store special case (brand == KIA) keeps its answer true.
        val s = UiState(platforms = mapOf("V2" to VehiclePlatform.CCNC))
        assertEquals(VehiclePlatform.CCNC, s.platformOf(usKia))
        assertFalse(s.isGen5WEffective(usKia))
        assertTrue(s.supportsConnectedStoreEffective(usKia))
    }

    @Test
    fun connectedStore_kiaAlwaysEligible_othersNeedCcnc() {
        // KIA US: the special case grants connected store regardless of gen.
        assertTrue(UiState().supportsConnectedStoreEffective(usKia))
        // Kia Canada is NOT Brand.KIA and not overridable -> no.
        assertFalse(UiState().supportsConnectedStoreEffective(kiaCanada))
        // A modern US Hyundai (gen >= 3) is ccNC -> store eligible.
        assertTrue(UiState().supportsConnectedStoreEffective(usHyundai))
        // An OLD US Hyundai (gen 2, genuine Gen5W) is NOT store-eligible.
        assertFalse(UiState().supportsConnectedStoreEffective(oldHyundai))
        // A Kia gen-1 gen override (GEN5W on Kia, which the picker can't even
        // write -- platformOverridable is false for KIA) still special-cases
        // true only because brand == KIA, and the test documents that.
        assertTrue(
            UiState(platforms = mapOf(usKiaGen3.vin to VehiclePlatform.GEN5W))
                .supportsConnectedStoreEffective(usKiaGen3),
        )
    }

    @Test
    fun gen5wEffective_followsPlatformOverride() {
        assertTrue(UiState(platforms = mapOf(usKiaGen3.vin to VehiclePlatform.GEN5W)).isGen5WEffective(usKiaGen3))
        assertFalse(UiState(platforms = mapOf(usKiaGen3.vin to VehiclePlatform.CCNC)).isGen5WEffective(usKiaGen3))
    }

    // --- seat fallback -------------------------------------------------------

    @Test
    fun seatConfigFor_fallsBackToDefaults() {
        val s = UiState()
        val cfg = s.seatConfigFor(usHyundai)
        assertEquals(SeatConfig(), cfg)
        assertEquals(cfg, s.seatConfigFor(usKia))
        // An explicit empty-ish config (all defaults) is stored, not absent.
        assertTrue(UiState(seatConfigs = mapOf("V1" to SeatConfig(driverHeat = true))).seatConfigFor(usHyundai).driverHeat)
    }

    @Test
    fun hotspotFor_andFetchedAt_matchStringByVin() {
        val s = UiState(hotspotSections = mapOf("V1" to "info"), lastFetched = mapOf("V1" to 1234L))
        assertEquals("info", s.hotspotFor("V1"))
        assertNull(s.hotspotFor("V2"))
        assertEquals(1234L, s.fetchedAt(usHyundai))
        assertNull(s.fetchedAt(usKia))
    }

    @Test
    fun powertrainLabel_readsTheEffectivePowertrain() {
        assertEquals("EV", UiState().powertrainLabel(usHyundai))
        assertEquals("Gas", UiState().powertrainLabel(usKia))
        assertEquals("PHEV", UiState(powertrains = mapOf(usKia.vin to Powertrain.PHEV)).powertrainLabel(usKia))
    }

    @Test
    fun shortcutSettings_absentSetMeansAllEnabled() {
        // No shortcutSet at all (fresh install, no wipe ever saved): every
        // shortcut is on -- this is the DEFAULT denial-behavior direction.
        assertTrue(UiState().isShortcutEnabled("V1", "horn"))
        // A saved set gates only its own entries.
        val s = UiState(shortcutSet = setOf("horn_V1"))
        assertTrue(s.isShortcutEnabled("V1", "horn"))
        assertFalse(s.isShortcutEnabled("V2", "horn"))
        assertFalse(s.isShortcutEnabled("V1", "lights"))
    }
}
