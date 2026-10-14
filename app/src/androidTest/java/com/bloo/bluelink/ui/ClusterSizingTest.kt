package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The button cluster's sizing rule, measured on a real layout. One rule, everywhere:
 *
 *  - a button shows its label whenever the row can hold it -- the label is never dropped while there
 *    is room, and the button is never wider than the label it shows;
 *  - an icon-only button is [IconButtonMinWidth]: the size of its glyph plus a tap target, NOT a
 *    square as tall as the header;
 *  - when a labelled button IS collapsed for want of room, its CONTAINER shrinks to the glyph instead
 *    of keeping the width the label would have needed.
 *
 * The label is a short one ("Run") so the assertions hold on every connected device, watches
 * included: the group is told a fixed dp width, and a watch screen clamps that to its own width.
 */
class ClusterSizingTest {
    @get:Rule val rule = createComposeRule()

    private val label = "Run"

    private fun widthOf(tag: String) = rule.onNodeWithTag(tag).getBoundsInRoot().let { it.right.value - it.left.value }

    /** A header-shaped host: a bounded height, exactly as the header row measures the cluster. */
    private fun header(widthDp: Int, content: @Composable () -> Unit) = @Composable {
        BlooTheme { Box(Modifier.width(widthDp.dp).height(PebbleHeaderHeight)) { content() } }
    }

    @Composable
    private fun cluster(actionLabel: String = label) {
        ButtonCluster(
            buttons = listOf(
                ClusterButton(onClick = {}, modifier = Modifier.testTag("action")) {
                    MorphButtonLabel(AppIcons.Bolt, actionLabel, pending = false)
                },
                ClusterButton(onClick = {}, modifier = Modifier.testTag("chevron")) {
                    Icon(AppIcons.Lock, contentDescription = null)
                },
            ),
            modifier = Modifier.testTag("cluster"),
        )
    }

    @Test
    fun aLabelledButtonOnItsOwnIsAsWideAsItsLabel() {
        // Isolation: the label's own intrinsic width, with no group involved. If THIS were small, the
        // group would be told the button needs almost nothing and would drop the label for no reason.
        rule.setContent {
            BlooTheme {
                Box(Modifier.width(360.dp)) {
                    MorphButton(onClick = {}, modifier = Modifier.testTag("solo")) {
                        MorphButtonLabel(AppIcons.Bolt, label, pending = false)
                    }
                }
            }
        }
        rule.waitForIdle()
        val solo = widthOf("solo")
        assertTrue("a standalone labelled button must be its label (width ${solo}dp)", solo > 60f)
    }

    @Test
    fun aLabelIsShownWheneverTheRowCanHoldIt() {
        rule.setContent(header(360) { cluster() })
        rule.waitForIdle()
        // The label is drawn, not merely measured: the regression was a label dropped INSIDE a button
        // the group had already sized for one -- a wide, empty pill.
        rule.onNodeWithText(label).assertIsDisplayed()
        val action = widthOf("action")
        assertTrue("the action is at least its glyph and padding (width ${action}dp)", action > 40f)
    }

    /**
     * The actual regression: a label too wide for the old 96dp content cap was dropped while the
     * container kept the width the group had given it. A short label hides it (it fits under the
     * cap), so this uses a long one -- and skips on a screen that genuinely cannot hold it, since the
     * rule under test is "collapse only when NECESSARY".
     */
    @Test
    fun aGroupedButtonIsNeverNarrowerThanItsOwnLabel() {
        val long = "Summarize"
        rule.setContent {
            BlooTheme {
                androidx.compose.foundation.layout.Column {
                    // The reference: the same label, the cluster's own padding, no group around it. A
                    // grouped button must be at least as wide as this, or it is not showing the label
                    // it was sized for -- the "wide, empty pill" the content cap used to produce.
                    MorphButton(
                        onClick = {},
                        modifier = Modifier.testTag("ref"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = GapRow),
                    ) {
                        MorphButtonLabel(AppIcons.Bolt, long, pending = false)
                    }
                    Box(Modifier.width(720.dp).height(PebbleHeaderHeight)) { cluster(long) }
                }
            }
        }
        rule.waitForIdle()
        val root = rule.onRoot().getBoundsInRoot()
        org.junit.Assume.assumeTrue(
            "screen is too narrow to hold this label (${root.right.value - root.left.value}dp)",
            (root.right.value - root.left.value) >= 320f,
        )
        val ref = widthOf("ref")
        val action = widthOf("action")
        assertTrue(
            "the grouped button came out narrower than its own label (action ${action}dp, label ${ref}dp)",
            action >= ref - 2f,
        )
    }

    @Test
    fun anIconOnlyButtonIsNeverNarrowerThanItIsTall() {
        rule.setContent(header(360) { cluster() })
        rule.waitForIdle()
        // The rule the user asked for: a button's minimum is square. Collapsed (pill corners) that
        // makes it a circle; the chevron here is the small case, since its glyph is 24dp.
        val b = rule.onNodeWithTag("chevron").getBoundsInRoot()
        val w = b.right.value - b.left.value
        val h = b.bottom.value - b.top.value
        assertTrue("an icon-only button must never be narrower than it is tall (w ${w}dp, h ${h}dp)", w >= h - 1.5f)
    }

    @Test
    fun aCollapsedButtonShrinksToItsGlyph() {
        rule.setContent(header(120) { cluster() })
        rule.waitForIdle()
        val action = widthOf("action")
        // No room for the label: the button drops to its glyph and the container follows it, rather
        // than keeping the width the label would have needed.
        assertTrue("a collapsed button kept its label's width (width ${action}dp)", action < 80f)
    }
}
