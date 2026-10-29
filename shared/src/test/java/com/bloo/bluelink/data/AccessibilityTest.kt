package com.bloo.bluelink.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessibilityTest {
    @Test fun aPlainDevice_keepsEveryChoice() {
        val r = resolveAccessibility(AccessibilityChoices(reduceMotion = false, motionBlur = true, keyboardShortcuts = false), AccessibilityDevice())
        assertFalse(r.reduceMotion.value); assertNull(r.reduceMotion.override)
        assertTrue(r.motionBlur.value); assertNull(r.motionBlur.override)
        assertFalse(r.keyboardShortcuts.value); assertFalse(r.keyboardShortcuts.locked)
    }

    @Test fun batterySaver_forcesReduceMotionOn_andMotionBlurOff_andSaysWhy() {
        val r = resolveAccessibility(AccessibilityChoices(reduceMotion = false, motionBlur = true), AccessibilityDevice(batterySaver = true))
        assertTrue(r.reduceMotion.value); assertEquals(OverrideReason.BATTERY_SAVER, r.reduceMotion.override)
        assertFalse(r.motionBlur.value); assertEquals(OverrideReason.BATTERY_SAVER, r.motionBlur.override)
        assertTrue(r.reduceMotion.locked && r.motionBlur.locked)
    }

    @Test fun batterySaver_keepsTheUsersChoices_forWhenItGoesOff() {
        val choices = AccessibilityChoices(reduceMotion = false, motionBlur = true)
        resolveAccessibility(choices, AccessibilityDevice(batterySaver = true))
        val back = resolveAccessibility(choices, AccessibilityDevice(batterySaver = false))
        assertFalse(back.reduceMotion.locked)
        assertTrue(back.motionBlur.value); assertFalse(back.motionBlur.locked)
    }

    @Test fun batterySaver_doesNotOverrideKeyboardShortcuts() {
        val r = resolveAccessibility(AccessibilityChoices(keyboardShortcuts = true), AccessibilityDevice(batterySaver = true))
        assertTrue(r.keyboardShortcuts.value); assertFalse(r.keyboardShortcuts.locked)
    }

    @Test fun systemRemoveAnimations_winsOverEverything_andLocksBoth() {
        val r = resolveAccessibility(
            AccessibilityChoices(reduceMotion = false, motionBlur = true),
            AccessibilityDevice(systemReduceMotion = true, batterySaver = false),
        )
        assertEquals(OverrideReason.SYSTEM_SETTING, r.reduceMotion.override)
        assertEquals(OverrideReason.SYSTEM_SETTING, r.motionBlur.override)
        assertFalse(r.motionBlur.value)
    }

    @Test fun theUsersOwnReduceMotion_turnsMotionBlurOff_withItsOwnReason() {
        val r = resolveAccessibility(AccessibilityChoices(reduceMotion = true, motionBlur = true), AccessibilityDevice())
        assertTrue(r.reduceMotion.value); assertNull(r.reduceMotion.override)
        assertFalse(r.motionBlur.value); assertEquals(OverrideReason.REDUCE_MOTION, r.motionBlur.override)
    }

    @Test fun screenReader_isReportedButChangesNoSwitch() {
        val r = resolveAccessibility(AccessibilityChoices(), AccessibilityDevice(screenReaderOn = true))
        assertTrue(r.screenReaderOn)
        assertFalse(r.reduceMotion.locked); assertFalse(r.motionBlur.locked); assertFalse(r.keyboardShortcuts.locked)
    }

    @Test fun theDefault_isTheUnforcedDefaults() {
        val d = AccessibilityResolved.Default
        assertFalse(d.reduceMotion.value)
        assertTrue(d.motionBlur.value)
        assertTrue(d.keyboardShortcuts.value)
        assertFalse(d.screenReaderOn)
    }
}
