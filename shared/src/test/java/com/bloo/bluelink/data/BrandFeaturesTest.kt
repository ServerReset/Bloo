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

    @Test fun genesisUsHasTheBlueLinkFeatures() {
        val c = Brand.GENESIS.capabilities
        assertTrue(c.surroundView && c.surround3D && c.chargeLimits)
        assertFalse(c.inAppCharging || c.store || c.chargePortAndPreconditioning)
    }

    @Test fun kiaUsHasOnlyChargeLimits() {
        val c = Brand.KIA.capabilities
        assertTrue(c.chargeLimits)
        assertFalse(c.inAppCharging || c.store || c.surroundView || c.surround3D || c.chargePortAndPreconditioning)
    }

    @Test fun kiaAndGenesisCanadaShareTheHyundaiCanadaFeatures() {
        for (b in listOf(Brand.KIA_CA, Brand.GENESIS_CA)) {
            val c = b.capabilities
            assertTrue(c.inAppCharging && c.store && c.surroundView && c.surround3D && c.chargePortAndPreconditioning, "$b")
            assertTrue(c.chargeLimitsSetOnly, "$b")
        }
    }
}
}
