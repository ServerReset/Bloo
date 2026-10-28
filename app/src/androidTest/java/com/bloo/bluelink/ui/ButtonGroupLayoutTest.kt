package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.ExpressiveButtonGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.bloo.uicommon.MorphTextButton

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
                    ExpressiveButtonGroup(Modifier.fillMaxWidth(), spacing = 8.dp) {
                        labels.forEachIndexed { i, label ->
                            MorphTextButton(label, onClick = {}, modifier = Modifier.testTag("b$i"), showIcon = false)
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
            // Every line of a wrapped row fills the row, a button alone on its line included: no half-empty lines.
            assertEquals("each wrapped line fills the row's full width; buttons were $layout", rowWidth.value, right - left, 2f)
        }
        // Balanced: no more lines than the width needs (two buttons to a line here, so three lines).
        assertEquals("five buttons of this width balance into three lines; buttons were $layout", 3, lines.size)
    }

    @Test
    fun aLoneButtonRestsNaturalAtTheStartAndGrowsToDoubleWhenPressed() {
        rule.setContent {
            BlooTheme {
                Box(Modifier.width(rowWidth)) {
                    ExpressiveButtonGroup(Modifier.fillMaxWidth(), spacing = 8.dp) {
                        MorphTextButton("Sync now", onClick = {}, modifier = Modifier.testTag("lone"), showIcon = false)
                    }
                }
            }
        }
        rule.waitForIdle()
        val rest = bounds("lone")
        val restWidth = widthOf("lone")
        assertTrue("a lone button rests narrower than its row", restWidth < rowWidth.value - 40f)
        assertEquals("and sits on the start edge", 0f, rest.left.value, 1.5f)
        rule.onNodeWithTag("lone").performTouchInput { down(center) }
        // Pressed, it grows, but never past DOUBLE its resting width.
        rule.waitUntil(3_000) { widthOf("lone") > restWidth + 4f }
        rule.waitForIdle()
        val grown = widthOf("lone")
        assertTrue("a lone button grows on press ($restWidth -> $grown)", grown > restWidth)
        assertTrue("a lone button never grows past 2x its resting width (was $grown, rest $restWidth)", grown <= restWidth * 2f + 4f)
        rule.onNodeWithTag("lone").performTouchInput { up() }
    }

    @Test
    fun aLoneButtonRestsOnTheRightInRightToLeft() {
        rule.setContent {
            BlooTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.width(rowWidth)) {
                        ExpressiveButtonGroup(Modifier.fillMaxWidth(), spacing = 8.dp) {
                            MorphTextButton("Sync now", onClick = {}, modifier = Modifier.testTag("lone"), showIcon = false)
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
