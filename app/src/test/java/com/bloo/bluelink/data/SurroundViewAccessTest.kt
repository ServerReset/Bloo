package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurroundViewAccessTest {
    @Test
    fun autoShowsOnlyOnLimitedCcnc() {
        assertTrue(surroundViewShown(true, VehiclePlatform.CCNC, "LIMITED", null))
        assertTrue(surroundViewShown(true, VehiclePlatform.CCNC, "Limited", SurroundViewAccess.AUTO))
        assertFalse(surroundViewShown(true, VehiclePlatform.CCNC, "SEL", null))
        assertFalse(surroundViewShown(true, VehiclePlatform.CCNC, null, null))
        assertFalse(surroundViewShown(true, VehiclePlatform.GEN5W, "LIMITED", null))
    }

    @Test
    fun onShowsWhereTheBrandHasItAndOffHidesIt() {
        assertTrue(surroundViewShown(true, VehiclePlatform.GEN5W, "SEL", SurroundViewAccess.ON))
        assertFalse(surroundViewShown(true, VehiclePlatform.CCNC, "LIMITED", SurroundViewAccess.OFF))
    }

    @Test
    fun noBrandFeatureMeansNoFeatureWhateverTheChoice() {
        assertFalse(surroundViewShown(false, VehiclePlatform.CCNC, "LIMITED", SurroundViewAccess.ON))
    }
}
