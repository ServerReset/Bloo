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
