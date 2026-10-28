package com.bloo.uicommon

/** The virtual page range is this many real-item cycles long, so a pager can wrap a long way in either direction before it recentres. */
private const val WRAP_MULTIPLIER = 1000

/**
 * Recentre once the pager drifts within this many real-item-widths of either edge of the virtual range: the recentre is silent, since the
 * page it lands on maps to the same real item.
 */
private const val RECENTER_MARGIN_CYCLES = 10

/** Pure wrap arithmetic: the real item index a virtual page maps to. */
fun wrapRealIndex(page: Int, realCount: Int): Int =
    if (realCount <= 1) 0 else ((page % realCount) + realCount) % realCount

/**
 * Pure wrap arithmetic: the nearest virtual page whose real index is [target], without animating a long fly-through across the virtual
 * range. [currentPage] is the page the pager is on, [pageCount] is the pager's total virtual page count, [realCount] is the number of real
 * items.
 */
fun wrapPageToward(currentPage: Int, pageCount: Int, realCount: Int, target: Int): Int {
    if (realCount <= 1) return currentPage
    val t = target.coerceIn(0, realCount - 1)
    val delta = t - wrapRealIndex(currentPage, realCount)
    return (currentPage + delta).coerceIn(0, pageCount - 1)
}

/** The number of virtual pages a wrapping pager needs for [realCount] items: a long range, or just one page when there is nothing to wrap. */
fun virtualPageCount(realCount: Int): Int =
    if (realCount > 1) realCount * WRAP_MULTIPLIER else realCount.coerceAtLeast(1)

/** The virtual page a wrapping pager opens on: the middle of its range, plus the real item it should show first. */
fun startingVirtualPage(realCount: Int, initialRealIndex: Int): Int {
    val loop = realCount > 1
    return (if (loop) virtualPageCount(realCount) / 2 else 0) +
        initialRealIndex.coerceIn(0, (realCount - 1).coerceAtLeast(0))
}

/**
 * The few pager operations a wrapping pager needs. The phone and the watch each implement it over their own pager, so the wrap rules are
 * written once.
 */
interface VirtualPager {
    val currentPage: Int
    val pageCount: Int

    /** Jump to [page] without animating. */
    suspend fun scrollTo(page: Int)

    /** Swipe to [page], animated. */
    suspend fun animateTo(page: Int)
}

/**
 * Wrap rules over a [VirtualPager] of [realCount] real items. Pages are keyed by raw virtual index by their pagers, never by [real]: real-index
 * keying made two virtual copies of one item share a composition slot and crashed.
 */
class WrapPositions(private val pager: VirtualPager, val realCount: Int) {
    /** The real item a virtual page shows. */
    fun real(page: Int): Int = wrapRealIndex(page, realCount)

    /** Jump so the shown page maps to [target], picking the nearest virtual page in the current direction (no long fly-through). */
    suspend fun snapToReal(target: Int) {
        if (realCount <= 1) return
        val page = wrapPageToward(pager.currentPage, pager.pageCount, realCount, target)
        if (page != pager.currentPage) pager.scrollTo(page)
    }

    /**
     * Swipe (animated) so the shown page maps to [target], taking the nearest virtual page in either direction. Works in page indices, so a
     * right-to-left layout gets the same swipe mirrored.
     */
    suspend fun animateToReal(target: Int) {
        if (realCount <= 1) return
        val page = wrapPageToward(pager.currentPage, pager.pageCount, realCount, target)
        if (page != pager.currentPage) pager.animateTo(page)
    }

    /**
     * Call after every settle, never mid-drag: if the pager has drifted near either edge of its virtual range, silently jump back to the page
     * nearest the middle that maps to the same real item, so it has room to keep going in either direction.
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
        if (target != page) pager.scrollTo(target)
    }
}
