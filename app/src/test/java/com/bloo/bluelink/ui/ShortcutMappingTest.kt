package com.bloo.bluelink.ui

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The hardware-keyboard map: what a desktop or keyboard user's keys do, and that plain typing never does anything. */
class ShortcutMappingTest {
    @Test fun ctrlK_andCtrlF_openSearch() {
        assertEquals(Shortcut.Search, shortcutFor(Key.K, ctrl = true, shift = false))
        assertEquals(Shortcut.Search, shortcutFor(Key.F, ctrl = true, shift = false))
    }

    @Test fun escAndF1_needNoModifier() {
        assertEquals(Shortcut.Back, shortcutFor(Key.Escape, ctrl = false, shift = false))
        assertEquals(Shortcut.Help, shortcutFor(Key.F1, ctrl = false, shift = false))
    }

    @Test fun ctrlPageKeys_andCtrlTab_moveBetweenCars() {
        assertEquals(Shortcut.NextCar, shortcutFor(Key.PageDown, ctrl = true, shift = false))
        assertEquals(Shortcut.NextCar, shortcutFor(Key.Tab, ctrl = true, shift = false))
        assertEquals(Shortcut.PreviousCar, shortcutFor(Key.Tab, ctrl = true, shift = true))
    }

    @Test fun ctrlNumber_goesToThatCar() {
        assertEquals(Shortcut.GoToCar(0), shortcutFor(Key.One, ctrl = true, shift = false))
        assertEquals(Shortcut.GoToCar(8), shortcutFor(Key.Nine, ctrl = true, shift = false))
    }

    @Test fun unlockNeedsShift_soItIsNeverOneSlip() {
        assertNull(shortcutFor(Key.U, ctrl = true, shift = false))
        assertEquals(Shortcut.Unlock, shortcutFor(Key.U, ctrl = true, shift = true))
    }

    @Test fun plainLetters_inAFieldDoNothing() {
        assertNull(shortcutFor(Key.K, ctrl = false, shift = false))
        assertNull(shortcutFor(Key.R, ctrl = false, shift = false))
        assertNull(shortcutFor(Key.L, ctrl = false, shift = false))
    }
}
