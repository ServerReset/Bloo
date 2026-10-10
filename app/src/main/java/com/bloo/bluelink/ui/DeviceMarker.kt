package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.MapTiles
import kotlin.math.roundToInt

/**
 * The phone's own position on the map: a halo + dot when it is inside the box, or a marker clamped to
 * the box edge (tappable to centre the map) when it is farther than the map shows -- so "my location"
 * is never simply nowhere. Drawn outside the map's scaled layer, so it keeps a constant size.
 */
@Composable
internal fun BoxScope.DeviceMarker(
    dev: GeoLocation,
    zoom: Int,
    xTileF: Double,
    yTileF: Double,
    tilePx: Float,
    panX: Float,
    panY: Float,
    scale: Float,
    halfW: Float,
    halfH: Float,
    marginPx: Float,
    color: Color,
    onCenter: () -> Unit,
) {
    val sx = (((MapTiles.tileX(dev.longitude, zoom) - xTileF) * tilePx).toFloat() + panX) * scale
    val sy = (((MapTiles.tileY(dev.latitude, zoom) - yTileF) * tilePx).toFloat() + panY) * scale
    if (markerOffBox(sx, sy, halfW, halfH, marginPx)) {
        val (ex, ey) = clampMarkerToEdge(sx, sy, halfW, halfH, marginPx)
        Box(
            Modifier
                .align(Alignment.Center)
                .offset { IntOffset(ex.roundToInt(), ey.roundToInt()) }
                .size(30.dp)
                .background(color, CircleShape)
                .clickable(onClick = onCenter)
                .semantics { contentDescription = "Your location is off screen. Tap to centre on it" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    } else {
        Box(
            Modifier
                .align(Alignment.Center)
                .offset { IntOffset(sx.roundToInt(), sy.roundToInt()) }
                .size(30.dp)
                .semantics { contentDescription = "Your location" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(30.dp).background(color.copy(alpha = 0.22f), CircleShape))
            Box(
                Modifier
                    .size(16.dp)
                    .background(Color.White, CircleShape)
                    .padding(3.dp)
                    .background(color, CircleShape),
            )
        }
    }
}
