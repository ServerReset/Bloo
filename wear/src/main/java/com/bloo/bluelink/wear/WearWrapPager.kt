package com.bloo.bluelink.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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

/**
 * Dots for the cars on screen: one per real item, the current one filled. The stock page indicator counts the pager's own pages, which wrap
 * over a long virtual range, so the cars are counted here instead.
 */
@Composable
internal fun CarPageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            Box(
                Modifier.padding(horizontal = 3.dp).size(if (i == current) 7.dp else 5.dp).clip(CircleShape)
                    .background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)),
            )
        }
    }
}
