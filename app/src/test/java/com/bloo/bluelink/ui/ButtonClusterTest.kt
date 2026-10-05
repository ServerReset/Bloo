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
        val s = clusterShape(listOf(button()), 0, 0f, 50)
        assertEquals(pill, px(s) { it.topStart }, 0.01f)
        assertEquals(pill, px(s) { it.topEnd }, 0.01f)
    }

    @Test
    fun twoButtonsAreRoundOutsideAndSoftInside() {
        val bs = listOf(button(), button())
        val first = clusterShape(bs, 0, 0f, 50)
        val last = clusterShape(bs, 1, 0f, 50)
        assertEquals(pill, px(first) { it.topStart }, 0.01f)
        assertNotEquals(pill, px(first) { it.topEnd })
        assertNotEquals(pill, px(last) { it.topStart })
        assertEquals(pill, px(last) { it.topEnd }, 0.01f)
    }

    @Test
    fun threeOrMoreButtonsHaveSeamsOnBothSidesOfTheMiddle() {
        val bs = List(4) { button() }
        val middle = clusterShape(bs, 1, 0f, 50)
        val seam = px(middle) { it.topStart }
        assertNotEquals(pill, seam)
        assertEquals(seam, px(middle) { it.topEnd }, 0.01f)
        assertEquals(pill, px(clusterShape(bs, 0, 0f, 50)) { it.topStart }, 0.01f)
        assertEquals(pill, px(clusterShape(bs, 3, 0f, 50)) { it.topEnd }, 0.01f)
    }

    @Test
    fun seamsOpenAsAButtonIsPressed() {
        val bs = List(3) { button() }
        val rest = px(clusterShape(bs, 1, 0f, 50)) { it.topStart }
        val pressed = px(clusterShape(bs, 1, 1f, 50)) { it.topStart }
        assert(pressed > rest)
    }
}
