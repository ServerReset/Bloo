package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Tab moves through a settings-style form in reading order, one control at a time, and Shift+Tab goes back; Enter acts on whichever
 * control has focus. The checks are on what each key does, not on a focus flag: where the keys land is what a keyboard user feels. The
 * first Tab from nothing focused lands on the first switch, as it would for a keyboard user. Runs on the emulator in CI.
 */
class KeyboardNavigationTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tabAndShiftTab_moveFocus_andEnterActsOnWhereFocusIs() {
        var saves = 0
        rule.setContent {
            BlooTheme {
                Column {
                    var motion by remember { mutableStateOf(true) }
                    var shortcuts by remember { mutableStateOf(true) }
                    ToggleRow(
                        "Motion blur", motion,
                        modifier = Modifier.testTag("motion"),
                        onChange = { motion = it },
                    )
                    ToggleRow("Keyboard shortcuts", shortcuts, modifier = Modifier.testTag("shortcuts"), onChange = { shortcuts = it })
                    MorphTextButton("Save", onClick = { saves++ }, modifier = Modifier.testTag("save"), emphasis = ButtonEmphasis.Confirm)
                }
            }
        }
        rule.waitForIdle()

        // The first Tab from nothing focused lands on the first switch; Enter on it turns it off.
        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        rule.onNodeWithTag("motion").assertIsOff()

        // One Tab: Enter now turns the second switch off, and leaves the first alone.
        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        rule.onNodeWithTag("shortcuts").assertIsOff()
        rule.onNodeWithTag("motion").assertIsOff()

        // Another Tab reaches Save, and Enter presses it.
        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        assertEquals(1, saves)

        // Shift+Tab goes back to the second switch: Enter turns it back on.
        rule.onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        rule.onNodeWithTag("shortcuts").assertIsOn()
        assertEquals(1, saves)
    }
}
