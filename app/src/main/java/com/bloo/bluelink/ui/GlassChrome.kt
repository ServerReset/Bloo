package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.GlassStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect

/**
 * Real hardware-accelerated blur (Haze) for floating chrome (icons, the
 * search bar) so they read as actual glass over whatever's scrolling
 * underneath, instead of a flat semi-transparent fill. Null until [BlooApp]
 * registers a source above the current screen's content.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** User's chosen floating-chrome material; read by [GlassBackdrop]. */
val LocalGlassStyle = staticCompositionLocalOf { GlassStyle.LIQUID }

/**
 * Draws the blurred backdrop for one piece of floating chrome (a
 * [GlassBackdrop] sibling drawn behind the caller's own icon/text content).
 * A no-op when no [LocalHazeState] is registered (e.g. previews) — callers
 * keep their own solid-tint fallback for that case.
 */
@Composable
fun GlassBackdrop(shape: Shape, modifier: Modifier = Modifier) {
    val hazeState = LocalHazeState.current ?: return
    val style = LocalGlassStyle.current
    val fallbackTint = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(
        modifier
            .clip(shape)
            .hazeEffect(state = hazeState) {
                blurEffect {
                    blurRadius = if (style == GlassStyle.LIQUID) 26.dp else 16.dp
                    noiseFactor = if (style == GlassStyle.LIQUID) 0.05f else 0.15f
                    colorEffects = listOf(
                        HazeColorEffect.tint(
                            if (style == GlassStyle.LIQUID) {
                                Color.White.copy(alpha = 0.14f)
                            } else {
                                fallbackTint.copy(alpha = 0.55f)
                            },
                        ),
                    )
                }
            },
    )
}

/** Convenience overload for the common circular floating-icon case. */
@Composable
fun GlassBackdropCircle(modifier: Modifier = Modifier) = GlassBackdrop(CircleShape, modifier)
