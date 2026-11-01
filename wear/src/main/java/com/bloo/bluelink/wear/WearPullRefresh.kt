package com.bloo.bluelink.wear

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Pull down on the face to ask the car for a fresh reading. While the face is the page on screen and at rest, a downward drag is taken
 * before the pager sees it: the face gives under the finger, so the pager does not wrap to another page. Letting go past [thresholdPx]
 * refreshes; letting go before it springs back. Pushing back up takes the pull back first, before the pager can scroll.
 */
@Stable
internal class WearPullRefresh(
    private val thresholdPx: Float,
    /** True when the face is the page on screen and is at rest, so a pull is the face's and not the pager's. */
    private val canPull: () -> Boolean,
    private val onRefresh: () -> Unit,
) {
    /** How far the face has been pulled down, in pixels, with resistance: the face's own offset while it is pulled. */
    var offsetPx by mutableFloatStateOf(0f)
        private set

    private var pulled = 0f

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || !canPull()) return Offset.Zero
            // Nothing pulled, and not pulling down: the pager's own scroll.
            if (pulled <= 0f && available.y <= 0f) return Offset.Zero
            val next = (pulled + available.y).coerceAtLeast(0f)
            val used = next - pulled
            pulled = next
            offsetPx = resist(pulled)
            return Offset(0f, used)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            val wasPulled = pulled > 0f
            if (wasPulled && pulled >= thresholdPx) onRefresh()
            pulled = 0f
            offsetPx = 0f
            // A fling that ended a pull belongs to the pull, not the pager.
            return if (wasPulled) available else Velocity.Zero
        }
    }

    /** Pulls get harder the further they go, so the face stretches under the finger rather than following it one to one. */
    private fun resist(p: Float): Float = p * 0.5f
}
