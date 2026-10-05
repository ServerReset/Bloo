package com.bloo.uicommon

/**
 * The elastic travel budget at an edge, as a fraction of the control's own range -- one value both
 * the slider (range = its value span) and the segmented control (range = its option count) size
 * their bounce from, so the stretch reads proportionally on both.
 */
const val EdgeOverscrollFraction = 0.07f

/**
 * The app's one edge-overscroll response: how far past a hard limit a dragged value is allowed to
 * travel, given how far past it the finger already is.
 */
fun rubberBand(extra: Float, limit: Float): Float {
    if (extra <= 0f || limit <= 0f) return 0f
    return limit * (1f - 1f / (1f + extra / limit))
}
