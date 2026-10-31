package com.bloo.bluelink.wear

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/*
 * The face's sizes: the numbers every piece of the face shares, scaled to the display.
 */

/** The ring's stroke and the gap between it and the tiles inside it. */
internal val RingStroke = 8.dp
internal val RingGap = 5.dp

/** The gap between the four quarter tiles. */
internal val TileGap = 3.dp

/** The hub's share of the inner circle's diameter. */
internal const val HubFraction = 0.34f

/** The display the face's fixed sizes (ring, gaps, glyphs) were drawn for; every size scales by (real display / this). */
internal val ReferenceFace = 227.dp

/**
 * The face's size relative to the 227dp it was designed on, so a 192dp watch and a 240dp watch (and any display size setting, which changes
 * the dp the display has) get the same face, proportionally.
 */
internal val LocalFaceUnit = compositionLocalOf { 1f }
