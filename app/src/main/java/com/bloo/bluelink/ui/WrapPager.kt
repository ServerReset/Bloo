package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.floor
import kotlin.math.abs

// Pages are keyed by raw virtual index (see [WrapPagerState]) because real-index keying crashed
// ("Key already used") twice.
private const val WRAP_MULTIPLIER = 80
/**
 * Recenter once the pager drifts within this many real-item-widths of either edge of the virtual
 * range -- see [WRAP_MULTIPLIER]'s own doc.
 */
private const val RECENTER_MARGIN_CYCLES = 10
/** Max per-page scale shrink at full off-screen offset (floor 0.94). */
private const val PAGER_SHRINK = 0.06f

/** Pure wrap arithmetic: the real item index a virtual page maps to. */
internal fun wrapRealIndex(page: Int, realCount: Int): Int =
    if (realCount <= 1) 0 else ((page % realCount) + realCount) % realCount

/**
 * Pure wrap arithmetic: the nearest virtual page whose real index is [target], without animating a
 * long fly-through across the virtual range. [currentPage] is the page the pager is on, [pageCount]
 * is the pager's total virtual page count, [realCount] is the number of real items.
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

    /**
     * Jump so the currently-shown page maps to [target], picking the nearest virtual page in the
     * current direction (no long fly-through).
     */
    suspend fun snapToReal(target: Int) {
        if (realCount <= 1) return
        val page = wrapPageToward(pager.currentPage, pager.pageCount, realCount, target)
        if (page != pager.currentPage) pager.scrollToPage(page)
    }

    /**
     * Call after every settle (never mid-drag -- see each call site's own `snapshotFlow {
     * pager.settledPage }` collector): if the pager has drifted within [RECENTER_MARGIN_CYCLES]
     * real-item-widths of either edge of the (small, fixed) virtual range, silently jump back to
     * the page nearest the middle that maps to the SAME real index -- so nothing visibly changes,
     * but the pager has room to keep going in either direction.
     */
    suspend fun recenterIfNearEdge() {
        if (realCount <= 1) return
        val margin = realCount * RECENTER_MARGIN_CYCLES
        val page = pager.currentPage
        val count = pager.pageCount
        // Comfortably inside both edges already -- nothing to do.
        if (page in margin..(count - 1 - margin)) return
        val center = count / 2
        val target = center - (center % realCount) + real(page)
        if (target != page) pager.scrollToPage(target)
    }
}

/**
 * Creates a [WrapPagerState] seeded at the middle of the virtual range plus [initialRealIndex], so
 * the pager opens on that real item and can wrap in both directions. Falls back to a plain
 * single-page state when [realCount] <= 1.
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
 * Per-page depth transform for the horizontal car pagers: a subtle scale shrink proportional to how
 * far [page] is from the settled one, read only in the draw phase (graphicsLayer) so a drag never
 * recomposes page content.
 */
internal fun Modifier.pagerDepth(pager: PagerState, page: Int): Modifier = graphicsLayer {
    // Offset formula matches the Compose Pager docs' sample: (currentPage - page) + offsetFraction.
    val off = abs((pager.currentPage - page).toFloat() + pager.currentPageOffsetFraction)
        .coerceIn(0f, 1f)
    scaleX = 1f - off * PAGER_SHRINK
    scaleY = 1f - off * PAGER_SHRINK
}
