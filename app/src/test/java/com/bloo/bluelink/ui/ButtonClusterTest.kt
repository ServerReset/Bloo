package com.bloo.bluelink.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import com.bloo.uicommon.PillCornerPercent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** The cluster's silhouettes for one, two and three or more buttons. */
class ButtonClusterTest {
    private val size = Size(200f, 50f)
    private val density = Density(1f)
    private fun button() = ClusterButton(onClick = {}) {}
    private fun px(shape: androidx.compose.ui.graphics.Shape, pick: (RoundedCornerShape) -> androidx.compose.foundation.shape.CornerSize) =
        pick(shape as RoundedCornerShape).toPx(size, density)

    private val pill = PillCornerPercent / 100f * 50f

    @Test
    fun aLoneButtonIsFullyRound() {
        val s = connectedShape(true, 0 == listOf(button()).lastIndex, 50, 0f, 0f)
        assertEquals(pill, px(s) { it.topStart }, 0.01f)
        assertEquals(pill, px(s) { it.topEnd }, 0.01f)
    }

    @Test
    fun twoButtonsAreRoundOutsideAndSoftInside() {
        val bs = listOf(button(), button())
        val first = connectedShape(true, 0 == bs.lastIndex, 50, 0f, 0f)
        val last = connectedShape(false, 1 == bs.lastIndex, 50, 0f, 0f)
        assertEquals(pill, px(first) { it.topStart }, 0.01f)
        assertNotEquals(pill, px(first) { it.topEnd })
        assertNotEquals(pill, px(last) { it.topStart })
        assertEquals(pill, px(last) { it.topEnd }, 0.01f)
    }

    @Test
    fun threeOrMoreButtonsHaveSeamsOnBothSidesOfTheMiddle() {
        val bs = List(4) { button() }
        val middle = connectedShape(false, 1 == bs.lastIndex, 50, 0f, 0f)
        val seam = px(middle) { it.topStart }
        assertNotEquals(pill, seam)
        assertEquals(seam, px(middle) { it.topEnd }, 0.01f)
        assertEquals(pill, px(connectedShape(true, 0 == bs.lastIndex, 50, 0f, 0f)) { it.topStart }, 0.01f)
        assertEquals(pill, px(connectedShape(false, 3 == bs.lastIndex, 50, 0f, 0f)) { it.topEnd }, 0.01f)
    }

    @Test
    fun seamsOpenAsAButtonIsPressed() {
        val bs = List(3) { button() }
        val rest = px(connectedShape(false, 1 == bs.lastIndex, 50, 0f, 0f)) { it.topStart }
        val pressed = px(connectedShape(false, 1 == bs.lastIndex, 50, 1f, 1f)) { it.topStart }
        assert(pressed > rest)
    }
}
