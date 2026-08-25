package com.bloo.uicommon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.bloo.uicommon.dropShadow

/**
 * The shared "floating chrome" visual kit: the semi-transparent base-fill
 * alpha, the top-lit hairline rim, and the symmetric ambient halo -- the
 * pieces every floating pill, dialog, arrow and pebble edge in the family
 * shares, usable by the PHONE app, the WATCH app, the widget surfaces and
 * anything else that wants its floating chrome to read as one family without
 * importing the phone module.
 *
 * Foundation-only by design (no Material dependency, matching :uicommon's
 * own rule): the rim takes its tint as a parameter. The phone passes
 * `MaterialTheme.colorScheme.onSurface`; the watch passes
 * wear-material3's own onSurface; a widget passes whatever its surface
 * resolves. Nothing here invents a theme.
 *
 * Alpha history (one number every frosted surface must agree on): 0.62 read
 * as barely-there over a bright patch of car photo, so it was raised to
 * 0.74; lowered again to 0.68 on request, staying clear of the 0.62 that
 * was already found insufficient.
 */
fun glassContainerAlpha(frosted: Float = 0.68f): Float = frosted

/**
 * The hairline rim every piece of frosted chrome (floating pills, dialogs,
 * pebbles) should share -- brighter along the top, fading down the sides,
 * like a real card's edge catching ambient light. Without real glass blur
 * behind these surfaces, this rim is what reads as "this element is a
 * distinct, lifted piece of material" rather than a flat color patch.
 *
 * [onSurface] is the tint; pass your theme's onSurface and the rim
 * self-inverts for the surface (white-ish in dark, dark in light).
 */
fun Modifier.frostedRim(shape: Shape, onSurface: Color): Modifier =
    // A single 1dp border stroke, but painted with a vertical gradient brush
    // instead of a flat color: brightest at the top edge (0.24 alpha), dimmest
    // through the middle (0.10), then a touch brighter again at the bottom
    // (0.16) -- the top-lit/bottom-dim asymmetry is what reads as a physical
    // highlight, since Compose's border draws this same brush uniformly all
    // the way around the shape's outline.
    this.border(
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

/**
 * A soft, symmetric dark halo with no offset, drawn all the way around the
 * shape. [dropShadow]'s own default (used right alongside this on every
 * floating piece of chrome) is offset downward for a depth cue, which only
 * ever darkens the backdrop on one side -- against a bright patch of car
 * photo elsewhere around the shape, nothing was darkening the background
 * there, and the frostedRim's onSurface-tinted border washes out the same
 * way against a light photo. Meant to be chained before dropShadow/
 * frostedRim on anything that floats over an unpredictable photo background.
 */
fun Modifier.ambientRing(shape: Shape): Modifier =
    this.dropShadow(shape, color = Color.Black.copy(alpha = 0.30f), blurRadius = 10.dp, offsetY = 0.dp, offsetX = 0.dp)