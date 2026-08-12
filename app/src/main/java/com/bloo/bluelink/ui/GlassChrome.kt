package com.bloo.bluelink.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.dropShadow

/**
 * The alpha floating chrome's own solid tint should use for its base fill --
 * a flat, frosted semi-transparent look used everywhere in the app (search
 * bar, floating buttons, pebble backgrounds, dialogs, widget). Liquid/Ultra
 * glass (a real hardware-blurred, refractive material) was removed in favor
 * of this simpler, consistent look across every device and surface.
 *
 * The history: 0.62 read as barely-there over a bright patch of car photo
 * (no blur to separate floating chrome from what's behind it), so it was
 * raised to 0.74. Lowered again to 0.68 on request -- more transparent than
 * that 0.74 baseline was -- while still staying clear of the specific 0.62
 * value already found insufficient. If a bright-photo legibility complaint
 * comes back, this is the number that moved.
 *
 * Every call site is meant to share this one constant with NO override, which
 * several stopped doing (0.86 to 0.98, scattered across a dialog, a settings
 * card row, and two panels) -- each without a legibility reason distinct from
 * what every other piece of frosted chrome already has, which made "the same
 * transparency everywhere" untrue in practice despite this function's own
 * intent. Those overrides are gone; every one of those call sites now takes
 * the bare default. The two that keep an explicit override (the cover
 * screen's collapsed/compact search pill, in SettingsScreen) have their own
 * standing comment for why: a very small, isolated control over an
 * unpredictable photo hero, where "reads as a distinct tappable thing at
 * all" is a different, stronger requirement than the general frosted look.
 */
fun glassContainerAlpha(frosted: Float = 0.68f): Float = frosted

/**
 * The hairline rim every piece of frosted chrome (floating pills, dialogs,
 * pebbles) should share -- brighter along the top, fading down the sides,
 * like a real card's edge catching ambient light. Without the old glass
 * blur behind these surfaces, this rim is what now reads as "this element
 * is a distinct, lifted piece of material" rather than a flat color patch.
 *
 * Strengthened alongside [glassContainerAlpha] for the same reason: over a
 * busy or light car photo, a near-invisible 0.05-0.14 alpha border wasn't
 * enough edge definition to keep floating buttons from blending into the
 * image behind them.
 */
@Composable
fun Modifier.frostedRim(shape: Shape): Modifier {
    val onSurface = MaterialTheme.colorScheme.onSurface
    // A single 1dp border stroke, but painted with a vertical gradient brush instead of a
    // flat color: brightest at the top edge (0.24 alpha), dimmest through the middle
    // (0.10), then a touch brighter again at the bottom (0.16) -- the top-lit/bottom-dim
    // asymmetry is what reads as a physical highlight, since Compose's border draws this
    // same brush uniformly all the way around the shape's outline.
    return this.border(
        BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    onSurface.copy(alpha = 0.24f),
                    onSurface.copy(alpha = 0.10f),
                    onSurface.copy(alpha = 0.16f),
                ),
            ),
        ),
        shape,
    )
}

/**
 * A soft, symmetric dark halo with no offset, drawn all the way around the
 * shape. [com.bloo.uicommon.dropShadow]'s own default (used right alongside
 * this on every floating piece of chrome) is offset downward for a depth
 * cue, which only ever darkens the backdrop on one side -- against a bright
 * patch of car photo elsewhere around the shape (above it, or to a side),
 * nothing was darkening the background there, and frostedRim's onSurface-tinted
 * border (white in dark mode) washes out the same way against a light photo.
 * Meant to be chained before dropShadow/frostedRim on anything that floats
 * over an unpredictable photo background.
 */
@Composable
fun Modifier.ambientRing(shape: Shape): Modifier =
    this.dropShadow(shape, color = Color.Black.copy(alpha = 0.30f), blurRadius = 10.dp, offsetY = 0.dp, offsetX = 0.dp)
