package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
import androidx.compose.ui.test.center
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The button-group behaviour, measured on a real layout: lines of buttons fill their width edge to
 * edge, a lone button rests at its natural width on the start edge, and widens to the row when
 * pressed -- left to right and right to left.
 */
class ButtonGroupLayoutTest {
    @get:Rule val rule = createComposeRule()

    private val rowWidth = 360.dp

    private fun bounds(tag: String) = rule.onNodeWithTag(tag).getBoundsInRoot()

    private fun widthOf(tag: String) = bounds(tag).let { it.right.value - it.left.value }

    @Test
    fun wrappedButtonsFillEachLineEdgeToEdge() {
        val labels = listOf("Alpha button", "Bravo button", "Charlie button", "Delta button", "Echo button")
        rule.setContent {
            BlooTheme {
                Box(Modifier.width(rowWidth)) {
                    ExpressiveButtonRow(Modifier.fillMaxWidth(), spacing = 8.dp) {
                        labels.forEachIndexed { i, label ->
                            SafeMorphTextButton(label, onClick = {}, modifier = Modifier.testTag("b$i"), showIcon = false)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val all = labels.indices.map { bounds("b$it") }
        val lines = all.groupBy { it.top.value.toInt() }.toSortedMap().values.toList()
        assertTrue("five wide buttons must wrap onto more than one line, got ${lines.size}", lines.size > 1)
        val layout = all.joinToString { "[l=${it.left.value} t=${it.top.value} r=${it.right.value}]" }
        lines.forEach { line ->
            val right = line.maxOf { it.right.value }
            val left = line.minOf { it.left.value }
            if (line.size > 1) {
                assertEquals("a shared line fills the row's full width; buttons were $layout", rowWidth.value, right - left, 2f)
            } else {
                // A button alone on its line rests at its own width on the start edge.
                assertTrue("a lone button keeps its natural width; buttons were $layout", right - left < rowWidth.value - 40f)
                assertEquals("and starts at the start edge; buttons were $layout", 0f, left, 1.5f)
            }
        }
        // Balanced: no more lines than the width needs (two buttons to a line here, so three lines).
        assertEquals("five buttons of this width balance into three lines; buttons were $layout", 3, lines.size)
    }

    @Test
    fun aLoneButtonRestsNaturalAtTheStartAndGrowsToTheCapWhenPressed() {
        rule.setContent {
            BlooTheme {
                Box(Modifier.width(rowWidth)) {
                    ExpressiveButtonRow(Modifier.fillMaxWidth(), spacing = 8.dp) {
                        SafeMorphTextButton("Sync now", onClick = {}, modifier = Modifier.testTag("lone"), showIcon = false)
                    }
                }
            }
        }
        rule.waitForIdle()
        val rest = bounds("lone")
        assertTrue("a lone button rests narrower than its row", widthOf("lone") < rowWidth.value - 40f)
        assertEquals("and sits on the start edge", 0f, rest.left.value, 1.5f)
        rule.onNodeWithTag("lone").performTouchInput { down(center) }
        // Pressed, it grows to fill the row, but never past the shared single-button cap.
        val target = minOf(rowWidth.value, MaxSingleButtonWidth.value)
        rule.waitUntil(3_000) { widthOf("lone") >= target - 8f }
        assertTrue(
            "a lone button never exceeds MaxSingleButtonWidth (was ${widthOf("lone")})",
            widthOf("lone") <= MaxSingleButtonWidth.value + 2f,
        )
        rule.onNodeWithTag("lone").performTouchInput { up() }
    }

    @Test
    fun aLoneButtonRestsOnTheRightInRightToLeft() {
        rule.setContent {
            BlooTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.width(rowWidth)) {
                        ExpressiveButtonRow(Modifier.fillMaxWidth(), spacing = 8.dp) {
                            SafeMorphTextButton("Sync now", onClick = {}, modifier = Modifier.testTag("lone"), showIcon = false)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        val rest: androidx.compose.ui.unit.DpRect = bounds("lone")
        assertEquals("in RTL the start edge is the right one", rowWidth.value, rest.right.value, 1.5f)
    }
}
