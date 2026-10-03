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
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun Modifier.appGlassEffect(state: HazeState, shape: Shape, fadeOut: Boolean = false): Modifier {
    if (shape !is RoundedCornerShape) return this.appHazeEffect(state)
    // A theme-matched backing behind the refracted backdrop, so a floating element is never a black
    // hole where the backdrop has nothing to show (light mode especially).
    // The user's clarity setting drives both the backing's opacity and how much it blurs: frosted is a
    // thick soft pane, clear is a thin one with the refraction showing.
    val clarity = LocalAppearance.current.glassClarity
    val backing = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f - 0.88f * clarity)
    val blur = (18f - 16.5f * clarity).dp
    val style = remember(shape, backing, blur, fadeOut) { glassStyle(shape, backing, blur, fadeOut) }
    return this.hazeGlass(input = HazeInput.Sources(state), style = style)
}

@OptIn(ExperimentalHazeApi::class)
private fun glassStyle(shape: RoundedCornerShape, backing: Color, blur: androidx.compose.ui.unit.Dp, fadeOut: Boolean): GlassStyle = GlassStyle.clear.then {
    // Liquid glass: nearly clear in the middle (a whisper of blur, no milky white lift), with the
    // bending concentrated in a bezel at the edge and a specular glint riding it. Starts from Haze's
    // "clear" material, which keeps the backdrop legible, rather than the frosted "regular" one.
    shape(shape)
    backgroundColor(backing)
    optics(
        GlassOptics(
            refractionStrength = 1f,
            refractionHeightFraction = 0.35f,
            refractionDisplacement = 24.dp,
            depth = OpticalSizeValue.Fixed(1f),
            blurRadius = OpticalSizeValue.Fixed(blur),
            refractionDetailIntensity = 0.7f,
            refractionProfile = RefractionProfile.Edge(18.dp),
            // The status-bar scrim: full strength at the top, thinning to nothing at its bottom edge.
            progressive = if (fadeOut) dev.chrisbanes.haze.HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f) else null,
        ),
    )
    // A visible colour fringe along the edge, where the glass bends the light.
    chromaticAberrationStrength(0.12f)
    chromaticAberrationMode(ChromaticAberrationMode.Simple)
    specularIntensity(0.7f)
    ambientResponse(0.45f)
    whitePoint(0.05f)
    chromaMultiplier(1.1f)
    contrast(0.04f)
}
