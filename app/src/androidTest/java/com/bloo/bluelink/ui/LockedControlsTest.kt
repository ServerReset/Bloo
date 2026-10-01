package com.bloo.bluelink.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Controls behind the glass lock swallow taps and say why; unlocked, they work again. */
class LockedControlsTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun lockedControlsIgnoreTapsAndExplainWhy() {
        var taps = 0
        var locked by mutableStateOf(true)
        rule.setContent {
            BlooTheme {
                LockedControls(locked = locked, message = "Stop climate to change settings") {
                    Box(Modifier.size(120.dp).clickable { taps++ }) { Text("Set temperature") }
                }
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Stop climate to change settings").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Set temperature").performClick()
        rule.waitForIdle()
        assertEquals("a tap through the glass must not reach the control", 0, taps)

        rule.runOnUiThread { locked = false }
        rule.waitUntil(5_000) { rule.onAllNodesWithText("Stop climate to change settings").fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText("Set temperature").performClick()
        rule.waitForIdle()
        assertEquals("once unlocked the control works", 1, taps)
    }
}
