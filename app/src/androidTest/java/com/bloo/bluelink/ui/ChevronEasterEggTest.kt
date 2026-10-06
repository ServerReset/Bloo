package com.bloo.bluelink.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The pebble header chevron: a TAP toggles, a LONG-PRESS runs the spin easter egg and must NOT
 * toggle.
 */
class ChevronEasterEggTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun tapTogglesButLongPressDoesNot() {
        var toggles = 0
        rule.setContent {
            BlooTheme {
                SplitExpandButton(action = null, expanded = false, onToggle = { toggles++ })
            }
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Expand").performClick()
        assertEquals("a tap toggles", 1, toggles)
        // Long-press: spins the chevron, never toggles.
        rule.onNodeWithContentDescription("Expand").performTouchInput { longClick() }
        rule.waitForIdle()
        assertEquals("a long-press does not toggle", 1, toggles)
    }
}
