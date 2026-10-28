package com.bloo.bluelink.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.bloo.uicommon.VirtualPager
import com.bloo.uicommon.WrapPositions
import com.bloo.uicommon.startingVirtualPage
import com.bloo.uicommon.virtualPageCount

/** The phone's pager as a [VirtualPager]: the wrap rules live in :uicommon, and this only says how to move the pager. */
private class PhonePager(private val pager: PagerState) : VirtualPager {
    override val currentPage: Int get() = pager.currentPage
    override val pageCount: Int get() = pager.pageCount
    override suspend fun scrollTo(page: Int) = pager.scrollToPage(page)
    override suspend fun animateTo(page: Int) = pager.animateScrollToPage(page)
}

/**
 * The phone's wrapping pager: the [pager] the pager composable is given, and the wrap rules over it. The garage reads both.
 */
@Stable
internal class WrapPagerState(val pager: PagerState, realCount: Int) {
    private val positions = WrapPositions(PhonePager(pager), realCount)

    fun real(page: Int): Int = positions.real(page)
    suspend fun snapToReal(target: Int) = positions.snapToReal(target)
    suspend fun animateToReal(target: Int) = positions.animateToReal(target)
    suspend fun recenterIfNearEdge() = positions.recenterIfNearEdge()
}

/**
 * Creates a [WrapPagerState] seeded at the middle of a phone pager's range plus [initialRealIndex], so the pager opens on that real item and
 * can wrap in both directions. Falls back to a plain single page when [realCount] <= 1.
 */
@Composable
internal fun rememberWrapPager(realCount: Int, initialRealIndex: Int = 0): WrapPagerState {
    val pager = rememberPagerState(initialPage = startingVirtualPage(realCount, initialRealIndex)) { virtualPageCount(realCount) }
    return remember(pager, realCount) { WrapPagerState(pager, realCount) }
}
