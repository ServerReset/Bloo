@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.bloo.bluelink.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
// No `motionScheme` import: it is a member of the MaterialTheme object (verified as
// MaterialTheme.getMotionScheme in the resolved material3 AAR), as are defaultEffectsSpec
// and defaultSpatialSpec on MotionScheme. Screens.kt imports none of them either.
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
// State<T>'s `by` delegate isn't a member -- it resolves to this file-scope operator
// extension, which the compiler will not find without an explicit import (unlike most of
// this file's other extension functions, which show up as unresolved-reference errors
// instead of this one's more oblique "has no method getValue... cannot serve as a delegate").
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.VerticalAlignmentLine
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The phone UI's shared design vocabulary: the sizes, colours and motion that more than one
 * screen has to agree on.
 *
 * Extracted from Screens.kt, which is 14.6k lines and 28% of this codebase. That extraction
 * is worth being honest about: the empirical literature does NOT support splitting a large
 * file to reduce defects (every study that controls for size either reverses the effect or
 * dissolves it, and none performs a refactoring intervention at all), and the build-speed
 * argument does not survive verification either. What a split does buy is navigability, and
 * it is the prerequisite for any further split of that file, because Kotlin's top-level
 * `private` is FILE-scoped -- so shared helpers have to become `internal` and live somewhere
 * common BEFORE any screen can move out from under them.
 *
 * This file is therefore deliberately the least interesting one: no logic, no layout, just
 * the values that would silently diverge if each screen kept its own copy. That is not
 * hypothetical here -- `AdvancedModeStiffness` below already drifted from the shared collapse
 * spec while it was buried at line ~10,700 of the monolith.
 *
 * `internal`, not `public`: it is the narrowest visibility that survives a file boundary.
 */

// ---- Colour -------------------------------------------------------------------

// Was a phone-only re-declaration of the same hex values shared/BlooColors.kt already
// centralizes (bit-identical today, one edit away from silently diverging like
// chargerLabel's text had).
internal val ChargeGreen = Color(com.bloo.bluelink.data.BlooColors.chargeGreen)
internal val ChargeGreenDark = Color(com.bloo.bluelink.data.BlooColors.chargeGreenDark)

/** The one "there's something new" colour -- the small notification dot on an update
 *  card's corner when a build is available, unread-count orange rather than a
 *  theme-relative tint, so it reads the same "you should look at this" way regardless
 *  of the active colour palette (unlike ChargeGreen/error, this isn't standing in for a
 *  car state the theme might reasonably recolour). */
internal val UpdateAvailableAmber = Color(0xFFFF9800)

/** The charge bar's "topped up" state: the pack has reached its own configured limit,
 *  so the fill reads as done rather than still climbing. See ChargeSegmentBar. */
internal val ChargeBlue = Color(com.bloo.bluelink.data.BlooColors.chargeBlue)
internal val ChargeBlueDark = Color(com.bloo.bluelink.data.BlooColors.chargeBlueDark)

/** The app's muted/secondary-text alpha, applied over LocalContentColor. */
internal const val MutedContentAlpha = 0.7f

/**
 * Text and icons drawn ON the hero's car photo.
 *
 * Fixed rather than theme-derived, because what sits behind it is a photograph and
 * a dark scrim, not a themed surface -- so it is the same in light and dark, and the
 * colour scheme's own `onSurface` is the wrong answer in both. It was what the
 * expanded hero used, which rendered the car's name in near-black on a dark photo.
 *
 * Slightly off pure white: at full white the name reads as harsher than the photo
 * behind it, and every other light-on-dark surface in the app lands here too.
 */
internal val HeroOnPhoto = Color(0xFFF2F2F5)

// ---- Sizing -------------------------------------------------------------------

/** Shared control height: a collapsed pebble matches the lock/unlock button. */
internal val ControlHeight = 76.dp

/** Uniform collapsed-header height so every pebble lines up at the same size. */
internal val PebbleHeaderHeight = ControlHeight
internal val PebbleCornerCollapsed = 38.dp
internal val PebbleCornerExpanded = 20.dp

/** The charge bar's height, shared by every surface that draws this bar so the
 *  proportions read as one component rather than five near-misses. */
internal val ChargeBarHeight = 18.dp

/** The gap reserved on both sides of every internal boundary in the charge bar, so
 *  each segment (fill, track-to-limit, dim-track-past-it) is its own visibly
 *  separate, independently-rounded piece rather than any two reading as one shape. */
internal val ChargeSegmentGap = 5.dp

/**
 * Gap between the hero's collapsed readout and the bottom edge of its card.
 *
 * Named because it is needed in TWO places that must agree: the readout's own bottom padding,
 * and the height the header reserves so its title does not sit on top of the readout. When it
 * was a bare `6.dp` at the padding site only, the reservation accounted for the readout's
 * content but not for this inset, so the reserved space was one gap short of what the node
 * actually occupies. Two copies of a spatial constant is how this slot has gone wrong every
 * previous time; one name means the reservation cannot drift from the thing it reserves for.
 */
internal val HeroReadoutBottomInset = 14.dp

/** Gap between settings cards. Lives inside SettingsCard as bottom padding rather than in
 *  the parent's arrangement, so a card collapsing to zero height takes its gap with it --
 *  see the comment at that padding for what went wrong when the parent owned it. */
/**
 * The horizontal inset every pebble's own content sits at -- the header icon, the summary line,
 * and anything a pebble reveals inside itself. Named because it is a shared alignment, not a
 * local choice: the lock pebble reaches it as 4 + 12 through a nested Box, and its revealed
 * remote-action history has to land on the same line or it reads as misaligned against the row
 * directly above it.
 */
internal val PebbleContentInset = 16.dp

/**
 * The Settings screen's vertical rhythm, as four steps instead of the seven raw values that had
 * accumulated (4, 6, 8, 10, 12, 14 and 16dp, with 6/10/14 sitting exactly between the steps
 * around them). Material 3's spacing system is built on 8 with 4dp sub-steps, and a screen of
 * seventeen cards is precisely where an ad-hoc gap is invisible on its own and obvious in
 * aggregate -- the stray values are why the spacing read as uneven from card to card even when
 * each individual gap looked deliberate.
 *
 * The strays snap DOWN rather than up, so the screen tightens slightly instead of growing by
 * ~2dp in thirty places.
 */
internal val SettingsGapHairline = 4.dp
internal val SettingsGapRow = 8.dp
internal val SettingsGapGroup = 12.dp
internal val SettingsGapSection = 16.dp

internal val SettingsCardGap = 10.dp

// ---- Shapes ---------------------------------------------------------------------
//
// Common corner radius values used throughout the app, extracted for consistency.
// When a shape needs updating, only change it here instead of across 12+ files.
internal val SmallShape = RoundedCornerShape(12.dp)   // Smaller components, chips
internal val StandardShape = RoundedCornerShape(16.dp) // Buttons, most cards
internal val LargeShape = RoundedCornerShape(20.dp)   // Large cards, sheets
internal val ExtraLargeShape = RoundedCornerShape(28.dp) // Modal dialogs

// ---- Icons ----------------------------------------------------------------------
//
// Commonly-used icons imported in 16+ files are centralized here to reduce
// duplicate imports across the codebase. Use AppIcons.Settings instead of
// importing Icons.Filled.Settings in 16 different files.
object AppIcons {
  // Icons.Filled.X (not a bare `X`): each of these is an extension property on
  // `Icons.Filled`, not a plain top-level symbol, so a bare `val Settings = Settings`
  // here doesn't resolve to the imported icon at all -- with no other `Settings` in
  // scope to supply the `Icons.Filled` receiver, Kotlin resolves the right-hand side
  // back to this very property, a property whose initializer referenced itself. That
  // shipped for a while unnoticed (nothing here could be verified against a real
  // Kotlin compiler until CI actually ran it) and was the true cause of the
  // "recursive type checking" errors CI reported across every file that touched
  // AppIcons -- not the IconBadge overload an earlier pass blamed and fixed first.
  val Settings = Icons.Filled.Settings
  val Lock = Icons.Filled.Lock
  val Bolt = Icons.Filled.Bolt
  val Close = Icons.Filled.Close
  val Build = Icons.Filled.Build
  val Search = Icons.Filled.Search
  val Refresh = Icons.Filled.Refresh
  val Check = Icons.Filled.Check
  val CheckCircle = Icons.Filled.CheckCircle
  val LockOpen = Icons.Filled.LockOpen
  val Info = Icons.Filled.Info
  val DirectionsCar = Icons.Filled.DirectionsCar
  val AutoAwesome = Icons.Filled.AutoAwesome
  val Warning = Icons.Filled.Warning
  val Thermostat = Icons.Filled.Thermostat
}

// ---- Blur -----------------------------------------------------------------------
//
// Every real Haze backdrop-blur site in the app (StatusBarScrim, the map sheet's
// scrim, FloatingIcon, the map's drag-handle chip) had drifted into its own
// hand-copied `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` gate and, for the
// two full-height scrims, its own literal `HazeProgressive.verticalGradient(
// startIntensity = 1f, endIntensity = 0f)` call -- three copies of the exact same
// expression. Reported directly as wanting the blur "standardized" across the
// app: pulled here so every site shares the same instances instead of four
// separately-typed, easy-to-drift copies.

/**
 * Single, app-wide source of truth for [android.os.PowerManager.isPowerSaveMode],
 * kept live via exactly ONE [android.content.BroadcastReceiver] for the whole
 * process -- [BlooApplication] calls [ensureInitialized] once, at startup, the
 * same place it installs its own uncaught-exception handler.
 *
 * [isBatterySaverOn] used to register its OWN receiver inline, every time it was
 * called -- fine when [canBlurBackdrops] had a couple of call sites, but between
 * every [GlassSurface] in the app and every [lowPowerAwareSpring], that function
 * is now read from dozens of places on a single screen. Each one registering its
 * own receiver for the exact same system broadcast, each keeping its own separate
 * copy of the same boolean, is pure waste this object collapses to one.
 */
internal object BatterySaverState {
    private fun current(context: android.content.Context) =
        (context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)
            ?.isPowerSaveMode == true

    private val _isOn = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isOn: kotlinx.coroutines.flow.StateFlow<Boolean> = _isOn

    @Volatile
    private var initialized = false

    fun ensureInitialized(context: android.content.Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            _isOn.value = current(appContext)
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                    _isOn.value = current(appContext)
                }
            }
            // ContextCompat, not Context.registerReceiver directly: this app's targetSdk (36)
            // is well past the API 33 cutover where a context-registered receiver MUST say
            // whether other apps can send it broadcasts, and the plain 2-arg platform call
            // throws SecurityException at runtime on 34+ once targetSdk requires that flag
            // instead of just warning about its absence. NOT_EXPORTED is correct here
            // specifically -- this only ever needs the system's OWN battery-saver broadcast,
            // never one from another app.
            androidx.core.content.ContextCompat.registerReceiver(
                appContext, receiver,
                android.content.IntentFilter(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            initialized = true
        }
    }
}

/** Live, reactive battery-saver state, read from the one shared [BatterySaverState]
 *  -- so toggling it while the app is already open (Quick Settings, the battery-
 *  saver notification) takes effect immediately everywhere at once, rather than
 *  only on the next cold start. [canBlurBackdrops] is this value's biggest
 *  consumer today, but it's a plain top-level composable precisely so anything
 *  else wanting to drop expensive visual effects under battery saver -- not just
 *  blur -- can read it the same way. */
@Composable
internal fun isBatterySaverOn(): Boolean {
    val active by BatterySaverState.isOn.collectAsState()
    return active
}

/** True on API 31+ (where Haze's real RenderEffect-backed blur exists at all --
 *  below that it silently no-ops) AND battery saver is off. Call sites gate on
 *  this to fall back to a plain darkened/tinted layer instead of asking for a
 *  blur that either can't render, or that shouldn't be spending the GPU/battery
 *  cost it has even when it can. Reported directly: "when it's on battery saver,
 *  all of [the blur] should get disabled or turned into solid colors" -- since
 *  every real blur site in the app already gates on this ONE flag (see this
 *  section's own doc for why), turning it off here turns every one of them into
 *  their own already-existing solid-colour fallback, with nothing further to
 *  change at each individual site. */
@Composable
internal fun canBlurBackdrops(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !isBatterySaverOn()

/**
 * Battery-saver-aware replacement for a bouncy [spring]: the real spring when battery saver
 * is off, a short, critically-damped [tween] (no overshoot, no settle-jitter) when it's on.
 *
 * Reported directly alongside the blur ask, in the same message: "standardize all the low
 * power controls to reduce animations." [collapseEnter]/[collapseExit]/[PopVisible] below are
 * the single shared spring behind the app's pebble expand/collapse motion -- their own doc
 * describes 14 call sites that used to each hand-roll a slightly different spring/tween before
 * being migrated onto these two functions -- so gating THIS one spot under battery saver is
 * what turns every one of those sites calm without touching any of them individually, the
 * same leverage [canBlurBackdrops] already gets from every blur site sharing it.
 *
 * A spring isn't just slower under low power, it is MORE work per frame than a tween for the
 * same visual distance: an underdamped spring overshoots and keeps re-evaluating position/
 * velocity for several extra frames settling back to rest, where a critically-damped tween
 * arrives once and stops. 140ms is short enough to still read as a deliberate transition
 * rather than a hard cut, without the bounce's extra settle tail.
 */
@Composable
internal fun <T> lowPowerAwareSpring(dampingRatio: Float, stiffness: Float) =
    if (isBatterySaverOn()) {
        tween<T>(durationMillis = 140, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    } else {
        spring<T>(dampingRatio = dampingRatio, stiffness = stiffness)
    }

/** The one shape every full-height scrim's blur uses: strong right at the edge
 *  it grows from, tapering to none by its own far edge -- "like a gradient of
 *  blur", not a flat smear with a hard cutoff. Shared by StatusBarScrim and the
 *  map sheet's own scrim; anything single-value/flat (FloatingIcon, the drag-
 *  handle chip) has no shape to standardize and doesn't use this. */
internal val StandardBlurProgressive
    get() = dev.chrisbanes.haze.HazeProgressive.verticalGradient(
        // Progressive blur: strong at top (status bar icons need legibility),
        // fading to none at bottom (content below needs normal clarity)
        startIntensity = 1f,
        endIntensity = 0f
    )

