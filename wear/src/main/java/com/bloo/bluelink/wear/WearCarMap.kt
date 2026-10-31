package com.bloo.bluelink.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.uicommon.RevealText
import kotlin.math.abs
import kotlin.math.cos

/** One car's place on the watch's map: [x] and [y] run from -1 to 1 from the middle, north up. */
internal data class CarSpot(val vin: String, val name: String, val x: Float, val y: Float, val selected: Boolean)

/** A label's box in the map's own dp, before it is placed: [index] is its car's place in the list it came from. */
internal data class LabelBox(val index: Int, val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * Moves labels down until none overlaps an earlier one, so names of cars that sit close together stay readable. A label that cannot find a
 * clear place within [maxSteps] moves stays where its last step put it. Pure, so it can be tested.
 */
internal fun spreadLabels(boxes: List<LabelBox>, maxSteps: Int = 8): List<LabelBox> {
    val placed = mutableListOf<LabelBox>()
    for (box in boxes.sortedBy { it.y }) {
        var current = box
        var steps = 0
        while (steps < maxSteps && placed.any { overlaps(it, current) }) {
            current = current.copy(y = current.y + box.h)
            steps++
        }
        placed += current
    }
    return placed.sortedBy { it.index }
}

private fun overlaps(a: LabelBox, b: LabelBox): Boolean =
    a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h

/** Rough label metrics on the watch, in dp, for placing names before they are drawn. */
private const val LabelCharDp = 5.6f
private const val LabelLineDp = 14f

/** The least the map spans, in degrees (about 400 m), so one car does not blow up to the edge of the display. */
private const val MinSpanDeg = 0.004

/**
 * Where each car with a known position sits, flat, with the [selectedVin] car in the middle and the rest placed by their distance from it.
 * The cars that are farthest apart reach 80% of the way to the edge, so the whole group fits. Pure, so it can be tested.
 */
internal fun carSpots(cars: List<VehicleSnapshot>, selectedVin: String): List<CarSpot> {
    val placed = cars.filter { it.lat != null && it.lon != null }
    if (placed.isEmpty()) return emptyList()
    val anchor = placed.firstOrNull { it.vin == selectedVin } ?: placed.first()
    val lat0 = anchor.lat ?: return emptyList()
    val lon0 = anchor.lon ?: return emptyList()
    val cosLat = cos(Math.toRadians(lat0))
    val offsets = placed.map { c -> ((c.lon!! - lon0) * cosLat) to (c.lat!! - lat0) }
    val span = maxOf(offsets.maxOf { maxOf(abs(it.first), abs(it.second)) }, MinSpanDeg)
    val scale = 0.8 / span
    return placed.mapIndexed { i, c ->
        val (dx, dy) = offsets[i]
        CarSpot(
            vin = c.vin,
            name = c.name,
            x = (dx * scale).toFloat().coerceIn(-0.9f, 0.9f),
            y = (-dy * scale).toFloat().coerceIn(-0.9f, 0.9f),
            selected = c.vin == anchor.vin,
        )
    }
}

/**
 * The cars on a flat round map: each car a dot, the one on screen brighter and larger, each named beside its dot. It is only the positions
 * the cars last reported, drawn with no tiles, so it costs nothing to draw.
 */
@Composable
internal fun WearCarMap(cars: List<VehicleSnapshot>, selectedVin: String) {
    val spots = carSpots(cars, selectedVin)
    if (spots.isEmpty()) {
        WearCaption("No positions yet. Locate a car to see it here.")
        return
    }
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val radius = minOf(maxWidth, maxHeight) / 2f * 0.86f
        Canvas(Modifier.fillMaxSize()) {
            val r = radius.toPx()
            drawCircle(scheme.onSurface.copy(alpha = 0.12f), radius = r, style = Stroke(1.dp.toPx()))
            spots.forEach { s ->
                val c = Offset(size.width / 2f + s.x * r, size.height / 2f + s.y * r)
                if (s.selected) {
                    drawCircle(scheme.primary, radius = 6.dp.toPx(), center = c)
                } else {
                    drawCircle(scheme.onSurfaceVariant.copy(alpha = 0.7f), radius = 4.dp.toPx(), center = c)
                }
            }
        }
        // Each name sits just right of its dot; names that would collide are moved down (see [spreadLabels]).
        val rDp = radius.value
        val boxes = spots.mapIndexed { i, s ->
            val name = s.name
            LabelBox(index = i, x = rDp * s.x + 8f, y = rDp * s.y - 8f, w = name.length * LabelCharDp + 4f, h = LabelLineDp)
        }
        spreadLabels(boxes).forEach { box ->
            val s = spots[box.index]
            Box(
                Modifier.align(Alignment.Center).offset(x = box.x.dp, y = box.y.dp).size(width = box.w.dp, height = box.h.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                RevealText(
                    s.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (s.selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (s.selected) scheme.primary else scheme.onSurface,
                )
            }
        }
        // North, at the top of the ring, so the layout reads at a glance.
        RevealText(
            "N",
            modifier = Modifier.align(Alignment.TopCenter),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = scheme.onSurfaceVariant,
        )
    }
}
