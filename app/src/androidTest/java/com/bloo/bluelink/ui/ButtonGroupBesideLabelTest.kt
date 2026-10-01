package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A button group sharing a row with a weighted label (the Logs card header) must leave the label
 * its room and must not draw over it. Regression: the group stretched to the loose width a Row
 * hands it, the label got zero width and wrapped one letter per line, buttons on top of it.
 */
class ButtonGroupBesideLabelTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun theLabelKeepsItsRoomAndTheButtonsDoNotOverlapIt() {
        rule.setContent {
            BlooTheme {
                Row(Modifier.width(380.dp)) {
                    Text("Activity log, 125 lines", Modifier.weight(1f).testTag("label"))
                    ExpressiveButtonRow(spacing = 8.dp) {
                        SafeMorphTextButton("Copy", onClick = {}, modifier = Modifier.testTag("copy"))
                        SafeMorphTextButton("Clear", onClick = {}, modifier = Modifier.testTag("clear"))
                    }
                }
            }
        }
        rule.waitForIdle()
        val label = rule.onNodeWithTag("label").getBoundsInRoot()
        val copy = rule.onNodeWithTag("copy").getBoundsInRoot()
        val clear = rule.onNodeWithTag("clear").getBoundsInRoot()
        val labelWidth = label.right.value - label.left.value
        assertTrue("the label is squeezed to ${labelWidth}dp", labelWidth > 60f)
        assertTrue("the buttons overlap the label (label right ${label.right.value}, copy left ${copy.left.value})", copy.left.value >= label.right.value - 1f)
        assertTrue("the buttons overlap each other", clear.left.value >= copy.right.value - 1f)
        assertTrue("the buttons run off the row", clear.right.value <= 380.5f)
    }
}
