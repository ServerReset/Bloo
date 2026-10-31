package com.bloo.bluelink.wear

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.TimeText
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.uicommon.RevealText
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

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

/** The cars' flat layout in degrees from the selected car: each car's offset, and the widest offset (before the minimum span). */
private class FlatLayout(val anchorVin: String, val offsets: List<Pair<Double, Double>>, val cars: List<VehicleSnapshot>, val widest: Double)

/** Where the cars with a known position are, measured from the [selectedVin] car, or null when none has one. */
private fun flatLayout(cars: List<VehicleSnapshot>, selectedVin: String): FlatLayout? {
    val placed = cars.filter { it.lat != null && it.lon != null }
    val anchor = placed.firstOrNull { it.vin == selectedVin } ?: placed.firstOrNull() ?: return null
    val lat0 = anchor.lat ?: return null
    val lon0 = anchor.lon ?: return null
    val cosLat = cos(Math.toRadians(lat0))
    val offsets = placed.map { c -> ((c.lon!! - lon0) * cosLat) to (c.lat!! - lat0) }
    val widest = offsets.maxOf { maxOf(abs(it.first), abs(it.second)) }
    return FlatLayout(anchor.vin, offsets, placed, widest)
}

/**
 * Where each car with a known position sits, flat, with the [selectedVin] car in the middle and the rest placed by their distance from it.
 * The cars that are farthest apart reach 80% of the way to the edge, so the whole group fits. Pure, so it can be tested.
 */
internal fun carSpots(cars: List<VehicleSnapshot>, selectedVin: String): List<CarSpot> {
    val layout = flatLayout(cars, selectedVin) ?: return emptyList()
    val scale = 0.8 / maxOf(layout.widest, MinSpanDeg)
    return layout.cars.mapIndexed { i, c ->
        val (dx, dy) = layout.offsets[i]
        CarSpot(
            vin = c.vin,
            name = c.name,
            x = (dx * scale).toFloat().coerceIn(-0.9f, 0.9f),
            y = (-dy * scale).toFloat().coerceIn(-0.9f, 0.9f),
            selected = c.vin == layout.anchorVin,
        )
    }
}

/** Metres in one degree of latitude, for turning the map's degrees into a distance the wearer can read. */
private const val MetresPerDegree = 111_320.0

/** The share of the map's radius the widest car sits at (see [carSpots]); the outer ring is the distance that share stands for. */
private const val WidestCarShare = 0.8

/**
 * What the map's outer ring stands for, in metres, and whether every car is within a few metres of the others: the map's scale, so a
 * close pair reads as close rather than as a dot on a fixed-size map. Null when no car has a position. Pure, so it can be tested.
 */
internal fun mapScale(cars: List<VehicleSnapshot>, selectedVin: String): Pair<Double, Boolean>? {
    val layout = flatLayout(cars, selectedVin) ?: return null
    val ringMetres = maxOf(layout.widest, MinSpanDeg) / WidestCarShare * MetresPerDegree
    val sameSpot = layout.cars.size > 1 && layout.widest * MetresPerDegree < SameSpotMetres
    return ringMetres to sameSpot
}

/** Cars nearer than this many metres are drawn as one spot, and the map says so. */
private const val SameSpotMetres = 15.0

/** A distance for the map's scale line: whole metres under a kilometre, then kilometres to one place. */
internal fun scaleLabel(metres: Double): String =
    if (metres < 1000.0) "${(metres / 10).roundToInt() * 10} m" else "${"%.1f".format(metres / 1000.0)} km"

/** The ring's radius as a share of the display's half-width: inside the round edge, so the names and the scale line stay on the face. */
private const val MapRingShare = 0.78f

/**
 * The Cars page: the cars on a flat map that fills the round display. Each car is a dot, the one on screen brighter and larger, and its
 * name sits beside the dot. It is only the positions the cars last reported, drawn with no tiles, so it costs nothing to draw.
 */
@Composable
internal fun WearCarMapPage(cars: List<VehicleSnapshot>, selectedVin: String) {
    ScreenScaffold(timeText = { TimeText() }) {
        val spots = carSpots(cars, selectedVin)
        val scale = mapScale(cars, selectedVin)
        if (spots.isEmpty() || scale == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                WearCaption("No positions yet. Locate a car to see it here.")
            }
        } else {
            val (ringMetres, sameSpot) = scale
            val scheme = MaterialTheme.colorScheme
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val radius = minOf(maxWidth, maxHeight) / 2f * MapRingShare
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
                    LabelBox(index = i, x = rDp * s.x + 8f, y = rDp * s.y - 8f, w = s.name.length * LabelCharDp + 4f, h = LabelLineDp)
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
                // North, just inside the top of the ring, so the layout reads at a glance.
                RevealText(
                    "N",
                    modifier = Modifier.align(Alignment.Center).offset(y = (-rDp + 10f).dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurfaceVariant,
                )
                // The scale, just inside the bottom of the ring: the outer ring's distance, or that the cars are at one spot.
                RevealText(
                    if (sameSpot) "All at one spot" else "Outer ring about ${scaleLabel(ringMetres)}",
                    modifier = Modifier.align(Alignment.Center).offset(y = (rDp - 16f).dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
