package com.bloo.bluelink.ui

import kotlin.math.abs
import kotlin.math.min

/**
 * Geometry for a map marker placed relative to the box centre. The map is car-centred, so a marker's
 * position is its offset from the centre; when that offset would fall outside the box (the phone is
 * farther than the map shows), it is clamped onto the box edge in its own direction so it can still
 * be pointed at. Pure, so the clamping is unit-tested rather than eyeballed.
 */

/** True when a marker at ([dx], [dy]) from the box centre falls outside the box inset by [marginPx]. */
internal fun markerOffBox(dx: Float, dy: Float, halfW: Float, halfH: Float, marginPx: Float): Boolean =
    abs(dx) > halfW - marginPx || abs(dy) > halfH - marginPx

/**
 * Clamp ([dx], [dy]) onto the edge of the box inset by [marginPx], keeping its direction: the axis
 * that would leave the inset box first sets the scale, so the marker lands on that edge.
 */
internal fun clampMarkerToEdge(dx: Float, dy: Float, halfW: Float, halfH: Float, marginPx: Float): Pair<Float, Float> {
    if (dx == 0f && dy == 0f) return 0f to 0f
    val kx = if (dx != 0f) (halfW - marginPx) / abs(dx) else Float.MAX_VALUE
    val ky = if (dy != 0f) (halfH - marginPx) / abs(dy) else Float.MAX_VALUE
    val k = min(kx, ky).coerceIn(0f, 1f)
    return dx * k to dy * k
}

/**
 * True when a marker at ([dx], [dy]) from the box centre falls outside the strip that is clear of the chrome: inside the box, inset
 * by [marginPx] on every side, and kept below the top bar ([topPx]) and above the bottom buttons ([bottomPx]).
 */
internal fun markerOffVisible(dx: Float, dy: Float, halfW: Float, halfH: Float, marginPx: Float, topPx: Float, bottomPx: Float): Boolean =
    dx < -halfW + marginPx || dx > halfW - marginPx ||
        dy < -halfH + topPx + marginPx || dy > halfH - bottomPx - marginPx

/**
 * Spreads the off-screen pointers that share an edge of the strip along that edge, [sepPx] apart, so no car's photo covers another's.
 * Each pointer stays on its own edge and inside the strip; only its place along the edge moves. [off] says which pointers may move.
 */
internal fun spreadPointersAlongEdges(
    points: List<Pair<Float, Float>>,
    off: List<Boolean>,
    halfW: Float,
    halfH: Float,
    marginPx: Float,
    topPx: Float,
    bottomPx: Float,
    sepPx: Float,
): List<Pair<Float, Float>> {
    val minX = -halfW + marginPx
    val maxX = halfW - marginPx
    val minY = -halfH + topPx + marginPx
    val maxY = halfH - bottomPx - marginPx
    val eps = 0.5f
    val out = points.toMutableList()
    // Grouped by the edge each moving pointer was clamped to. A pointer in a corner joins the left or right edge first.
    val edges = points.indices.filter { off[it] }.groupBy { i ->
        val (x, y) = points[i]
        when {
            abs(x - minX) < eps -> 'L'
            abs(x - maxX) < eps -> 'R'
            abs(y - minY) < eps -> 'T'
            abs(y - maxY) < eps -> 'B'
            else -> null
        }
    }
    for ((edge, group) in edges) {
        if (edge == null) continue
        val vertical = edge == 'L' || edge == 'R'
        val lo = if (vertical) minY else minX
        val hi = if (vertical) maxY else maxX
        val along = { i: Int -> if (vertical) points[i].second else points[i].first }
        val sorted = group.sortedBy(along)
        val pos = sorted.map(along).toMutableList()
        for (j in 1 until pos.size) {
            if (pos[j] - pos[j - 1] < sepPx) pos[j] = pos[j - 1] + sepPx
        }
        // Pushed past the far end of the strip: hold the last one there and stack the rest back from it.
        if (pos.isNotEmpty() && pos.last() > hi) {
            pos[pos.lastIndex] = hi
            for (j in pos.lastIndex - 1 downTo 0) {
                if (pos[j + 1] - pos[j] < sepPx) pos[j] = pos[j + 1] - sepPx
            }
        }
        sorted.forEachIndexed { j, i ->
            val p = pos[j].coerceIn(lo, hi)
            out[i] = if (vertical) points[i].first to p else p to points[i].second
        }
    }
    return out
}

/**
 * Clamp ([dx], [dy]) onto the edge of the strip clear of the chrome, keeping its direction from the box centre: the axis that would
 * leave the strip first sets the scale, so the marker lands on that edge and never under a bar or a button.
 */
internal fun clampMarkerToVisible(
    dx: Float,
    dy: Float,
    halfW: Float,
    halfH: Float,
    marginPx: Float,
    topPx: Float,
    bottomPx: Float,
): Pair<Float, Float> {
    if (dx == 0f && dy == 0f) return 0f to 0f
    val minX = -halfW + marginPx
    val maxX = halfW - marginPx
    val minY = -halfH + topPx + marginPx
    val maxY = halfH - bottomPx - marginPx
    val kx = when {
        dx > 0f -> maxX / dx
        dx < 0f -> minX / dx
        else -> Float.MAX_VALUE
    }
    val ky = when {
        dy > 0f -> maxY / dy
        dy < 0f -> minY / dy
        else -> Float.MAX_VALUE
    }
    val k = min(kx, ky).coerceIn(0f, 1f)
    return dx * k to dy * k
}
