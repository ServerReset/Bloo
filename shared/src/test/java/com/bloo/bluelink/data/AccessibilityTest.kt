package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessibilityTest {
    @Test fun aPlainDevice_keepsEveryChoice() {
        val r = resolveAccessibility(AccessibilityChoices(reduceMotion = false, keyboardShortcuts = false), AccessibilityDevice())
        assertFalse(r.reduceMotion.value); assertNull(r.reduceMotion.override)
        assertFalse(r.keyboardShortcuts.value); assertFalse(r.keyboardShortcuts.locked)
    }

    @Test fun batterySaver_forcesReduceMotionOn_andSaysWhy() {
        val r = resolveAccessibility(AccessibilityChoices(reduceMotion = false), AccessibilityDevice(batterySaver = true))
        assertTrue(r.reduceMotion.value); assertEquals(OverrideReason.BATTERY_SAVER, r.reduceMotion.override)
        assertTrue(r.reduceMotion.locked)
    }

    @Test fun batterySaver_keepsTheUsersChoice_forWhenItGoesOff() {
        val choices = AccessibilityChoices(reduceMotion = false)
        resolveAccessibility(choices, AccessibilityDevice(batterySaver = true))
        val back = resolveAccessibility(choices, AccessibilityDevice(batterySaver = false))
        assertFalse(back.reduceMotion.value); assertFalse(back.reduceMotion.locked)
    }

    @Test fun batterySaver_doesNotOverrideKeyboardShortcuts() {
        val r = resolveAccessibility(AccessibilityChoices(keyboardShortcuts = true), AccessibilityDevice(batterySaver = true))
        assertTrue(r.keyboardShortcuts.value); assertFalse(r.keyboardShortcuts.locked)
    }

    @Test fun systemRemoveAnimations_winsOverEverything_andLocks() {
        val r = resolveAccessibility(
            AccessibilityChoices(reduceMotion = false),
            AccessibilityDevice(systemReduceMotion = true, batterySaver = false),
        )
        assertTrue(r.reduceMotion.value)
        assertEquals(OverrideReason.SYSTEM_SETTING, r.reduceMotion.override)
    }

    @Test fun screenReader_isReportedButChangesNoSwitch() {
        val r = resolveAccessibility(AccessibilityChoices(), AccessibilityDevice(screenReaderOn = true))
        assertTrue(r.screenReaderOn)
        assertFalse(r.reduceMotion.locked); assertFalse(r.keyboardShortcuts.locked)
    }

    @Test fun theDefault_isTheUnforcedDefaults() {
        val d = AccessibilityResolved.Default
        assertFalse(d.reduceMotion.value)
        assertTrue(d.keyboardShortcuts.value)
        assertFalse(d.screenReaderOn)
    }
}
