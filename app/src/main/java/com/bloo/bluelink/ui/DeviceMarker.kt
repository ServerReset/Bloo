package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.GeoLocation
import kotlin.math.roundToInt

/**
 * The phone's own position on the map: a dot where it is. Past the map's window it morphs into a small pin on the window's edge, the same
 * way a car's icon does (see [EdgeWindow]), so the phone is never lost off the edge. It is drawn outside the map's scaled layer, so it keeps
 * one size whatever the zoom.
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
    // The map's size, read in the layout and draw phases only, so a resize never recomposes the dot.
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val edge = rememberEdgeWindow()
    val place = tilePlace(dev.latitude, dev.longitude, zoom, xTileF, yTileF, tilePx)
    Box(Modifier.matchParentSize().onSizeChanged { viewport = it })
    Box(
        Modifier.align(Alignment.Center)
            .offset {
                val at = mapScreenOffset(place.x, place.y, state.panX, state.panY, panWeight(), state.scale)
                val pin = edge.pin(at.x, at.y, viewport)
                // Centre-aligned already: the offset is the centre's place from the map's centre.
                IntOffset(pin.x.roundToInt(), pin.y.roundToInt())
            }
            .graphicsLayer {
                val at = mapScreenOffset(place.x, place.y, state.panX, state.panY, panWeight(), state.scale)
                val s = edge.pin(at.x, at.y, viewport).scale
                scaleX = s
                scaleY = s
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

private val DotSize = 40.dp
