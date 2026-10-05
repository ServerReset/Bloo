package com.bloo.bluelink.ui

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
 * behind the element is displaced and split into its colours along the edge, with a specular rim on top.
 * Haze's glass effect does this in an AGSL shader on Android 13+, and on older devices it falls back
 * to its own simplified renderer, so every caller gets the best the device can do.
 *
 * For FLOATING elements only (chips, pills, dialogs, the search bar); cards keep the flat blur. Only
 * rounded-rectangle shapes can be refracted (a circle counts); anything else keeps the plain blur.
 *
 * [edgeWarp] picks the refraction profile:
 *  - true (default): [RefractionProfile.Edge], bending concentrated in a bezel at the shape's own
 *    boundary. Right for a small chip/pill, where the eye reads the curve at its rim.
 *  - false: [RefractionProfile.Surface], which refracts across the WHOLE surface, scaled by
 *    `refractionHeightFraction` of the shape's shortest side. Right for the full-width status-bar
 *    scrim: its "edge" is a straight line the user never sees curve, so the Edge profile bent light
 *    only in a hairline at the top/bottom and left the middle flat, which is why the top bar stopped
 *    looking like glass at all. Surface warps the entire strip behind the status bar.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun Modifier.appGlassEffect(
    state: HazeState,
    shape: Shape,
    fadeOut: Boolean = false,
    edgeWarp: Boolean = true,
): Modifier {
    if (shape !is RoundedCornerShape) return this.appHazeEffect(state)
    // A theme-matched backing behind the refracted backdrop, so a floating element is never a black
    // hole where the backdrop has nothing to show (light mode especially).
    // The user's clarity setting drives both the backing's opacity and how much it blurs: frosted is a
    // thick soft pane, clear is a thin one with the refraction showing.
    val appearance = LocalAppearance.current
    // Ultra glass: no backing at all (pure refraction) and the clearest possible blur, so the
    // material reads as bare glass over whatever is behind it. Clarity is still respected for
    // the blur radius's lower bound in normal mode; ultra just pins it to its clearest end.
    val ultra = appearance.ultraGlass
    val clarity = if (ultra) 1f else appearance.glassClarity
    // `surface`, not `surfaceContainer`: surfaceContainer is a DARKER tonal step in both themes,
    // so using it as the backing cast a grey/dark tint over the app's light empty background
    // (where the Aurora sits) -- the reported "liquid glass is darker over the empty background".
    // `surface` matches the app's own base tone, so the backing only ever lifts or matches, never
    // darkens. Its alpha still falls to ~0 at high clarity, so clear glass has no tint at all.
    val backing = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f * (1f - clarity))
    val blur = (18f - 16.5f * clarity).dp
    val style = remember(shape, backing, blur, fadeOut, edgeWarp, ultra) {
        glassStyle(shape, backing, blur, fadeOut, edgeWarp, ultra)
    }
    return this.hazeGlass(input = HazeInput.Sources(state), style = style)
}

@OptIn(ExperimentalHazeApi::class)
private fun glassStyle(
    shape: RoundedCornerShape,
    backing: Color,
    blur: androidx.compose.ui.unit.Dp,
    fadeOut: Boolean,
    edgeWarp: Boolean,
    ultra: Boolean,
): GlassStyle = GlassStyle.clear.then {
    // Liquid glass: nearly clear in the middle (a whisper of blur, no milky white lift), with the
    // bending riding the material and a specular glint on top. Starts from Haze's "clear" material,
    // which keeps the backdrop legible, rather than the frosted "regular" one.
    shape(shape)
    backgroundColor(backing)
    optics(
        GlassOptics(
            // Ultra pushes the refraction to its ceiling even on the edge profile, so the
            // bending reads strongly across a small chip too, not just at its rim.
            refractionStrength = if (ultra) 1f else if (edgeWarp) 0.85f else 1f,
            // Surface profile scales its refraction by this fraction of the SHORTEST side, so on a
            // ~status-bar-height strip a large fraction is what makes the warp read across the whole
            // bar instead of a faint ripple. Edge ignores it for refraction (lighting only).
            refractionHeightFraction = if (edgeWarp) 0.35f else 1f,
            refractionDisplacement = if (edgeWarp) 18.dp else 32.dp,
            depth = OpticalSizeValue.Fixed(1f),
            blurRadius = OpticalSizeValue.Fixed(blur),
            // Fold + detail to max on the surface warp so the bend is pronounced, not a gentle haze.
            refractionFoldStrength = if (edgeWarp) 0f else 0.6f,
            refractionDetailIntensity = if (edgeWarp) 0.7f else 1f,
            refractionProfile = if (edgeWarp) RefractionProfile.Edge(16.dp) else RefractionProfile.Surface,
            // The status-bar scrim: full strength at the top, thinning to nothing at its bottom edge.
            progressive = if (fadeOut) dev.chrisbanes.haze.HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f) else null,
        ),
    )
    // A visible colour fringe along the edge, where the glass bends the light.
    chromaticAberrationStrength(0.4f)
    chromaticAberrationMode(ChromaticAberrationMode.Simple)
    specularIntensity(0.7f)
    ambientResponse(0.45f)
    whitePoint(0.05f)
    chromaMultiplier(1.1f)
    contrast(0.04f)
}
