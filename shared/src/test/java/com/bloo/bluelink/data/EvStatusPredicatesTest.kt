package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [isPluggedIn] and [isPluggedOrCharging].
 *
 * `isPluggedOrCharging` replaced three inline copies of
 * `ev?.isPluggedIn == true || ev?.batteryCharge == true` in Screens.kt (CoverActionBar,
 * ChargePebble, the charge status row). All three agreed at the time they were unified, so
 * these tests exist to pin the behaviour they agreed ON -- the point of extracting a
 * predicate is worthless if the extraction itself changed one of the answers.
 *
 * The `charging while batteryPlugin says 0` case is the one that matters and the reason the
 * OR is not redundant: it must report true, because "can I stop this charge" has to be
 * answerable in a state cars really do report.
 */
class EvStatusPredicatesTest {

    private fun ev(plugin: Int? = null, charging: Boolean? = null) =
        EvStatus(batteryCharge = charging, batteryPlugin = plugin)

    @Test
    fun `batteryPlugin encodes plugged state, absent means unplugged`() {
        assertFalse(ev(plugin = 0).isPluggedIn, "0 is the API's explicit 'unplugged'")
        assertTrue(ev(plugin = 1).isPluggedIn, "1 = DC fast")
        assertTrue(ev(plugin = 2).isPluggedIn, "2 = portable/AC")
        assertFalse(ev(plugin = null).isPluggedIn, "a missing plug value is treated as unplugged")
    }

    @Test
    fun `plugged or charging is true whenever a charger is connected`() {
        assertTrue(ev(plugin = 1).isPluggedOrCharging)
        assertTrue(ev(plugin = 2).isPluggedOrCharging)
        assertTrue(ev(plugin = 2, charging = false).isPluggedOrCharging,
            "connected but not drawing power is still plugged in")
    }

    @Test
    fun `charging with no plug value reported is still plugged or charging`() {
        // The whole reason the OR exists. Cars report an active charge while batteryPlugin
        // still reads 0 or null -- a plug field that has not caught up, or a session it does
        // not describe. Answering false here would disable the stop-charge control during a
        // charge that is demonstrably happening.
        assertTrue(ev(plugin = 0, charging = true).isPluggedOrCharging)
        assertTrue(ev(plugin = null, charging = true).isPluggedOrCharging)
    }

    @Test
    fun `neither plugged nor charging is false`() {
        assertFalse(ev().isPluggedOrCharging, "nothing reported at all")
        assertFalse(ev(plugin = 0, charging = false).isPluggedOrCharging)
        assertFalse(ev(plugin = 0).isPluggedOrCharging)
    }

    @Test
    fun `null EvStatus answers false rather than throwing`() {
        // Nullable receiver on purpose: a non-EV car, or an EV whose status has not been
        // fetched yet, has no EvStatus at all. Every call site used to spell this out with
        // its own `?.` plus `== true`, which is exactly the boilerplate that lets one copy
        // drift from the others.
        val none: EvStatus? = null
        assertFalse(none.isPluggedOrCharging)
    }
}
