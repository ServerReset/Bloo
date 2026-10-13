package com.bloo.bluelink.ui

import dev.chrisbanes.haze.HazePerformanceMode
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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

/**
 * The app's screen-edge warp: the liquid-glass REFRACTION along every edge of the window -- top,
 * bottom, left and right together -- with NO blur at all, so the content bends into the frame at the
 * very edges without frosting them. Subtle by default and turned up to extreme under Ultra glass.
 *
 * The caller gates this on [canBlurBackdrops], so it is off under battery saver (and pre-S), and it
 * is drawn under the floating chrome (search, toasts, dialogs) so it never warps them. One
 * full-screen node: a shape's refraction rides its own boundary, and a full-window shape has exactly
 * the four edges we want -- an edge *band* would bend at its inner edge too and leave a seam.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun Modifier.screenEdgeWarp(state: HazeState, ultra: Boolean): Modifier {
    val style = remember(ultra) { glassEdgeWarpStyle(ultra) }
    // Performance, not Balanced: this is a full-window pass that must stay as cheap as it can, and a
    // pure refraction has no fine blur detail to lose.
    return this.hazeGlass(input = HazeInput.Sources(state), style = style, performanceMode = HazePerformanceMode.Performance)
}

@OptIn(ExperimentalHazeApi::class)
private fun glassEdgeWarpStyle(ultra: Boolean): GlassStyle = GlassStyle.clear.then {
    // A full-window rectangle: its boundary is the screen's own four edges.
    shape(RoundedCornerShape(0.dp))
    backgroundColor(Color.Transparent)
    optics(
        GlassOptics(
            refractionStrength = if (ultra) 1f else 0.4f,
            // Only the very edges, so the middle of the screen is untouched.
            refractionHeightFraction = if (ultra) 0.06f else 0.035f,
            refractionDisplacement = if (ultra) 44.dp else 16.dp,
            depth = OpticalSizeValue.Fixed(1f),
            // No blur: this is purely the warp.
            blurRadius = OpticalSizeValue.Fixed(0.dp),
            refractionFoldStrength = 0f,
            refractionDetailIntensity = if (ultra) 1f else 0.45f,
            refractionProfile = RefractionProfile.Edge(if (ultra) 36.dp else 14.dp),
        ),
    )
    chromaticAberrationStrength(if (ultra) 0.9f else 0.25f)
    chromaticAberrationMode(ChromaticAberrationMode.Simple)
    // No specular and no backing: nothing but the bend.
    specularIntensity(0f)
}
