package com.bloo.bluelink.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.bloo.bluelink.R
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
 * Three separately-rounded segments -- filled up to the current charge, track up
 * to the limit, a darker-backdrop dim track past it -- or two when the charge is
 * already at (or past) its own limit, since there's no "still filling toward the
 * limit" zone left once it's already there. Ported from the phone's own
 * ChargeSegmentBar, which explains the whole progression (a marker, then gapped,
 * then flush, now separately-rounded again) in full -- this file has always
 * mirrored whichever design that one landed on, and does again here.
 *
 * Glance corner-rounds each box independently anyway (no clip-path API to round
 * only the bar's outer ends the way the phone's Canvas can choose to), so this
 * surface was already halfway to "three separate pills" by construction --
 * explicitly requested over a flush, gapless version this file used to draw
 * instead. A real [Spacer] now sits between every piece to match. Glance also
 * has no fractional width, but the exact slot width is known here, so every
 * segment is computed in dp rather than guessed.
 */
@Composable
internal fun ChargeBar(car: VehicleSnapshot, theme: WidgetTheme, width: Dp, height: Dp) {
    val pct = (car.percent ?: 0).coerceIn(0, 100)
    val limit = car.chargeLimitPct?.takeIf { it in 1..99 }
    // Independent of car.charging -- see the phone's ChargeReadout.stuckAtLimit for why:
    // a car reported charged to its limit reads blue even hours later, unplugged.
    val stuckAtLimit = limit != null && pct >= limit
    val fillColor = if (stuckAtLimit) theme.chargeAtLimit else theme.charge
    // The actual segment math lives in widgetChargeBarLayout, a plain function with no
    // Glance/Compose dependency, specifically so it's unit-testable -- see
    // WidgetChargeBarLayoutTest, which sweeps a wide range of width x percent x limit
    // combinations asserting the three-segment case genuinely produces three
    // positive-width, correctly-gapped segments, not just that the formula looks
    // right by eye.
    val layout = widgetChargeBarLayout(width, height, pct / 100f, limit, stuckAtLimit)
    Row(modifier = GlanceModifier.width(width).height(height)) {
        if (layout.filled > 0.dp) {
            Box(
                modifier = GlanceModifier.width(layout.filled).height(height)
                    .cornerRadius(height / 2).background(fillColor),
            ) {}
        }
        if (layout.hasSingleTrack) {
            // One remaining segment: the ordinary track when there's no limit to speak
            // of, the darker backdrop + dim tint when the charge is stuck there -- the
            // whole remainder past the current charge means "won't fill further" in
            // that case, not "still on the way".
            if (layout.singleTrackWidth > 0.dp) {
                Spacer(GlanceModifier.width(layout.gap))
                if (layout.singleTrackDim) {
                    ChargeBarFarSegment(layout.singleTrackWidth, height, theme)
                } else {
                    Box(
                        modifier = GlanceModifier.width(layout.singleTrackWidth).height(height)
                            .cornerRadius(height / 2).background(theme.surfaceVariant),
                    ) {}
                }
            }
        } else {
            // Two remaining segments, each its own rounded piece: current -> limit
            // (still filling toward it), limit -> 100% (won't fill past it).
            if (layout.mid > 0.dp) {
                Spacer(GlanceModifier.width(layout.gap))
                Box(
                    modifier = GlanceModifier.width(layout.mid).height(height)
                        .cornerRadius(height / 2).background(theme.surfaceVariant),
                ) {}
            }
            if (layout.far > 0.dp) {
                Spacer(GlanceModifier.width(layout.gap))
                ChargeBarFarSegment(layout.far, height, theme)
            }
        }
    }
}

/**
 * The past-the-limit segment: a genuinely darker BACKDROP box, with the ordinary
 * dim tint nested inside it at the same size -- two layered colours, like the
 * phone's own two `drawRoundRect` calls, rather than one translucent tint that
 * reads as "the same track, slightly quieter" instead of a distinct zone. Its own
 * independent corner-rounding (Glance has no clip-path API to round only the
 * bar's outer ends the way the phone's Canvas can choose to) is no longer a
 * limitation to work around here -- every segment is meant to read as its own
 * separately-rounded piece now, gapped from its neighbours by the caller.
 */
@Composable
private fun ChargeBarFarSegment(width: Dp, height: Dp, theme: WidgetTheme) {
    Box(
        modifier = GlanceModifier.width(width).height(height)
            .cornerRadius(height / 2).background(theme.chargeLimitBackdrop),
    ) {
        Box(
            modifier = GlanceModifier.width(width).height(height)
                .cornerRadius(height / 2).background(theme.surfaceVariantDim),
        ) {}
    }
}

/**
 * Pure segment-boundary math for [ChargeBar], pulled out of the composable specifically
 * so it can be unit-tested without a Glance/Compose runtime -- see
 * WidgetChargeBarLayoutTest. Mirrors the phone's own `chargeBarLayout` (Screens.kt) --
 * same shape, same reasoning, different unit system (Glance has no fractional Dp width,
 * so this works in whole Dp throughout rather than px).
 */
internal data class WidgetChargeBarLayout(
    val filled: Dp,
    /** True for the collapsed one-segment remainder (no limit at all, or already at/past
     *  it) -- [mid]/[far] are both 0 in that case, and vice versa. */
    val hasSingleTrack: Boolean,
    val singleTrackWidth: Dp,
    /** Dim track when stuck at the limit, ordinary track when there's no limit to speak
     *  of -- only meaningful when [hasSingleTrack]. */
    val singleTrackDim: Boolean,
    val mid: Dp,
    val gap: Dp,
    val far: Dp,
)

/** Same value as the phone's own ChargeSegmentGap (UiTokens.kt) -- not imported
 *  cross-package, matching this file's existing convention of keeping its own
 *  size constants local rather than reaching into the phone UI package. */
private val SEGMENT_GAP = 5.dp

internal fun widgetChargeBarLayout(width: Dp, height: Dp, frac: Float, limit: Int?, stuckAtLimit: Boolean): WidgetChargeBarLayout {
    // Floored at the bar's own height when there is ANY charge, so a low one reads as a
    // rounded nub rather than a hairline: below that the 50% corner radius eats the
    // whole shape and 3% looks identical to 0%. This is the CONCEPTUAL current-charge
    // boundary -- the fill segment's own width is derived from it below, shrunk by
    // half the gap, mirroring the phone's own chargeBarLayout exactly (see its doc).
    val clampedFrac = frac.coerceIn(0f, 1f)
    val filledRaw = if (clampedFrac <= 0f) 0.dp else minOf(width, maxOf(width * clampedFrac, height))
    val halfGap = SEGMENT_GAP / 2
    val filled = (filledRaw - halfGap).coerceAtLeast(0.dp)
    if (limit == null || stuckAtLimit) {
        val trackStart = minOf(width, filledRaw + halfGap)
        val trackWidth = (width - trackStart).coerceAtLeast(0.dp)
        return WidgetChargeBarLayout(
            filled = filled, hasSingleTrack = true, singleTrackWidth = trackWidth, singleTrackDim = limit != null,
            mid = 0.dp, gap = SEGMENT_GAP, far = 0.dp,
        )
    }
    val limitXRaw = (width * (limit / 100f)).coerceIn(filledRaw, width)
    val midStart = minOf(width, filledRaw + halfGap)
    val midEnd = (limitXRaw - halfGap).coerceIn(midStart, width)
    val mid = (midEnd - midStart).coerceAtLeast(0.dp)
    val farStart = (limitXRaw + halfGap).coerceIn(limitXRaw, width)
    val far = (width - farStart).coerceAtLeast(0.dp)
    return WidgetChargeBarLayout(
        filled = filled, hasSingleTrack = false, singleTrackWidth = 0.dp, singleTrackDim = false,
        mid = mid, gap = SEGMENT_GAP, far = far,
    )
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
