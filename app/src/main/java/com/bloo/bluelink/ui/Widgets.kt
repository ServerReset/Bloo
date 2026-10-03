@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class,
)

package com.bloo.bluelink.ui

import dev.chrisbanes.haze.HazeState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.bloo.bluelink.data.SettingsStore
import kotlinx.coroutines.flow.first
import com.bloo.uicommon.SegmentOption

/**
 * True while the Activity is in Android's multi-window/split-screen/freeform
 * presentation ("floating" -- the app no longer owns the whole display).
 * Driven entirely from [com.bloo.bluelink.MainActivity]: seeded from
 * `Activity.isInMultiWindowMode()` at `onCreate`, kept live via its
 * `onMultiWindowModeChanged` override. A plain top-level `mutableStateOf`,
 * not a CompositionLocal -- there is exactly one Activity for this whole
 * process, so there is nothing to scope it to, and every composable that
 * cares (today, just [StatusBarScrim]) can read it directly.
 *
 * Why [StatusBarScrim] needs this at all: in multi-window mode the OS draws
 * its own real, opaque status/task bar and this app's own window does NOT
 * extend edge-to-edge behind it the way [enableEdgeToEdge][androidx.activity.enableEdgeToEdge]
 * makes it do full-screen -- so "the area behind the status bar" is no
 * longer transparent app content the scrim is free to blur, it is empty
 * space outside this app's own window. Blurring it there would do nothing
 * useful and cost a RenderEffect for no visible effect.
 */
internal var inMultiWindowMode by mutableStateOf(false)


/**
 * A soft blurred scrim behind the status bar so scrolling content underneath
 * (a car photo, Aurora, dense text) doesn't fight the system clock/battery
 * icons drawn on top of it. Not the normal (non-cover-screen) layouts -- the
 * cover screen already reserves real space above its content instead of
 * drawing under the status bar at all, so it has nothing to scrim -- and not
 * [inMultiWindowMode] ("floating"), where this app doesn't draw behind the
 * status bar at all (see that flag's own doc). Callers still gate the
 * cover-screen case themselves (`if (!isCompactCoverScreen()) StatusBarScrim()`)
 * since that check is already cheap and in scope at every call site; the
 * multi-window check lives HERE instead of being repeated at each one, since
 * it is a single process-wide flag every caller should honour identically.
 *
 * The blur is always active when [hazeState] and hardware capability
 * ([canBlurBackdrops]) allow it -- there used to be an `active: Boolean`
 * escape hatch here (meant for pausing the blur during a pager fling to save
 * a per-frame RenderEffect recomposite), but every real call site already
 * passed `true` unconditionally, so the flag was dead flexibility that only
 * risked a future caller accidentally turning the blur off. Removed rather
 * than left unused -- reported directly as wanting this scrim's blur to
 * always be on, with no path to disable it by mistake.
 */
@Composable
internal fun StatusBarScrim(
    /**
     * The [HazeState] whose matching [dev.chrisbanes.haze.hazeSource] marks the
     * content actually behind this scrim (the car photo, Aurora, scrolling list --
     * one per screen, applied where that screen already draws its own background).
     * Non-null makes this a REAL backdrop blur of that content; null (every screen
     * not yet wired to a HazeState) falls back to the old self-blur behaviour
     * unchanged, so adopting Haze screen-by-screen carries no regression for the
     * ones that haven't yet.
     *
     * Plain `Modifier.blur` was always a no-op here regardless of API level: it only
     * ever blurred what THIS composable's own modifier chain draws, which is a flat
     * vertical gradient with no detail in it for a blur convolution to soften --
     * reported directly as "still not working" even after gating it to real API 31+
     * hardware. A gradient blurred is the same gradient. What legibility under the
     * status bar icons actually needs is the CONTENT drawn behind this scrim to look
     * soft, which requires capturing that content into its own layer first -- Haze's
     * whole job, and not something `Modifier.blur` alone can do without it.
     */
    hazeState: HazeState? = null,
) {
    if (inMultiWindowMode) return
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Modifier.blur (the hazeState == null fallback path below) is backed by
    // RenderEffect, which the Android framework only implements from API 31 (S)
    // onward -- Compose has no software fallback for it, and neither does Haze's own
    // blur. minSdk here is 26, so on any API 26-30 device both paths are a visual
    // no-op: the gradient alone -- with no blur softening it -- is the only thing
    // anyone on those devices ever actually sees. Gating on the real capability
    // rather than leaving a dead modifier attached, and giving pre-S devices a
    // stronger gradient (blur's whole job, legibility under the status bar icons,
    // otherwise falls entirely on a fairly light 0.55 alpha fade) instead of
    // silently doing less than intended.
    val canBlur = canBlurBackdrops()
    // glassTint, not this scrim's own separately-tuned alpha pair or scheme.surface --
    // the exact same fill (colour AND alpha) every other glass surface in the app
    // resolves to for this canBlur state. There is now exactly one function in the
    // whole app that decides what a "not blurred, fall back to solid" (or "blurred,
    // go lighter") tint actually looks like.
    val tint = glassTint(canBlur)
    Box(
        Modifier
            .fillMaxWidth()
            // The status bar's own real height, not that plus an extra margin --
            // 20dp (and 28dp before that) was still reported as "too thick", reaching
            // past the icons it exists to back into content below (a segmented
            // toggle). Basing this directly on the actual inset means it
            // covers exactly the status bar and nothing past it, on every device.
            .height(topInset)
            .then(
                if (hazeState != null && canBlur) {
                    // The actual fix: blurs whatever is really drawn behind this scrim, via
                    // the matching Modifier.hazeSource(hazeState) on that screen's own
                    // background -- not this Box's own gradient. That gradient (below,
                    // applied identically either way) still does the same legibility
                    // tinting job it always did, now over a genuinely blurred backdrop.
                    //
                    // appHazeEffect: the one shared Haze configuration (see its own doc)
                    // every blurred surface in the app now goes through. progressive = true
                    // here specifically -- reported directly, once, as wanting the blur
                    // itself to actually be strong right at the status bar and taper to
                    // none by this Box's own bottom edge, "like a gradient of blur" -- the
                    // one shape this scrim actually needs, unlike a small floating chip
                    // (appHazeEffect's own default), which reads better with a flat,
                    // uniformly full-strength blur instead.
                    Modifier.appGlassEffect(hazeState, RoundedCornerShape(0.dp), fadeOut = true)
                } else {
                    Modifier
                },
            )
            .background(
                Brush.verticalGradient(
                    // Weaker glass effect: reduce tint alpha by half for a lighter scrim
                    // that still provides legibility without being too heavy.
                    listOf(tint.copy(alpha = tint.alpha * 0.5f), Color.Transparent),
                ),
            )
            .then(
                if (hazeState == null && canBlur) {
                    Modifier.blur(14.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                } else {
                    Modifier
                },
            ),
    )
}


/**
 * Settings mode (Simple/Advanced) toggle, flush against the status bar's own
 * bottom edge so it reads as one continuous piece of chrome hanging from it,
 * not a second, separately-bordered pill floating below it.
 *
 * Previously this nested a fully-rounded [MorphSegmented] pill (its own
 * background AND its own hairline border, from the app-level `MorphSegmented`
 * wrapper's hardcoded `borderColor`) inside an outer, flat-topped/round-
 * bottomed [GlassSurface] that ALSO carried its own border (glassEdge's
 * frostedRim) plus an [ambientRing] halo shadow drawn on all four sides --
 * including the top edge, right where it meets the status bar. Two
 * differently-shaped bordered surfaces stacked on each other, with a shadow
 * ring interrupting the seam between this tab and the status bar above it,
 * is what read as disjointed rather than as one element.
 *
 * Now there is exactly one bordered/shadowed/blurred surface -- this
 * [GlassSurface], using its normal edge treatment (a plain downward
 * dropShadow, which only ever darkens BELOW the shape, so it can't interrupt
 * the seam above) and no [ambientRing]. [MorphSegmented] is called directly
 * (bypassing the app wrapper's fixed border) with a transparent container and
 * no border of its own, so it draws only its segment labels and highlight
 * indicator on top of this surface's own fill.
 */
@Composable
internal fun SettingsModeTab(
    settingsMode: String,
    onSettingsModeChange: (String) -> Unit,
    hazeState: HazeState? = null,
) {
    val haptics = LocalHaptics.current
    val scheme = MaterialTheme.colorScheme

    // Sits BELOW the status bar, like every other piece of floating header
    // chrome in the app (FloatingIcon, the refresh badge) -- reported
    // directly as looking bad: extending the glass fill up under the status
    // bar icons (a previous pass, chasing "make it feel like it comes out of
    // the status bar") instead blurred together with the system clock/
    // battery/signal glyphs and any notification pill drawn there, reading
    // as visual clutter rather than a deliberate "tab" shape.
    Box(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = HeaderCornerGap, end = HeaderCornerGap),
        contentAlignment = Alignment.TopEnd,
    ) {
        GlassSurface(
            shape = StandardShape,
            modifier = Modifier.width(172.dp),
            hazeState = hazeState,
        ) {
            com.bloo.uicommon.MorphSegmented(
                options = listOf(
                    SegmentOption("simple", "Simple", null),
                    SegmentOption("advanced", "Advanced", null),
                ),
                selectedKey = settingsMode,
                onSelect = onSettingsModeChange,
                containerColor = Color.Transparent,
                indicatorColor = scheme.primary,
                selectedTextColor = scheme.onPrimary,
                unselectedTextColor = scheme.onSurfaceVariant,
                textStyle = ButtonLabelStyle,
                onTick = { haptics?.tick() },
                trackHeight = HeaderButtonSize,
                borderColor = null,
            )
        }
    }
}


/** The one shared "gap below the status bar" every free-floating header
 *  element -- [FloatingIcon]'s own default [FloatingIcon.outerPadding], the
 *  page-dot overlays -- lines up against, so they all sit on the same row
 *  instead of each surface
 *  reproducing its own close-but-not-quite value (this used to be `12.dp` in
 *  some places and `10.dp` in others, an inconsistency invisible on any one
 *  screen alone but obvious the moment two headers are compared side by
 *  side). */
internal val HeaderCornerGap = 12.dp


/** The one shared size every free-floating header BUTTON -- [FloatingIcon]'s
 *  circle, and anything meant to sit in the same row as one -- is drawn at,
 *  so two buttons on the same header always share a vertical centre. Used to
 *  be re-typed as a bare `48.dp` at each call site (and, in one place,
 *  [LockOverlay]'s own hand-rolled back button, mistyped as `46.dp` -- a
 *  silent 2dp size/alignment drift from every other header button in the
 *  app). */
internal val HeaderButtonSize = 48.dp


/** Extra breathing room reserved *below* a header button's own footprint
 *  (`HeaderCornerGap + HeaderButtonSize`) before real content is allowed to
 *  start, on top of whatever `Arrangement.spacedBy` a column already adds.
 *  Needed because a button's true on-screen silhouette is bigger than its
 *  logical box: [FloatingIcon] draws `ambientRing()`/`dropShadow()` glow
 *  outside its 48dp circle, and content below it (e.g. a [Pebble] row) has
 *  its own card shadow -- so reserving exactly the button's geometric
 *  footprint (as ExpandedCar's dual-column header used to) leaves only the
 *  column's incidental 12dp `spacedBy` gap as buffer, which those two halos
 *  can visibly eat into. Mirrors the same "bare inset isn't enough, add a
 *  named clearance" pattern [PagerDotClearance] already uses below. */
internal val HeaderContentClearance = 12.dp


/** A small translucent circular icon button used as a floating overlay control.
 *  [outerPadding] is the breathing room around the [HeaderButtonSize] circle -
 *  the default ([HeaderCornerGap], a 72dp footprint) suits free-floating
 *  overlay corners; tight rows (the cover screen's title row, at 2dp) keep
 *  that footprint down to 52dp on a ~260dp-tall screen. */
@Composable
internal fun FloatingIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outerPadding: Dp = HeaderCornerGap,
    // Overrides for surfaces that float over something other than the app's
    // content: the lock overlay's back arrow sits on a dark scrim, not a
    // card, so it deliberately uses plain white instead of the glass fill
    // (see LockOverlay's own note -- the old hand-rolled Surface there was
    // this exact shape re-built by hand; it now passes these instead).
    containerColor: Color? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    /** The screen's own [HazeState] (its `hazeSource` marks the content actually
     *  behind this button), giving this a REAL backdrop blur instead of just a
     *  translucent tonal fill -- reported directly as wanting every floating
     *  button's background to carry the same blur the status bar/map sheet
     *  already do. Null (the default) keeps every existing call site exactly as
     *  it was: a plain glass tint, no blur, opted into per screen as each one's
     *  own hazeState becomes available here, the same gradual-adoption shape
     *  [StatusBarScrim]'s own `hazeState` param already uses. */
    hazeState: HazeState? = null,
    /** Swaps the static [icon] for a spinning [LoadingIndicator] and ignores taps --
     *  the same "icon becomes its own busy state" pattern [PebbleHeaderAction.pending]
     *  uses, for a floating button whose action is itself already in flight (the
     *  global refresh button while [AppViewModel]'s own `refreshing` is true). */
    busy: Boolean = false,
) {
    val haptics = LocalHaptics.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = lowPowerAwareSpring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "floatIconScale",
    )
    // GlassSurface (GlassChrome.kt) is the shared layered fill/blur/rim/shadow
    // every floating pill/circle/chip in the app now goes through -- see its own
    // doc for why this used to be its own hand-rolled Box+Surface here.
    GlassSurface(
        shape = CircleShape,
        modifier = modifier
            .padding(outerPadding)
            .size(HeaderButtonSize)
            // Lambda form: the press spring is read at DRAW time, so the animation
            // never recomposes this button (the arg-taking overload reads it in
            // composition instead -- see ExpressiveButtons.kt for the same fix).
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .ambientRing(CircleShape),
        hazeState = hazeState,
        tint = containerColor ?: glassTint(hazeState != null && canBlurBackdrops()),
        contentColor = contentColor,
        contentDescription = description,
        interactionSource = interaction,
        onClick = { if (!busy) { haptics?.click(); onClick() } },
    ) {
        if (busy) {
            LoadingIndicator(Modifier.size(22.dp))
        } else {
            Icon(icon, contentDescription = null)
        }
    }
}







/**
 * When true (cover-screen tiles), pebbles render permanently open with no
 * collapse chevron or drag handle - collapsing a full-screen tile makes no sense.
 */
/**
 * The current [SettingsStore.Appearance], provided once at the app root (see
 * BlooApp) so pebbles/tiles read it via LocalAppearance.current instead of each
 * opening its own vm.appearance.collectAsStateWithLifecycle() coroutine collector. ~20 hot
 * per-pebble/per-tile collectors collapse to one. Default is a fresh Appearance()
 * (all defaults) so a reader outside the provider degrades gracefully rather than
 * crashing — but every real screen is inside the provider.
 */
internal val LocalAppearance = staticCompositionLocalOf { SettingsStore.Appearance() }


internal val LocalForceExpanded = staticCompositionLocalOf { false }


/**
 * When true (cover-screen tiles), a pebble stretches to fill the available height
 * and scrolls internally if its content is taller - so each tile fills the screen.
 */
internal val LocalPebbleFillHeight = staticCompositionLocalOf { false }


/** Tile names that [CoverCar] can render — unknown sections are excluded. */
internal val CompactKnownTiles = setOf(
    // No "controls" here, deliberately. It was added when the lock/horn
    // controls were unreachable on the cover, but as its own page it was one
    // short row of buttons above two thirds of an empty screen. Those same
    // controls now live in CoverMainTile's permanent action bar, on the page
    // the cover opens on -- so a separate page for them would be a second,
    // emptier copy of something already on screen.
    // "update" IS here: the update-available card is a first-class pebble on
    // every phone page, and it silently vanished from the cover (reported).
    // Rendered through the same SinglePebble routing as every other tile, so
    // the Install/Remind-me/Not-now card works on the flip screen exactly as
    // it does unfolded.
    "climate", "charge", "location", "trips", "info", "diagnostics", "ai", "update"
)


/**
 * When set, [Pebble] in fill-height cover-screen mode uses this scroll state
 * instead of creating a local one — lets the parent observe scroll position
 * to decide whether to switch pager pages or scroll tile content.
 */
internal val LocalCoverScrollState = compositionLocalOf<ScrollState?> { null }


/**
 * Shared flag set true while the cover-screen page scrubber is active, so the
 * parent [CompactGarage] can suspend horizontal car-switching swipes during a
 * scrub. Provided around the HorizontalPager content.
 */
internal val LocalCoverScrubbing = staticCompositionLocalOf<MutableState<Boolean>?> { null }


/**
 * The live pull-to-refresh distance (0..1+), published by [Refreshable] so the
 * floating overlays in [GarageScreen] (settings/back/flip buttons)
 * can track the pull in real time instead of only animating once refresh starts.
 */
internal val LocalPullFraction =

    staticCompositionLocalOf<androidx.compose.runtime.MutableState<Float>> { mutableFloatStateOf(0f) }
