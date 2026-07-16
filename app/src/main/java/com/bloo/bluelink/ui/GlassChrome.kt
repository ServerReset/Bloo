package com.bloo.bluelink.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.GlassStyle
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * Real per-pixel Liquid Glass refraction (io.github.kyant0:backdrop, aka
 * "AndroidLiquidGlass") for floating chrome (icons, the search bar) so they
 * actually bend/refract whatever's behind them instead of just a flat blur.
 * Null until [BlooApp] registers a source above the current screen's content.
 */
val LocalLiquidBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** User's chosen floating-chrome material; read by [GlassBackdrop]. */
val LocalGlassStyle = staticCompositionLocalOf { GlassStyle.LIQUID }

/**
 * Draws the real refracted backdrop for one piece of floating chrome (a
 * sibling drawn behind the caller's own icon/text content) -- only for the
 * [GlassStyle.LIQUID] style. [GlassStyle.FROSTED] is deliberately the plain,
 * simple semi-transparent fill with no shader at all (what this looked like
 * before any glass library was added), so it's a no-op here too -- callers
 * apply that look themselves via [glassContainerAlpha] on their own solid
 * tint. Also a no-op when no [LocalLiquidBackdrop] is registered (e.g.
 * previews). The library itself no-ops its shader effects on devices without
 * RenderEffect/RuntimeShader support (roughly API < 31/33), so this always
 * degrades to the caller's plain tint instead of crashing.
 */
@Composable
fun GlassBackdrop(shape: Shape, modifier: Modifier = Modifier) {
    val backdrop = LocalLiquidBackdrop.current ?: return
    if (LocalGlassStyle.current != GlassStyle.LIQUID) return
    val density = LocalDensity.current
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                with(density) {
                    blur(8.dp.toPx())
                    lens(12.dp.toPx(), 22.dp.toPx(), depthEffect = true)
                }
            },
        ),
    )
}

/** Convenience overload for the common circular floating-icon case. */
@Composable
fun GlassBackdropCircle(modifier: Modifier = Modifier) = GlassBackdrop(CircleShape, modifier)

/**
 * The alpha floating chrome's own solid tint should use for its fallback/
 * base fill: low when Liquid glass is doing the real work of reading as
 * glass (the refraction provides the depth, so the tint just needs to nudge
 * contrast), higher for Frosted's plain semi-transparent look.
 */
@Composable
fun glassContainerAlpha(liquid: Float = 0.28f, frosted: Float = 0.62f): Float {
    val isLiquidGlass = LocalLiquidBackdrop.current != null && LocalGlassStyle.current == GlassStyle.LIQUID
    return if (isLiquidGlass) liquid else frosted
}
