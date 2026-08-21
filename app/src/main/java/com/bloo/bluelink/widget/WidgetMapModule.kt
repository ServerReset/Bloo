package com.bloo.bluelink.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height

/**
 * The Glance display layer over [WidgetMap]'s bitmap: the location thumbnail
 * as a tier layout actually places it, either filling the room it was given
 * ([MapFill], a ColumnScope extension so it can take the remaining weight) or
 * as a fixed-height module above other content ([MapModule]).
 *
 * Separate from WidgetMap.kt on purpose. That file is deliberately Glance-free
 * -- it exists because Glance cannot use async image painters, so the map has
 * to be fetched, mosaicked and drawn to a plain Bitmap off the composition.
 * Mixing the composables that DISPLAY that bitmap into the file that PRODUCES
 * it would put Glance imports back into the one part of the map path that is
 * meant not to have them.
 */

// Extension on ColumnScope, NOT a plain composable: defaultWeight() is
// declared INSIDE Row/ColumnScope rather than on GlanceModifier itself, so
// it only resolves where that receiver is in scope. Every other use in this
// file happens to sit directly inside a Column lambda and gets it for free;
// pulling this out into its own function is what surfaced that.
/** The map as a weighted module, taking the slack its caller reserved
 *  for it ([room]) -- which is 0 when the caller worked out there wasn't
 *  enough of it to be a map, and then this is a plain spacer again. The
 *  weight is what actually sizes it; [room] is how the caller and this
 *  function agree on whether it is drawn at all. */
/**
 * Fills a tall layout's leftover vertical space with the location map.
 *
 * Every big tier used to end with a bare `Spacer(defaultWeight())`, which
 * by definition collects ALL the slack in one place -- on a 600x520dp tile
 * that was a black gap taller than the content above it, with the buttons
 * shoved against the bottom edge. Reported from a real device.
 *
 * The map is exactly what belongs there: it's the one piece of content
 * that genuinely wants more room the more room there is, so it takes the
 * weight instead of a spacer and grows with the widget. Without
 * coordinates (or with the map switched off) it falls back to the spacer,
 * because leaving a gap is still better than stretching something that
 * was never meant to fill.
 */
@Composable
internal fun ColumnScope.MapFill(render: Render, room: Dp) {
    val bmp = render.mapBitmap
    if (bmp == null || room <= 0.dp) {
        Spacer(GlanceModifier.defaultWeight())
        return
    }
    Spacer(GlanceModifier.height(8.dp))
    Image(
        provider = ImageProvider(bmp),
        contentDescription = "Car location",
        contentScale = ContentScale.Crop,
        // Tappable, like every other part of this widget. A map of where
        // the car is invites a tap more than anything else on the tile,
        // and it was the one large region that did nothing.
        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            .cornerRadius(innerCorner(render.config))
            .clickable(openAction(LocalContext.current, render.car?.vin)),
    )
    Spacer(GlanceModifier.height(8.dp))
}

/** The map at a fixed height, for the tiers that place it among
 *  fixed-height siblings. [room] is what the caller has actually got
 *  left for it -- the height used to be [Scale.mapHeight] unconditionally,
 *  which on a cramped square tile was more than the whole column had
 *  left and pushed the ring beside it to nothing.
 *
 *  [room] is the TOTAL this module may spend, leading Spacer included --
 *  the image's own height is [room] minus that spacer, not [room]
 *  itself. This used to hand the Image the whole of [room] as its own
 *  cap, on top of the Spacer drawn right before it, so whenever the
 *  caller's own budget was the binding constraint (not [Scale.mapHeight]),
 *  the module consumed exactly 8dp more than it was ever given -- the
 *  same silent overflow class fixed everywhere else in this file, just
 *  reintroduced by a spacer the room math forgot to subtract. Confirmed
 *  by rebuilding [Scale.mapReserve]/[Scale.ringRoom] for real XL_WIDE
 *  sizes: the overflow reproduces to exactly 8dp whenever the map's own
 *  budget is capped below its ideal height, not just at one contrived
 *  point. */
/** The location map thumbnail, shown only when the pre-fetched bitmap exists
 *  (config.showMap on + car has coords + tile fetched OK). Rounded corners to
 *  match the widget's card language. */
@Composable
internal fun MapModule(render: Render, room: Dp) {
    val bmp = render.mapBitmap ?: return
    // The MIN check is against the image's own height, not room itself
    // -- room includes the leading spacer, and a map judged "worth
    // drawing" has to mean the picture itself clears the floor, not the
    // spacer padding it out to look like it does.
    // FILLS the room it was given, rather than capping at a preferred height and
    // leaving the rest of the band blank.
    //
    // The map is the greediest module in the allocator (it takes the largest share
    // of leftover height, because it is the one piece that genuinely improves with
    // room) and it was then refusing most of what it won: capped at ~110dp, a tall
    // tile handed it 250dp and it drew 110, leaving 140dp of empty card between the
    // range and the buttons. That gap is what made big widgets look unfinished.
    //
    // Safe to fill because the bitmap is Crop-scaled: a taller box shows MORE of the
    // neighbourhood around the car, which is the thing a location thumbnail wants
    // anyway, rather than stretching the image.
    val imageHeight = (room - 8.dp).coerceAtLeast(0.dp)
    if (imageHeight < Scale.MAP_MIN) return
    Spacer(GlanceModifier.height(8.dp))
    Image(
        provider = ImageProvider(bmp),
        contentDescription = "Car location",
        contentScale = ContentScale.Crop,
        modifier = GlanceModifier.fillMaxWidth()
            .height(imageHeight)
            .cornerRadius(innerCorner(render.config))
            .clickable(openAction(LocalContext.current, render.car?.vin)),
    )
}
