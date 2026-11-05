package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pins what each brand can do, so a feature is switched on only by a deliberate change here. */
class BrandFeaturesTest {
    @Test fun hyundaiCanadaHasTheInAppFeatures() {
        val c = Brand.HYUNDAI_CA.capabilities
        assertTrue(c.inAppCharging && c.store && c.surroundView && c.surround3D && c.chargePortAndPreconditioning)
        assertTrue(c.chargeLimitsSetOnly)
        assertFalse(c.chargeLimits, "Canadian cars do not report their current targets")
    }

    @Test fun hyundaiUsHasSurroundViewButNoCanadianFeatures() {
        val c = Brand.HYUNDAI.capabilities
        assertTrue(c.surroundView && c.surround3D && c.chargeLimits)
        assertFalse(c.inAppCharging || c.store || c.chargePortAndPreconditioning)
    }

    @Test fun kiaAndGenesisUsStartWithOnlyChargeLimits() {
        for (b in listOf(Brand.KIA, Brand.GENESIS)) {
            val c = b.capabilities
            assertTrue(c.chargeLimits, "$b reads and writes its limits")
            assertFalse(c.inAppCharging || c.store || c.surroundView || c.surround3D || c.chargePortAndPreconditioning, "$b is unverified")
        }
    }

    @Test fun kiaAndGenesisCanadaStartWithEverythingOff() {
        for (b in listOf(Brand.KIA_CA, Brand.GENESIS_CA)) {
            val c = b.capabilities
            assertFalse(c.inAppCharging || c.store || c.surroundView || c.chargeLimits || c.chargeLimitsSetOnly, "$b is unverified")
        }
    }
}
