package com.bloo.bluelink.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Scales THIS element so it sits inside the round display: its widest edge is compared with the chord of the circle at the far end of its
 * height, and it shrinks (never below [minScale]) to fit that chord. A row at the middle is full size, and one nearing the top or bottom
 * narrows with the curve, instead of being clipped by the bezel.
 */
@Composable
fun Modifier.fitRound(minScale: Float = 0.55f): Modifier {
    val window = LocalWindowInfo.current.containerSize
    var centreY by remember { mutableFloatStateOf(Float.NaN) }
    var width by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    return this
        .onGloballyPositioned {
            centreY = it.positionInWindow().y + it.size.height / 2f
            width = it.size.width.toFloat()
            height = it.size.height.toFloat()
        }
        .graphicsLayer {
            if (centreY.isNaN() || width <= 0f || window.width <= 0) return@graphicsLayer
            val s = roundFitScale(centreY, width, height, window.width.toFloat(), window.height.toFloat(), minScale)
            scaleX = s
            scaleY = s
        }
}

/**
 * The scale at which an element [width] x [height] centred at [centreY] fits the round display [displayWidth] x [displayHeight]: its far
 * edge's distance from the middle gives the circle's chord there, and the element shrinks to that chord, never below [minScale] and never
 * above 1. Pure, so it can be tested.
 */
internal fun roundFitScale(centreY: Float, width: Float, height: Float, displayWidth: Float, displayHeight: Float, minScale: Float): Float {
    if (width <= 0f || displayWidth <= 0f) return 1f
    val radius = minOf(displayWidth, displayHeight) / 2f
    val reach = kotlin.math.abs(centreY - displayHeight / 2f) + height / 2f
    val chord = if (reach >= radius) 0f else 2f * kotlin.math.sqrt(radius * radius - reach * reach)
    return (chord / width).coerceIn(minScale, 1f)
}
