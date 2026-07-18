package com.bloo.bluelink.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.bloo.bluelink.data.GlassStyle
import com.bloo.uicommon.dropShadow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect

/**
 * A radial lens/refraction distortion, applied as a self-contained
 * *foreground* effect on each glass element's own already-rendered content
 * (never a shared capture object multiple consumers read from -- that
 * architecture is what crashed the app when this used
 * io.github.kyant0:backdrop, see the doc comment below). Bows light toward
 * the edges like a real curved-glass surface catches it, on top of Haze's
 * blur, with a touch of chromatic aberration (R/G/B sampled at slightly
 * different bulge strengths) at the rim -- the same prism-fringe cue real
 * curved glass/acrylic edges show, and what makes a refraction effect read
 * as "glass" rather than just "warped." Deliberately a static per-layout
 * distortion, not a continuously-animated time-based sweep: this class of
 * effect has a real crash history in this app (see below), so the shader
 * stays as simple as it can while still looking convincing, rather than
 * adding a per-frame animation loop for a subtler win. AGSL requires API
 * 33+; both shader construction and the per-frame RenderEffect build are
 * wrapped in [runCatching] so any shader compile/runtime failure just
 * silently skips the lens effect (blur-only, previous appearance) rather
 * than crashing.
 */
private const val RefractionShaderSrc = """
    uniform shader content;
    uniform float2 size;
    half4 main(float2 coord) {
        float2 uv = coord / max(size, float2(1.0));
        float2 centered = uv - 0.5;
        float dist = length(centered);
        // Pushed noticeably stronger than the first pass -- at the previous
        // strength the bulge was too subtle to read as "glass" rather than a
        // slightly-off blur on real hardware, which is what prompted this.
        float bulgeBase = smoothstep(0.0, 0.62, dist) * 0.14;
        // Chromatic aberration: each channel bulges by a slightly different
        // amount, so the rim shows a faint red/blue fringe like light
        // splitting through a real curved edge.
        float2 rBulge = uv - centered * (bulgeBase * 1.22);
        float2 gBulge = uv - centered * bulgeBase;
        float2 bBulge = uv - centered * (bulgeBase * 0.78);
        half r = content.eval(clamp(rBulge, 0.0, 1.0) * size).r;
        half g = content.eval(clamp(gBulge, 0.0, 1.0) * size).g;
        half4 bSample = content.eval(clamp(bBulge, 0.0, 1.0) * size);
        float edgeGlint = smoothstep(0.42, 1.0, dist) * 0.16;
        return half4(r + edgeGlint, g + edgeGlint, bSample.b + edgeGlint, bSample.a);
    }
"""

/** Remembers one shader instance per call site; null below API 33 or on any construction failure. */
@Composable
private fun rememberRefractionShader(): RuntimeShader? {
    if (Build.VERSION.SDK_INT < 33) return null
    return remember { runCatching { RuntimeShader(RefractionShaderSrc) }.getOrNull() }
}

/**
 * Applies the AGSL refraction lens to this element, sized to its own layout
 * bounds. No-op if [shader] is null (unsupported API level or construction
 * failed) or if building the per-frame [RenderEffect] ever throws.
 */
private fun Modifier.refractionLens(shader: RuntimeShader?): Modifier {
    if (shader == null) return this.refractionFallback()
    return this
        .onSizeChanged { sz ->
            runCatching { shader.setFloatUniform("size", sz.width.toFloat(), sz.height.toFloat()) }
        }
        .graphicsLayer {
            val effect = runCatching {
                RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
            }.getOrNull() ?: return@graphicsLayer
            renderEffect = effect
            clip = true
        }
}

/**
 * Below API 33 (or if [RuntimeShader] construction ever fails) the AGSL lens
 * is a total no-op, which used to mean the element silently lost its "glass"
 * read with zero compensating treatment. This paints the same edge-catches-
 * the-light cue as a plain radial gradient instead -- brighter dead centre,
 * fading through the middle, a faint bright rim -- so every device shows
 * *some* refraction-like highlight, not just devices new enough for AGSL.
 */
private fun Modifier.refractionFallback(): Modifier = this.background(
    Brush.radialGradient(
        colorStops = arrayOf(
            0.0f to Color.White.copy(alpha = 0.10f),
            0.55f to Color.White.copy(alpha = 0.02f),
            0.85f to Color.White.copy(alpha = 0.0f),
            1.0f to Color.White.copy(alpha = 0.12f),
        ),
    ),
)

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

/** True for both glass styles that render real blur (Liquid and Ultra); false for Frosted's flat tint. */
private val GlassStyle.rendersBlur: Boolean
    get() = this == GlassStyle.LIQUID || this == GlassStyle.ULTRA

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
    if (!LocalGlassStyle.current.rendersBlur) return
    val refractionShader = rememberRefractionShader()
    Box(
        modifier
            .clip(shape)
            .refractionLens(refractionShader)
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
            }
            // A full-perimeter edge, brighter along the top and fading down
            // the sides -- real glass/acrylic catches the light all around
            // its rim, not just a single flat line across the top, which
            // read as a sticker/decal rather than an actual material edge.
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.22f),
                        ),
                    ),
                ),
                shape,
            ),
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
    val isLiquidGlass = LocalHazeState.current != null && LocalGlassStyle.current.rendersBlur
    return if (isLiquidGlass) liquid else frosted
}

/**
 * True only for [GlassStyle.ULTRA] (with a registered [LocalHazeState]) --
 * gates the pebble section backgrounds' own real-glass treatment, which is
 * one step further than Liquid glass applies (floating chrome only).
 */
@Composable
fun isUltraGlass(): Boolean = LocalHazeState.current != null && LocalGlassStyle.current == GlassStyle.ULTRA
