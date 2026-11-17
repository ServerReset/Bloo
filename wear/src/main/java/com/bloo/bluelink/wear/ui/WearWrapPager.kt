package com.bloo.bluelink.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.foundation.pager.rememberPagerState
import com.bloo.uicommon.VirtualPager
import com.bloo.uicommon.WrapPositions
import com.bloo.uicommon.startingVirtualPage
import com.bloo.uicommon.virtualPageCount

/** The watch's pager as a [VirtualPager]: the wrap rules live in :uicommon, and this only says how to move the pager. */
private class WearPager(private val pager: PagerState) : VirtualPager {
    override val currentPage: Int get() = pager.currentPage
    override val pageCount: Int get() = pager.pageCount
    override suspend fun scrollTo(page: Int) = pager.scrollToPage(page)
    override suspend fun animateTo(page: Int) = pager.animateScrollToPage(page)
}

/** A wrapping watch pager: the [pager] the pager composable is given, and the [wrap] that maps its virtual pages to real items. */
internal class WearWrap(val pager: PagerState, val wrap: WrapPositions)

/**
 * A watch pager that wraps in both directions over [realCount] items, opening on [initialRealIndex]. With one item or none it is a plain
 * single page, so a lone car does not swipe to nothing.
 */
@Composable
internal fun rememberWearWrap(realCount: Int, initialRealIndex: Int = 0): WearWrap {
    val pager = rememberPagerState(initialPage = startingVirtualPage(realCount, initialRealIndex)) { virtualPageCount(realCount) }
    return remember(pager, realCount) { WearWrap(pager, WrapPositions(WearPager(pager), realCount)) }
}
