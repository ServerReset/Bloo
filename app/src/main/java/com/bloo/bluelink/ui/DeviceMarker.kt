package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import com.bloo.uicommon.LocalReduceMotion
import com.bloo.uicommon.lowPowerCalm
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    /** The car's photo for its own off-screen pointer; null keeps the plain glyph. */
    carPhoto: String? = null,
) {
    // Read here, in this composable's own scope: a map being resized recomposes this marker alone, not the map around it.
    val halfW = box.value.width / 2f
    val halfH = box.value.height / 2f
    if (halfW <= 0f || halfH <= 0f) return
    val weight = panWeight()
    val topPx = chromeInsets.topPx
    val bottomPx = chromeInsets.bottomPx
    // The phone: its tile delta from the car, plus the pan, all times the scale.
    val devPlace = tilePlace(dev.latitude, dev.longitude, zoom, xTileF, yTileF, tilePx)
    val devScreen = mapScreenOffset(devPlace.x, devPlace.y, panX, panY, weight, scale)
    val sx = devScreen.x
    val sy = devScreen.y
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

    OffScreenPointer(
        key = "device-car",
        offscreen = carOff,
        x = carX,
        y = carY,
        photo = carPhoto,
        tint = carColor,
        contentDescription = "Your car is off screen. Tap to centre on it",
        onClick = onCenterCar,
    )

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

/**
 * The cars that are off screen, each as its photo at the edge of the visible strip, in the direction the car lies. The strip is the map
 * less the top bar and the bottom buttons ([chromeInsets]), so a pointer never sits under one of them.
 */
@Composable
internal fun BoxScope.OffScreenCarPointers(
    cars: List<CarPin>,
    zoom: Int,
    xTileF: Double,
    yTileF: Double,
    tilePx: Float,
    panX: Float,
    panY: Float,
    panWeight: () -> Float,
    scale: Float,
    box: State<IntSize>,
    marginPx: Float,
    tint: Color,
    chromeInsets: MapChromeInsets,
    onPick: (String) -> Unit,
) {
    val halfW = box.value.width / 2f
    val halfH = box.value.height / 2f
    if (halfW <= 0f || halfH <= 0f || cars.isEmpty()) return
    val weight = panWeight()
    val topPx = chromeInsets.topPx
    val bottomPx = chromeInsets.bottomPx
    for (car in cars) {
        // The same tile arithmetic as the phone's own pointer: the car's tile delta from the map centre, plus the pan, all times the scale.
        val carPlace = tilePlace(car.location.latitude, car.location.longitude, zoom, xTileF, yTileF, tilePx)
        val carScreen = mapScreenOffset(carPlace.x, carPlace.y, panX, panY, weight, scale)
        val sx = carScreen.x
        val sy = carScreen.y
        val off = markerOffVisible(sx, sy, halfW, halfH, marginPx, topPx, bottomPx)
        val (x, y) = if (off) clampMarkerToVisible(sx, sy, halfW, halfH, marginPx, topPx, bottomPx) else sx to sy
        // Every car keeps its pointer composed, so one that leaves the screen can animate out to the edge, and one that comes back in.
        key(car.vin) {
            OffScreenPointer(
                key = car.vin,
                offscreen = off,
                x = x,
                y = y,
                photo = car.photo,
                tint = tint,
                contentDescription = "${car.name} is off screen. Tap to select it",
                onClick = { onPick(car.vin) },
            )
        }
    }
}

/**
 * An off-screen car's photo at the edge of the visible strip. A car leaving the screen springs from where it was to the edge and fades in,
 * and one coming back springs in and fades out, so the change reads as a move rather than a pop. Off screen it takes taps; on screen it is
 * invisible and takes none, so it cannot take the tap meant for a car under it.
 */
@Composable
private fun BoxScope.OffScreenPointer(
    key: Any,
    offscreen: Boolean,
    x: Float,
    y: Float,
    photo: String?,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val motion = rememberPointerMotion(key, x, y, offscreen)
    CarPhotoTile(
        photo = photo,
        tint = tint,
        contentDescription = contentDescription,
        onClick = if (offscreen) onClick else null,
        modifier = Modifier
            .align(Alignment.Center)
            .offset { IntOffset(motion.x.value.roundToInt(), motion.y.value.roundToInt()) }
            .graphicsLayer {
                alpha = motion.show.value
                // Smaller while it is only half there, so it grows as it settles at the edge.
                val s = 0.6f + 0.4f * motion.show.value
                scaleX = s
                scaleY = s
            },
    )
}

/** Where an off-screen pointer is, and how far it is shown (0 hidden, 1 shown), animated between its places. See [rememberPointerMotion]. */
internal class PointerMotion(x: Float, y: Float, offscreen: Boolean) {
    val x = Animatable(x)
    val y = Animatable(y)
    val show = Animatable(if (offscreen) 1f else 0f)

    /** Which side of the screen the pointer was last drawn on, so a change to the other side is noticed once. */
    var offscreen = offscreen

    /** True while a spring is taking the pointer to its new place, so the snapping that follows the pan waits for it. */
    var springing = false
}

/**
 * The pointer's place for this frame. While it stays on its side of the screen it snaps to [x] and [y] exactly, so it tracks the pan and
 * the zoom. When it crosses to the other side, a spring takes it from where it was to where it is now, and its show amount fades to match.
 */
@Composable
internal fun rememberPointerMotion(key: Any, x: Float, y: Float, offscreen: Boolean): PointerMotion {
    val motion = remember(key) { PointerMotion(x, y, offscreen) }
    val reduceMotion = LocalReduceMotion.current
    val latestX by rememberUpdatedState(x)
    val latestY by rememberUpdatedState(y)
    LaunchedEffect(x, y) {
        if (!motion.springing) {
            motion.x.snapTo(x)
            motion.y.snapTo(y)
        }
    }
    LaunchedEffect(offscreen) {
        if (offscreen == motion.offscreen) return@LaunchedEffect
        motion.offscreen = offscreen
        val show = if (offscreen) 1f else 0f
        if (reduceMotion) {
            motion.x.snapTo(latestX)
            motion.y.snapTo(latestY)
            motion.show.snapTo(show)
            return@LaunchedEffect
        }
        motion.springing = true
        coroutineScope {
            launch { motion.show.animateTo(show, tween(PointerFadeMs)) }
            launch { motion.x.animateTo(latestX, lowPowerCalm<Float>(Spring.StiffnessMediumLow)) }
            launch { motion.y.animateTo(latestY, lowPowerCalm<Float>(Spring.StiffnessMediumLow)) }
        }
        motion.springing = false
    }
    return motion
}

/** How long an off-screen pointer takes to fade in or out. */
private const val PointerFadeMs = 180
