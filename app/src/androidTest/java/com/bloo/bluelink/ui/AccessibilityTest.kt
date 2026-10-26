package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import com.bloo.uicommon.MorphTextButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** What a screen reader and a keyboard meet: one labelled button per button, the shortcuts pop-up, and the keys themselves. */
class AccessibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test fun aButtonIsOneLabelledNodeWithTheButtonRole() {
        rule.setContent { BlooTheme { MorphTextButton("Lock", onClick = {}) } }
        rule.onNodeWithText("Lock")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test fun theShortcutsPopUpListsWhatTheKeysDo() {
        rule.setContent { BlooTheme { ShortcutsDialog(onDismiss = {}) } }
        for ((keys, what) in ShortcutHelp) {
            assertTrue("$what is listed", rule.onAllNodesWithText(what).fetchSemanticsNodes().isNotEmpty())
            assertTrue("$keys is listed", rule.onAllNodesWithText(keys).fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test fun aShortcutKeyReachesTheHandler() {
        val heard = mutableStateOf<Shortcut?>(null)
        rule.setContent { Box(Modifier.fillMaxSize().appShortcuts(enabled = true) { heard.value = it }) }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.K) } }
        rule.waitForIdle()
        assertEquals(Shortcut.Search, heard.value)
    }

    @Test fun largeTextKeepsAButtonInsideItsWidthAndOnScreen() {
        rule.setContent {
            BlooTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2f)) {
                    Box(Modifier.width(300.dp)) { MorphTextButton("Start the climate now", onClick = {}, modifier = Modifier.fillMaxWidth()) }
                }
            }
        }
        rule.onNodeWithText("Start the climate now").assertIsDisplayed()
        val bounds = rule.onNodeWithText("Start the climate now").getBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue("the button stays within 300dp at double text size (was $width)", width <= 300.dp)
    }

    @Test fun aDisabledShortcutHandlerHearsNothing() {
        val heard = mutableStateOf<Shortcut?>(null)
        rule.setContent { Box(Modifier.fillMaxSize().appShortcuts(enabled = false) { heard.value = it }) }
        rule.waitForIdle()
        rule.onRoot().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.K) } }
        rule.waitForIdle()
        assertEquals(null, heard.value)
    }
}
