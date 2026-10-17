package com.bloo.bluelink.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
