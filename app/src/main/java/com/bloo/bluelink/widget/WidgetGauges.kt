package com.bloo.bluelink.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider
import com.bloo.bluelink.R
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.VehicleSnapshot

/**
 * Fourth slice out of CarWidget.kt: everything that DRAWS the car's state as a
 * graphic rather than as text -- the charge ring, the lock/plug glyph, the
 * choice between them, and the horizontal charge bar with its two fallbacks.
 *
 * A provably closed set, which is why it was safe to take in one move: none of
 * these six reference anything still left in CarWidget, and their own
 * cross-references (RingOrGlyph to the ring and the glyph, MiniStatus to
 * RingOrGlyph, ChargeBarFallback to ChargeBar) stay inside this file. They are
 * called from thirteen of the eighteen tier layouts, which is exactly why they
 * belong somewhere those layouts can share rather than buried among them.
 *
 * The one gauge NOT here is BarHero: it composes NameAndStat and
 * PrimaryInfoLine when its big percentage will not fit, so it is really an
 * info module wearing a gauge's clothes and it moves with them, not with these.
 */

/**
 * The widget's hero status mark: the charge [RingImage] when the ring is enabled and a
 * percent exists, otherwise the [StatusGlyph] (lock/plug icon) at the same [edgeDp].
 *
 * This one choice was hand-written identically in eight tier heroes -- every one passing
 * the SAME edge to both branches -- so the ring-vs-glyph rule (and the fact both take the
 * same size) lived in eight places that could drift. Both underlying composables already
 * early-return when [edgeDp] is too small to read, so callers with a real reserved edge
 * need no size guard: a caller that was given a real edge by the blueprint can pass it
 * straight through, and one that was not never reaches here, because an undrawable mark
 * is decided against up front rather than discovered halfway down.
 */
@Composable
internal fun RingOrGlyph(car: VehicleSnapshot, render: Render, edgeDp: Int) {
    if (render.config.showRing && car.percent != null) {
        RingImage(car, render, edgeDp = edgeDp)
    } else {
        StatusGlyph(car, render.theme, sizeDp = edgeDp)
    }
}

@Composable
internal fun RingImage(car: VehicleSnapshot, render: Render, edgeDp: Int) {
    // Scale.ring yields 0 when the column can't fit a legible ring.
    if (edgeDp <= 0) return
    val ctx = LocalContext.current
    val density = ctx.resources.displayMetrics.density
    val px = (edgeDp * density).toInt().coerceAtLeast(24)
    val frac = (car.percent ?: 0) / 100f
    val arc = ChargeRing.arcColorFor(car.percent, car.charging == true, render.theme.accentArgb)
    val bmp = ChargeRing.render(
        sizePx = px,
        fraction = frac,
        arcColor = arc,
        trackColor = render.theme.trackArgb,
        centerText = car.percent?.let { "$it%" },
        centerColor = arc,
        // Only on rings big enough for the notch to read as one. Below
        // that it's a nick in a small circle, which says less than the
        // unbroken ring does.
        limitFraction = car.chargeLimitPct
            ?.takeIf { edgeDp >= 44 }
            ?.let { it.coerceIn(0, 100) / 100f },
    )
    Image(
        provider = ImageProvider(bmp),
        contentDescription = "${car.percent ?: 0} percent",
        modifier = GlanceModifier.size(edgeDp.dp),
    )
}

@Composable
internal fun StatusGlyph(car: VehicleSnapshot, theme: WidgetTheme, sizeDp: Int) {
    if (sizeDp <= 0) return
    // A car that has never reported a lock state renders NOTHING, rather
    // than resolving `null` into the unlocked branch and putting a confident
    // red open padlock on the home screen for a state nobody has heard yet.
    // `infoValue`'s own LOCK case already draws this distinction (it returns
    // null rather than guessing), and the watch's ToggleStateComplication
    // omits its icon entirely for the same reason: a definite glyph is
    // indistinguishable from a confirmed reading.
    //
    // Yielding nothing is the same contract Scale.ring and heroSpIn use, and
    // the callers already handle it -- this is only reached when the ring is
    // off or percent is unknown, i.e. a tile that has little else to show
    // either way.
    val locked = car.locked ?: return
    val res = if (locked) R.drawable.ic_shortcut_lock else R.drawable.ic_shortcut_unlock
    val tint = if (locked) theme.accentProvider else theme.unlocked
    Image(
        provider = ImageProvider(res),
        contentDescription = if (locked) "Locked" else "Unlocked",
        colorFilter = ColorFilter.tint(tint),
        modifier = GlanceModifier.size(sizeDp.dp),
    )
}

/**
 * The horizontal charge bar under [BarHero]'s number.
 *
 * Split at the car's charge limit when it reported one, so the stretch
 * the car has been told not to fill reads as a separate, dimmer segment
 * rather than as more headroom -- the same shape the phone's hero card
 * and the live charging notification now use for the same value.
 *
 * Glance has no fractional width, but the exact slot width is known
 * here, so every piece is computed in dp rather than guessed. Each
 * segment's fill is its own local share of the global charge, which is
 * what keeps a charge that has overrun its limit reading correctly
 * instead of clamping invisibly at the seam.
 */
@Composable
internal fun ChargeBar(car: VehicleSnapshot, theme: WidgetTheme, width: Dp, height: Dp) {
    val pct = (car.percent ?: 0).coerceIn(0, 100)
    val frac = pct / 100f
    val limit = car.chargeLimitPct?.takeIf { it in 1..99 }
    // Split at the CHARGE: green is what's in the pack, grey is what
    // isn't, and the gap between them is the level. The limit is a marker
    // ON that bar, drawn below, not a second division of it -- the same
    // model the phone hero, the watch ring and the widget's own ring use.
    //
    // The common case is the charge sitting AT its own limit -- a car
    // set to 80% and charged to 80%, which is most of the time this bar
    // is drawn at all. The split and the dot then land on the same
    // pixel: a real gap cut into the bar right where the dot's own halo
    // is also trying to cut one, reading as a ragged double-notch rather
    // than either device on its own. Ported from the phone's own
    // ChargeSegmentBar, which found this the same way. Near the split
    // the gap yields and the dot alone carries it.
    val atSplit = limit != null && kotlin.math.abs(limit / 100f - frac) < 0.03f
    // The gap costs real width, so it is skipped on a narrow slot, at
    // the extremes where there is nothing to separate, and right at the
    // limit where the dot already marks the same spot.
    val gap = if (width >= 60.dp && frac > 0.02f && frac < 0.98f && !atSplit) 3.dp else 0.dp
    val usable = (width - gap).coerceAtLeast(0.dp)
    // Floored at the bar's own height when there is ANY charge, so a low
    // one reads as a rounded nub rather than a hairline: below that the
    // 50% corner radius eats the whole shape and 3% looks identical to 0%.
    // Capped at `usable` so the floor can't overrun a narrow slot, and
    // `rest` is derived from the result rather than computed in parallel,
    // so the two always still sum to the bar.
    val filled = if (frac <= 0f) 0.dp else minOf(usable, maxOf(usable * frac, height))
    val rest = (usable - filled).coerceAtLeast(0.dp)
    val fillColor = if (car.charging == true) theme.charge else theme.accentProvider
    // Glance has no z-stacking of a marker over a Row without a Box, so
    // the whole bar lives in one: the Row paints, the dot overlays.
    Box(modifier = GlanceModifier.width(width).height(height)) {
        Row(modifier = GlanceModifier.width(width).height(height)) {
            if (filled > 0.dp) {
                Box(
                    modifier = GlanceModifier.width(filled).height(height)
                        .cornerRadius(height / 2).background(fillColor),
                ) {}
            }
            if (rest > 0.dp) {
                if (filled > 0.dp) Spacer(GlanceModifier.width(gap))
                Box(
                    modifier = GlanceModifier.width(rest).height(height)
                        .cornerRadius(height / 2).background(theme.surfaceVariant),
                ) {}
            }
        }
        // The marker. Positioned by a leading spacer rather than an offset
        // -- Glance has no translation modifier, so "put this at x" is
        // spelled "reserve x of empty space first".
        if (limit != null && width >= 60.dp) {
            val l = limit / 100f
            // Exactly the bar's height, NOT taller: the dot lives in a
            // Box sized to the bar (see above), which every caller has
            // already budgeted exactly `height` of room for -- a dot
            // that reads bigger without actually being taller than the
            // bar it sits on has to come from its own proportions, not
            // from claiming more room this composable was never given.
            val dot = height
            val x = (usable * l + (if (l > frac) gap else 0.dp) - dot / 2)
                .coerceIn(0.dp, (width - dot).coerceAtLeast(0.dp))
            Row(modifier = GlanceModifier.width(width).height(height)) {
                Spacer(GlanceModifier.width(x))
                Box(
                    // FIXED colours, not swapped by which side of the fill
                    // this lands on -- see the phone's drawChargeLimitDot for
                    // the full reasoning. Flipping the core between
                    // theme.background and fillColor made the marker a
                    // same-coloured hole once the charge reached the
                    // limit (background halo + background core), which is
                    // exactly the state this bar is in most of the time.
                    // theme.background (not surface -- WidgetTheme has no
                    // `surface`) cuts a visible window in the bar
                    // regardless of what's under it; onSurface inside
                    // that window is guaranteed to contrast against it.
                    modifier = GlanceModifier.size(dot)
                        .cornerRadius(dot / 2)
                        .background(theme.background),
                ) {
                    // A PROPORTION of the dot (0.6x), not dot minus a flat
                    // 5dp -- the flat version left almost nothing at small
                    // sizes (an 8dp dot on a small tile's thin bar had a
                    // 3dp core, reading as a grey smudge rather than a
                    // ring with a visible centre) while barely mattering
                    // at large ones. Proportional keeps the ring-to-core
                    // ratio legible at every bar thickness this scales to.
                    val core = (dot * 0.6f).coerceAtLeast(3.dp)
                    Box(
                        modifier = GlanceModifier.size(core)
                            .cornerRadius(core / 2)
                            .background(theme.onSurface),
                    ) {}
                }
            }
        }
    }
}

/**
 * The charge as a VERTICAL bar that fills from the bottom, for a tile that has
 * height and almost no width.
 *
 * The mirror of [ChargeBar], and it exists because the horizontal one is the
 * wrong instrument on a tall narrow tile: a 60dp-wide, 300dp-tall shape gave a
 * ring a 60dp circle with 240dp of nothing under it, and a horizontal bar a
 * 60dp stub. Both read as an empty tile. A fill rising up the long axis uses
 * exactly the room that shape actually has, and it needs no label to be
 * understood -- which matters, because a tile this narrow may not have room for
 * a legible percentage beside it.
 *
 * Deliberately simpler than [ChargeBar]: no limit marker and no split gap. Both
 * are width-dependent devices (the gap is skipped below 60dp there for the same
 * reason) and this bar's whole premise is that width is what it does not have.
 * The charge colour still carries the charging state.
 */
@Composable
internal fun VerticalChargeBar(
    car: VehicleSnapshot,
    theme: WidgetTheme,
    width: Dp,
    height: Dp,
) {
    val pct = (car.percent ?: 0).coerceIn(0, 100)
    val frac = pct / 100f
    // Floored at the bar's own width when there is any charge at all, so a low
    // reading is a rounded nub rather than a hairline -- the same reasoning as
    // ChargeBar's floor, on the other axis.
    val filled = if (frac <= 0f) 0.dp else minOf(height, maxOf(height * frac, width))
    val empty = (height - filled).coerceAtLeast(0.dp)
    val fillColor = if (car.charging == true) theme.charge else theme.accentProvider
    Column(modifier = GlanceModifier.width(width).height(height)) {
        // Empty portion FIRST: a Column stacks downward, so the fill has to be
        // the last child for it to sit at the bottom and rise as charge grows.
        if (empty > 0.dp) {
            Box(
                modifier = GlanceModifier.width(width).height(empty)
                    .cornerRadius(width / 2).background(theme.surfaceVariant),
            ) {}
        }
        if (filled > 0.dp) {
            Box(
                modifier = GlanceModifier.width(width).height(filled)
                    .cornerRadius(width / 2).background(fillColor),
            ) {}
        }
    }
}
