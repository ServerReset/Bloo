package com.bloo.bluelink.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The toast stack: several at once, oldest on top / newest at the bottom, a repeat refreshes instead of stacking. */
class ToastStackTest {
    @get:Rule val rule = createComposeRule()

    private fun exists(text: String) = rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun toastsStackOldestOnTopAndARepeatDoesNotDuplicate() {
        val state = ToastState()
        rule.setContent { BlooTheme { ToastHost(state, HazeState(), onCopy = {}) } }
        rule.runOnUiThread {
            state.show("First toast", ToastKind.INFO)
            state.show("Second toast", ToastKind.INFO)
            state.show("Second toast", ToastKind.INFO)
            state.show("Third toast", ToastKind.SUCCESS)
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
            state.show("Saved", ToastKind.SUCCESS)
            state.show("Saved", ToastKind.SUCCESS)
            state.show("Saved", ToastKind.SUCCESS)
        }
        rule.waitUntil(8_000) { exists("Saved") && exists("\u00d73") }
        assertEquals("one toast, not three", 1, rule.onAllNodesWithText("Saved").fetchSemanticsNodes().size)
    }

    @Test
    fun aStackIsExpandedByDefaultWithNoTap() {
        val state = ToastState()
        rule.setContent { BlooTheme { ToastHost(state, HazeState(), onCopy = {}) } }
        rule.runOnUiThread {
            state.show("First", ToastKind.INFO)
            state.show("Second", ToastKind.INFO)
        }
        rule.waitUntil(8_000) { exists("First") && exists("Second") }
        val first = rule.onNodeWithText("First").getBoundsInRoot().top.value
        val second = rule.onNodeWithText("Second").getBoundsInRoot().top.value
        // Already a full slot apart (SearchElementHeight + gap), no tap needed.
        assertTrue("the stack is expanded with no tap (${second - first}px)", second - first > 40f)
    }

    @Test
    fun movingTheSearchDoesNotCrashTheStack() {
        // Regression: the placement springs are underdamped, so an inset animating toward zero used
        // to undershoot below zero and blow up Modifier.padding ("Padding must be non-negative").
        val anchor = SearchAnchor()
        val toasts = ToastState().apply { show("Saved", ToastKind.SUCCESS) }
        // Drive the clock by hand so the placement springs actually run, but the toast's own expiry
        // timer does not fire mid-test.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            BlooTheme {
                CompositionLocalProvider(LocalSearchAnchor provides anchor) {
                    ToastHost(state = toasts, hazeState = HazeState(), onCopy = {})
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        // A left-docked search beside the toast: a large positive start inset.
        rule.runOnUiThread { anchor.publish(Rect(16f, 1800f, 56f, 1852f), SearchDock.LEFT) }
        rule.mainClock.advanceTimeBy(900)
        // Then a centred search: the inset springs back to zero (this is the crash path).
        rule.runOnUiThread { anchor.publish(Rect(440f, 1200f, 560f, 1252f), SearchDock.CENTER) }
        rule.mainClock.advanceTimeBy(900)
        rule.runOnUiThread { anchor.publish(Rect(16f, 1800f, 56f, 1852f), SearchDock.LEFT) }
        rule.mainClock.advanceTimeBy(900)
        assertTrue("the stack survived the placement changes", toasts.items.isNotEmpty())
    }
}
