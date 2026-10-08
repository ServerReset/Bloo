package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The search element and a toast are the SAME height ([SearchElementHeight]), so a toast reads as the
 * bar it emerged from. The toast's content used to force 56dp (40dp icon buttons + 16dp padding)
 * while the search was 52, so a `heightIn(min)` never matched them.
 */
class SearchToastHeightTest {
    @get:Rule val rule = createComposeRule()

    private fun height(tag: String) = rule.onNodeWithTag(tag).getBoundsInRoot().let { (it.bottom - it.top).value }

    private fun width(tag: String) = rule.onNodeWithTag(tag).getBoundsInRoot().let { (it.right - it.left).value }

    @Test
    fun theSearchElementAndAToastAreTheSameHeight() {
        val toasts = ToastState().apply { show("Saved", ToastKind.SUCCESS) }
        rule.setContent {
            BlooTheme {
                Column {
                    Box(Modifier.testTag("search")) {
                        SearchPill(
                            query = "",
                            focused = false,
                            form = SearchForm.BUBBLE,
                            width = SearchElementHeight,
                            height = SearchElementHeight,
                            onQueryChange = {},
                            onFocusChange = {},
                            onSubmit = {},
                            onDrag = null,
                        )
                    }
                    ToastHost(
                        state = toasts,
                        hazeState = remember { HazeState() },
                        onCopy = {},
                    )
                }
            }
        }
        rule.waitForIdle()
        val target = SearchElementHeight.value.toDouble()
        assertEquals("the search element is SearchElementHeight tall", target, height("search").toDouble(), 1.0)
        assertEquals("the toast is SearchElementHeight tall", target, height(ToastCardTag).toDouble(), 1.0)
        assertEquals("and they match each other", height("search").toDouble(), height(ToastCardTag).toDouble(), 0.5)
    }

    @Test
    fun aToastFillsTheAvailableWidth() {
        // Toasts are full-width bars (the bar the search element emerged from), not compact pills.
        val toasts = ToastState().apply { show("Hi", ToastKind.INFO) }
        rule.setContent {
            BlooTheme {
                ToastHost(state = toasts, hazeState = remember { HazeState() }, onCopy = {})
            }
        }
        rule.waitForIdle()
        val root = rule.onRoot().getBoundsInRoot().let { (it.right - it.left).value }
        val w = width(ToastCardTag)
        assertTrue("the toast fills the width minus the two edge insets (root=$root, w=$w)", w >= root - 40f)
    }
}
