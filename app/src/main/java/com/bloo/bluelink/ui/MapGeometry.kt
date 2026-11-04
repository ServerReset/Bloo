package com.bloo.bluelink.ui

/*
 * The map's layout arithmetic, kept apart from its drawing: where an off-edge car is pinned, the window the markers keep to, and how
 * cars that share a spot group into one pill. Pure functions and the window's own state, so the map file carries only what it draws.
 */

import com.bloo.bluelink.data.StartupLabels
import com.bloo.bluelink.data.StartupTrace
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import com.bloo.uicommon.RevealText
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.bloo.bluelink.data.GeoLocation
import com.bloo.bluelink.data.MapTiles
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import com.bloo.uicommon.LocalPowerPolicy

/** The size of a car that has gone off the edge, as a share of its icon: it shrinks into a pin as it reaches the edge. */
internal const val EdgePinScale = 0.75f

/** How far past the edge a car travels while it morphs from its icon into its edge pin, in dp. */
internal val EdgeMorphDp = 72.dp

/** Where a car's icon is kept clear of the map's edges, in dp: the top and bottom carry the bar and the buttons. */
internal val EdgeInsetSidesDp = 44.dp

/** The gap an off-edge marker keeps from the map's edge, past the bars where the map has them. */
internal val EdgeMarginDp = 8.dp

/**
 * Where a car shows, for its place [x], [y] from the map's centre in pixels, and a window of [halfW] by [halfH] round that centre. Inside the
 * window it is its icon, at its place. Past the window it slides to the window's edge on the line from the centre to it, and shrinks into a
 * pin there. The change is continuous: it starts the moment the car crosses the edge, and the pin keeps the car's direction.
 */
internal data class EdgePin(val x: Float, val y: Float, val scale: Float)

internal fun edgePin(x: Float, y: Float, halfW: Float, halfTop: Float, halfBottom: Float, morphPx: Float): EdgePin {
    // Before the map has a size there is no window to keep a car in: it stays where it is.
    if (halfW <= 0f || halfTop <= 0f || halfBottom <= 0f) return EdgePin(x, y, 1f)
    val ax = abs(x)
    val ay = abs(y)
    // The window is not symmetric (the bottom carries the buttons): each vertical side has its own half-height.
    val halfH = if (y > 0f) halfBottom else halfTop
    // How far along its own direction the window's edge is: 1 or more means the car is inside.
    val t = min(if (ax > 0f) halfW / ax else Float.MAX_VALUE, if (ay > 0f) halfH / ay else Float.MAX_VALUE)
    if (t >= 1f) return EdgePin(x, y, 1f)
    val edgeX = x * t
    val edgeY = y * t
    // How far past the edge the car is, in pixels, and how far through the morph it has got.
    val beyond = hypot(x, y) - hypot(edgeX, edgeY)
    val f = if (morphPx > 0f) (beyond / morphPx).coerceIn(0f, 1f) else 1f
    // Never past the window itself: a marker that has morphed in still stays inside the map's edges and below the bar.
    val px = (x + (edgeX - x) * f).coerceIn(-halfW, halfW)
    val py = (y + (edgeY - y) * f).coerceIn(-halfTop, halfBottom)
    return EdgePin(px, py, 1f - (1f - EdgePinScale) * f)
}

/**
 * The window the map's off-edge markers stay inside, and how a marker past it morphs into its pin: the map less its insets, measured from the
 * map's centre (a marker at the centre is the anchor). Shared by every marker, so the car icons and the phone's dot follow one rule.
 */
internal class EdgeWindow(
    private val sides: Float,
    private val margin: Float,
    /** The map's measured chrome, read at each placement: the window follows the real top bar and bottom buttons. */
    private val chrome: MapChromeInsets?,
    private val morph: Float,
) {
    fun pin(x: Float, y: Float, viewport: IntSize): EdgePin {
        val top = margin + (chrome?.topPx() ?: 0f)
        val bottom = margin + (chrome?.bottomPx() ?: 0f)
        return edgePin(x, y, viewport.width / 2f - sides, viewport.height / 2f - top, viewport.height / 2f - bottom, morph)
    }
}

@Composable
internal fun rememberEdgeWindow(): EdgeWindow {
    val density = LocalDensity.current
    val chrome = LocalMapChrome.current
    return remember(density, chrome) {
        with(density) {
            EdgeWindow(EdgeInsetSidesDp.toPx(), EdgeMarginDp.toPx(), chrome, EdgeMorphDp.toPx())
        }
    }
}

/**
 * Every car on the map as its own icon (its photo, or the car glyph), at the place the car is. A car that goes past the edge of the map
 * morphs into a small pin on that edge, pointing the way it is, and comes back into its icon as it returns. Tapping an icon selects that car.
 */
/** Off-edge markers this close on screen, in dp, share one pill: the first of them in list order leads it. */
internal val PillGroupRadiusDp = 28.dp

/** The most photos one pill shows; a larger group shows the first of them and a count of the rest. */
internal const val PillMaxPhotos = 10

/** A small photo in a pill. */
internal val PillPhotoSize = DpSize(34.dp, 24.dp)

/** One car's marker this frame: where its icon's centre sits, the pin scale, and whether it is past the edge (moved off its own place). */
internal class CarMarker(val vin: String, val x: Float, val y: Float, val scale: Float, val offEdge: Boolean)

/**
 * The leader of each car's pill, by index: a car that is past the edge and lands within the radius of an earlier leader joins that
 * leader's pill; anything else leads (or stands) alone. Computed in list order, so every car agrees on the same groups.
 */
internal fun pillLeaders(markers: List<CarMarker>, radiusPx: Float): IntArray {
    val leader = IntArray(markers.size) { it }
    for (i in markers.indices) {
        val m = markers[i]
        if (!m.offEdge) continue
        for (j in 0 until i) {
            val other = markers[j]
            if (other.offEdge && leader[j] == j && hypot(m.x - other.x, m.y - other.y) < radiusPx) {
                leader[i] = j
                break
            }
        }
    }
    return leader
}
