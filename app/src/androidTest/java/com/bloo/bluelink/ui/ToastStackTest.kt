package com.bloo.bluelink.ui

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** The toast stack: several at once, oldest on top / newest at the bottom, a repeat refreshes instead of stacking. */
class ToastStackTest {
    @get:Rule val rule = createComposeRule()

    private fun exists(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun toastsStackOldestOnTopAndARepeatDoesNotDuplicate() {
        val state = ToastState()
        rule.setContent { BlooTheme { ToastHost(state, HazeState(), onCopy = {}) } }
        rule.runOnUiThread {
            state.show("First toast", "info")
            state.show("Second toast", "info")
            state.show("Second toast", "info")
            state.show("Third toast", "success")
        }
        rule.waitUntil(8_000) { exists("First toast") && exists("Second toast") && exists("Third toast") }
        assertEquals("a repeat of the newest message is refreshed, not stacked", 1, rule.onAllNodesWithText("Second toast").fetchSemanticsNodes().size)
        val first = rule.onNodeWithText("First toast").getBoundsInRoot().top.value
        val second = rule.onNodeWithText("Second toast").getBoundsInRoot().top.value
        val third = rule.onNodeWithText("Third toast").getBoundsInRoot().top.value
        assertTrue("oldest at the top, newest at the bottom ($first, $second, $third)", first < second && second < third)
    }

    @Test
    fun aRepeatBumpsACountInsteadOfStacking() {
        val state = ToastState()
        rule.setContent { BlooTheme { ToastHost(state, HazeState(), onCopy = {}) } }
        rule.runOnUiThread {
            state.show("Saved", "success")
            state.show("Saved", "success")
            state.show("Saved", "success")
        }
        rule.waitUntil(8_000) { exists("Saved") && exists("\u00d73") }
        assertEquals("one toast, not three", 1, rule.onAllNodesWithText("Saved").fetchSemanticsNodes().size)
    }

    @Test
    fun tappingAStackedToastFansThePileOut() {
        val state = ToastState()
        rule.setContent { BlooTheme { ToastHost(state, HazeState(), onCopy = {}) } }
        rule.runOnUiThread {
            state.show("First", "info")
            state.show("Second", "info")
        }
        rule.waitUntil(8_000) { exists("First") && exists("Second") }
        fun gap() = abs(
            rule.onNodeWithText("First").getBoundsInRoot().top.value -
                rule.onNodeWithText("Second").getBoundsInRoot().top.value,
        )
        val collapsed = gap()
        // The front (newest) toast carries the fan-out tap.
        rule.onNodeWithText("Second").performClick()
        rule.waitForIdle()
        val expanded = gap()
        assertTrue("tapping fans the pile out ($collapsed -> $expanded)", expanded > collapsed + 20f)
    }
}
