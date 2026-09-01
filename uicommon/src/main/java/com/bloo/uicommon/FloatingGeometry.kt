package com.bloo.uicommon

import androidx.compose.ui.geometry.Rect

/**
 * The pure rules behind the app's floating chrome -- the elements drawn OVER a page rather than
 * in its scroll flow (the car name once it docks into its pill, the page dots, corner buttons).
 *
 * Two questions, both of which used to be arithmetic inlined into whichever component happened to
 * ask first: "has this thing scrolled far enough to become floating?" ([shouldDock], lifted out of
 * the flying title) and "are two floating things in each other's way?" ([floatersOverlap], lifted
 * out of the page dots). Neither is about the component it came from, both are consulted every
 * frame, and both have boundary behaviour that is invisible until it is wrong -- so they live
 * here, in the module whose JVM tests can reach them, and the app's floating registry
 * (FloatingSystem.kt) routes through them rather than carrying a third copy.
 *
 * Pure and non-composable on purpose: cheap enough to call from a draw block or a derivedStateOf
 * without touching composition.
 */

/**
 * Whether an element anchored in the page has scrolled far enough to become floating chrome --
 * the "is it docked yet" rule, with hysteresis.
 *
 * [topPx] is the anchor's own top edge, [dockLinePx] the line it docks at (in practice the
 * status-bar inset). [hysteresisPx] widens the threshold ONLY while already docked, so an anchor
 * resting almost exactly on the line cannot flap in and out between frames as a scroll settles.
 * Pass 0 for a first/settled report, where there is no previous state to be sticky about and the
 * bare line is the honest answer.
 *
 * Extracted from the flying-title implementation so the decision is one tested rule rather than
 * two arithmetic expressions inside a 1,200-line file: any element that wants to move from fixed
 * on the page to floating asks the same question, and this is it.
 */
fun shouldDock(topPx: Float, dockLinePx: Float, currentlyDocked: Boolean, hysteresisPx: Float): Boolean =
    if (currentlyDocked) topPx < dockLinePx + hysteresisPx else topPx < dockLinePx

/** Whether two pieces of floating chrome overlap, with [marginPx] of slop around [b] -- so a
 *  name ellipsizing right up against the page dots' edge still counts as being in the way,
 *  without literal pixel overlap. Pure and non-composable, so a caller can run it every frame
 *  (from a draw block, or a derivedStateOf) without touching composition. A null rect means
 *  "not on screen / nothing measured yet", which never collides.
 *
 *  Lives here rather than beside its one-time only caller because the app's floating registry
 *  (FloatingSystem.kt) now runs every floater-vs-floater test through it, and this module is
 *  where the JVM tests that pin its boundary behaviour can reach it. */
fun floatersOverlap(a: Rect?, b: Rect?, marginPx: Float): Boolean {
    if (a == null || b == null) return false
    // Vertical gate first -- floating elements usually sit on different rows (an inline name
    // sits well below the dots' fixed top row; only a DOCKED pill, or one mid-flight toward
    // it, climbs high enough to matter), so most calls bail out here.
    if (b.bottom < a.top || b.top > a.bottom) return false
    return b.right + marginPx >= a.left && b.left - marginPx <= a.right
}
