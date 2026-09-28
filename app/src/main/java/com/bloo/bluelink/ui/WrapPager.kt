@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.abs

// The virtual page range is realCount * WRAP_MULTIPLIER, seeded at its midpoint, so the pager
// opens with room to wrap both ways. [recenterIfNearEdge] jumps back toward the middle when it
// drifts near either edge, keeping the range effectively infinite. 80 balances two past bugs:
// a huge range (1000) let distinct pages accumulate until an OOM; a tiny one (10) recentered
// every few swipes and visibly broke the "does it loop" feel. Pages are keyed by raw virtual
// index (see [WrapPagerState]) because real-index keying crashed ("Key already used") twice.
private const val WRAP_MULTIPLIER = 80
/** Recenter once the pager drifts within this many real-item-widths of
 *  either edge of the virtual range -- see [WRAP_MULTIPLIER]'s own doc. This
 *  only needs to be big enough that a single fling can't overshoot past it in
 *  one frame (it does not need to scale with [WRAP_MULTIPLIER] -- that
 *  constant is what controls how RARELY this triggers, this one just needs
 *  enough runway once it does). */
private const val RECENTER_MARGIN_CYCLES = 10
/** Max per-page scale shrink at full off-screen offset (floor 0.94). */
private const val PAGER_SHRINK = 0.06f

/**
 * Pure wrap arithmetic: the real item index a virtual page maps to.
 *
 * Extracted as an `internal` top-level function (not a member) so the modulo
 * trick is pinnable from a plain JVM test -- WrapPagerState's own member
 * forwards here, so the pin lives where the bugs would be. The double-modulo
 * form is deliberate: Kotlin's `%` keeps the NEGATIVE operand's sign, so a
 * page ever so slightly below the virtual midpoint (page 0 vs realCount 1m?)
 * -- i.e. a wrap pushed a page below zero by a snapshot/offset quirk -- would
 * map to a NEGATIVE real index and silently read a non-existent item. Adding
 * realCount once and modding again pulls every result into [0, realCount).
 */
internal fun wrapRealIndex(page: Int, realCount: Int): Int =
    if (realCount <= 1) 0 else ((page % realCount) + realCount) % realCount

/**
 * Pure wrap arithmetic: the nearest virtual page whose real index is [target],
 * without animating a long fly-through across the virtual range.
 *
 * [currentPage] is the page the pager is on, [pageCount] is the pager's total
 * virtual page count, [realCount] is the number of real items. When
 * [realCount] <= 1 there is only ever one item and [currentPage] is returned
 * unchanged. `delta` is computed FROM the real index of the CURRENT page (not
 * from [currentPage] itself), so jumping from any virtual copy of an item to
 * another item always takes the shortest real step; the result is clamped to
 * the pager's own [pageCount] - 1 because the virtual range is finite.
 */
internal fun wrapPageToward(currentPage: Int, pageCount: Int, realCount: Int, target: Int): Int {
    if (realCount <= 1) return currentPage
    val t = target.coerceIn(0, realCount - 1)
    val delta = t - wrapRealIndex(currentPage, realCount)
    return (currentPage + delta).coerceIn(0, pageCount - 1)
}

@Stable
internal class WrapPagerState(val pager: PagerState, val realCount: Int) {
    fun real(page: Int): Int = wrapRealIndex(page, realCount)

    // Pager pages are keyed by raw virtual index (`key = { page }`), never by real(page):
    // real-index keying made two virtual copies of one item share a composition slot and crashed.

    /** Jump so the currently-shown page maps to [target], picking the nearest
     *  virtual page in the current direction (no long fly-through). */
    suspend fun snapToReal(target: Int) {
        if (realCount <= 1) return
        val page = wrapPageToward(pager.currentPage, pager.pageCount, realCount, target)
        if (page != pager.currentPage) pager.scrollToPage(page)
    }

    /**
     * Call after every settle (never mid-drag -- see each call site's own
     * `snapshotFlow { pager.settledPage }` collector): if the pager has
     * drifted within [RECENTER_MARGIN_CYCLES] real-item-widths of either edge
     * of the (small, fixed) virtual range, silently jump back to the page
     * nearest the middle that maps to the SAME real index -- so nothing
     * visibly changes, but the pager has room to keep going in either
     * direction. This -- not a huge fake page count -- is what makes the
     * wrap feel genuinely infinite while keeping the number of distinct
     * virtual pages this pager can ever compose small and constant, instead
     * of growing with how long or how far someone swipes. See
     * [WRAP_MULTIPLIER]'s own doc for the full reasoning.
     */
    suspend fun recenterIfNearEdge() {
        if (realCount <= 1) return
        val margin = realCount * RECENTER_MARGIN_CYCLES
        val page = pager.currentPage
        val count = pager.pageCount
        // Comfortably inside both edges already -- nothing to do. Note this
        // margin is measured off the pager's OWN current pageCount, not a
        // value cached at creation, so it stays correct even if realCount
        // (and therefore pageCount) changes later -- see rememberWrapPager's
        // `remember(pager, realCount)` for why a realCount change re-seeds
        // this whole wrapper anyway, landing back at a fresh center.
        if (page in margin..(count - 1 - margin)) return
        val center = count / 2
        val target = center - (center % realCount) + real(page)
        if (target != page) pager.scrollToPage(target)
    }
}

/**
 * Creates a [WrapPagerState] seeded at the middle of the virtual range plus
 * [initialRealIndex], so the pager opens on that real item and can wrap in
 * both directions. Falls back to a plain single-page state when [realCount]
 * <= 1. The underlying [PagerState] survives recomposition; the wrapper is
 * re-created only when [realCount] changes (it holds no scroll state itself).
 */
@Composable
internal fun rememberWrapPager(realCount: Int, initialRealIndex: Int = 0): WrapPagerState {
    val loop = realCount > 1
    val virtualCount = if (loop) realCount * WRAP_MULTIPLIER else realCount.coerceAtLeast(1)
    val start = (if (loop) virtualCount / 2 else 0) + initialRealIndex.coerceIn(0, (realCount - 1).coerceAtLeast(0))
    val pager = rememberPagerState(initialPage = start) { virtualCount }
    return remember(pager, realCount) { WrapPagerState(pager, realCount) }
}

/**
 * Per-page depth transform for the horizontal car pagers: a subtle scale shrink proportional to
 * how far [page] is from the settled one, read only in the draw phase (graphicsLayer) so a drag
 * never recomposes page content. Scale only -- no alpha (alpha < 1 over overlapping pages makes
 * Compose allocate a full-screen offscreen buffer per frame) and no translation (which would pull
 * a neighbour's card into the screen edge at rest).
 */
internal fun Modifier.pagerDepth(pager: PagerState, page: Int): Modifier = graphicsLayer {
    // Offset formula matches the Compose Pager docs' sample: (currentPage - page) + offsetFraction.
    val off = abs((pager.currentPage - page).toFloat() + pager.currentPageOffsetFraction)
        .coerceIn(0f, 1f)
    scaleX = 1f - off * PAGER_SHRINK
    scaleY = 1f - off * PAGER_SHRINK
}

/** Screen height (dp) below which the phone gets the compact cover-screen
 *  layout -- a folding phone's small outer display (Galaxy Z Flip's ~260-280dp
 *  square cover, for instance), not a full unfolded/candybar phone screen.
 *  GarageScreen and LockOverlay used to each pick their own cutoff (570 vs
 *  440), so a screen sized between them got the compact UI on one but the
 *  full-size one on the other for the exact same physical device -- one
 *  shared threshold instead. Width is checked separately (see isCompactCoverScreen)
 *  so a wide-but-short screen (a tablet in landscape) doesn't false-positive. */
const val COVER_SCREEN_HEIGHT_DP = 570
const val COVER_SCREEN_WIDTH_DP = 600

/** True on a folding phone's compact cover screen; false on a full phone,
 *  foldable-unfolded, or tablet screen. See [COVER_SCREEN_HEIGHT_DP]. */
@Composable
internal fun isCompactCoverScreen(): Boolean {
    val windowInfo = LocalWindowInfo.current
    return with(LocalDensity.current) {
        windowInfo.containerSize.width.toDp() < COVER_SCREEN_WIDTH_DP.dp &&
            windowInfo.containerSize.height.toDp() < COVER_SCREEN_HEIGHT_DP.dp
    }
}
