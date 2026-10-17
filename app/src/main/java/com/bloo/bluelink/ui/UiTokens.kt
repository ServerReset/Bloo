package com.bloo.bluelink.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
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

// ---- Blur -----------------------------------------------------------------------
//
// Shared Haze backdrop-blur gate and progressive-blur instances used by every blur site.



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


// ---- Motion durations (ms) -------------------------------------------------------
//
// The handful of tween lengths the UI uses, so the same kind of transition takes the same time
// everywhere. Springs set their own timing; these are for fades, slides and colour changes.
internal const val MotionFast = 140    // micro feedback, hints leaving
