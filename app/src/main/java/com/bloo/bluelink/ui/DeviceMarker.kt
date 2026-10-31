package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.GeoLocation
import kotlin.math.roundToInt

/**
 * The phone's own position on the map: a dot where it is. It is not drawn when it is off the map, since the map clips it, so there is no
 * pointer at the edge. It is drawn outside the map's scaled layer, so it keeps one size whatever the zoom.
 */
@Composable
internal fun BoxScope.DeviceMarker(
    dev: GeoLocation,
    zoom: Int,
    xTileF: Double,
    yTileF: Double,
    tilePx: Float,
    state: CarMapState,
    panWeight: () -> Float,
    color: Color,
) {
    val place = tilePlace(dev.latitude, dev.longitude, zoom, xTileF, yTileF, tilePx)
    Box(
        Modifier.align(Alignment.Center)
            .offset {
                val at = mapScreenOffset(place.x, place.y, state.panX, state.panY, panWeight(), state.scale)
                IntOffset((at.x - DotSize.toPx() / 2f).roundToInt(), (at.y - DotSize.toPx() / 2f).roundToInt())
            }
            .size(DotSize)
            .semantics { contentDescription = "Your location" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(DotSize).background(color.copy(alpha = 0.22f), CircleShape))
        Box(
            Modifier.size(16.dp).background(Color.White, CircleShape).padding(3.dp).background(color, CircleShape),
        )
    }
}

private val DotSize = 30.dp
