package com.bloo.bluelink.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import dev.chrisbanes.haze.HazePerformanceMode
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.ambientRing as sharedAmbientRing
import com.bloo.uicommon.dropShadow
import com.bloo.uicommon.frostedRim as sharedFrostedRim
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Unified glass system: every floating surface takes its tint, blur, rim and shadow from here.
 * [GlassSurface] is the floating panel, [ScrimBlur] the full-screen dim, [pebbleCardEdge] the
 * opaque card edge.
 */

/**
 * Glass edge treatment: shadow + frosted rim. Pass [shadow] = false for glass nested inside an
 * already-elevated card, where a second full shadow reads as a smudge.
 */
@Composable
internal fun Modifier.glassEdge(shape: Shape, shadow: Boolean = true): Modifier =
    (if (shadow) this.glassDropShadow(shape) else this).glassRim(shape)

/**
 * The floating-glass drop shadow, at the weight the CURRENT theme can carry. Every [GlassSurface]
 * in the app goes through here, so that one line put a heavy black silhouette under every floating
 * chip, pill, dialog, status bar, refresh circle and [FloatingIcon] in both themes.
 */
@Composable
private fun Modifier.glassDropShadow(shape: Shape): Modifier =
    if (appIsDarkTheme()) {
        this.dropShadow(shape)
    } else {
        this.dropShadow(shape, color = Color.Black.copy(alpha = 0.06f), blurRadius = 8.dp, offsetY = 1.dp)
    }

/**
 * Rim for glass surfaces: the shared frosted rim with Material's onSurface color. Called by
 * [glassEdge]. Separated for special cases needing just the rim.
 */
@Composable
internal fun Modifier.glassRim(shape: Shape): Modifier =
    this.sharedFrostedRim(shape, MaterialTheme.colorScheme.onSurface)

/**
 * The symmetric ambient halo, at the weight the current theme can carry; see
 * [com.bloo.uicommon.ambientRing]. Stacks with [glassDropShadow], so light mode uses a weaker halo.
 */
@Composable
fun Modifier.ambientRing(shape: Shape): Modifier =
    if (appIsDarkTheme()) {
        this.sharedAmbientRing(shape)
    } else {
        this.dropShadow(shape, color = Color.Black.copy(alpha = 0.05f), blurRadius = 6.dp, offsetY = 0.dp, offsetX = 0.dp)
    }

/**
 * The standard opaque pebble/card edge: an outline always, plus a drop shadow in dark mode.
 */
@Composable
internal fun Modifier.pebbleCardEdge(shape: Shape): Modifier {
    // [appIsDarkTheme], NOT a raw isSystemInDarkTheme() read: a user who set the app to Light while the
    // SYSTEM is dark must get the light treatment.
    val dark = appIsDarkTheme()
    // The outline is always on. Dark adds the drop shadow and a firmer line; light has no shadow at all and
    // a light hairline, so a pale pebble on a pale page still has an edge without anything sticking out.
    val rim = MaterialTheme.colorScheme.outline.copy(alpha = if (dark) 0.55f else 0.30f)
    return (if (dark) this.dropShadow(shape, blurRadius = 12.dp, offsetY = 4.dp) else this)
        .border(BorderStroke(if (dark) 1.dp else 0.75.dp, rim), shape)
}

// ---- One floating-glass surface, everywhere -------------------------------

/**
 * The two alphas every neutral glass fill shares: one for "no real blur behind this" (pre-S,
 * battery saver), a lighter one for when a real blur is doing the legibility work. Theme only
 * changes the base colour.
 */
internal const val GlassTintAlpha = 0.10f

internal const val GlassBlurredTintAlpha = 0.02f

/**
 * Resolves [GlassSurface]'s own fill color -- also called directly by places that can't host
 * [GlassSurface] itself because they don't own their own layout, just a `Color` parameter on a
 * platform composable or a `Modifier.background()`.
 */
@Composable
internal fun glassTint(blurred: Boolean): Color {
    // Resolve dark via the app's override (Theme.kt), not the system setting.
    val dark = appIsDarkTheme()
    return if (dark) {
        val alpha = if (blurred) GlassBlurredTintAlpha else GlassTintAlpha
        Color.White.copy(alpha = alpha)
    } else {
        // Theme-aware surface colour keeps floating buttons from reading as black overlays.
        val scheme = MaterialTheme.colorScheme
        // Blurred needs less alpha; unblurred needs more visual weight.
        val surfaceColor = scheme.surfaceContainer
        val alpha = if (blurred) 0.06f else 0.09f
        surfaceColor.copy(alpha = alpha)
    }
}

 internal fun Modifier.appHazeEffect(state: HazeState, progressive: Boolean = false, cheap: Boolean = false): Modifier =
    this.hazeBlur(
        input = HazeInput.Sources(state),
        style = HazeBlurStyle {
            // [cheap] is for surfaces there are many of at once (every card on screen): a smaller
            // radius is a smaller blur kernel per pixel, and no grain pass at all.
            blurRadius(if (cheap) CardBlurRadius else StandardBlurRadius)
            if (cheap) noiseFactor(0f)
            if (progressive) this.progressive(StandardBlurProgressive)
        },
        // Pinned, not Adaptive: the adaptive mode re-picks the sample resolution as the card moves, so
        // it can change at the start and end of every swipe (a layer re-allocation and a visible
        // re-sample). Cards blur a soft backdrop by 20dp, where half resolution looks the same.
        performanceMode = if (cheap) HazePerformanceMode.Performance else HazePerformanceMode.Balanced,
    )

/**
 * The blurred-background source every card sits on: the app's gradient and aurora, marked as a Haze
 * source in Screens.
 */
/**
 * True while a pebble is lifted, dragged or in flight. A backdrop blur does not follow a scaled,
 * translated layer (the gradient behind it smears), so a floating pebble wears the flat fill and
 * goes back to glass when it is put down.
 */
internal val LocalPebbleFloating = androidx.compose.runtime.staticCompositionLocalOf { false }

internal val LocalBackdropHaze = androidx.compose.runtime.staticCompositionLocalOf<HazeState?> { null }

/**
 * Tint kept by a glass card over the blur: enough to read text on, little enough to look frosted.
 */
private const val GlassCardTintAlpha = 0.26f

/**
 * The one glass-sheen brush: light from the top-left fading across, so a surface reads as glass
 * rather than a flat tint. [glassSheen] lays it behind a surface; the locked-control frost draws the
 * same brush directly, on top of the controls it clouds.
 */
internal fun glassSheenBrush(): Brush = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.09f),
    0.45f to Color.White.copy(alpha = 0.01f),
    1f to Color.White.copy(alpha = 0.04f),
)

/** The shared glass sheen; see [glassSheenBrush]. */
internal fun Modifier.glassSheen(): Modifier = this.background(glassSheenBrush())

/**
 * How much of the cards' blur is showing, as a lambda to read in the DRAW phase (so a change redraws,
 * never recomposes): 0 the instant the car pager starts moving, easing back to 1 once it rests, so the
 * blur fades in rather than popping. 1 outside a pager.
 */
internal val LocalPagerBlur = androidx.compose.runtime.staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * A pebble card's glass fill as the first child of the card, instead of a modifier on it: the same
 * look as the old modifier-based glassCardFill, but the blur layer is skipped while the pager moves. A moving card makes
 * Haze re-capture and re-blur its backdrop every frame (about twenty cards per swipe on a large
 * screen), and over a soft backdrop the unblurred frame is indistinguishable, so the blur simply is
 * not drawn until the page stops. The gate sits OUTSIDE the Haze node on a sibling layer, so the
 * card's own content always draws.
 */
@Composable
internal fun BoxScope.GlassCardBackdrop(shape: Shape, tint: Color) {
    val floating = LocalPebbleFloating.current
    val haze = LocalBackdropHaze.current.takeUnless { floating }
    val ultra = LocalAppearance.current.ultraGlass
    val blur = LocalPagerBlur.current
    if (haze != null && canBlurBackdrops()) {
        Box(
            Modifier
                .matchParentSize()
                // Faded, and not drawn at all at 0: while it is skipped Haze does no capture or blur work.
                .graphicsLayer { alpha = blur() }
                .drawWithContent { if (blur() > 0.01f) drawContent() }
                .clip(shape)
                .then(if (ultra) Modifier.appGlassEffect(haze, shape) else Modifier.appHazeEffect(haze, cheap = true)),
        )
        // The veil and sheen are plain draws and stay on while moving.
        Box(
            Modifier
                .matchParentSize()
                .then(if (ultra) Modifier else Modifier.background(tint.copy(alpha = GlassCardTintAlpha)))
                .glassSheen(),
        )
    } else {
        Box(Modifier.matchParentSize().background(tint.copy(alpha = if (floating) 0.6f else 0.82f), shape))
    }
}

/**
 * The radius every glass surface blurs its backdrop by (Haze 2.0 defaults to 20dp; this matches the
 * tuned look).
 */
private val StandardBlurRadius = 20.dp

/** The blur radius cards use; see [appHazeEffect]'s `cheap`. */
private val CardBlurRadius = 20.dp

/**
 * A drop shadow at the weight the current theme can carry: full strength in dark, a soft contact
 * shadow in light (a heavy black one on a pale surface reads as a smudge).
 */
@Composable
internal fun Modifier.themedDropShadow(
    shape: Shape,
    blurRadius: androidx.compose.ui.unit.Dp = 14.dp,
    offsetY: androidx.compose.ui.unit.Dp = 4.dp,
): Modifier = this.dropShadow(
    shape,
    color = Color.Black.copy(alpha = if (appIsDarkTheme()) 0.38f else 0.06f),
    blurRadius = blurRadius,
    offsetY = offsetY,
)

/**
 * [appHazeEffect] when there is a backdrop to blur AND the device can do it; unchanged otherwise.
 */
@Composable
internal fun Modifier.hazeWhenAble(state: HazeState?, progressive: Boolean = false): Modifier =
    if (state != null && canBlurBackdrops()) appHazeEffect(state, progressive) else this
