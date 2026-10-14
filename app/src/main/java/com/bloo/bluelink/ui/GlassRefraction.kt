package com.bloo.bluelink.ui

import dev.chrisbanes.haze.HazePerformanceMode
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.RefractionProfile
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * Real glass: the backdrop is blurred AND refracted. Light bends where the pane curves, so what is
 * behind the element is displaced and split into its colours along the edge, with a specular rim on
 * top.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun Modifier.appGlassEffect(
    state: HazeState,
    shape: Shape,
    /** Bare glass: no backing and the clearest blur, so only the bending rim and the specular show. */
    clear: Boolean = false,
): Modifier {
    if (shape !is RoundedCornerShape) return this.appHazeEffect(state)
    // A theme-matched backing behind the refracted backdrop, so a floating element is never a black
    // hole where the backdrop has nothing to show (light mode especially). The user's clarity
    // setting drives both the backing's opacity and how much it blurs: frosted is a thick soft
    // pane, clear is a thin one with the refraction showing.
    val appearance = LocalAppearance.current
    // Ultra glass: no backing at all (pure refraction) and the clearest possible blur, so the
    // material reads as bare glass over whatever is behind it.
    val ultra = appearance.ultraGlass
    val clarity = if (ultra || clear) 1f else appearance.glassClarity
    // `surface` matches the app's own base tone, so the backing only ever lifts or matches, never
    // darkens. Its alpha still falls to ~0 at high clarity, so clear glass has no tint at all.
    val backing = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f * (1f - clarity))
    val blur = (18f - 16.5f * clarity).dp
    val style = remember(shape, backing, blur, ultra) { glassStyle(shape, backing, blur, ultra) }
    // A fixed middle quality rather than Adaptive, so the resolution cannot change mid-gesture.
    return this.hazeGlass(input = HazeInput.Sources(state), style = style, performanceMode = HazePerformanceMode.Balanced)
}

/**
 * The app's screen-edge glass: ONE render pass that BENDS the content into the frame at every edge --
 * top, bottom, left and right -- and FROSTS it only in the top band, behind the status bar.
 *
 * The frost is PROGRESSIVE down that band: strongest at the very top, down to a little by the bottom of
 * the status bar, and clear just below it -- so the icons sit on a frosted layer without the strip
 * reading as a bar. The other three edges bend only; they never frost.
 *
 * It is a RENDER EFFECT on the content layer, deliberately NOT a Haze glass panel. A glass node DRAWS
 * a copy of the captured source, so a full-window one covers everything under it (it did: the pebbles'
 * refraction was redrawn over the full-screen map). A render effect samples the content the layer has
 * already drawn and displaces/blurs it, so nothing is drawn on top, there is no source capture, and it
 * is ONE pass rather than one per card. The backdrop is inside this layer too, so the band behind the
 * status bar samples IT.
 *
 * Applied to the app's content and UNDER the floating chrome, so the search, the toasts, the dialogs
 * and the lock overlay are never bent. Light by default; turned up under Ultra glass. Off below API 33
 * (RuntimeShader) and whenever the caller passes [enabled] false (battery saver).
 */
private const val EDGE_GLASS_AGSL = """
    uniform shader content;
    uniform float2 size;
    uniform float topBand;
    uniform float edgeBand;
    uniform float strength;
    uniform float frost;
    uniform float frostSpan;

    half4 main(float2 coord) {
        float2 d = min(coord, size - coord);
        float m = min(d.x, d.y);
        // The top band is taller: it has to cover the status bar and its icons.
        float band = (m == coord.y) ? topBand : edgeBand;
        float t = clamp(1.0 - m / max(band, 1.0), 0.0, 1.0);
        float t2 = t * t;
        float2 dir;
        if (d.x < d.y) {
            dir = float2(coord.x < size.x * 0.5 ? -1.0 : 1.0, 0.0);
        } else {
            dir = float2(0.0, coord.y < size.y * 0.5 ? -1.0 : 1.0);
        }
        float2 hi = size - float2(1.0);
        // Clamped to the layer: a bend toward the edge samples PAST it, and a sample outside the layer
        // reads black -- which is exactly the black band the edges had.
        float2 base = clamp(coord + dir * (strength * t2), float2(0.0), hi);
        // Frost ONLY in the top band, tapering down it: full at the very top, a little by the bottom of
        // the status bar, gone just below. The sides and the bottom never frost.
        float fade = clamp(1.0 - coord.y / max(frostSpan, 1.0), 0.0, 1.0);
        float r = frost * fade;
        // A five-tap frost, weighted so the middle of the screen is untouched.
        half4 c = content.eval(base) * 0.36;
        c += content.eval(clamp(base + float2(r, 0.0), float2(0.0), hi)) * 0.16;
        c += content.eval(clamp(base - float2(r, 0.0), float2(0.0), hi)) * 0.16;
        c += content.eval(clamp(base + float2(0.0, r), float2(0.0), hi)) * 0.16;
        c += content.eval(clamp(base - float2(0.0, r), float2(0.0), hi)) * 0.16;
        return c;
    }
"""

/** How far the bend reaches on the sides and the bottom, and how hard, in dp -- light by default, extreme ultra. */
private const val EdgeGlassEdgeBandDp = 22f
private const val EdgeGlassStrengthDp = 10f

/** How hard the top frost is at its strongest, in dp. */
private const val EdgeGlassFrostDp = 16f

/**
 * How far past the status bar the frost's taper reaches. At the bar's own bottom edge the frost is
 * therefore `1 - 1/overshoot` of its full strength -- "a little", as asked -- and it clears just below.
 */
private const val EdgeGlassFrostOvershoot = 1.2f

@Composable
internal fun Modifier.edgeGlass(enabled: Boolean, ultra: Boolean, topBandDp: Float, statusBarDp: Float): Modifier {
    if (!enabled || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return this
    val shader = remember { android.graphics.RuntimeShader(EDGE_GLASS_AGSL) }
    return this.graphicsLayer {
        shader.setFloatUniform("size", size.width.toFloat(), size.height.toFloat())
        shader.setFloatUniform("topBand", topBandDp.dp.toPx())
        shader.setFloatUniform("edgeBand", EdgeGlassEdgeBandDp.dp.toPx())
        shader.setFloatUniform("strength", (if (ultra) EdgeGlassStrengthDp * 2.6f else EdgeGlassStrengthDp).dp.toPx())
        shader.setFloatUniform("frost", (if (ultra) EdgeGlassFrostDp * 1.6f else EdgeGlassFrostDp).dp.toPx())
        shader.setFloatUniform("frostSpan", (statusBarDp * EdgeGlassFrostOvershoot).dp.toPx())
        renderEffect = android.graphics.RenderEffect
            .createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
    }
}

@OptIn(ExperimentalHazeApi::class)
private fun glassStyle(
    shape: RoundedCornerShape,
    backing: Color,
    blur: androidx.compose.ui.unit.Dp,
    ultra: Boolean,
): GlassStyle = GlassStyle.clear.then {
    // Liquid glass: nearly clear in the middle (a whisper of blur, no milky white lift), with the
    // bending riding the material and a specular glint on top.
    shape(shape)
    backgroundColor(backing)
    optics(
        GlassOptics(
            // The bending rides a bezel at the shape's own boundary; Ultra pushes every part of it to its ceiling.
            refractionStrength = if (ultra) 1f else 0.85f,
            refractionHeightFraction = 0.35f,
            refractionDisplacement = if (ultra) 36.dp else 18.dp,
            depth = OpticalSizeValue.Fixed(1f),
            blurRadius = OpticalSizeValue.Fixed(blur),
            refractionFoldStrength = 0f,
            refractionDetailIntensity = 0.7f,
            refractionProfile = RefractionProfile.Edge(if (ultra) 30.dp else 16.dp),
        ),
    )
    // A visible colour fringe along the edge, where the glass bends the light.
    chromaticAberrationStrength(if (ultra) 1f else 0.4f)
    chromaticAberrationMode(ChromaticAberrationMode.Simple)
    specularIntensity(if (ultra) 1f else 0.7f)
    ambientResponse(0.45f)
    whitePoint(0.05f)
    chromaMultiplier(1.1f)
    contrast(0.04f)
}
