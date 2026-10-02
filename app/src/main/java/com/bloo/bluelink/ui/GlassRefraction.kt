package com.bloo.bluelink.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
 * behind a card is displaced and split into its colours along the edge, with a specular rim on top.
 * Haze's glass effect does this in an AGSL shader on Android 13+, and on older devices it falls back
 * to its own simplified renderer, so every caller gets the best the device can do.
 *
 * Only rounded-rectangle shapes can be refracted (a circle counts); anything else keeps the plain
 * blur. [card] picks the wider, stronger bezel big cards carry over the tighter one chips and pills
 * use, because a bezel as wide as a chip would refract its whole face.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun Modifier.appGlassEffect(state: HazeState, shape: Shape, card: Boolean): Modifier {
    if (shape !is RoundedCornerShape) return this.appHazeEffect(state, cheap = card)
    val style = remember(shape, card) { glassStyle(shape, card) }
    return this.hazeGlass(input = HazeInput.Sources(state), style = style)
}

@OptIn(ExperimentalHazeApi::class)
private fun glassStyle(shape: RoundedCornerShape, card: Boolean): GlassStyle = GlassStyle.clear.then {
    // Liquid glass: nearly clear in the middle (a whisper of blur, no milky white lift), with the
    // bending concentrated in a bezel at the edge and a specular glint riding it. Starts from Haze's
    // "clear" material, which keeps the backdrop legible, rather than the frosted "regular" one.
    shape(shape)
    optics(
        GlassOptics(
            refractionStrength = 1f,
            refractionHeightFraction = 0.35f,
            refractionDisplacement = if (card) 40.dp else 16.dp,
            depth = OpticalSizeValue.Fixed(1f),
            blurRadius = OpticalSizeValue.Fixed(if (card) 3.dp else 2.dp),
            refractionDetailIntensity = if (card) 0.8f else 0.5f,
            refractionProfile = RefractionProfile.Edge(if (card) 30.dp else 12.dp),
        ),
    )
    chromaticAberrationStrength(if (card) 0.07f else 0.05f)
    chromaticAberrationMode(ChromaticAberrationMode.Simple)
    specularIntensity(0.7f)
    ambientResponse(0.45f)
    whitePoint(0.05f)
    chromaMultiplier(1.1f)
    contrast(0.04f)
}
