package com.bloo.bluelink.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EdgePinTest {
    // A window 200px wide each side, 150px up, 250px down, with a 60px morph band.
    private fun pin(x: Float, y: Float) = edgePin(x, y, halfW = 200f, halfTop = 150f, halfBottom = 250f, morphPx = 60f)

    @Test
    fun insideTheWindowTheIconStaysPutAtFullSize() {
        val p = pin(120f, -90f)
        assertEquals(120f, p.x, 0.001f)
        assertEquals(-90f, p.y, 0.001f)
        assertEquals(1f, p.scale, 0.001f)
    }

    @Test
    fun justPastTheEdgeTheIconIsStillItsIcon() {
        // Exactly on the right edge: no morph yet.
        val p = pin(200f, 0f)
        assertEquals(200f, p.x, 0.001f)
        assertEquals(1f, p.scale, 0.001f)
    }

    @Test
    fun farOffTheCarIsPinnedOnTheEdgeOnItsOwnLine() {
        val p = pin(900f, 0f)
        assertEquals(200f, p.x, 0.001f)
        assertEquals(0f, p.y, 0.001f)
        assertEquals(0.75f, p.scale, 0.001f)
    }

    @Test
    fun theBottomEdgeUsesTheBottomInset() {
        // Below the centre: the bottom half-height (250), not the top (150).
        val p = pin(0f, 900f)
        assertEquals(250f, p.y, 0.001f)
        assertEquals(0f, p.x, 0.001f)
    }

    @Test
    fun theMorphIsContinuousAcrossTheEdge() {
        // Walk a car out along x: the position and size never jump.
        var last = pin(150f, 0f)
        var x = 150f
        while (x < 400f) {
            x += 1f
            val now = pin(x, 0f)
            assertTrue(abs(now.x - last.x) <= 1.01f, "position jumped at x=$x")
            assertTrue(abs(now.scale - last.scale) <= 0.02f, "scale jumped at x=$x")
            last = now
        }
    }

    @Test
    fun aMapWithNoSizeLeavesTheCarWhereItIs() {
        val p = edgePin(500f, 0f, halfW = 0f, halfTop = 0f, halfBottom = 0f, morphPx = 60f)
        assertEquals(500f, p.x, 0.001f)
        assertEquals(1f, p.scale, 0.001f)
    }
}
