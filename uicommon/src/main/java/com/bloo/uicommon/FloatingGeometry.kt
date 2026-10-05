package com.bloo.uicommon

import androidx.compose.ui.geometry.Rect

/**
 * The pure rules behind the app's floating chrome -- the elements drawn OVER a page rather than in
 * its scroll flow (corner buttons, the search bubble, the refresh indicator).
 */

/**
 * Whether an element anchored in the page has scrolled far enough to become floating chrome -- the
 * "is it docked yet" rule, with hysteresis. [topPx] is the anchor's own top edge, [dockLinePx] the
 * line it docks at (in practice the status-bar inset). [hysteresisPx] widens the threshold ONLY
 * while already docked, so an anchor resting almost exactly on the line cannot flap in and out
 * between frames as a scroll settles.
 */
fun shouldDock(topPx: Float, dockLinePx: Float, currentlyDocked: Boolean, hysteresisPx: Float): Boolean =
    if (currentlyDocked) topPx < dockLinePx + hysteresisPx else topPx < dockLinePx

/**
 * Whether two pieces of floating chrome overlap, with [marginPx] of slop around [b] -- so a name
 * ellipsizing right up against a neighbouring chip's edge still counts as being in the way, without
 * literal pixel overlap.
 */
fun floatersOverlap(a: Rect?, b: Rect?, marginPx: Float): Boolean {
    if (a == null || b == null) return false
    // Vertical gate first -- floating elements usually sit on different rows, so most calls bail
    // out here.
    if (b.bottom < a.top || b.top > a.bottom) return false
    return b.right + marginPx >= a.left && b.left - marginPx <= a.right
}
