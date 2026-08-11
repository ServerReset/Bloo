package com.bloo.bluelink.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bloo.bluelink.R
import com.bloo.bluelink.data.BlooColors
import com.bloo.bluelink.data.formatDistance
import com.bloo.bluelink.data.VehicleSnapshot
import com.bloo.bluelink.data.relativeLabel

/**
 * Fifth slice out of CarWidget.kt: the shared CONTENT modules -- the pieces a
 * tier layout arranges, as opposed to the arranging itself.
 *
 * The header (name + status, with the car-switcher pill), the last-updated
 * footer, the info stat stack and its rows, the one-line primary value, the
 * name-plus-stat pairing, and BarHero -- the big percentage over a charge bar.
 *
 * BarHero sits here rather than with the other gauges in [WidgetGauges]
 * despite drawing one, because when its percentage will not fit it falls back
 * to NameAndStat or PrimaryInfoLine: it depends on the info-text modules, so
 * it moves with them. That dependency was checked mechanically rather than
 * eyeballed -- the two build breaks earlier in this rebuild were both a moved
 * symbol reaching back into something that had not moved.
 */

/** Below this width, [InfoStack] stops putting a value beside its label
 *  and starts stacking instead -- the same "give up on one line" width
 *  the original widget used for its own narrow-text fallback. Lives here
 *  rather than in CarWidget because InfoStack and InfoRow are its only
 *  readers, and they are here now. */
internal val NARROW_WIDTH = 90.dp

@Composable
internal fun HeaderRow(
    car: VehicleSnapshot, render: Render,
    // The width the header's text column has. Defaults to the real inner width (tile
    // minus root padding) -- NOT the raw tile width, which over-reported by up to 18dp
    // per side and let the name render wider than the padded box. The wide-MEDIUM tier
    // overrides it with its own narrower RingWithContent column width; those callers were
    // already correct, this only fixes the ones that took the default.
    availableWidth: Dp = Scale.innerWidth(render.frame(LocalSize.current)),
) {
    // Gated here rather than at each of the dozen call sites, so the
    // option can't be honoured by some tiers and quietly ignored by
    // others as layouts get added.
    if (!render.config.showHeader) return
    // Rough reserve for the car-switcher pill (its own size plus
    // spacing) when it's present -- an estimate, same spirit as every
    // other maxWidth passed to FitText in this file (see wouldOverflow).
    val pillReserve = if (render.multiCar && render.config.vin == null) {
        Scale.pillSize(LocalSize.current) + 8.dp
    } else 0.dp
    val textWidth = (availableWidth - pillReserve - 4.dp).coerceAtLeast(16.dp)
    Row(modifier = GlanceModifier.fillMaxWidth().clickable(openAction(LocalContext.current)), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            // singleLine: Scale.headerHeight reserves exactly one title + one subtitle
            // line, so neither may wrap -- a wrapped name would make the header 3 lines
            // and overflow that reservation. FitText shrinks to fit one line instead.
            FitText(
                car.name, titleStyle(render.theme), maxWidth = textWidth,
                singleLine = true, allowStack = false,
            )
            FitText(
                statusSubtitle(car), subtitleStyle(render.theme), maxWidth = textWidth,
                singleLine = true, allowStack = false,
            )
        }
        // Follow-selected widgets get a car switcher chevron. Via
        // Render.hasSwitcher so Scale.headerHeight reserves for exactly the
        // cases that draw it.
        if (render.hasSwitcher) {
            IconPill(
                iconRes = R.drawable.ic_shortcut_car,
                onClick = actionRunCallback<WidgetSwitchCarAction>(),
                theme = render.theme,
            )
        }
    }
}

@Composable
internal fun FooterRow(car: VehicleSnapshot, render: Render) {
    if (!render.config.showFooter) return
    val updated = relativeLabel(car.fetchedAt.takeIf { it > 0 })
    if (updated.isNotBlank()) {
        Spacer(GlanceModifier.height(6.dp))
        // Stale data gets an amber "· may be out of date" tail so an hours-old
        // lock/charge state can't masquerade as live. Tap the footer to refresh.
        // Stale changes the COLOUR only. It used to hard-code fontSize = 11.sp,
        // which both diverged from the subtitleSp * textScale that
        // Scale.footerHeight reserves -- reserved and rendered coming from two
        // unrelated font sizes -- and silently ignored the user's text-size
        // setting, so asking for 1.4x text still got an 11sp footer whenever
        // the data went stale.
        val style = if (render.stale)
            subtitleStyle(render.theme).copy(color = ColorProvider(Color(BlooColors.warn)))
        else subtitleStyle(render.theme)
        val text = if (render.stale) "Updated $updated · may be stale" else "Updated $updated"
        // innerWidth + singleLine: the footer sits inside Content's root padding, and
        // Scale.footerHeight reserves exactly one subtitle line for it -- so it must fit
        // the real inner width on one line, not wrap. See FitText's singleLine note.
        FitText(
            text,
            style,
            maxWidth = Scale.innerWidth(render.frame(LocalSize.current)),
            singleLine = true,
            modifier = GlanceModifier.clickable(
                actionRunCallback<WidgetRefreshAction>(actionParametersOf(WidgetKeys.VIN to car.vin)),
            ),
        )
    }
}

/** [maxWidth] is passed in rather than derived from the tile, because
 *  this line's caller is the only thing that knows how much of the row
 *  it actually owns -- the old hard-coded "36% of the tile" was a
 *  CompactWide-specific guess baked into a shared module. */
@Composable
internal fun PrimaryInfoLine(car: VehicleSnapshot, render: Render, maxWidth: Dp) {
    FitText(primaryValue(car, render), subtitleStyle(render.theme), maxWidth = maxWidth)
}

/**
 * The ordinary text pair -- the car's name over its primary stat -- with
 * the stat dropped when the row can't hold both lines.
 *
 * The pair was previously written out at each call site as two unguarded
 * [FitText]s. FitText budgets WIDTH only; nothing was checking that two
 * stacked lines fit the tile's HEIGHT, so on a 640x40 strip at the 1.4x
 * text size they overran it by 9.8dp -- and RemoteViews renders that as a
 * line bleeding past the bottom edge, not as a clip. The name always fits
 * on its own (both it and the budget are driven by the short side), so
 * the stat is the only thing this has to decide about.
 */
@Composable
internal fun NameAndStat(car: VehicleSnapshot, render: Render, width: Dp) {
    val size = LocalSize.current
    val scale = render.theme.textScale
    val avail = Scale.innerHeight(render.frame(size))
    FitText(car.name, titleStyle(render.theme), maxWidth = width, allowStack = false)
    val both = Scale.lineHeight(Scale.titleSp(size).value, scale) +
        Scale.lineHeight(Scale.subtitleSp(size).value, scale)
    if (both <= avail) PrimaryInfoLine(car, render, maxWidth = width)
}

/** The stacked read-only stats, honoring the user's chosen fields + order,
 *  capped to what fits ([max]). Glance has no overflow-detection callback
 *  the way real Compose Text does (RemoteViews just silently ellipsizes),
 *  so "might not fit" is decided ahead of time from the measured tile
 *  width instead of reactively -- below [NARROW_WIDTH] every row drops
 *  the label beside its value in favour of stacking the value on its own
 *  full-width line underneath, and [FitText] falls back further to one
 *  character per line (see [VerticalText]) for either one if even that's
 *  tight, so a reading like "82%" degrades to
 *
 *  8
 *  2
 *  %
 *
 *  rather than ever being cut off. */
@Composable
internal fun InfoStack(
    car: VehicleSnapshot, render: Render, max: Int,
    // The width this stack's own column actually gets, which is NOT the
    // tile width whenever it sits beside a ring (see RingWithContent) --
    // measuring against the whole tile there would let a label clear the
    // wrap threshold on paper and still be clipped in a column half that
    // wide, which is exactly the failure this whole FitText path exists
    // to prevent.
    availableWidth: Dp = LocalSize.current.width,
    // True on tiers that also render FooterRow, which already reads
    // "Updated 9 min ago". Without this the same sentence appeared twice
    // on one widget -- once as an info row, once as the footer directly
    // beneath the buttons. Reported from a real device.
    footerShown: Boolean = false,
    // Non-empty on tiers whose hero already shows one or both of these
    // numbers itself -- XlTallLayout's primaryValue() headline reads
    // "69% · 361 mi" (both), the wide tiers' bar hero shows just "69%".
    // Without this, a user with Percent or Range in their chosen info
    // fields saw that exact number a second time, right below the
    // first, on the same tile. Reported from a real device screenshot.
    hideFields: Set<WidgetInfoField> = emptySet(),
) {
    val fields = render.config.infoFields
        .mapNotNull { WidgetInfoField.fromKey(it) }
        .filterNot { footerShown && it == WidgetInfoField.UPDATED }
        .filterNot { it in hideFields }
        // FILTER before TAKE. Taking first meant a field the car has not reported consumed
        // one of the `max` rows and was then dropped, so a tier with room for three stats
        // could render one -- and the fields that DID have values were the ones hidden,
        // because they sorted after the empty ones. infoValue is a plain non-composable
        // function, so calling it here is free of composition concerns, and filtering
        // before take can never yield MORE than max rows.
        .filter { infoValue(it, car, render) != null }
        .take(max)
    val narrow = availableWidth < NARROW_WIDTH
    // Two columns once the slot is wide enough for both halves to still
    // clear the narrow threshold. A single column on a genuinely wide
    // tier stacked six stats down a strip while the space beside them sat
    // empty, and made the block tall enough to crowd the ring and map it
    // shares a column with. Paired, the same stats read in half the
    // height, which is what the big tiers actually needed.
    val columnGap = 12.dp
    val paired = !narrow && fields.size > 2 &&
        (availableWidth - columnGap) / 2 >= NARROW_WIDTH + 20.dp
    if (paired) {
        val cellWidth = (availableWidth - columnGap) / 2
        Column {
            fields.chunked(2).forEach { pair ->
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    pair.forEachIndexed { i, field ->
                        if (i > 0) Spacer(GlanceModifier.width(columnGap))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            InfoRow(field, car, render, cellWidth)
                        }
                    }
                    // An odd count leaves the last row half-full; the empty
                    // half holds its place so the pair above stays aligned
                    // rather than the lone stat stretching across.
                    if (pair.size == 1) {
                        Spacer(GlanceModifier.width(columnGap))
                        Spacer(GlanceModifier.defaultWeight())
                    }
                }
                Spacer(GlanceModifier.height(2.dp))
            }
        }
        return
    }
    Column {
        fields.forEach { field ->
            // InfoRow is the same label/value block this used to inline. It recomputes
            // narrow from its width arg, and here width == availableWidth, so
            // `width < NARROW_WIDTH` equals this stack's own `narrow` -- identical output,
            // one definition. (The paired branch above already routes through InfoRow; the
            // single-column branch was the one copy that had drifted back inline.)
            InfoRow(field, car, render, availableWidth)
            Spacer(GlanceModifier.height(2.dp))
        }
    }
}

/** One label/value stat, in whatever width its cell actually has. Shared
 *  by [InfoStack]'s single-column and paired layouts so the two can't
 *  drift in how they wrap or when they fall back to stacking. */
@Composable
internal fun InfoRow(field: WidgetInfoField, car: VehicleSnapshot, render: Render, width: Dp) {
    val value = infoValue(field, car, render) ?: return
    if (width < NARROW_WIDTH) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            FitText(
                fieldLabel(field, car), subtitleStyle(render.theme),
                maxWidth = width - 4.dp, allowStack = false,
            )
            FitText(value, valueStyle(render.theme), maxWidth = width - 4.dp)
        }
    } else {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            FitText(
                fieldLabel(field, car), subtitleStyle(render.theme),
                maxWidth = width * 0.5f, modifier = GlanceModifier.defaultWeight(),
            )
            FitText(value, valueStyle(render.theme), maxWidth = width * 0.45f)
        }
    }
}

/**
 * The bar treatment: a big percentage over a horizontal charge bar.
 *
 * A ring is the right hero when a tile has height to spend on it. On a
 * wide, short tile it is the wrong shape entirely -- it's bounded by the
 * SHORT side, so it shrinks to a token while the width it can't use sits
 * empty. This spends the axis that tile actually has: the number reads
 * from across a room, and the bar carries the same information as the
 * ring in a fraction of the height.
 *
 * It's also what the app's own hero card does, so a wide widget and the
 * app now show charge the same way instead of two different visual
 * languages for one value.
 */
@Composable
internal fun BarHero(
    car: VehicleSnapshot, render: Render, width: Dp,
    // Budget the vertical the same way every other module here does: the
    // number sizes itself to what's left after the bar, the sub-line is
    // the first thing to go, and the whole treatment steps aside when
    // even the number can't be big enough to be one. Unbudgeted this
    // overran 422 sizes across the resize range -- worst a 220x40 strip
    // by 1.7dp, and RemoteViews doesn't clip an overflowing Column.
    //
    // Defaults to the WHOLE tile, correct for BannerLayout/
    // CompactWideLayout where this call is the row's only vertical
    // content. LargeWideLayout/XlWideLayout share their column with a
    // header, footer, info rows and a button row, so THEY pass the real
    // remainder instead -- otherwise this sized the hero number off the
    // full tile height while other modules were also claiming space out
    // of it, the same double-booking mistake this whole file exists to
    // avoid.
    avail: Dp = Scale.innerHeight(render.frame(LocalSize.current)),
    // False for LargeWideLayout/XlWideLayout, which already show the
    // car's name via their own HeaderRow above this call. NameAndStat's
    // fallback repeats it -- "Lanas Whip" once from the header, then
    // "Lanas Whip / 67% · 219 mi" again right under it -- on any tile
    // too short for the hero number to fit. Reported from a real
    // device. BannerLayout/CompactWideLayout have no header of their
    // own, so the fallback is the ONLY place their name shows and stays on.
    showNameFallback: Boolean = true,
) {
    val theme = render.theme
    val size = LocalSize.current
    val pct = car.percent
    val barH = Scale.barHeight(size)
    val heroSp = pct?.let { Scale.heroSpIn(size, avail, barH + 4.dp, theme.textScale) }
    if (pct == null || heroSp == null) {
        // No percentage to make a hero of, or no room to make it big
        // enough to be one. Either way the bar treatment isn't what this
        // tile wants, so it gets the ordinary name/stat pair rather than a
        // shrunken imitation of a hero -- unless the caller already has
        // its own header, in which case a NAME would duplicate it.
        //
        // A NUMBER wouldn't, though, and dropping to genuinely nothing
        // here was its own real bug: the generic room-available
        // gate (24dp, tuned for a CIRCLE) is looser than what BarHero's
        // OWN heroSpIn floor actually needs (~35dp, HERO_MIN_SP scaled
        // plus the bar's own reserve) -- so a caller could clear the
        // first gate, hand this a real but insufficient `avail`, and get
        // silence instead of either treatment. Confirmed by simulating
        // this exact arithmetic outside the codebase: every nominal
        // LARGE_WIDE grid size at 3 rows (250x180 through 460x180) landed
        // in precisely this gap -- a widget with a real percentage to
        // show, and genuine (if modest) vertical room, rendering neither
        // the hero number nor any fallback at all.
        when {
            showNameFallback -> NameAndStat(car, render, width = width)
            pct != null && avail >= Scale.lineHeight(Scale.subtitleSp(size).value, theme.textScale) + 4.dp ->
                PrimaryInfoLine(car, render, maxWidth = width)
            // Truly no room for anything, or no percent to show at all.
            else -> {}
        }
        return
    }
    val heroH = Scale.lineHeight(heroSp, 1f)
    val subH = Scale.lineHeight(Scale.subtitleSp(size).value, theme.textScale) + 4.dp
    val showSub = heroH + 4.dp + barH + subH <= avail
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        FitText(
            "$pct%",
            TextStyle(
                color = theme.onSurface,
                fontSize = heroSp.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxWidth = width,
        )
        Spacer(GlanceModifier.height(4.dp))
        ChargeBar(car, theme, width = width, height = barH)
        // NOT primaryValue: that leads with the percentage, which is the
        // 44sp number directly above it. The sub-line's job here is to say
        // what primaryValue's version of this layout couldn't fit -- whose
        // car it is, and how far it goes.
        // The NAME is gated on showNameFallback too, which it was not before. That flag
        // was added for the fallback path above -- the no-room-for-a-hero case -- and this
        // sub-line, on the path where the hero number DID fit, kept printing the name
        // unconditionally. So LargeWideLayout/XlWideLayout drew "Lanas Whip" in their own
        // HeaderRow and then "Lanas Whip · 246 mi" again directly under the big number:
        // exactly the duplication the flag was introduced to stop, on the sibling branch it
        // never reached.
        //
        // The RANGE stays either way. The hero number says the percentage and nothing else,
        // so the range is the one thing here that is not already on screen -- and unlike the
        // name it is not something the header draws. (An InfoStack row CAN be configured to
        // show range as well, but those fields are user-chosen from ten options, so dropping
        // it here unconditionally would remove information on every tile that has not opted
        // into that row.)
        val sub = listOfNotNull(
            car.name.takeIf { it.isNotBlank() && showNameFallback },
            car.rangeMi?.let { formatDistance(it.toDouble(), render.metric) },
        ).joinToString(" · ").takeIf { showSub && it.isNotBlank() }
        if (sub != null) {
            Spacer(GlanceModifier.height(4.dp))
            FitText(sub, subtitleStyle(theme), maxWidth = width, allowStack = false)
        }
    }
}

@Composable
internal fun IconPill(iconRes: Int, onClick: androidx.glance.action.Action, theme: WidgetTheme) {
    val size = LocalSize.current
    val pillSize = Scale.pillSize(size)
    Box(
        // Always a true circle (half its own size), not a fixed corner
        // radius -- a fixed radius reads as "barely rounded square" once
        // the pill itself scales up on larger tiles.
        modifier = GlanceModifier.size(pillSize).cornerRadius(pillSize / 2)
            .background(theme.surfaceVariant).clickable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = "Switch car",
            colorFilter = ColorFilter.tint(theme.onSurfaceVariant),
            modifier = GlanceModifier.size(Scale.pillIcon(size)),
        )
    }
}
