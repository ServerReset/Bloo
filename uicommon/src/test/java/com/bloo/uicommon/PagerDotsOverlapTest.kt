package com.bloo.uicommon

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JVM pins for [floatersOverlap] -- the pure rect-overlap check that hides
 * the page-dot pill whenever the car's name collides with it.
 *
 * Wrong answers here are directly visible to users: a FALSE negative lets the
 * pill sit on top of a car name that is flying/docked (two floating pieces of
 * chrome competing for the same spot on every page change); a FALSE positive
 * hides the dots entirely when a name is merely NEAR them. Both directions
 * were historically tuned by eye against screenshots, so the boundary
 * behaviour -- the vertical gate, the padding, the decimal-precision frame --
 * gets pinned instead of retuned next time the chrome moves a few dp.
 */
class PagerDotsOverlapTest {

    private val dots = Rect(100f, 10f, 180f, 30f)   // the pill itself
    private val name = Rect(110f, 100f, 170f, 120f) // the hero-card inline name, far below

    @Test
    fun nothingOverlapsWhenNameIsBelowDots() {
        // The common case: the name sits well below the dots' fixed top row.
        assertFalse(floatersOverlap(dots, name, marginPx = 0f))
        // And even the generous margin can't invent an overlap there.
        assertFalse(floatersOverlap(dots, name, marginPx = 40f))
    }

    @Test
    fun nullEitherSideMeansNoConflict() {
        assertFalse(floatersOverlap(null, name, 0f))
        assertFalse(floatersOverlap(dots, null, 0f))
        assertFalse(floatersOverlap(null, null, 0f))
    }

    @Test
    fun verticalGateRejectsNamesFullyAboveOrBelow() {
        // Entirely below: bottom < dots.top -> reject, even with a fat margin.
        assertFalse(floatersOverlap(dots, Rect(120f, 50f, 160f, 70f), marginPx = 40f))
        // Entirely above: top > dots.bottom -> reject.
        assertFalse(floatersOverlap(dots, Rect(120f, -50f, 160f, -20f), marginPx = 40f))
        // The vertical gate swallows MARGIN too: a name below is never dragged
        // into conflict by the horizontal expansion.
        assertFalse(floatersOverlap(dots, Rect(120f, 31f, 160f, 60f), marginPx = 8f))
    }

    @Test
    fun boundaryTouchCountsAsOverlap() {
        // The implementation treats a name whose top exactly equals the pill's
        // bottom as overlapping (glyph bounding boxes include their own
        // leading, so "exactly touching" usually IS a visible collision).
        // Equal-boundary touch vertically IS reported, zero-padding included.
        val touching = Rect(120f, 30f, 160f, 50f)
        assertTrue(floatersOverlap(dots, touching, marginPx = 0f))
        // And one dp into the band also counts.
        val entered = Rect(120f, 29.9f, 160f, 50f)
        assertTrue(floatersOverlap(dots, entered, marginPx = 0f))
    }

    @Test
    fun horizontalOverlapRespected() {
        // Vertically inside the dots band; horizontally overlapping the pill.
        val mid = Rect(90f, 20f, 130f, 24f)
        assertTrue(floatersOverlap(dots, mid, marginPx = 0f))
        // Horizontally fully clear -> no.
        val beside = Rect(5f, 20f, 95f, 24f) // ends at 95 < dots.left 100
        assertFalse(floatersOverlap(dots, beside, marginPx = 0f))
    }

    @Test
    fun marginExpandsHorizontallyOnBothSides() {
        // The name ellipsizes right up against the pill's edge: 4dp outside it,
        // which is still "overlapping" given a margin because a real device
        // could render the ellipsis into that exact gutter.
        val close = Rect(182f, 20f, 200f, 24f) // 2dp past dots.right 180
        assertFalse(floatersOverlap(dots, close, marginPx = 0f))
        assertTrue(floatersOverlap(dots, close, marginPx = 8f))
        val closeLeft = Rect(60f, 20f, 96f, 24f) // 4dp before dots.left 100
        assertFalse(floatersOverlap(dots, closeLeft, marginPx = 2f))
        assertTrue(floatersOverlap(dots, closeLeft, marginPx = 8f))
    }

    @Test
    fun nameEnclosingDotsIsOverlapping() {
        // A docked pill with a much taller name (or a letter landing in the
        // band) still counts: the dot pill is INSIDE the name's vertical span.
        val wrapping = Rect(0f, 15f, 300f, 35f)
        assertTrue(floatersOverlap(dots, wrapping, marginPx = 0f))
    }
}
