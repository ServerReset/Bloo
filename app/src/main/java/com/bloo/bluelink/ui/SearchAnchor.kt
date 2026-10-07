package com.bloo.bluelink.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one source of truth for where the search element is, shared by the search UI and the toasts so
 * they agree without either observing a layout pass.
 *
 * The search element writes its RESTING rect (window coordinates) and its dock here as they settle;
 * the toast stack and the page-bottom spacers read them. A drag or a morph never writes here, so
 * nothing that reads the anchor is invalidated by the search's own motion.
 */
@Stable
internal class SearchAnchor {
    /** The search element's resting rect, or null when it is not on screen. */
    var rect: Rect? by mutableStateOf(null)
        internal set

    /** Where it is docked: LEFT/RIGHT = a toast sits beside it, CENTER = above it. */
    var dock: SearchDock by mutableStateOf(SearchDock.RIGHT)
        internal set

    internal fun publish(rect: Rect?, dock: SearchDock) {
        this.rect = rect
        this.dock = dock
    }
}

/** Provided once in `BlooApp`; the default lets previews and tests run without a host. */
 internal val LocalSearchAnchor = staticCompositionLocalOf { SearchAnchor() }

/**
 * The room a page leaves at the bottom so its content never hides behind the search element: from the
 * element's top to the window bottom, plus a breath of margin. Falls back to [fallback] while the
 * element is not on screen.
 */
@Composable
internal fun searchBarClearance(fallback: Dp): Dp {
    val rect = LocalSearchAnchor.current.rect ?: return fallback
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current
    return with(density) { (windowHeightPx - rect.top).coerceAtLeast(0f).toDp() } + 16.dp
}
