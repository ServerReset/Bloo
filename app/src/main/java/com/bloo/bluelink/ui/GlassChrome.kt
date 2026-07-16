package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
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
 * Draws the real blurred backdrop for one piece of floating chrome (a
 * sibling drawn behind the caller's own icon/text content) -- only for the
 * [GlassStyle.LIQUID] style. [GlassStyle.FROSTED] is deliberately the old,
 * simple semi-transparent fill with no blur at all (what this looked like
 * before Haze was added), so it's a no-op here too -- callers apply that
 * look themselves via [glassContainerAlpha] on their own solid tint.
 * Also a no-op when no [LocalHazeState] is registered (e.g. previews).
 */
@Composable
fun GlassBackdrop(shape: Shape, modifier: Modifier = Modifier) {
    val hazeState = LocalHazeState.current ?: return
    if (LocalGlassStyle.current != GlassStyle.LIQUID) return
    Box(
        modifier
            .clip(shape)
            .hazeEffect(state = hazeState) {
                blurEffect {
                    blurRadius = 26.dp
                    noiseFactor = 0.05f
                    colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.14f)))
                }
            },
    )
}

/** Convenience overload for the common circular floating-icon case. */
@Composable
fun GlassBackdropCircle(modifier: Modifier = Modifier) = GlassBackdrop(CircleShape, modifier)

/**
 * The alpha floating chrome's own solid tint should use for its fallback/
 * base fill: low when Liquid glass is doing the real work of reading as
 * glass (the blur provides the depth, so the tint just needs to nudge
 * contrast), higher for Frosted's plain semi-transparent look -- but still
 * more transparent than the pre-Haze flat fill this replaces, per feedback
 * that the floating buttons read as too opaque.
 */
@Composable
fun glassContainerAlpha(liquid: Float = 0.28f, frosted: Float = 0.62f): Float {
    val isLiquidGlass = LocalHazeState.current != null && LocalGlassStyle.current == GlassStyle.LIQUID
    return if (isLiquidGlass) liquid else frosted
}
