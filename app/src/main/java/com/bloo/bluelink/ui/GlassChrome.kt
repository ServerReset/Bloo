package com.bloo.bluelink.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import dev.chrisbanes.haze.HazePerformanceMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.foundation.background
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
import com.bloo.uicommon.canBlurBackdrops

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
    return (if (dark) this.dropShadow(shape, blurRadius = 14.dp, offsetY = 5.dp) else this)
        .liquidRim(shape, dark)
}

/**
 * The liquid-glass edge: a thin line brightest where light would catch it (the top-left), fading through the
 * middle to a fainter catch at the bottom-right, plus a soft inner glow along the top lip. It is what makes a
 * frosted card read as a pane of glass lying OVER the page, and it is a static draw (one stroke, one gradient
 * per size), so every card can wear it.
 */
@Composable
internal fun Modifier.liquidRim(shape: Shape, dark: Boolean = appIsDarkTheme()): Modifier {
    val scheme = MaterialTheme.colorScheme
    val highlight = Color.White
    val edge = scheme.outline
    return this.drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val line = 1.25.dp.toPx()
        val rim = androidx.compose.ui.graphics.Brush.linearGradient(
            0f to highlight.copy(alpha = if (dark) 0.62f else 0.9f),
            0.38f to edge.copy(alpha = if (dark) 0.22f else 0.20f),
            0.62f to edge.copy(alpha = if (dark) 0.14f else 0.16f),
            1f to highlight.copy(alpha = if (dark) 0.28f else 0.55f),
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset(size.width, size.height),
        )
        val lip = androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to highlight.copy(alpha = if (dark) 0.14f else 0.35f),
            1f to Color.Transparent,
            endY = minOf(size.height, 28.dp.toPx()),
        )
        onDrawWithContent {
            drawContent()
            // The lip is clipped to the shape's outline by drawing it as the outline's own fill.
            drawOutline(outline, lip)
            drawOutline(outline, rim, style = androidx.compose.ui.graphics.drawscope.Stroke(line))
        }
    }
}

// ---- One floating-glass surface, everywhere -------------------------------

/**
 * The two alphas every neutral glass fill shares: one for "no real blur behind this" (pre-S,
 * battery saver), a lighter one for when a real blur is doing the legibility work. Theme only
 * changes the base colour.
 */


/**
 * Resolves [GlassSurface]'s own fill color -- also called directly by places that can't host
 * [GlassSurface] itself because they don't own their own layout, just a `Color` parameter on a
 * platform composable or a `Modifier.background()`.
 */
@Composable

internal fun Modifier.appHazeEffect(
    state: HazeState,
    progressive: Boolean = false,
    cheap: Boolean = false,
    /** With [progressive]: how much blur is left at the far end (0 fades to none); negative uses the standard taper. */
    progressiveEnd: Float = -1f,
): Modifier =
    this.hazeBlur(
        input = HazeInput.Sources(state),
        style = HazeBlurStyle {
            // [cheap] is for surfaces there are many of at once (every card on screen): a smaller
            // radius is a smaller blur kernel per pixel, and no grain pass at all.
            blurRadius(if (cheap) CardBlurRadius else StandardBlurRadius)
            if (cheap) noiseFactor(0f)
            if (progressive) {
                this.progressive(
                    if (progressiveEnd < 0f) StandardBlurProgressive
                    else dev.chrisbanes.haze.HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = progressiveEnd),
                )
            }
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
private const val GlassCardTintAlpha = 0.34f

/**
 * The one glass-sheen brush: light from the top-left fading across, so a surface reads as glass
 * rather than a flat tint. [glassSheen] lays it behind a surface; the locked-control frost draws the
 * same brush directly, on top of the controls it clouds.
 *
 * ONE shared instance: the gradient is immutable and identical every time, and every glass card asks
 * for it on every composition -- so building a fresh brush each call was pure per-card allocation.
 */
private val GlassSheen = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.09f),
    0.45f to Color.White.copy(alpha = 0.01f),
    1f to Color.White.copy(alpha = 0.04f),
)

internal fun glassSheenBrush(): Brush = GlassSheen

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
    // A pebble in the air is scaled and translated, and a backdrop blur does not follow a moving
    // layer cleanly (it smears), so while lifted it wears a flat fill instead -- but a genuinely
    // TRANSPARENT one, so it still reads as glass rather than a solid card.
    val haze = LocalBackdropHaze.current.takeUnless { floating }
    val ultra = LocalAppearance.current.ultraGlass
    val blur = LocalPagerBlur.current
    val drawBlur = androidx.compose.runtime.remember(blur) { androidx.compose.runtime.derivedStateOf { blur() > 0.01f } }
    if (haze != null && canBlurBackdrops()) {
        Box(
            Modifier
                .matchParentSize()
                // Faded, and not drawn at all at 0: while it is skipped Haze does no capture or blur work.
                // The fade lives in the layer (a re-composite, not a redraw); only crossing the on/off
                // threshold invalidates the draw. Reading blur() in drawWithContent made every card re-capture
                // and re-blur its backdrop on every frame of the post-swipe fade.
                .graphicsLayer {
                    alpha = blur()
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                }
                .drawWithContent { if (drawBlur.value) drawContent() }
                .clip(shape)
                // ALWAYS the cheap blur, even in Ultra glass. A card is large and flat: the liquid
                // refraction's edge bend is lost across it, but the displacement + chromatic pass is
                // paid in full, on the most numerous glass nodes on the screen and again on every
                // tick the aurora moves. Refraction stays where it reads: the floating surfaces.
                .appHazeEffect(haze, cheap = true),
        )
        // The veil and sheen are plain draws and stay on while moving.
        Box(
            Modifier
                .matchParentSize()
                .then(if (ultra) Modifier else Modifier.background(tint.copy(alpha = GlassCardTintAlpha)))
                .glassSheen(),
        )
    } else {
        Box(
            Modifier
                .matchParentSize()
                // 0.30 while lifted, not 0.6: a picked-up pebble has to stay clearly see-through.
                .background(tint.copy(alpha = if (floating) 0.30f else 0.82f), shape)
                .glassSheen(),
        )
    }
}

/**
 * The radius every glass surface blurs its backdrop by (Haze 2.0 defaults to 20dp; this matches the
 * tuned look).
 */
private val StandardBlurRadius = 20.dp

/** The blur radius cards use; see [appHazeEffect]'s `cheap`. */
private val CardBlurRadius = 22.dp

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
