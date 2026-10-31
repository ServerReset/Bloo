package com.bloo.bluelink.wear

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import com.bloo.uicommon.canBlurBackdrops
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bloo.uicommon.frostedRim
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo

/*
 * The watch's two glass ingredients, shared by every screen so they all read as one language with the phone:
 * the hairline rim of a glass surface.
 */

/** The hairline rim of a glass surface: the phone's own frosted rim (:uicommon), in white on the watch's black. */
fun Modifier.wearGlassRim(shape: Shape): Modifier = frostedRim(shape, Color.White)


internal val LocalWearHaze = androidx.compose.runtime.staticCompositionLocalOf<HazeState?> { null }

/** The background behind every watch screen: plain, so the face's own colours carry the screen. */
@Composable
internal fun WearBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(MaterialTheme.colorScheme.background)) { content() }
}

/** One drift cycle, in seconds: slow enough to read as ambient, not as animation. */


/**
 * Frosted glass for a watch surface: the backdrop blurred behind [shape] when the device can and may (API 31+, not lean),
 * else nothing -- the caller's own translucent fill and [wearGlassRim] then carry the look. Put it BEFORE the fill.
 */
@Composable
fun Modifier.wearGlassBlur(shape: Shape): Modifier {
    val haze = LocalWearHaze.current
    if (haze == null || !canBlurBackdrops()) return this
    return this.clip(shape).hazeBlur(
        input = HazeInput.Sources(haze),
        style = HazeBlurStyle { blurRadius(WearBlurRadius) },
        performanceMode = HazePerformanceMode.Performance,
    )
}

private val WearBlurRadius = 14.dp


/**
 * Scales THIS element so it sits inside the round display: its widest edge is compared with the chord of the
 * circle at the far end of its height, and it shrinks (never below [minScale]) to fit that chord -- text,
 * a row, a heading, each on its own, wherever it currently is on the screen. A row at the middle is full
 * size and one nearing the top or bottom narrows with the curve, instead of being clipped by the bezel or the
 * whole card being scaled as one.
 */
@Composable
fun Modifier.fitRound(minScale: Float = 0.55f): Modifier {
    val window = LocalWindowInfo.current.containerSize
    var centreY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(Float.NaN) }
    var width by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var height by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    return this
        .onGloballyPositioned { centreY = it.positionInWindow().y + it.size.height / 2f; width = it.size.width.toFloat(); height = it.size.height.toFloat() }
        .graphicsLayer {
            if (centreY.isNaN() || width <= 0f || window.width <= 0) return@graphicsLayer
            val s = roundFitScale(centreY, width, height, window.width.toFloat(), window.height.toFloat(), minScale)
            scaleX = s
            scaleY = s
        }
}

/**
 * The scale at which an element [width] x [height] centred at [centreY] fits the round display [displayWidth] x
 * [displayHeight]: its far edge's distance from the middle gives the circle's chord there, and the element shrinks
 * to that chord, never below [minScale] and never above 1. Pure, so it can be tested.
 */
internal fun roundFitScale(centreY: Float, width: Float, height: Float, displayWidth: Float, displayHeight: Float, minScale: Float): Float {
    if (width <= 0f || displayWidth <= 0f) return 1f
    val radius = minOf(displayWidth, displayHeight) / 2f
    val reach = kotlin.math.abs(centreY - displayHeight / 2f) + height / 2f
    val chord = if (reach >= radius) 0f else 2f * kotlin.math.sqrt(radius * radius - reach * reach)
    return (chord / width).coerceIn(minScale, 1f)
}
