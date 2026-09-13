package com.bloo.bluelink.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.dropShadow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import com.bloo.uicommon.frostedRim as sharedFrostedRim
import com.bloo.uicommon.ambientRing as sharedAmbientRing

/*
 * The phone app's floating-chrome helpers are now ONE shared kit in
 * :uicommon (see com.bloo.uicommon.GlassChrome), usable by the watch and
 * widget surfaces too. This file keeps the app's original call sites
 * unchanged by re-supplying the theme tint they used to read internally:
 * the platform Material is the one thing the shared module deliberately
 * does NOT depend on, so the wrapper passes onSurface in.
 *
 * frostedRim / ambientRing: thin alias wrappers. There used to be a third,
 * `glassContainerAlpha` -- every one of its call sites is gone now (each was
 * its own slightly-different alpha override on the exact fill [GlassSurface]/
 * [glassTint] now compute once, shared, below), so the wrapper itself is gone
 * too rather than left behind as a stale, now-uncalled indirection.
 */

/**
 * The phone's default rim: the shared [com.bloo.uicommon.frostedRim] with
 * this platform's onSurface (the watch reads its own; a widget reads its
 * own). The tint passed here is what makes the rim follow the theme's
 * light/dark state.
 */
@Composable
fun Modifier.frostedRim(shape: Shape): Modifier =
    this.sharedFrostedRim(shape, MaterialTheme.colorScheme.onSurface)

/** See [com.bloo.uicommon.ambientRing]. */
fun Modifier.ambientRing(shape: Shape): Modifier =
    this.sharedAmbientRing(shape)

/**
 * The standard opaque pebble/card edge: a drop shadow, plus -- only when the user
 * has turned on the "pebble outline" appearance setting -- a bolder solid border.
 * Shared by every pebble-shaped card in the app (PebbleShell's own card, the
 * single-column pull-to-refresh content in Pebbles.kt, the cover screen's hero
 * tile in Cover.kt), which used to each carry an identical, separately copy-pasted
 * three-line block -- literally the same code, typed three times.
 *
 * Distinct from [GlassSurface]: this is for opaque, themed card content (a pebble
 * IS its own content, not a translucent overlay on top of something else), so it
 * has no fill or blur of its own to standardize -- just the shadow/outline pairing
 * every pebble already shares.
 */
@Composable
internal fun Modifier.pebbleCardEdge(shape: Shape, outline: Boolean): Modifier =
    this.dropShadow(shape, blurRadius = 12.dp, offsetY = 4.dp).then(
        if (outline) {
            Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)), shape)
        } else {
            Modifier
        },
    )

// ---- One floating-glass surface, everywhere -------------------------------

/**
 * The two alphas every neutral glass fill in the app shares -- one number for "no
 * real blur behind this" (pre-S devices, or battery saver), a lighter second number
 * for "a real blur is already doing most of the legibility work underneath." Used to
 * be four: a separate pair for dark and light theme, on top of the blurred/unblurred
 * split. Splitting by theme brightness never had a real reason behind it -- the
 * THEME only ever needs to change which base colour (black or white) this tints
 * with, not how transparent that colour is -- so it was really the same two numbers,
 * typed twice. Literally one set of numbers now, referenced by both branches below.
 */
// Lowered twice now -- 0.65/0.3, then 0.4/0.12, reported both times as wanting
// the glass more transparent still. The blurred number goes lowest: the whole
// point of a real blur behind it is that it does most of the legibility work
// itself, so the tint on top of it only needs to be enough to keep a
// consistent neutral cast over whatever's showing through, not to carry
// contrast on its own the way the no-blur fallback still has to.
private const val GlassTintAlpha = 0.28f
private const val GlassBlurredTintAlpha = 0.06f

/**
 * Resolves [GlassSurface]'s own fill color -- also called directly by the one place
 * in the app that can't host [GlassSurface] itself because it doesn't own its own
 * layout, just a `Color` parameter on a platform composable: the single-column pull-
 * to-refresh indicator (`PullToRefreshDefaults.LoadingIndicator`'s `containerColor`
 * in Pebbles.kt).
 *
 * Plain black/white by theme, deliberately NOT a `MaterialTheme.colorScheme` tonal
 * role -- see [MetaChip]'s own doc for the reason: this app's dynamic/custom palette
 * feeds every tonal role, even the "neutral" ones, a slice of the seed colour, so a
 * technically-neutral token can still render as a flatly-tinted chip. [blurred]
 * defaults to whatever [GlassSurface] would itself decide (a real blur actually
 * playing behind this fill), so passing nothing here and passing nothing for
 * [GlassSurface]'s own `hazeState` agree automatically.
 */
@Composable
internal fun glassTint(blurred: Boolean): Color {
    val dark = isSystemInDarkTheme()
    val alpha = if (blurred) GlassBlurredTintAlpha else GlassTintAlpha
    return if (dark) Color.Black.copy(alpha = alpha) else Color.White.copy(alpha = alpha)
}

/**
 * The one Haze configuration every blurred surface in the app uses -- literally the
 * same function call, not a same-looking copy of one. Before this, three different
 * spots each built their own `hazeEffect(state = ...) { ... }` block: [GlassSurface]
 * called it completely plain (Haze's own default style), [ScrimBlur] and
 * `StatusBarScrim` (Widgets.kt) each separately set `progressive = StandardBlurProgressive`,
 * and the map's own drag-handle chip (WeatherPebble.kt) called it plain again -- three
 * different-looking blurs on three different classes of surface, none of them actually
 * wrong on their own, but never actually the same call. Every one of those now goes
 * through this, so there is exactly one place that decides what "the app's blur" looks
 * like, at any radius, style or intensity -- change it here and every surface in the
 * app changes together, and no future call site can quietly drift onto its own version.
 */
internal fun Modifier.appHazeEffect(state: HazeState): Modifier =
    this.hazeEffect(state = state) { progressive = StandardBlurProgressive }

/**
 * The app's one floating-glass surface: a real Haze backdrop blur of [hazeState]
 * when one is given and [CanBlurBackdrops] allows it (API 31+, battery saver off),
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
    tint: Color = glassTint(hazeState != null && CanBlurBackdrops()),
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    interactionSource: MutableInteractionSource? = null,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit = {},
) {
    val canBlur = hazeState != null && CanBlurBackdrops()
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .dropShadow(shape)
            .appGlassRim(shape)
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
                .then(if (canBlur) Modifier.appHazeEffect(hazeState!!) else Modifier)
                .background(tint),
        )
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
    // battery saver or not. CanBlurBackdrops() is the real gate, same as every
    // other blur site in the app.
    val canBlur = hazeState != null && CanBlurBackdrops()
    // glassTint, not a literal Color.Black -- the exact same fill (colour AND alpha)
    // every other glass surface in the app resolves to for this canBlur state, read
    // once here at composable scope (glassTint is itself @Composable) rather than
    // inside the drawBehind block below. `drawRect`'s own `alpha` parameter multiplies
    // with this color's already-baked-in alpha, so the entrance/exit fraction still
    // animates purely at draw time exactly as it did before -- nothing about the
    // performance property this function's own doc describes changes.
    val tint = glassTint(canBlur)
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
                .appHazeEffect(hazeState!!),
        )
    }
}