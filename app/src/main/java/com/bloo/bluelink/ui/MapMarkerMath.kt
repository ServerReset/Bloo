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
