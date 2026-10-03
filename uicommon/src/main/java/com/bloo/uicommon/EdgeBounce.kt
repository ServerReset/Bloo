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

/**
 * A small velocity-aware kick for an edge impact, so hitting the end of a
 * slider/segmented control registers physically rather than silently stopping:
 * scales a release velocity (px/s) into an extra overscroll nudge, clamped so a
 * hard flick does not throw the value off-screen.
 *
 * @param velocityPxPerSec the drag velocity at release (Compose convention).
 * @param limit the same elastic budget [rubberBand] takes.
 * @param strength 0..1, how much of the velocity becomes displacement.
 */
fun edgeKick(velocityPxPerSec: Float, limit: Float, strength: Float = 0.15f): Float {
    if (limit <= 0f) return 0f
    return (velocityPxPerSec * strength).coerceIn(-limit, limit)
}

/** True when [value] is outside the inclusive [range] built from [min]..[max]. */
fun isOverEdge(value: Float, min: Float, max: Float): Boolean = value < min || value > max

/** Distance past the nearer edge of `min..max`, or 0 when inside it. */
fun overshoot(value: Float, min: Float, max: Float): Float = when {
    value < min -> min - value
    value > max -> value - max
    else -> 0f
}

/** The sign of the nearest crossing: -1 past [min], +1 past [max], 0 inside. */
fun overshootSign(value: Float, min: Float, max: Float): Int = when {
    value < min -> -1
    value > max -> 1
    else -> 0
}
