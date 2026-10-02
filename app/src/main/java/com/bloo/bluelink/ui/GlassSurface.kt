package com.bloo.bluelink.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import com.bloo.uicommon.dropShadow
import dev.chrisbanes.haze.HazeState

/**
 * The app's one floating-glass surface: a real Haze backdrop blur of [hazeState]
 * when one is given and [canBlurBackdrops] allows it (API 31+, battery saver off),
 * layered under a neutral [tint], inside the shared [dropShadow]/[appGlassRim] edge
 * treatment -- all wrapped in one function instead of separately hand-rolled at
 * every call site.
 *
 * This is the exact Box-over-Box structure that used to be independently copied,
 * with small unintentional drifts between the copies, at every floating pill/circle/
 * chip in the app: [FloatingIcon]'s button fill, [MetaChip], the grid layout's own
 * pull-to-refresh circle (GarageScreen.kt), and the map sheet's vehicle-name pill,
 * refresh chip and drag-handle chip (WeatherPebble.kt, the last of those duplicated
 * across the pebble AND the full-screen map besides). Two of those five never
 * actually got a real blur layer at all, and three of them still carried the exact
 * `surfaceContainerHighest`-based fill already reported and fixed elsewhere in this
 * app for reading as flatly blue under this app's dynamic palette -- both were
 * copy/paste gaps, not deliberate differences, which is what asking for ONE shared
 * implementation instead of five hand-rolled ones actually fixes: a future change
 * to how this looks (or a future bug in it) now has exactly one place to make or fix.
 *
 * [modifier] carries this surface's size, position and (via [onClick]) interaction --
 * attach padding/size/align to it the same way you would a plain `Box`. The edge
 * treatment and fill are always applied here, in the same order, so no call site can
 * drift out of sync with another on layering.
 *
 * [onClick] is optional; non-null adds a ripple clipped to [shape], the same
 * click affordance `Surface` gave every call site this replaces. [interactionSource]
 * lets a caller that needs the press state itself (FloatingIcon's own press-scale
 * spring) supply and read its own, instead of one this function would otherwise
 * create and keep private -- passing nothing still gets a working ripple.
 */
@Composable
internal fun GlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    tint: Color = glassTint(hazeState != null && canBlurBackdrops()),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    interactionSource: MutableInteractionSource? = null,
    contentAlignment: Alignment = Alignment.Center,
    /** See [glassEdge]'s own doc -- false for a glass panel nested inside
     *  another already-elevated card instead of genuinely floating over the
     *  screen. */
    shadow: Boolean = true,
    /** Real liquid glass (see [LiquidGlassLayer]) instead of the flat blur. Floating elements want
     *  it; a large card or a panel nested in one wants the flat blur, so it follows [shadow] by
     *  default (nested = no shadow) and a card that floats on its own passes false. */
    liquid: Boolean = shadow,
    content: @Composable () -> Unit = {},
) {
    val canBlur = hazeState != null && canBlurBackdrops()
    val liquid = liquid && rememberLiquidGlassSupported()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .glassEdge(shape, shadow = shadow)
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .clickable(
                            interactionSource = interaction,
                            indication = ripple(),
                            onClickLabel = contentDescription,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            ),
        contentAlignment = contentAlignment,
    ) {
        // One node for blur+tint, not two -- the blur and the fill draw in the same
        // Box's own modifier chain (blur first, tint layered on top of it, same as
        // stacking two separate Boxes would), the same single-node pattern the
        // map's own drag-handle chip already used successfully. Every call site of
        // this function gets that one fewer layout node for free.
        Box(
            Modifier
                .matchParentSize()
                .clip(shape)
                // Liquid glass where the device can run it (it blurs and refracts the window behind
                // it itself); the plain Haze blur everywhere else.
                .then(if (canBlur && !liquid) Modifier.appHazeEffect(hazeState!!) else Modifier)
                .background(tint)
                .glassSheen(),
        )
        if (liquid) LiquidGlassLayer(shape, Modifier.matchParentSize())
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}


/**
 * The full-screen dim+blur scrim behind an expanded map sheet -- shared by
 * [ExpandableMapLayer] and [CarMapSheetBody] (WeatherPebble.kt), which used to each
 * carry a byte-for-byte identical copy of this exact two-Box chain. Its fill is now
 * [glassTint] and its blur [appHazeEffect] -- the same two calls every other glass
 * surface in the app makes, not a scrim-specific alpha/style of its own.
 *
 * [progress] is a LAMBDA, not a plain `Float`, on purpose: every read of it here
 * happens inside `drawBehind`/`graphicsLayer` blocks, i.e. at DRAW time, not
 * composition time. A plain `Float` parameter would still type-check, but passing
 * one computed from `someAnimatable.value` at the CALL SITE reads that value at
 * composition time to build the argument -- which is exactly the mistake both
 * original copies of this code were once fixed for (see either call site's own
 * history): it recomposed the entire sheet -- the map's tile loop, every button --
 * on every single frame of the open/close spring. Taking a lambda instead makes
 * that mistake impossible to reintroduce by accident at a future call site: there
 * is no way to pass one without wrapping the read in `{ }`.
 *
 * Deliberately its own thing rather than a [GlassSurface] mode: a full-screen scrim
 * has no shape to clip to, no rim/shadow edge (there's no edge, it fills the
 * screen), and no content slot -- fusing it into [GlassSurface] would mean adding
 * parameters to that function that only ever make sense for this one shape.
 */
@Composable
internal fun ScrimBlur(hazeState: HazeState?, progress: () -> Float, modifier: Modifier = Modifier) {
    // Both call sites always pass a real, non-null HazeState (each screen builds
    // one unconditionally via `remember { HazeState() }`), so `hazeState != null`
    // alone was never actually gating anything -- the blur ran unconditionally,
    // battery saver or not. canBlurBackdrops() is the real gate, same as every
    // other blur site in the app.
    val canBlur = hazeState != null && canBlurBackdrops()
    // Full-screen scrim needs strong dimming in both light and dark modes.
    // Use black with appropriate alpha for proper contrast and readability.
    val tint = if (canBlur) Color.Black.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.22f)
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(tint, alpha = progress().coerceIn(0f, 1f))
            },
    )
    if (canBlur) {
        Box(
            modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress().coerceIn(0f, 1f) }
                .appHazeEffect(hazeState!!, progressive = true),
        )
    }
}
