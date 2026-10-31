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
        spots.forEach { s ->
            Box(
                Modifier.align(Alignment.Center).offset(x = radius * s.x + 8.dp, y = radius * s.y - 8.dp).size(width = 80.dp, height = 40.dp),
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
    }
}
