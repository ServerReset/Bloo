package com.bloo.bluelink.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
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
 * Creates a [WrapPositions] over a phone pager seeded at the middle of its range plus [initialRealIndex], so the pager opens on that real
 * item and can wrap in both directions. Falls back to a plain single page when [realCount] <= 1.
 */
@Composable
internal fun rememberWrapPager(realCount: Int, initialRealIndex: Int = 0): WrapPositions {
    val pager = rememberPagerState(initialPage = startingVirtualPage(realCount, initialRealIndex)) { virtualPageCount(realCount) }
    return remember(pager, realCount) { WrapPositions(PhonePager(pager), realCount) }
}
