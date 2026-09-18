package com.bloo.bluelink.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.dropShadow
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import com.bloo.uicommon.frostedRim as sharedFrostedRim
import com.bloo.uicommon.ambientRing as sharedAmbientRing

/**
 * UNIFIED GLASS & BLUR SYSTEM — One Set of Numbers, Referenced Everywhere
 *
 * This file contains the COMPLETE, UNIFIED glass styling system for every
 * floating surface in the app: search results, dialogs, overlays, status bar,
 * drag handles, pills, chips. ONE place controls all glass/blur — change here,
 * everything changes together. NO scattered alpha values, NO per-site overrides.
 *
 * THE NUMBERS (All Floating Surfaces Share These):
 * ────────────────────────────────────────────────
 * [GlassTintAlpha] = 0.22f        // No blur fallback (pre-S, battery saver)
 * [GlassBlurredTintAlpha] = 0.05f // With real Haze blur (API 31+)
 *
 * These are the ONLY two alpha values the entire app uses for glass. Everything
 * else (dark/light theme, blurred/unblurred) is computed from these two numbers
 * and the device's actual capabilities. No separate overrides per-component.
 *
 * COMPONENT REFERENCE (Pick One):
 * ─────────────────────────────
 * 1. [GlassSurface] — Main floating surface (search pills, dialogs, chips)
 *    - Takes shape, modifier, hazeState, content
 *    - Handles blur, tint, rim, shadow automatically
 *    - Use this for: search results, dialogs, floating pills, alert dialogs
 *
 * 2. [ScrimBlur] — Full-screen dim + blur (map sheets, expandable overlays)
 *    - Takes hazeState, progress lambda, modifier
 *    - No shape, no content slot (full-screen only)
 *    - Use this for: dimming backdrop behind expanded sheets
 *
 * 3. [pebbleCardEdge] — Opaque card edges (pebbles, standard Material cards)
 *    - NOT glass (not translucent)
 *    - Takes shape, outline toggle
 *    - Use this for: pebble shells, standard cards, opaque containers
 *
 * CORE MODIFIERS (Combine into larger patterns or used directly):
 * ───────────────────────────────────────────────────────────
 * - [glassEdge(shape)] — Shadow + frosted rim (used by GlassSurface)
 * - [glassEffect(hazeState)] — Blur + tint combined (for manual styling)
 * - [glassTint(blurred)] — Just the tint color (compute or pass to background)
 * - [glassRim(shape)] — Just the frosted rim (rare specialized cases)
 * - [appHazeEffect(state)] — Just the blur (low-level, rarely needed)
 * - [ambientRing(shape)] — Glow ring effect (specialized cases only)
 *
 * WHY THIS STRUCTURE:
 * ──────────────────
 * Before: Five places with glass (FloatingIcon, MetaChip, pull-refresh circle,
 * map name pill, drag handle chip), each with slightly different alphas and
 * blur/rim/shadow — impossible to change all at once, easy to drift.
 *
 * After: One system everywhere. Change alpha here, ALL floating surfaces change.
 * Change blur strength here, entire app changes. One function call, one place
 * to check, one place to fix.
 *
 * The phone app's floating-chrome helpers are shared in :uicommon
 * (com.bloo.uicommon.GlassChrome) for watch and widget reuse. This file
 * re-supplies Material theme tint the shared module can't depend on.
 */

/**
 * Apply glass edge treatment: shadow + frosted rim. Used by all glass surfaces.
 * This is the ONLY glass edge styling in the app.
 *
 * [shadow] defaults to true (unchanged behaviour) for a surface genuinely
 * FLOATING over the screen -- search results, the status bar, a floating
 * icon button -- where a real drop shadow is what separates it from an
 * arbitrary, unpredictable backdrop (a car photo, scrolled content). Pass
 * false for a glass panel NESTED inside another already-elevated card (e.g.
 * a status callout inside a pebble): that card already carries its own edge
 * treatment, so stacking a second full-strength shadow (dropShadow's own
 * default is a fairly heavy 0.38 alpha, 14dp blur) on a small sub-panel a
 * few dp inside it read as a harsh, "baked-in" dark smudge rather than a
 * second, subtler layer of depth -- reported directly from a screenshot.
 * The frosted rim border still draws either way; only the shadow is optional.
 *
 * The shadow is also THEME-DEPENDENT now -- see [glassDropShadow]. That was the
 * remaining "black shadow behind floating elements" bug, reported about five times
 * and only partly addressed by the [pebbleCardEdge]/[glassTint] fixes: those two
 * were reading the WRONG dark-mode source, while this one never looked at the theme
 * at all.
 */
@Composable
internal fun Modifier.glassEdge(shape: Shape, shadow: Boolean = true): Modifier =
    (if (shadow) this.glassDropShadow(shape) else this).glassRim(shape)

/**
 * The floating-glass drop shadow, at the weight the CURRENT theme can carry.
 *
 * This is the third and last "black smudge behind floating chrome" root cause, and it
 * is a different one from the other two: [pebbleCardEdge] and [glassTint] were reading
 * dark mode from the wrong SOURCE (raw `isSystemInDarkTheme()` instead of the app's own
 * override -- now [appIsDarkTheme]); this drew `dropShadow(shape)` with its bare default
 * -- black at 0.38 alpha, 14dp blur, offset 5dp down -- with no light/dark gate of ANY
 * kind. Every [GlassSurface] in the app goes through here, so that one line put a heavy
 * black silhouette under every floating chip, pill, dialog, status bar, refresh circle
 * and [FloatingIcon] in both themes.
 *
 * Why that reads as a *smudge* specifically, rather than as depth, and why LIGHT mode is
 * where it was reported: the glass FILL is deliberately almost nothing. [glassTint] is
 * 0.10 alpha unblurred and 0.02 blurred (lowered five times, each time on a request for
 * more of the background to show through), and in light mode it is a pale
 * `surfaceContainer` at 0.08-0.12. On a device with no real backdrop blur -- pre-API-31,
 * or battery saver on, i.e. [CanBlurBackdrops] false, which is also every screen that
 * still passes no `hazeState` -- there is then nothing solid on the shape at all, and the
 * single most opaque thing anywhere near a floating chip is this 0.38 black behind it.
 * The chip reads as its own shadow. Dark mode hid it because the shadow lands on an
 * already-dark backdrop; light mode is where 0.38 black on near-white is exactly the
 * "flat black smudge" in the screenshots.
 *
 * Not dropped entirely in light mode, and that is the one thing this does differently
 * from [pebbleCardEdge]. A pebble is an opaque themed card on the app's own background,
 * so it can rely on its outline; this floats over an ARBITRARY backdrop -- a car photo,
 * scrolled content -- and the shadow is what stops a translucent chip dissolving into a
 * bright patch of photo (the same fact `ambientRing` exists for). So light mode keeps a
 * shadow, as a soft contact shadow rather than a silhouette: a third of the alpha, a
 * tighter blur and a much shorter offset, which still separates the shape from what is
 * behind it without ever becoming the most solid thing on screen. Dark mode is unchanged
 * (`dropShadow`'s own defaults), because nothing was ever wrong there.
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
 * The symmetric ambient halo, at the weight the CURRENT theme can carry --
 * see [com.bloo.uicommon.ambientRing] for what it draws and why it has no offset.
 *
 * Same root cause as [glassDropShadow], and the two STACK, which is why this is the
 * other half of the "black halo behind floating elements" report rather than a separate
 * issue: [FloatingIcon] (Widgets.kt) and the cover screen's camera band
 * (CoverGarage.kt) chain this ON a [GlassSurface], so a 48dp floating button was
 * carrying 0.38-alpha black offset below it AND 0.30-alpha black on all four sides,
 * neither gated on the theme, under a fill of 0.02-0.12 alpha. In light mode that is two
 * black layers and no button. [HeaderContentClearance]'s own doc already describes the
 * result from the layout side -- "a button's true on-screen silhouette is bigger than its
 * logical box... those two halos can visibly eat into" the content below it.
 *
 * The shared :uicommon implementation stays exactly as it is, and is still what the watch
 * and [com.bloo.uicommon.PagerDots] call: neither can see [LocalAppearance] (uicommon is
 * Material- and app-state-free by design, see its own file doc), and both draw over their
 * own always-dark backdrops anyway. This wrapper is the phone's theme-aware entry point,
 * which is what the wrapper existed for in the first place -- it just wasn't adding
 * anything yet.
 *
 * Light mode keeps a halo rather than dropping it (same reasoning as [glassDropShadow]:
 * the thing this defends against is a bright patch of car photo, which happens in either
 * theme) at a third of the alpha and a tighter radius, so it still darkens the backdrop
 * around the shape without being the shape's most visible feature.
 *
 * @Composable now, where the shared one is a plain function. Both current call sites
 * chain this inside a composable's modifier argument, so nothing had to move.
 */
@Composable
fun Modifier.ambientRing(shape: Shape): Modifier =
    if (appIsDarkTheme()) {
        this.sharedAmbientRing(shape)
    } else {
        this.dropShadow(shape, color = Color.Black.copy(alpha = 0.10f), blurRadius = 7.dp, offsetY = 0.dp, offsetX = 0.dp)
    }

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
 *
 * In light mode, skip the shadow entirely -- the outline provides sufficient visual
 * separation and shadows read as too heavy on light backgrounds.
 */
@Composable
internal fun Modifier.pebbleCardEdge(shape: Shape, outline: Boolean): Modifier {
    // [appIsDarkTheme], NOT a raw isSystemInDarkTheme() read. That was the bug: a
    // user who explicitly set the app to Light while their SYSTEM was in dark mode
    // got a light-themed app that still drew this shadow, because raw
    // isSystemInDarkTheme() only ever sees the phone's setting, not the app's own
    // override. The four-line `when` that fixed it in place here has moved into
    // appIsDarkTheme() (Theme.kt) -- it had been re-typed at five sites by then,
    // and that file's doc lists all five; a call is what keeps the sixth from
    // being typed by hand too.
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
 * The two alphas every neutral glass fill in the app shares -- one number for "no
 * real blur behind this" (pre-S devices, or battery saver), a lighter second number
 * for "a real blur is already doing most of the legibility work underneath."
 *
 * Used to be four separate pairs (dark/light theme × blurred/unblurred), but splitting
 * by theme was redundant: the THEME only needs to change the base colour (black or white),
 * not how transparent it is. Literally one set of numbers now, referenced everywhere.
 *
 * History: lowered four times (0.65/0.3 → 0.4/0.12 → 0.28/0.06 → 0.16/0.02), raised
 * once to 0.22/0.05 to restore neutral cast, lowered again to 0.15/0.03, and now
 * lowered once more to 0.10/0.02 -- reported directly as still wanting more of the
 * background to show through the glass.
 */
internal const val GlassTintAlpha = 0.10f
internal const val GlassBlurredTintAlpha = 0.02f

/**
 * Resolves [GlassSurface]'s own fill color -- also called directly by places that
 * can't host [GlassSurface] itself because they don't own their own layout, just a
 * `Color` parameter on a platform composable or a `Modifier.background()`.
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
    // Same fix as pebbleCardEdge (GlassChrome.kt): resolve dark the way
    // BlooTheme itself does (Theme.kt), not a raw isSystemInDarkTheme() read.
    // That mismatch is what made floating glass (search results, the status
    // bar, every GlassSurface) render as a near-solid black panel -- e.g. the
    // app forced to Dark while the SYSTEM was in light mode read `dark` as
    // false here, so this picked the "light mode" branch and tinted with
    // colorScheme.surfaceContainer at low alpha -- but the actual color
    // scheme in that case IS dark (the app is forced dark), so that low-alpha
    // tint was a low-alpha DARK color layered over a blur that, without a
    // real light backdrop to lighten it, read as flatly black.
    // (The `when` block that lived here is now appIsDarkTheme() -- see Theme.kt.)
    val dark = appIsDarkTheme()
    return if (dark) {
        val alpha = if (blurred) GlassBlurredTintAlpha else GlassTintAlpha
        Color.White.copy(alpha = alpha)
    } else {
        // Light mode: use theme-aware surface color for better color matching.
        // Use a semi-transparent overlay on top of the surface to maintain proper
        // contrast while respecting the theme's palette. This ensures floating
        // buttons don't appear as black overlays but instead blend with the theme.
        val scheme = MaterialTheme.colorScheme
        // Use surfaceContainer with adjusted alpha for proper glass effect
        // When blurred: lighter alpha (blur provides softness)
        // When not blurred: stronger alpha (need more visual weight)
        val surfaceColor = scheme.surfaceContainer
        val alpha = if (blurred) 0.08f else 0.12f
        surfaceColor.copy(alpha = alpha)
    }
}

/**
 * The one Haze configuration every blurred surface in the app uses -- literally the
 * same function call, not a same-looking copy of one. Every hazeEffect call in the
 * app goes through this, so there is exactly one place that decides what "the app's
 * blur" looks like -- change it here and every surface in the app changes together.
 *
 * [progressive] defaults OFF: a flat, full-intensity blur across the whole shape,
 * the SAME strength as the strongest point of [StandardBlurProgressive]'s own
 * gradient. It used to be forced on unconditionally for every call site, including
 * small floating chips (the map's drag handle/name pill, the cover screen's camera
 * band) -- reported directly as looking visibly weaker than the status bar's own
 * blur, and for a real reason: [StandardBlurProgressive] fades from full intensity
 * to none across whatever height it's applied to, which reads as "strong right at
 * the edge it grows from, soft by the far edge" on a TALL scrim (the status bar, a
 * map sheet's full-screen dim) -- the shape that gradient was actually designed for
 * -- but on a chip only 20-48dp tall, that exact same fade means the bottom half of
 * the chip is barely blurred at all, next to the status bar's own uniformly-strong
 * blur at its own (comparably small) height. Only the two full-height scrims
 * ([ScrimBlur], `StatusBarScrim` in Widgets.kt) opt into the gradient now; every
 * chip-shaped surface gets the same flat, full-strength blur the status bar's own
 * TOP edge -- its most blurred point -- already has.
 */
/**
 * Apply glass effect: combines blur (if possible) and tint in one modifier.
 * Used by specialized glass surfaces that can't use [GlassSurface] directly.
 * [progressive] controls whether the blur fades across the shape (full-screen scrims)
 * or stays uniform (small floating elements).
 */
@Composable
internal fun Modifier.glassEffect(
    hazeState: HazeState?,
    progressive: Boolean = false,
): Modifier {
    val canBlur = hazeState != null && CanBlurBackdrops()
    val tint = glassTint(canBlur)
    return this
        .then(if (canBlur) Modifier.appHazeEffect(hazeState!!, progressive) else Modifier)
        .background(tint)
}

internal fun Modifier.appHazeEffect(state: HazeState, progressive: Boolean = false): Modifier =
    this.hazeEffect(state = state) {
        if (progressive) {
            this.progressive = StandardBlurProgressive
        }
    }

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
    /** See [glassEdge]'s own doc -- false for a glass panel nested inside
     *  another already-elevated card instead of genuinely floating over the
     *  screen. */
    shadow: Boolean = true,
    content: @Composable () -> Unit = {},
) {
    val canBlur = hazeState != null && CanBlurBackdrops()
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