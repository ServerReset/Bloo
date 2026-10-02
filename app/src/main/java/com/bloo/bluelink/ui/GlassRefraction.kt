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
private fun glassStyle(shape: RoundedCornerShape, card: Boolean): GlassStyle = GlassStyle.regular then {
    shape(shape)
    optics(
        GlassOptics(
            refractionStrength = if (card) 0.85f else 0.7f,
            refractionHeightFraction = 0.3f,
            refractionDisplacement = if (card) 26.dp else 12.dp,
            depth = OpticalSizeValue.Fixed(1f),
            blurRadius = OpticalSizeValue.Fixed(if (card) 18.dp else 16.dp),
            refractionDetailIntensity = if (card) 0.35f else 0f,
            refractionProfile = RefractionProfile.Edge(if (card) 22.dp else 10.dp),
        ),
    )
    chromaticAberrationStrength(if (card) 0.05f else 0.03f)
    chromaticAberrationMode(ChromaticAberrationMode.Simple)
    specularIntensity(0.4f)
    ambientResponse(0.2f)
}
