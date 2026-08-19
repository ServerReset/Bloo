package com.bloo.bluelink.ui

import com.bloo.bluelink.data.VehiclePlatform
import com.bloo.bluelink.data.Vehicle
import com.bloo.bluelink.data.platformOverridable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the effective-value accessors the new per-car "head-unit generation"
 * override added to [UiState] -- [UiState.platformOf], [UiState.isGen5WEffective],
 * [UiState.supportsConnectedStoreEffective] -- against the exact contract their
 * own doc comments describe: user override wins when present, the API-derived
 * guess otherwise, and no effect at all for a vehicle where
 * [com.bloo.bluelink.data.platformOverridable] is false. Written the same way
 * [SettingsSearchTest]'s VehicleToggleSettings tests were: this can't be
 * compiled locally in this sandbox, so CI's `testDebugUnitTest` run is the
 * only real verification a change here actually behaves as documented.
 */
class VehiclePlatformTest {
    // Hyundai US -- brandIndicator "H" -- the one population isGen5W/
    // supportsConnectedStore/platformOverridable actually vary for.
    private fun hyundai(generation: String) = Vehicle(
        vin = "KMHL14JA1PA000001", regId = "reg1", name = "Ioniq",
        model = "Ioniq", generation = generation, brandIndicator = "H", isEv = true,
    )

    // Kia US -- brandIndicator "K" -- never reports a generation; isGen5W is
    // always false for it and supportsConnectedStore is always true.
    private val kia = Vehicle(
        vin = "KNAG241A6P5000001", regId = "reg2", name = "EV6",
        model = "EV6", generation = "", brandIndicator = "K", isEv = true,
    )

    @Test
    fun platformOf_fallsBackToApiInference_whenNoOverride() {
        val old = hyundai("2") // Gen5W by the raw API check
        val new = hyundai("3") // ccNC by the raw API check
        assertEquals(VehiclePlatform.GEN5W, UiState().platformOf(old))
        assertEquals(VehiclePlatform.CCNC, UiState().platformOf(new))
    }

    @Test
    fun platformOf_userOverrideWinsOverApiInference() {
        val v = hyundai("2") // API says Gen5W
        val overridden = UiState(platforms = mapOf(v.vin to VehiclePlatform.CCNC))
        assertEquals(VehiclePlatform.CCNC, overridden.platformOf(v))
    }

    @Test
    fun isGen5WEffective_reflectsTheOverride() {
        val v = hyundai("2") // API says Gen5W
        assertTrue(UiState().isGen5WEffective(v))
        val corrected = UiState(platforms = mapOf(v.vin to VehiclePlatform.CCNC))
        assertFalse(corrected.isGen5WEffective(v))
    }

    @Test
    fun supportsConnectedStoreEffective_kiaIsAlwaysEligibleRegardlessOfOverride() {
        // Mirrors supportsConnectedStore's own "brand == KIA ||" special case --
        // an override for a VIN that happens to collide (it can't, vins are
        // unique, but the point is the KIA branch short-circuits first) must
        // never be able to turn this off for a Kia.
        assertTrue(UiState().supportsConnectedStoreEffective(kia))
        val withStrayOverride = UiState(platforms = mapOf(kia.vin to VehiclePlatform.GEN5W))
        assertTrue(withStrayOverride.supportsConnectedStoreEffective(kia))
    }

    @Test
    fun supportsConnectedStoreEffective_hyundaiHonoursTheOverride() {
        val v = hyundai("2") // API says Gen5W -- no store link by default
        assertFalse(UiState().supportsConnectedStoreEffective(v))
        val corrected = UiState(platforms = mapOf(v.vin to VehiclePlatform.CCNC))
        assertTrue(corrected.supportsConnectedStoreEffective(v))
    }

    @Test
    fun platformOverridable_gatesEligibility() {
        assertTrue(hyundai("2").platformOverridable)
        assertFalse(kia.platformOverridable)
    }
}
