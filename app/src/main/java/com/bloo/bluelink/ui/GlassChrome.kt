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
 * Real hardware-accelerated blur (Haze, dev.chrisbanes.haze) for floating
 * chrome (icons, the search bar) so they read as actual glass over whatever's
 * scrolling underneath, instead of a flat semi-transparent fill.
 *
 * This app previously tried a full refraction shader library
 * (io.github.kyant0:backdrop) for a "liquid glass" look, wired through one
 * shared GraphicsLayer-backed backdrop at the app root. That crashed the app
 * immediately after biometric unlock -- an independent audit traced it to
 * documented, unresolved upstream bugs in that library for exactly this
 * app's usage pattern (one shared backdrop, read by several simultaneously-
 * visible, non-overlapping floating elements). Haze is a different,
 * self-contained per-node blur (no shared capture object multiple consumers
 * fight over) that this app already ran crash-free before that swap, so it's
 * the safer real-glass option: a strong, clearly-visible hardware blur
 * rather than true edge refraction/lensing.
 *
 * Null until [BlooApp] registers a source above the current screen's content.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** User's chosen floating-chrome material; read by [GlassBackdrop]. */
val LocalGlassStyle = staticCompositionLocalOf { GlassStyle.LIQUID }

/**
 * Draws the blurred backdrop for one piece of floating chrome (a sibling
 * drawn behind the caller's own icon/text content) -- only for the
 * [GlassStyle.LIQUID] style. [GlassStyle.FROSTED] is the plain, simple
 * semi-transparent fill with no blur at all, so it's a no-op here too --
 * callers apply that look themselves via [glassContainerAlpha] on their own
 * solid tint. Also a no-op when no [LocalHazeState] is registered (e.g.
 * previews).
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
                    // Strong and clearly-visible per feedback that the
                    // previous tuning "didn't look like it was doing
                    // anything" -- a deeper blur radius, a brighter frosted
                    // highlight tint, and real noise texture read as glass
                    // at a glance instead of a barely-there wash.
                    blurRadius = 34.dp
                    noiseFactor = 0.12f
                    colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.20f)))
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
 * contrast), higher for Frosted's plain semi-transparent look.
 */
@Composable
fun glassContainerAlpha(liquid: Float = 0.30f, frosted: Float = 0.62f): Float {
    val isLiquidGlass = LocalHazeState.current != null && LocalGlassStyle.current == GlassStyle.LIQUID
    return if (isLiquidGlass) liquid else frosted
}
