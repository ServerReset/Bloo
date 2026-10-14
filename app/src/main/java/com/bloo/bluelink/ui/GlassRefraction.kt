package com.bloo.bluelink.ui

import dev.chrisbanes.haze.HazePerformanceMode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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

/**
 * How wide the app's glass frame is down its sides. It is [ScreenGutter], the app's own margin from
 * the window edge to its content, so the frame sits in the gap rather than over anything.
 */
private val EdgeGlassSideWidth = ScreenGutter

/**
 * The app's OWN glass along the window's edges: one band per side, so the frame refracts what is
 * behind it and its inner boundary carries the same specular rim and colour fringe as every other
 * glass surface in the app.
 *
 * Deliberately the app's ORDINARY glass -- the very same [appGlassEffect] the search panel and the
 * dialogs use -- rather than anything bespoke. The refraction rides the band's boundary, and a band
 * whose outer edge IS the window edge therefore refracts along the app's own edge.
 *
 * The bands live in the app's margin (the content is inset from the window edges by [ScreenGutter]),
 * so nothing is covered. The top band is the status strip.
 */
@Composable
internal fun BoxScope.EdgeGlassFrame(hazeState: HazeState?) {
    if (hazeState == null) return
    // A plain rectangle: the boundary the refraction rides is the band's own.
    val shape = RoundedCornerShape(0.dp)
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // TOP: the status bar's frost (strong at the very top, a little at its bottom), then a thin band of liquid
    // glass just under it, so the top edge bends like the other three.
    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
        StatusBarScrim(hazeState)
        Box(
            Modifier.padding(top = top).fillMaxWidth().height(EdgeGlassRimHeight)
                .appGlassEffect(hazeState, shape, clear = true),
        )
    }
    // BOTTOM and SIDES: liquid glass, the window edge as the band's outer boundary.
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(maxOf(bottom, EdgeGlassRimHeight))
            .appGlassEffect(hazeState, shape, clear = true),
    )
    Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(EdgeGlassSideWidth).appGlassEffect(hazeState, shape, clear = true))
    Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(EdgeGlassSideWidth).appGlassEffect(hazeState, shape, clear = true))
}

/** The liquid band along the top (under the status bar) and the bottom when there is no navigation bar. */
private val EdgeGlassRimHeight = 14.dp

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
