package com.bloo.bluelink.ui

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.GlassStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect

/**
 * A real drop shadow -- an offset, blurred, dark silhouette of [shape] drawn
 * behind the composable -- rather than Material3 Surface's own tonal
 * `shadowElevation`, which reads as barely-there against most of this app's
 * backgrounds. Every piece of floating chrome (icons, pills, the search bar)
 * uses this so it visibly separates from whatever's behind it.
 */
fun Modifier.dropShadow(
    shape: Shape,
    color: Color = Color.Black.copy(alpha = 0.38f),
    blurRadius: Dp = 14.dp,
    offsetY: Dp = 5.dp,
    offsetX: Dp = 0.dp,
): Modifier = this.drawBehind {
    val paint = Paint().asFrameworkPaint().apply {
        this.color = color.toArgb()
        isAntiAlias = true
        if (blurRadius > 0.dp) {
            maskFilter = BlurMaskFilter(blurRadius.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
    }
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = when (outline) {
        is Outline.Rectangle -> androidx.compose.ui.graphics.Path().apply { addRect(outline.rect) }
        is Outline.Rounded -> androidx.compose.ui.graphics.Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
    }
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.translate(offsetX.toPx(), offsetY.toPx())
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
        canvas.restore()
    }
}

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
 * the safer real-glass option: a strong, clearly-visible hardware blur plus
 * a specular top-edge highlight, rather than true edge refraction/lensing.
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
    Box(modifier.clip(shape)) {
        Box(
            Modifier
                .matchParentSize()
                .hazeEffect(state = hazeState) {
                    blurEffect {
                        // Strong and clearly-visible per feedback that the
                        // previous tuning "didn't look like it was doing
                        // anything" -- a deeper blur radius, a brighter
                        // frosted highlight tint, and real noise texture
                        // read as glass at a glance instead of a barely-
                        // there wash.
                        blurRadius = 34.dp
                        noiseFactor = 0.12f
                        colorEffects = listOf(HazeColorEffect.tint(Color.White.copy(alpha = 0.20f)))
                    }
                },
        )
        // A thin bright rim along the top edge -- the same "catches the
        // light" cue real glass/acrylic panels show -- on top of the blur so
        // it reads as an actual material, not just a blurred pane.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .drawBehind { drawRect(Color.White.copy(alpha = 0.35f)) },
        )
    }
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
