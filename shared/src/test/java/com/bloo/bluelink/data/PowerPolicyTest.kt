package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PowerPolicyTest {
    @Test fun normalDevice_runsFull() {
        val p = PowerPolicy.of(DeviceState())
        assertFalse(p.lean); assertFalse(p.batterySaver)
        assertTrue(p.blurBackdrops && p.bouncySprings && p.ambientMotion && p.prefetchMapTiles && p.autoSummarize && p.highAccuracyLocation)
        assertEquals(1, p.pollIntervalMultiplier)
    }

    @Test fun batterySaver_runsLean_andReportsTheDeviceFlag() {
        val p = PowerPolicy.of(DeviceState(batterySaver = true))
        assertTrue(p.lean); assertTrue(p.batterySaver)
        assertFalse(p.blurBackdrops || p.bouncySprings || p.ambientMotion || p.prefetchMapTiles || p.autoSummarize || p.highAccuracyLocation)
        assertEquals(3, p.pollIntervalMultiplier)
    }

    @Test fun thermalThrottling_runsLean_butIsNotBatterySaver() {
        val p = PowerPolicy.of(DeviceState(thermalThrottled = true))
        assertTrue(p.lean); assertFalse(p.batterySaver)
    }
}
