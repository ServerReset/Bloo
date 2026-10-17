package com.bloo.uicommon

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's vertical rhythm: one base unit that every gap and inset is a multiple of, scaled at half
 * the display scale. Provided by the theme, read through [LocalSpaceScale].
 *
 * Here rather than in the app because the button system is built on it, and the button system is now
 * shared. Nothing about the numbers changed; only where they are declared.
 */

val LocalSpaceScale = androidx.compose.runtime.compositionLocalOf { 1f }

/** Display scale → gap scale: gaps breathe with the app's display scale at half its swing. */
fun spaceScaleFor(scale: Float): Float = 1f + (scale - 1f) * 0.5f

const val MaxFontScale = 1.8f

/** The one spacing unit (4dp at 1.0 scale). Everything below is a multiple of it. */
val SpaceUnit: Dp @Composable get() = 4.dp * LocalSpaceScale.current
val GapHairline: Dp @Composable get() = SpaceUnit
val GapRow: Dp @Composable get() = SpaceUnit * 2
val GapGroup: Dp @Composable get() = SpaceUnit * 3
val GapSection: Dp @Composable get() = SpaceUnit * 4
val GapPage: Dp @Composable get() = SpaceUnit * 5
val GapBlock: Dp @Composable get() = SpaceUnit * 6
val GapHero: Dp @Composable get() = SpaceUnit * 8

val ButtonPaddingH = 18.dp
val ChipPaddingH = 16.dp

