package com.bloo.bluelink.widget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the parts of [WidgetConfig] a silent typo could break without any
 * layout test ever catching it: the default a fresh install actually gets,
 * and that the three button-label modes are genuinely distinct strings (a
 * copy-paste duplicate here would make two of the three picker options
 * silently select the same saved value).
 */
class WidgetConfigTest {

    @Test
    fun `a fresh config defaults button labels to auto`() {
        assertEquals(WidgetConfig.BUTTON_LABELS_AUTO, WidgetConfig(vin = "test").buttonLabels)
    }

    @Test
    fun `the three button-label modes are distinct`() {
        val modes = setOf(
            WidgetConfig.BUTTON_LABELS_AUTO,
            WidgetConfig.BUTTON_LABELS_ALWAYS,
            WidgetConfig.BUTTON_LABELS_OFF,
        )
        assertEquals(3, modes.size)
    }

    @Test
    fun `an unrecognized saved value is not one of the two forced modes`() {
        // A config hand-edited or left over from a future version with a mode this
        // build doesn't know shouldn't silently land on ALWAYS or OFF -- CarWidget's
        // `when` falls through to the AUTO room-based check for anything else, and
        // this pins that "anything else" is a real, reachable branch, not dead code.
        val stale = WidgetConfig(vin = "test", buttonLabels = "future-mode")
        assertTrue(stale.buttonLabels != WidgetConfig.BUTTON_LABELS_ALWAYS)
        assertTrue(stale.buttonLabels != WidgetConfig.BUTTON_LABELS_OFF)
    }
}
