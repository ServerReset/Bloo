package com.bloo.bluelink.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.dropShadow
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import com.bloo.uicommon.frostedRim as sharedFrostedRim
import com.bloo.uicommon.ambientRing as sharedAmbientRing

/**
 * Unified glass system: every floating surface takes its tint, blur, rim and shadow from here.
 * [GlassSurface] is the floating panel, [ScrimBlur] the full-screen dim, [pebbleCardEdge] the opaque card edge.
 * Shared helpers live in :uicommon; this file adds the Material theme tint they can't depend on.
 */

/**
 * Glass edge treatment: shadow + frosted rim.
 * Pass [shadow] = false for glass nested inside an already-elevated card, where a second full shadow reads as a smudge.
 */
@Composable
internal fun Modifier.glassEdge(shape: Shape, shadow: Boolean = true): Modifier =
    (if (shadow) this.glassDropShadow(shape) else this).glassRim(shape)


/**
 * The floating-glass drop shadow, at the weight the current theme can carry.
 *
 * Light mode keeps a soft contact shadow (a heavy black one under a near-transparent fill reads as a
 * smudge) because the shape floats over an arbitrary backdrop. Dark mode uses `dropShadow` defaults.
 */
@Composable
private fun Modifier.glassDropShadow(shape: Shape): Modifier =
    if (appIsDarkTheme()) {
        this.dropShadow(shape)
    } else {
        this.dropShadow(shape, color = Color.Black.copy(alpha = 0.12f), blurRadius = 10.dp, offsetY = 2.dp)
    }


/**
 * Rim for glass surfaces: the shared frosted rim with Material's onSurface color.
 * Called by [glassEdge]. Separated for special cases needing just the rim.
 */
@Composable
internal fun Modifier.glassRim(shape: Shape): Modifier =
    this.sharedFrostedRim(shape, MaterialTheme.colorScheme.onSurface)


/**
 * The symmetric ambient halo, at the weight the current theme can carry; see [com.bloo.uicommon.ambientRing].
 *
 * Stacks with [glassDropShadow], so light mode uses a weaker halo. The shared :uicommon version stays
 * theme-free because it can't see [LocalAppearance].
 */
@Composable
fun Modifier.ambientRing(shape: Shape): Modifier =
    if (appIsDarkTheme()) {
        this.sharedAmbientRing(shape)
    } else {
        this.dropShadow(shape, color = Color.Black.copy(alpha = 0.10f), blurRadius = 7.dp, offsetY = 0.dp, offsetX = 0.dp)
    }


/**
 * The standard opaque pebble/card edge: a drop shadow, plus a solid border when the "pebble outline" setting is on.
 * Dark mode only draws the shadow; in light mode the outline provides the separation.
 */
@Composable
internal fun Modifier.pebbleCardEdge(shape: Shape, outline: Boolean): Modifier {
    // Uses the app's own dark override, not the system setting.
    val dark = appIsDarkTheme()
    return (if (dark) this.dropShadow(shape, blurRadius = 12.dp, offsetY = 4.dp) else this).then(
        if (outline) {
            Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)), shape)
        } else {
            Modifier
        },
    )
}


// ---- One floating-glass surface, everywhere -------------------------------

/**
 * The two alphas every neutral glass fill shares: one for "no real blur behind this" (pre-S, battery saver),
 * a lighter one for when a real blur is doing the legibility work. Theme only changes the base colour.
 */
internal const val GlassTintAlpha = 0.10f

internal const val GlassBlurredTintAlpha = 0.02f


/**
 * Resolves [GlassSurface]'s fill color; also used where [GlassSurface] can't be hosted.
 *
 * Plain black/white by theme, not a tonal `colorScheme` role (see [MetaChip]): the dynamic palette tints
 * even the neutral roles. [blurred] defaults to whether a real blur plays behind this fill.
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


/**
 * Glass effect: blur (if possible) plus tint in one modifier, for surfaces that can't use [GlassSurface].
 * [progressive] fades the blur across the shape; only full-height scrims use it, since on a small chip it
 * leaves half the shape barely blurred.
 */
@Composable
internal fun Modifier.glassEffect(
    hazeState: HazeState?,
    progressive: Boolean = false,
): Modifier {
    val canBlur = hazeState != null && canBlurBackdrops()
    val tint = glassTint(canBlur)
    return this
        .hazeWhenAble(hazeState, progressive)
        .background(tint)
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
    )


/**
 * The blurred-background source every card sits on: the app's gradient and aurora, marked as a Haze
 * source in Screens. Null where there is no such backdrop (a preview, a secondary window), in which
 * case cards fall back to a nearly opaque fill.
 */
internal val LocalBackdropHaze = androidx.compose.runtime.staticCompositionLocalOf<HazeState?> { null }


/** Tint kept by a glass card over the blur: enough to read text on, little enough to look frosted. */
private const val GlassCardTintAlpha = 0.26f


/** The shared glass sheen: light from the top-left fading across, so a surface reads as glass rather than a flat tint. */
internal fun Modifier.glassSheen(): Modifier = this.background(
    androidx.compose.ui.graphics.Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.09f),
        0.45f to Color.White.copy(alpha = 0.01f),
        1f to Color.White.copy(alpha = 0.04f),
    ),
)

/**
 * The standard card fill: the backdrop blurred behind a veil of [tint], while the card's contents keep
 * their own opaque panels. Falls back to a nearly opaque fill without a backdrop.
 */
@Composable
internal fun Modifier.glassCardFill(shape: Shape, tint: Color): Modifier {
    val haze = LocalBackdropHaze.current
    val ultra = LocalAppearance.current.ultraGlass
    // Ultra glass uses the liquid-glass effect, which needs a backdrop to refract; otherwise it falls back.
    return if (ultra && haze != null && canBlurBackdrops()) {
        this.clip(shape)
            .appGlassEffect(haze, shape)
            .glassSheen()
    } else if (haze != null && canBlurBackdrops()) {
        this.clip(shape)
            .appHazeEffect(haze, cheap = true)
            .background(tint.copy(alpha = GlassCardTintAlpha))
            .glassSheen()
    } else {
        this.background(tint.copy(alpha = 0.82f), shape)
    }
}


/** The radius every glass surface blurs its backdrop by (Haze 2.0 defaults to 20dp; this matches the tuned look). */
private val StandardBlurRadius = 20.dp

/** The blur radius cards use; see [appHazeEffect]'s `cheap`. */
private val CardBlurRadius = 20.dp


/**
 * A drop shadow at the weight the current theme can carry: full strength in dark, a soft contact
 * shadow in light (a heavy black one on a pale surface reads as a smudge). For lifted chips and
 * dragged rows; floating glass uses [glassEdge].
 */
@Composable
internal fun Modifier.themedDropShadow(
    shape: Shape,
    blurRadius: androidx.compose.ui.unit.Dp = 14.dp,
    offsetY: androidx.compose.ui.unit.Dp = 4.dp,
): Modifier = this.dropShadow(
    shape,
    color = Color.Black.copy(alpha = if (appIsDarkTheme()) 0.38f else 0.12f),
    blurRadius = blurRadius,
    offsetY = offsetY,
)


/** [appHazeEffect] when there is a backdrop to blur AND the device can do it; unchanged otherwise. */
@Composable
internal fun Modifier.hazeWhenAble(state: HazeState?, progressive: Boolean = false): Modifier =
    if (state != null && canBlurBackdrops()) appHazeEffect(state, progressive) else this
