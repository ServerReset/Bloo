package com.bloo.bluelink.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The phone UI's shared design vocabulary: the sizes, colours and motion that more than one screen
 * must agree on. `internal` because Kotlin's top-level `private` is file-scoped.
 */

// ---- Colour -------------------------------------------------------------------

// Was a phone-only re-declaration of the same hex values shared/BlooColors.kt already centralizes
// (bit-identical today, one edit away from silently diverging like chargerLabel's text had).
internal val ChargeGreen = Color(com.bloo.bluelink.data.BlooColors.chargeGreen)
internal val ChargeGreenDark = Color(com.bloo.bluelink.data.BlooColors.chargeGreenDark)

/** Climate seat/wheel tints, on the canonical semantic heat/cool tokens. */
internal val Heat = Color(com.bloo.bluelink.data.BlooColors.heat)
internal val Cool = Color(com.bloo.bluelink.data.BlooColors.cool)

/** The "something new" notification dot colour (the canonical warning amber). */
internal val UpdateAvailableAmber = Color(com.bloo.bluelink.data.BlooColors.warn)

/**
 * The charge bar's "topped up" state: the pack has reached its configured limit. See
 * ChargeSegmentBar.
 */
internal val ChargeBlue = Color(com.bloo.bluelink.data.BlooColors.chargeBlue)
internal val ChargeBlueDark = Color(com.bloo.bluelink.data.BlooColors.chargeBlueDark)

/**
 * The app's two action colours: green confirms (go ahead, save, sign in, start), red denies (remove, sign out,
 * stop, discard). One pair each, tuned per theme, used by every button that commits or reverses something.
 */
@androidx.compose.runtime.Immutable
internal class ActionTone(val container: Color, val content: Color)

@Composable
internal fun confirmTone(): ActionTone =
    if (appIsDarkTheme()) ActionTone(Color(0xFF7BD389), Color(0xFF06290E)) else ActionTone(Color(0xFF1E7B34), Color.White)

@Composable
internal fun denyTone(): ActionTone =
    if (appIsDarkTheme()) ActionTone(Color(0xFFFF8A80), Color(0xFF4A0507)) else ActionTone(Color(0xFFC62828), Color.White)

/** The app's muted/secondary-text alpha over LocalContentColor. 0.86: strong enough to clear contrast on busy glass, still a step below full so a value can stand out beside its label. */
internal const val MutedContentAlpha = 0.86f

/**
 * The ambient content colour, muted: secondary text on any surface (tinted pebbles, glass, the map
 * pill).
 */
@androidx.compose.runtime.Composable
internal fun mutedContentColor(): androidx.compose.ui.graphics.Color =
    androidx.compose.material3.LocalContentColor.current.copy(alpha = MutedContentAlpha)

/**
 * Text and icons drawn on the hero card, which is always the OPPOSITE tone of the theme (a dark card in
 * the light theme, a light card in the dark theme), so its text is the opposite of the surface's own.
 */
@Composable
internal fun heroOnPhoto(): Color = if (appIsDarkTheme()) Color(0xFF1A1B20) else Color(0xFFF7F7FA)

// ---- Sizing -------------------------------------------------------------------

/** Shared control height: a collapsed pebble matches the lock/unlock button. */
internal val ControlHeight = 84.dp

/** Uniform collapsed-header height so every pebble lines up at the same size. */
internal val PebbleHeaderHeight = ControlHeight

/** A collapsed pebble's corner: half its height, so it is a true pill. */
internal val PebbleCornerCollapsed = ControlHeight / 2
internal val PebbleCornerExpanded = 20.dp

/** The charge bar's height, shared by every surface that draws it. */
internal val ChargeBarHeight = 18.dp

/**
 * Gap on both sides of every internal charge-bar boundary so each segment is its own rounded piece.
 */
internal val ChargeSegmentGap = 5.dp

/**
 * Gap between the hero's collapsed readout and its card's bottom edge. Shared by the readout's
 * bottom padding and the header's reserved height, which must agree.
 */
internal val HeroReadoutBottomInset = 14.dp

/**
 * The horizontal inset every pebble's content sits at (header icon, summary, revealed content); the
 * lock pebble reaches it as 4 + 8 through a nested Box. Kept tight so content reaches closer to the
 * card edge.
 */
internal val PebbleContentInset = 12.dp

/** The horizontal gutter a full page's content sits inside (car pages and Settings). */
internal val ScreenGutter = 12.dp

/**
 * The app's one bottom-chrome bar height, and the whole family that shares it: the search element
 * (bubble / pill / bar) and every toast that grows out of it, plus the settings tab. A toast is
 * exactly the bar it emerged from, so the two must never disagree -- one token, used by all of them.
 * Sized a step taller than it used to be (52dp), for a more comfortable tap target and a calmer bar.
 */
internal val ChromeBarHeight = 62.dp

/**
 * The app's vertical rhythm: one base unit ([SpaceUnit]) that every gap and inset is a multiple of.
 * Scales at half the display scale (see [spaceScaleFor]); provided by [BlooTheme], read via
 * [LocalSpaceScale].
 */
internal val LocalSpaceScale = androidx.compose.runtime.compositionLocalOf { 1f }

/** Display scale → gap scale: gaps breathe with the app's display scale at half its swing. */
internal fun spaceScaleFor(scale: Float): Float = 1f + (scale - 1f) * 0.5f

internal const val MaxFontScale = 1.8f

/** The one spacing unit (4dp at 1.0 scale). Everything below is a multiple of it. */
internal val SpaceUnit: Dp @Composable get() = 4.dp * LocalSpaceScale.current
internal val GapHairline: Dp @Composable get() = SpaceUnit
internal val GapRow: Dp @Composable get() = SpaceUnit * 2
internal val GapGroup: Dp @Composable get() = SpaceUnit * 3
internal val GapSection: Dp @Composable get() = SpaceUnit * 4
internal val GapPage: Dp @Composable get() = SpaceUnit * 5
internal val GapBlock: Dp @Composable get() = SpaceUnit * 6
internal val GapHero: Dp @Composable get() = SpaceUnit * 8

/** The seam between the two halves of a split button (action + chevron, preset + menu). */
internal val SplitSeam = 3.dp

// ---- Shapes ---------------------------------------------------------------------
//
// Common corner radii; change a shape here, not per file.
internal val TinyShape = RoundedCornerShape(8.dp)    // Inline tap targets, swatches
internal val SmallShape = RoundedCornerShape(12.dp)   // Smaller components, chips
internal val StandardShape = RoundedCornerShape(16.dp) // Buttons, most cards
internal val ExtraLargeShape = RoundedCornerShape(28.dp) // Modal dialogs

// ---- Icons ----------------------------------------------------------------------
//
// Commonly used icons centralized to avoid duplicate imports.
object AppIcons {
  // Icons.Filled.X, not a bare `X`: these are extension properties, and a bare name would resolve
  // to this very property (self-referencing initializer).
  val Settings = Icons.Filled.Settings
  val Lock = Icons.Filled.Lock
  val Bolt = Icons.Filled.Bolt
  val Close = Icons.Filled.Close
  val ArrowForward = Icons.AutoMirrored.Filled.ArrowForward
  val Search = Icons.Filled.Search
  val Check = Icons.Filled.Check
  val CheckCircle = Icons.Filled.CheckCircle
  val Info = Icons.Filled.Info
  val DirectionsCar = Icons.Filled.DirectionsCar
  val AutoAwesome = Icons.Filled.AutoAwesome
  val Warning = Icons.Filled.Warning
  val Fullscreen = Icons.Filled.Fullscreen
  val FullscreenExit = Icons.Filled.FullscreenExit
}

// ---- Blur -----------------------------------------------------------------------
//
// Shared Haze backdrop-blur gate and progressive-blur instances used by every blur site.

/**
 * The device-resource policy (battery saver, thermal throttling), collected ONCE at the app root and
 * read here. [com.bloo.bluelink.data.PowerPolicy] is the single place that says what changes when the
 * device is short on power: every visual or animated "lean" decision below reads it, never the system
 * directly. Collected once because every call site used to collect the flow itself: about ten per
 * pebble, hundreds of live collectors across the pages in the pager, all created mid-swipe.
 */
internal val LocalPowerPolicy = androidx.compose.runtime.compositionLocalOf {
    com.bloo.bluelink.data.PowerPolicy.of(com.bloo.bluelink.data.DeviceState())
}

/** Real backdrop blur: API 31+ (Haze's RenderEffect; no-op below) and the policy allows it. */
@Composable
internal fun canBlurBackdrops(): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && LocalPowerPolicy.current.blurBackdrops

/**
 * Battery-saver-aware replacement for a bouncy [spring]: the real spring normally, a short
 * critically-damped [tween] (no overshoot, fewer frames) under battery saver.
 */
@Composable
internal fun <T> lowPowerAwareSpring(dampingRatio: Float, stiffness: Float) =
    if (!LocalPowerPolicy.current.bouncySprings) {
        tween<T>(durationMillis = 140, easing = androidx.compose.animation.core.FastOutSlowInEasing)
    } else {
        spring<T>(dampingRatio = dampingRatio, stiffness = stiffness)
    }

/**
 * The shape every full-height scrim's blur uses: strong at its edge, tapering to none by the far
 * edge.
 */
internal val StandardBlurProgressive
    get() = dev.chrisbanes.haze.HazeProgressive.verticalGradient(
        // Strong at top (status bar legibility), none at bottom.
        startIntensity = 1f,
        endIntensity = 0f
    )

/**
 * The one hairline: the outline of an inner box, a disabled button's rim, a divider-weight edge.
 */
internal const val HairlineAlpha = 0.14f

@androidx.compose.runtime.Composable
internal fun hairlineColor(): androidx.compose.ui.graphics.Color =
    androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = HairlineAlpha)

// ---- Motion durations (ms) -------------------------------------------------------
//
// The handful of tween lengths the UI uses, so the same kind of transition takes the same time
// everywhere. Springs set their own timing; these are for fades, slides and colour changes.
internal const val MotionFast = 140    // micro feedback, hints leaving
internal const val MotionShort = 180   // fades and small slides
internal const val MotionMedium = 320  // panels, cards, colour sweeps
internal const val MotionLong = 400    // large or emphasised moves
