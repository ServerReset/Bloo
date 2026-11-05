package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurroundViewAccessTest {
    @Test
    fun autoShowsOnlyOnLimitedCcnc() {
        assertTrue(surroundViewShown(true, VehiclePlatform.CCNC, "LIMITED", null, refused = false))
        assertTrue(surroundViewShown(true, VehiclePlatform.CCNC, "Limited", SurroundViewAccess.AUTO, refused = false))
        assertFalse(surroundViewShown(true, VehiclePlatform.CCNC, "SEL", null, refused = false))
        assertFalse(surroundViewShown(true, VehiclePlatform.CCNC, null, null, refused = false))
    }

    @Test
    fun aGen5wHeadUnitNeverShowsIt() {
        assertFalse(surroundViewShown(true, VehiclePlatform.GEN5W, "LIMITED", null, refused = false))
        assertFalse(surroundViewShown(true, VehiclePlatform.GEN5W, "SEL", SurroundViewAccess.ON, refused = false))
    }

    @Test
    fun onShowsOnEveryCcncCarAndOffHidesIt() {
        assertTrue(surroundViewShown(true, VehiclePlatform.CCNC, "SEL", SurroundViewAccess.ON, refused = false))
        assertFalse(surroundViewShown(true, VehiclePlatform.CCNC, "LIMITED", SurroundViewAccess.OFF, refused = false))
    }

    @Test
    fun aRefusalHidesItWhateverTheChoice() {
        assertFalse(surroundViewShown(true, VehiclePlatform.CCNC, "LIMITED", SurroundViewAccess.ON, refused = true))
        assertFalse(surroundViewShown(true, VehiclePlatform.CCNC, "LIMITED", null, refused = true))
    }

    @Test
    fun noBrandFeatureMeansNoFeature() {
        assertFalse(surroundViewShown(false, VehiclePlatform.CCNC, "LIMITED", SurroundViewAccess.ON, refused = false))
    }
}
