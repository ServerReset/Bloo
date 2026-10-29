package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.testTag
import com.bloo.uicommon.ButtonEmphasis
import com.bloo.uicommon.MorphTextButton
import org.junit.Rule
import org.junit.Test

/**
 * Tab moves through a settings-style form in reading order, one control at a time, and Shift+Tab goes back. A keyboard user (or a desktop
 * window with a keyboard) must never lose their place. Runs on the emulator in CI.
 */
class KeyboardNavigationTest {
    @get:Rule val rule = createComposeRule()

    private fun focused(tag: String) = rule.onNodeWithTag(tag)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true))

    @Test fun tabWalksTheFormInOrder_andShiftTabComesBack() {
        rule.setContent {
            BlooTheme {
                Column {
                    var motion by remember { mutableStateOf(true) }
                    var shortcuts by remember { mutableStateOf(true) }
                    ToggleRow("Motion blur", motion, modifier = Modifier.testTag("motion"), onChange = { motion = it })
                    ToggleRow("Keyboard shortcuts", shortcuts, modifier = Modifier.testTag("shortcuts"), onChange = { shortcuts = it })
                    MorphTextButton("Save", onClick = {}, modifier = Modifier.testTag("save"), emphasis = ButtonEmphasis.Confirm)
                }
            }
        }
        rule.waitForIdle()

        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        rule.waitForIdle()
        focused("motion")

        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        rule.waitForIdle()
        focused("shortcuts")

        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        rule.waitForIdle()
        focused("save")

        rule.onRoot().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        rule.waitForIdle()
        focused("shortcuts")
    }

    @Test fun enterActivatesTheFocusedSwitch() {
        rule.setContent {
            BlooTheme {
                var on by remember { mutableStateOf(false) }
                ToggleRow("Motion blur", on, modifier = Modifier.testTag("motion"), onChange = { on = it })
            }
        }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.Tab) }
        rule.waitForIdle()
        focused("motion")
        rule.onRoot().performKeyInput { pressKey(Key.Enter) }
        rule.waitForIdle()
        rule.onNodeWithTag("motion").assertIsOn()
    }
}
