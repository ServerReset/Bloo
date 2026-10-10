package com.bloo.bluelink.ui

import androidx.compose.ui.geometry.Rect

/** The newest toast's inset and the stack's lift, from where the search element is. */
internal data class ToastClearance(val startInsetPx: Float, val endInsetPx: Float, val bottomLiftPx: Float)

/**
 * Where the toast stack goes around the search element. A search docked to a side lets the NEWEST
 * toast slot beside it (inset on that side) as long as at least [minToastWidthPx] -- the newest
 * message's own natural width -- is left for the toast; a centred search, or one docked so close to
 * the middle that the toast would be squeezed, lifts the whole stack above it instead.
 *
 * The lift is measured against the stack's OWN content bottom: the toast column sits at the search's
 * inset (`max(nav, ime)`) plus [baseEdgePx], so the newest toast's bottom lines up exactly with the
 * search bar's bottom when it slots beside it.
 */
internal fun toastClearance(
    searchRect: Rect?,
    windowWidthPx: Float,
    windowHeightPx: Float,
    imeBottomPx: Float,
    navBottomPx: Float,
    baseEdgePx: Float,
    gapPx: Float,
    minToastWidthPx: Float = 0f,
): ToastClearance {
    if (searchRect == null || windowWidthPx <= 0f) return ToastClearance(0f, 0f, 0f)
    // The toast column's own bottom inset, the same `nav union ime` the search element uses.
    val contentBottom = windowHeightPx - maxOf(imeBottomPx, navBottomPx) - baseEdgePx
    val lift = ToastClearance(
        startInsetPx = 0f,
        endInsetPx = 0f,
        // From the search's top up to the toast content's bottom, plus one gap of breathing room.
        bottomLiftPx = (contentBottom - searchRect.top + gapPx).coerceAtLeast(0f),
    )
    val contentWidth = windowWidthPx - 2 * baseEdgePx
    return when (SearchDock.fromFrac(((searchRect.left + searchRect.right) / 2f) / windowWidthPx)) {
        SearchDock.LEFT -> {
            val inset = (searchRect.right + gapPx - baseEdgePx).coerceAtLeast(0f)
            if (contentWidth - inset < minToastWidthPx) lift else ToastClearance(inset, 0f, 0f)
        }
        SearchDock.RIGHT -> {
            val inset = ((windowWidthPx - baseEdgePx) - (searchRect.left - gapPx)).coerceAtLeast(0f)
            if (contentWidth - inset < minToastWidthPx) lift else ToastClearance(0f, inset, 0f)
        }
        SearchDock.CENTER -> lift
    }
}
