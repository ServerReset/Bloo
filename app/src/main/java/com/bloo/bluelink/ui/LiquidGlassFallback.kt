package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * The NO-Backdrop-dependency fallback for [Modifier.liquidGlass].
 *
 * Used whenever real refraction isn't available — the liquid-glass toggle is
 * on but the device is pre-API-31, we're inside a Dialog/Popup window that
 * can't reach the root backdrop layer, or (belt-and-suspenders) the Backdrop
 * library was removed from the build. It has ZERO `com.kyant.backdrop` imports
 * on purpose: if [LiquidGlass.kt] and the `io.github.kyant0:backdrop`
 * dependency are ever deleted, this file still compiles and the toggle keeps
 * working with an enhanced frosted-glass look.
 *
 * This is a *stronger* version of the app's default frosted chrome
 * ([glassContainerAlpha] / [frostedRim]): the same translucent tint, plus a
 * brighter top-lit specular edge, to read as "glass" without a real blur.
 * When the liquid-glass toggle is OFF the app uses the plain [frostedRim] /
 * [glassContainerAlpha] chrome instead and never calls this at all, so the
 * default look is unchanged.
 */
@Composable
fun Modifier.liquidGlassFallback(
    shape: Shape,
    tint: Color,
    tintAlpha: Float,
): Modifier {
    val onSurface = MaterialTheme.colorScheme.onSurface
    return this
        .clip(shape)
        .background(tint.copy(alpha = tintAlpha), shape)
        // A brighter, more specular edge than the plain frostedRim: a crisp
        // top highlight fading to a dim mid and a soft bottom catch, so the
        // element reads as a lifted glass pane even without a hardware blur.
        .border(
            BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    listOf(
                        onSurface.copy(alpha = 0.35f),
                        onSurface.copy(alpha = 0.08f),
                        onSurface.copy(alpha = 0.20f),
                    ),
                ),
            ),
            shape,
        )
}
