package com.bloo.bluelink.ui

import kotlin.math.roundToInt

// --- The pure width math behind ExpressiveButtonGroup's measure pass ---

/**
 * Per-seam reserve: each member's half of a seam is sized off its own content, and a member's total
 * is the sum of its halves across the seams it borders.
 */
internal fun seamPressReserve(memberIdx: List<Int>, basis: IntArray): IntArray {
    val out = IntArray(basis.size)
    for (k in 0 until memberIdx.size - 1) {
        val a = memberIdx[k]; val b = memberIdx[k + 1]
        out[a] += ((ExpressivePressGrowth * basis[a]) / 2f).roundToInt()
        out[b] += ((ExpressivePressGrowth * basis[b]) / 2f).roundToInt()
    }
    return out
}

/**
 * Each member's exact width once pressed. A pressed member draws only on the seams it shares with
 * real neighbours; every seam delta is zero-sum, so a line's total holds and a squeezed member's
 * far edge stays put. Each side's gain is capped by the smaller of the two halves and by the
 * neighbour's slack above its content need, and a defensive floor never lets content shrink below
 * what it needs.
 */
internal fun redistributeForPress(
    memberIdx: List<Int>,
    base: DoubleArray,
    basis: IntArray,
    press: FloatArray,
): DoubleArray {
    val exact = DoubleArray(basis.size)
    for (i in memberIdx) exact[i] = base[i]
    for (k in 0 until memberIdx.size - 1) {
        val a = memberIdx[k]; val b = memberIdx[k + 1]
        val bHalf = (ExpressivePressGrowth * basis[b]) / 2.0
        val aHalf = (ExpressivePressGrowth * basis[a]) / 2.0
        val seamCapacity = minOf(aHalf, bHalf)
        val bSlack = (base[b] - basis[b]).coerceAtLeast(0.0)
        val aSlack = (base[a] - basis[a]).coerceAtLeast(0.0)
        val delta = press[a] * minOf(seamCapacity, bSlack) - press[b] * minOf(seamCapacity, aSlack)
        exact[a] += delta
        exact[b] -= delta
    }
    for (i in memberIdx) exact[i] = exact[i].coerceAtLeast(basis[i].toDouble())
    return exact
}

/**
 * Final guarantee: a line's placed width never exceeds [total]. Claw any excess back from the
 * members with slack first, then proportionally, so the last member can never be pushed past the
 * edge of the row and off-screen.
 */
internal fun clampLineToBudget(memberIdx: List<Int>, exact: DoubleArray, basis: IntArray, total: Int) {
    val exactSum = memberIdx.sumOf { exact[it] }
    if (exactSum > total && total > 0) {
        var excess = exactSum - total
        val slack = memberIdx.map { (exact[it] - basis[it]).coerceAtLeast(0.0) }
        val slackTotal = slack.sum()
        if (slackTotal > 0.0) {
            val take = minOf(excess, slackTotal)
            memberIdx.forEachIndexed { k, i -> exact[i] -= take * slack[k] / slackTotal }
            excess -= take
        }
        if (excess > 0.0) {
            val sum = memberIdx.sumOf { exact[it] }
            if (sum > 0.0) {
                val scale = (sum - excess) / sum
                for (i in memberIdx) exact[i] *= scale
            }
        }
    }
}

/**
 * Largest-remainder rounding so integer widths sum to [total] exactly (no pixel breathing). Bounded
 * by construction: truncation can only leave a remainder smaller than the member count, but this
 * runs inside a measure pass, so it is bounded explicitly rather than trusting the arithmetic.
 */
internal fun largestRemainderWidths(memberIdx: List<Int>, exact: DoubleArray, total: Int): IntArray {
    val target = IntArray(exact.size)
    for (i in memberIdx) target[i] = exact[i].toInt()
    var remainder = total - memberIdx.sumOf { target[it] }
    if (remainder > 0) {
        val order = memberIdx.sortedByDescending { exact[it] - exact[it].toInt() }
        var k = 0
        while (remainder > 0 && k < order.size) {
            target[order[k]]++; remainder--; k++
        }
    }
    return target
}
