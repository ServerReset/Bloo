package com.bloo.uicommon

import kotlin.math.abs

// --- MorphSegmented geometry: how much the chosen segment stretches and the rest squash ---

/**
 * Each segment's share of the track at highlight position [pos] (fractional while travelling): 1,
 * plus up to ([ratio] - 1) where the highlight sits, fading linearly to the neighbour as it moves,
 * scaled by [shown] (0 = nothing selected, all equal).
 */
fun segmentWeights(n: Int, pos: Float, shown: Float, ratio: Float): FloatArray = FloatArray(n) { i ->
    1f + (ratio - 1f) * shown * (1f - abs(pos - i)).coerceAtLeast(0f)
}

/**
 * Pixel widths for the segments: [free] split by [weights], except no segment goes below its
 * [naturals] (the width its label needs), so a squashed neighbour never truncates its text.
 */
fun fitSegmentWidths(free: Float, weights: FloatArray, naturals: FloatArray): FloatArray {
    val n = weights.size
    if (naturals.sum() >= free) {
        val total = naturals.sum().coerceAtLeast(0.01f)
        return FloatArray(n) { free * naturals[it] / total }
    }
    val pinned = BooleanArray(n)
    val out = FloatArray(n)
    repeat(n) {
        val pinnedSum = (0 until n).filter { pinned[it] }.sumOf { naturals[it].toDouble() }.toFloat()
        val share = (free - pinnedSum).coerceAtLeast(0f)
        val total = (0 until n).filter { !pinned[it] }.sumOf { weights[it].toDouble() }.toFloat().coerceAtLeast(0.01f)
        var changed = false
        for (i in 0 until n) {
            out[i] = if (pinned[i]) naturals[i] else share * weights[i] / total
            if (!pinned[i] && out[i] < naturals[i]) { pinned[i] = true; changed = true }
        }
        if (!changed) return out
    }
    return FloatArray(n) { maxOf(out[it], naturals[it]) }
}

/** How much wider the chosen segment is than each of the others, by option count. */
internal fun stretchRatio(n: Int): Float = when {
    n <= 2 -> 2.2f
    n == 3 -> 1.9f
    n == 4 -> 1.6f
    else -> 1.4f
}
