package com.bloo.bluelink.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp

/*
 * The watch's two glass ingredients, shared by every screen so they all read as one language with the phone:
 * the hairline rim of a glass surface, and the soft wash of colour (the phone's aurora) behind a full screen.
 */

/** A hairline rim, bright toward the top-left and again faintly at the bottom-right, like the phone's cards. */
fun Modifier.wearGlassRim(shape: Shape): Modifier = border(
    1.dp,
    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.2f))),
    shape,
)

/** The wash behind a full screen: [tint] fading to nothing from the middle. */
fun Modifier.wearAuroraWash(tint: Color): Modifier = background(
    Brush.radialGradient(listOf(tint.copy(alpha = 0.22f), Color.Transparent)),
)

/**
 * Scales THIS element so it sits inside the round display: its widest edge is compared with the chord of the
 * circle at the far end of its height, and it shrinks (never below [minScale]) to fit that chord -- text,
 * a row, a heading, each on its own, wherever it currently is on the screen. A row at the middle is full
 * size and one nearing the top or bottom narrows with the curve, instead of being clipped by the bezel or the
 * whole card being scaled as one.
 */
@androidx.compose.runtime.Composable
fun Modifier.fitRound(minScale: Float = 0.55f): Modifier {
    val window = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    var centreY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(Float.NaN) }
    var width by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var height by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    return this
        .onGloballyPositioned { centreY = it.positionInWindow().y + it.size.height / 2f; width = it.size.width.toFloat(); height = it.size.height.toFloat() }
        .graphicsLayer {
            if (centreY.isNaN() || width <= 0f || window.width <= 0) return@graphicsLayer
            val r = minOf(window.width, window.height) / 2f
            val reach = kotlin.math.abs(centreY - window.height / 2f) + height / 2f
            val chord = if (reach >= r) 0f else 2f * kotlin.math.sqrt(r * r - reach * reach)
            val s = (chord / width).coerceIn(minScale, 1f)
            scaleX = s
            scaleY = s
        }
}
