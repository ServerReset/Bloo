package com.bloo.uicommon

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JVM pins for [shouldDock] -- the rule that decides when an element anchored in the page has
 * scrolled far enough to become floating chrome.
 *
 * Worth pinning because the interesting behaviour is the asymmetry, and asymmetry is exactly what
 * a well-meaning simplification deletes. The threshold is deliberately WIDER while already docked
 * than while undocked: an anchor resting within a pixel or two of the dock line would otherwise
 * flip state on alternating frames as a fling settles, and the flying title would visibly buzz
 * between its inline slot and its corner pill. The bug that produces is not "wrong position", it
 * is "position is right but it strobes", which reads as a rendering fault rather than a logic one.
 */
class ShouldDockTest {

    private val line = 100f
    private val hyst = 8f

    @Test
    fun `undocked docks only once fully past the line`() {
        assertFalse(shouldDock(topPx = 101f, dockLinePx = line, currentlyDocked = false, hysteresisPx = hyst))
        assertFalse(shouldDock(topPx = 100f, dockLinePx = line, currentlyDocked = false, hysteresisPx = hyst))
        assertTrue(shouldDock(topPx = 99f, dockLinePx = line, currentlyDocked = false, hysteresisPx = hyst))
    }

    @Test
    fun `docked stays docked through the hysteresis band`() {
        // Inside the band: an undocked anchor here would NOT dock, but a docked one holds on.
        assertTrue(shouldDock(topPx = 105f, dockLinePx = line, currentlyDocked = true, hysteresisPx = hyst))
        assertFalse(shouldDock(topPx = 105f, dockLinePx = line, currentlyDocked = false, hysteresisPx = hyst))
    }

    @Test
    fun `docked releases only past the far edge of the band`() {
        assertTrue(shouldDock(topPx = 107f, dockLinePx = line, currentlyDocked = true, hysteresisPx = hyst))
        assertFalse(shouldDock(topPx = 108f, dockLinePx = line, currentlyDocked = true, hysteresisPx = hyst))
        assertFalse(shouldDock(topPx = 120f, dockLinePx = line, currentlyDocked = true, hysteresisPx = hyst))
    }

    @Test
    fun `zero hysteresis makes both directions agree, which is what a settled report wants`() {
        for (top in listOf(99f, 100f, 101f)) {
            val docked = shouldDock(top, line, currentlyDocked = true, hysteresisPx = 0f)
            val undocked = shouldDock(top, line, currentlyDocked = false, hysteresisPx = 0f)
            assertTrue(docked == undocked, "state should not matter at zero hysteresis (top=$top)")
        }
    }

    @Test
    fun `a dock line at zero still behaves, for a surface with no status bar inset`() {
        assertTrue(shouldDock(topPx = -1f, dockLinePx = 0f, currentlyDocked = false, hysteresisPx = hyst))
        assertFalse(shouldDock(topPx = 0f, dockLinePx = 0f, currentlyDocked = false, hysteresisPx = hyst))
        assertTrue(shouldDock(topPx = 5f, dockLinePx = 0f, currentlyDocked = true, hysteresisPx = hyst))
    }
}
