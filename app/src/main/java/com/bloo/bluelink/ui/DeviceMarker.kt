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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.MapTiles
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The space the map's top bar and bottom buttons take, in pixels, measured as they are laid out. The off-screen pointers keep to the
 * strip between them, so a pointer never sits under a bar or a button.
 */
internal class MapChromeInsets {
    /** The space at the top of the map that the top bar takes. */
    var topPx by mutableFloatStateOf(0f)

    /** The space at the bottom of the map that the bottom buttons take. */
    var bottomPx by mutableFloatStateOf(0f)
}

/**
 * The phone's own position on the map: a halo + dot when it is inside the box, or a marker clamped to the visible strip (tappable to
 * centre the map) when it is farther than the map shows. The car gets the same treatment, so a car that is off screen has its own
 * pointer. Both are drawn outside the map's scaled layer, so they keep a constant size.
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
    /** How much of the pan shows right now (0 to 1), as the map's own content is shifted by it. */
    panWeight: () -> Float,
    scale: Float,
    box: State<IntSize>,
    marginPx: Float,
    color: Color,
    onCenter: () -> Unit,
    chromeInsets: MapChromeInsets = MapChromeInsets(),
    carColor: Color = color,
    onCenterCar: () -> Unit = {},
) {
    // Read here, in this composable's own scope: a map being resized recomposes this marker alone, not the map around it.
    val halfW = box.value.width / 2f
    val halfH = box.value.height / 2f
    if (halfW <= 0f || halfH <= 0f) return
    val weight = panWeight()
    val topPx = chromeInsets.topPx
    val bottomPx = chromeInsets.bottomPx
    // The phone: its tile delta from the car, plus the pan, all times the scale.
    val sx = (((MapTiles.tileX(dev.longitude, zoom) - xTileF) * tilePx).toFloat() + panX * weight) * scale
    val sy = (((MapTiles.tileY(dev.latitude, zoom) - yTileF) * tilePx).toFloat() + panY * weight) * scale
    // The car is the map's pan origin, so its only offset is the pan.
    val cx = panX * weight * scale
    val cy = panY * weight * scale

    val deviceOff = markerOffVisible(sx, sy, halfW, halfH, marginPx, topPx, bottomPx)
    val carOff = markerOffVisible(cx, cy, halfW, halfH, marginPx, topPx, bottomPx)
    var (dx, dy) = if (deviceOff) clampMarkerToVisible(sx, sy, halfW, halfH, marginPx, topPx, bottomPx) else sx to sy
    var (carX, carY) = if (carOff) clampMarkerToVisible(cx, cy, halfW, halfH, marginPx, topPx, bottomPx) else cx to cy
    // Both pointers off the same edge: nudge them apart, so one never hides the other.
    if (deviceOff && carOff && hypot(dx - carX, dy - carY) < PointerSeparationPx) {
        dx += PointerSeparationPx / 2f
        carX -= PointerSeparationPx / 2f
    }

    if (carOff) {
        Box(
            Modifier.align(Alignment.Center)
                .offset { IntOffset(carX.roundToInt(), carY.roundToInt()) }
                .size(30.dp)
                .background(carColor, CircleShape)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onCenterCar)
                .semantics { contentDescription = "Your car is off screen. Tap to centre on it" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }

    if (deviceOff) {
        Box(
            Modifier.align(Alignment.Center)
                .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                .size(30.dp)
                .background(color, CircleShape)
                .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onCenter)
                .semantics { contentDescription = "Your location is off screen. Tap to centre on it" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.MyLocation, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    } else {
        Box(
            Modifier.align(Alignment.Center)
                .offset { IntOffset(sx.roundToInt(), sy.roundToInt()) }
                .size(30.dp)
                .semantics { contentDescription = "Your location" },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(30.dp).background(color.copy(alpha = 0.22f), CircleShape))
            Box(
                Modifier.size(16.dp).background(Color.White, CircleShape).padding(3.dp)
                    .background(color, CircleShape),
            )
        }
    }
}

/** How far apart two off-screen pointers are kept, so neither hides the other. */
private const val PointerSeparationPx = 36f
