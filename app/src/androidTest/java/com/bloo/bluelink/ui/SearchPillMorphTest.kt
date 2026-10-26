package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The pill's morph moves in layout: its size follows the animated width and height, and nothing recomposes to do it. */
class SearchPillMorphTest {
    @get:Rule val rule = createComposeRule()

    @Test fun theSizeFollowsTheStateWithoutRecomposing() {
        val width = mutableStateOf(80.dp)
        val height = mutableStateOf(48.dp)
        var composed = 0
        rule.setContent {
            BlooTheme {
                SideEffect { composed++ }
                Box(Modifier.testTag("pill")) {
                    SearchPill(
                        query = "",
                        focused = false,
                        form = SearchForm.BUBBLE,
                        width = { width.value },
                        height = { height.value },
                        onQueryChange = {},
                        onFocusChange = {},
                        onSubmit = {},
                        onDrag = null,
                    )
                }
            }
        }
        rule.waitForIdle()
        val before = composed
        val start = rule.onNodeWithTag("pill").getBoundsInRoot()
        assertEquals(80f, (start.right - start.left).value, 1f)
        width.value = 200.dp
        height.value = 64.dp
        rule.waitForIdle()
        val bounds = rule.onNodeWithTag("pill").getBoundsInRoot()
        assertEquals(200f, (bounds.right - bounds.left).value, 1f)
        assertEquals(64f, (bounds.bottom - bounds.top).value, 1f)
        assertEquals("the parent did not recompose", before, composed)
    }
}
