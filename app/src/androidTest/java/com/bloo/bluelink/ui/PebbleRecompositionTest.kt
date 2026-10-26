package com.bloo.bluelink.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Opening a pebble is an animation of its corner and height; its content must not be rebuilt on every frame of it.
 * The content is composed once as it enters (a few times at most as the layout settles), not once per frame.
 */
class PebbleRecompositionTest {
    @get:Rule val rule = createComposeRule()

    @Test fun openingAPebbleDoesNotRecomposeItsContentEveryFrame() {
        var expanded by mutableStateOf(false)
        var composed = 0
        rule.setContent {
            BlooTheme {
                PebbleShell(
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                    icon = AppIcons.Lock,
                    title = "Pebble",
                ) {
                    SideEffect { composed++ }
                    Text("Body")
                }
            }
        }
        rule.waitForIdle()
        val before = composed
        expanded = true
        rule.waitForIdle()
        val during = composed - before
        assertTrue("content composed $during times while opening", during <= 4)
    }
}
