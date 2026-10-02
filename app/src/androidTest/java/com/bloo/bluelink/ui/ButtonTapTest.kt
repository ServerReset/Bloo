package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * A tap on a button must reach its click handler, whether the label fits or has shrunk to its symbol.
 * A pointer handler laid over a button's content once swallowed every tap (the chrome that takes
 * clicks sits underneath it), which no other test noticed.
 */
class ButtonTapTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun aTapReachesAButtonWhoseLabelFits() {
        var clicks = 0
        rule.setContent { BlooTheme { SafeMorphTextButton("Save", onClick = { clicks++ }) } }
        rule.onNodeWithText("Save").performClick()
        rule.waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun aTapReachesAButtonThatHasCollapsedToItsSymbol() {
        var clicks = 0
        rule.setContent {
            BlooTheme {
                Box(Modifier.width(52.dp)) {
                    SafeMorphTextButton("A rather long button label", onClick = { clicks++ })
                }
            }
        }
        rule.onRoot().performTouchInput { click(center) }
        rule.waitForIdle()
        assertEquals(1, clicks)
    }
}
