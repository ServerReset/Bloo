package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.GroupHeading
import com.bloo.uicommon.MorphIconButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What a screen reader and a keyboard are given: the semantics TalkBack reads (states, reasons, names, live regions, headings), and the
 * keyboard behaviour that holds in a desktop window. These run on the emulator in CI (the ui-tests job).
 */
class AccessibilitySuiteTest {
    @get:Rule val rule = createComposeRule()

    // --- Battery Saver and Android hold switches: shown on, unchangeable, and says why -----------------------------------------------

    @Test fun aHeldSwitch_isOn_disabled_andSaysWhy() {
        val why = "Battery Saver is on, so this is held. It returns to your choice when Battery Saver is off."
        rule.setContent {
            BlooTheme { ToggleRow("Reduce motion", checked = true, enabled = false, description = why, onChange = {}) }
        }
        rule.onNode(isToggleable()).assertIsNotEnabled().assertIsOn()
        rule.onNodeWithText(why).assertIsDisplayed()
    }

    @Test fun aFreeSwitch_isEnabled_andTogglesOnTap() {
        rule.setContent {
            BlooTheme {
                var on by remember { mutableStateOf(false) }
                ToggleRow("Motion blur", checked = on, description = "Soft blur on moving things.", onChange = { on = it })
            }
        }
        rule.onNode(isToggleable()).assertIsEnabled().assertIsOff().performClick()
        rule.onNode(isToggleable()).assertIsOn()
    }

    @Test fun aSwitch_hasTheSwitchRole() {
        rule.setContent { BlooTheme { ToggleRow("Keyboard shortcuts", checked = true, onChange = {}) } }
        rule.onNode(isToggleable())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
    }

    // --- Icon-only buttons are named ------------------------------------------------------------------------------------------------

    @Test fun anIconOnlyButton_isOneNamedButton() {
        var taps = 0
        rule.setContent {
            BlooTheme {
                MorphIconButton(onClick = { taps++ }, description = "Copy message") { Box(Modifier.size(20.dp)) }
            }
        }
        rule.onNodeWithContentDescription("Copy message")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(1, taps)
    }

    // --- Status that changes while the person is elsewhere is announced politely -----------------------------------------------------

    @Test fun aChangingStatusLine_isAPoliteLiveRegion() {
        rule.setContent { BlooTheme { Text("Downloading", modifier = Modifier.announcesChanges()) } }
        rule.onNodeWithText("Downloading")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    // --- Settings groups are headings, so a screen reader can jump between them ------------------------------------------------------

    @Test fun aSettingsGroupTitle_isAHeading() {
        rule.setContent { BlooTheme { GroupHeading("Accessibility") } }
        rule.onNodeWithText("Accessibility")
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    // --- Keyboard shortcuts: on, and they reach the handler; off, and they do not -----------------------------------------------------

    @Test fun aShortcut_reachesTheHandler_andTheHelpShortcutToo() {
        val heard = mutableStateOf<Shortcut?>(null)
        rule.setContent { Box(Modifier.fillMaxSize().appShortcuts(enabled = true) { heard.value = it }) }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.K) } }
        rule.waitForIdle()
        assertEquals(Shortcut.Search, heard.value)
        heard.value = null
        rule.onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Slash) } }
        rule.waitForIdle()
        assertEquals(Shortcut.Help, heard.value)
    }

    @Test fun plainTypingIsNeverTakenAsAShortcut() {
        val heard = mutableStateOf<Shortcut?>(null)
        rule.setContent { Box(Modifier.fillMaxSize().appShortcuts(enabled = true) { heard.value = it }) }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { pressKey(Key.K) }
        rule.waitForIdle()
        assertTrue("a bare letter is typing, not a shortcut", heard.value == null)
    }
}
