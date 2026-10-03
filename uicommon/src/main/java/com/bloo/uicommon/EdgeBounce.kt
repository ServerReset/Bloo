package com.bloo.uicommon

/**
 * The elastic travel budget at an edge, as a fraction of the control's own
 * range -- one value both the slider (range = its value span) and the
 * segmented control (range = its option count) size their bounce from, so the
 * stretch reads proportionally on both.
 */
const val EdgeOverscrollFraction = 0.07f

/**
 * The app's one edge-overscroll response: how far past a hard limit a dragged
 * value is allowed to travel, given how far past it the finger already is.
 *
 * This is the standard asymptotic rubber-band (the same shape iOS scroll views
 * use): the first pixel past the edge moves almost 1:1, and each further pixel
 * moves less and less, approaching but never reaching `limit`. It is physics,
 * not a clamp -- the resistance is a continuous function of the pull, so the
 * control feels like it is being stretched rather than stopped, and letting go
 * hands the value back to a spring that carries it home (with a little
 * overshoot, since the spring is under-damped).
 *
 * Shared so the slider's ends and the segmented control's first/last option
 * bounce IDENTICALLY -- they are the two "you can only go this far" controls,
 * and they used to differ: the slider rubber-banded, the segmented control
 * hard-clamped with no give at all.
 *
 * @param extra how far past the limit the finger has travelled, in the same
 *              units as [limit] (and always >= 0).
 * @param limit the elastic travel budget at the edge -- how far one full
 *              "pull" is expected to stretch. Sized by the caller relative to
 *              its own range so the stretch looks proportional.
 * @return the overscroll distance to render (0..limit, approaching `limit`).
 */
fun rubberBand(extra: Float, limit: Float): Float {
    if (extra <= 0f || limit <= 0f) return 0f
    return limit * (1f - 1f / (1f + extra / limit))
}
