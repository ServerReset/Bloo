package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UnitResolveTest {
    @Test fun autoFollowsTheMainUnitsSetting() {
        assertTrue(resolveFahrenheit("imperial", "auto"))
        assertFalse(resolveFahrenheit("metric", "auto"))
        assertFalse(resolveMetricDistance("imperial", "auto"))
        assertTrue(resolveMetricDistance("metric", "auto"))
    }

    @Test fun missingValuesDefaultToImperial() {
        assertTrue(resolveFahrenheit(null, null))
        assertFalse(resolveMetricDistance(null, null))
    }

    @Test fun temperatureAndDistanceCanBeMixed() {
        // Celsius with miles, on an imperial phone...
        assertFalse(resolveFahrenheit("imperial", "c"))
        assertFalse(resolveMetricDistance("imperial", "auto"))
        // ...and Fahrenheit with kilometres, on a metric one.
        assertTrue(resolveFahrenheit("metric", "f"))
        assertTrue(resolveMetricDistance("metric", "km"))
    }

    @Test fun anExplicitDistanceUnitWinsOverUnits() {
        assertTrue(resolveMetricDistance("imperial", "km"))
        assertFalse(resolveMetricDistance("metric", "mi"))
    }

    @Test fun anUnknownOverrideFallsBackToAuto() {
        assertEquals(resolveFahrenheit("metric", null), resolveFahrenheit("metric", "kelvin"))
        assertEquals(resolveMetricDistance("metric", null), resolveMetricDistance("metric", "furlongs"))
    }
}
