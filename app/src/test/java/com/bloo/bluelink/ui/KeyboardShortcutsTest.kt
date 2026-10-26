package com.bloo.bluelink.ui

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which key presses are shortcuts: typing is never taken, and the dangerous ones need more than one slip of a finger. */
class KeyboardShortcutsTest {
    @Test fun plainTypingIsNeverAShortcut() {
        for (k in listOf(Key.K, Key.R, Key.L, Key.U, Key.C, Key.F, Key.One, Key.Slash, Key.Comma, Key.Tab, Key.PageDown)) {
            assertNull(shortcutFor(k, ctrl = false, shift = false), "plain $k")
        }
    }

    @Test fun theHelpKeys() {
        assertEquals(Shortcut.Help, shortcutFor(Key.Slash, ctrl = true, shift = false))
        assertEquals(Shortcut.Help, shortcutFor(Key.Slash, ctrl = true, shift = true))
        assertEquals(Shortcut.Help, shortcutFor(Key.F1, ctrl = false, shift = false))
    }

    @Test fun escapeIsBackAndNeedsNoModifier() {
        assertEquals(Shortcut.Back, shortcutFor(Key.Escape, ctrl = false, shift = false))
    }

    @Test fun searchAndRefresh() {
        assertEquals(Shortcut.Search, shortcutFor(Key.K, ctrl = true, shift = false))
        assertEquals(Shortcut.Search, shortcutFor(Key.F, ctrl = true, shift = false))
        assertEquals(Shortcut.Refresh, shortcutFor(Key.R, ctrl = true, shift = false))
        assertEquals(Shortcut.Refresh, shortcutFor(Key.F5, ctrl = false, shift = false))
    }

    @Test fun carsAreSwitchedLikeBrowserTabs() {
        assertEquals(Shortcut.NextCar, shortcutFor(Key.PageDown, ctrl = true, shift = false))
        assertEquals(Shortcut.NextCar, shortcutFor(Key.Tab, ctrl = true, shift = false))
        assertEquals(Shortcut.PreviousCar, shortcutFor(Key.PageUp, ctrl = true, shift = false))
        assertEquals(Shortcut.PreviousCar, shortcutFor(Key.Tab, ctrl = true, shift = true))
        assertEquals(Shortcut.GoToCar(0), shortcutFor(Key.One, ctrl = true, shift = false))
        assertEquals(Shortcut.GoToCar(8), shortcutFor(Key.Nine, ctrl = true, shift = false))
    }

    @Test fun arrowKeysAreLeftToTextFields() {
        assertNull(shortcutFor(Key.DirectionLeft, ctrl = true, shift = false))
        assertNull(shortcutFor(Key.DirectionRight, ctrl = true, shift = false))
    }

    @Test fun unlockingNeedsShiftAsWell() {
        assertEquals(Shortcut.Lock, shortcutFor(Key.L, ctrl = true, shift = false))
        assertNull(shortcutFor(Key.U, ctrl = true, shift = false))
        assertEquals(Shortcut.Unlock, shortcutFor(Key.U, ctrl = true, shift = true))
        assertNull(shortcutFor(Key.L, ctrl = true, shift = true))
    }

    @Test fun climateNeedsShiftToo() {
        assertNull(shortcutFor(Key.C, ctrl = true, shift = false))
        assertEquals(Shortcut.Climate, shortcutFor(Key.C, ctrl = true, shift = true))
    }
}
