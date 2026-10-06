package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The pebble header's main action (the action + chevron [ButtonCluster]) must be the same height as
 * the legacy lock/unlock/horn/lights group and every other button, so a row of pebbles lines up.
 *
 * Regression: the cluster rested at its natural content height (~40dp) while the lock group used
 * [ButtonTargetHeight] (50dp), so a pebble's header action sat visibly shorter than the quick-action
 * buttons beside it in the controls pebble.
 */
class ButtonHeightTest {
    @get:Rule val rule = createComposeRule()

    private fun heightOf(tag: String) = rule.onNodeWithTag(tag).getBoundsInRoot().let { it.bottom.value - it.top.value }

    @Test
    fun thePebbleHeaderActionMatchesTheButtonTargetHeight() {
        rule.setContent {
            BlooTheme {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    SplitExpandButton(
                        action = PebbleHeaderAction(
                            label = "Locate",
                            icon = Icons.Filled.LocationOn,
                            onClick = {},
                        ),
                        expanded = false,
                        onToggle = {},
                        modifier = Modifier.testTag("headerAction"),
                    )
                }
            }
        }
        rule.waitForIdle()
        assertEquals(
            "the pebble header action must be ButtonTargetHeight tall",
            ButtonTargetHeight.value,
            heightOf("headerAction"),
            1f,
        )
    }

    @Test
    fun aChevronOnlyHeaderMatchesTheButtonTargetHeight() {
        rule.setContent {
            BlooTheme {
                Box(Modifier.fillMaxWidth().padding(16.dp)) {
                    SplitExpandButton(
                        action = null,
                        expanded = false,
                        onToggle = {},
                        modifier = Modifier.testTag("chevronOnly"),
                    )
                }
            }
        }
        rule.waitForIdle()
        assertEquals(
            "a chevron-only header must be ButtonTargetHeight tall",
            ButtonTargetHeight.value,
            heightOf("chevronOnly"),
            1f,
        )
    }
}
