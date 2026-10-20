package com.bloo.uicommon

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NumberChangeTest {
    @Test
    fun aChangedNumberWithTheSameWordsIsANumberChange() {
        assertTrue(onlyNumbersChanged("78%", "79%"))
        assertTrue(onlyNumbersChanged("4 min ago", "12 min ago"))
        assertTrue(onlyNumbersChanged("Locked · 242 mi", "Locked · 241 mi"))
    }

    @Test
    fun changedWordsAreNot() {
        assertFalse(onlyNumbersChanged("Locked · 242 mi", "Unlocked · 242 mi"))
        assertFalse(onlyNumbersChanged("78%", "Charging"))
    }

    @Test
    fun anUnchangedOrNumberlessTextIsNot() {
        assertFalse(onlyNumbersChanged("78%", "78%"))
        assertFalse(onlyNumbersChanged("Daisy", "Daisy"))
    }
}
