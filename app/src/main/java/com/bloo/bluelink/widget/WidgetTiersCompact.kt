package com.bloo.bluelink.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.bloo.bluelink.data.VehicleSnapshot

/**
 * The nine SMALL tier layouts: the two MICRO floors, the one-row BANNER strip
 * (every 2x1 through 7x1 tile), the one-column RAIL, and the four COMPACT
 * shapes plus their narrow variants.
 *
 * What separates these from [WidgetTiersLarge] is not size for its own sake
 * but what they can afford to say. Every layout here is making a subtraction
 * -- no header, no footer, one info line at most, buttons or the ring but
 * rarely both -- and each one is tuned against a specific real failure at
 * that shape. The large tiers are the opposite problem: enough room that the
 * question becomes what to do with the slack.
 */

@Composable
internal fun MicroTinyLayout(car: VehicleSnapshot, render: Render) {
    // The true floor -- literally no room for any text at all (a name at
    // any legible size would overflow a <60dp tile), so this is pure
    // iconography: ring/glyph, or one button filling the whole tile.
    val size = LocalSize.current
    val fit = (minOf(size.width, size.height) - 16.dp).coerceAtLeast(10.dp)
    if (controlsPriority(render)) {
        val action = resolvedActions(car, render, max = 1).firstOrNull()
        if (action != null) {
            val iconSize = fit.coerceIn(12.dp, 26.dp)
            ActionButton(action, car, render, modifier = GlanceModifier.fillMaxSize(), fixedHeight = false, iconSize = iconSize)
            return
        }
    }
    Box(
        modifier = GlanceModifier.fillMaxSize().clickable(openAction(LocalContext.current)),
        contentAlignment = Alignment.Center,
    ) {
        if (render.config.showRing && car.percent != null) {
            RingImage(car, render, edgeDp = Scale.ring(size, fit).value.toInt())
        } else {
            StatusGlyph(car, render.theme, sizeDp = fit.coerceIn(12.dp, 34.dp).value.toInt())
        }
    }
}

@Composable
internal fun MicroLayout(car: VehicleSnapshot, render: Render) {
    // A little roomier than MICRO_TINY -- same ring/glyph/button core,
    // but now there's just enough space for one tiny caption underneath
    // when the ring itself isn't shown. FitText's own vertical fallback
    // still covers the case where even that single caption is too wide.
    val size = LocalSize.current
    val fit = (minOf(size.width, size.height) - 22.dp).coerceAtLeast(14.dp)
    if (controlsPriority(render)) {
        val action = resolvedActions(car, render, max = 1).firstOrNull()
        if (action != null) {
            val iconSize = fit.coerceIn(14.dp, 30.dp)
            ActionButton(action, car, render, modifier = GlanceModifier.fillMaxSize(), fixedHeight = false, iconSize = iconSize)
            return
        }
    }
    Column(
        modifier = GlanceModifier.fillMaxSize().clickable(openAction(LocalContext.current)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (render.config.showRing && car.percent != null) {
            RingImage(car, render, edgeDp = Scale.ring(size, fit).value.toInt())
        } else {
            StatusGlyph(car, render.theme, sizeDp = fit.coerceIn(14.dp, 36.dp).value.toInt())
            Spacer(GlanceModifier.height(2.dp))
            // innerWidth, not size.width - 8: the caption is inside Content's root padding.
            // NOT singleLine -- this is the terminal element in a centred column with slack,
            // so wrapping is an acceptable last resort here (unlike the compact name tiers).
            FitText(
                car.name, subtitleStyle(render.theme),
                maxWidth = Scale.innerWidth(render.frame(size)), horizontalAlignment = Alignment.CenterHorizontally,
            )
        }
    }
}

@Composable
internal fun BannerLayout(car: VehicleSnapshot, render: Render) {
    // A long thin horizontal strip, down to 640x40 -- a 16:1 tile the
    // launcher genuinely allows. Everything sits on ONE vertically
    // centered row, because there is no room for a header above or a
    // footer below: at 40dp tall the padded content box is 28dp, barely
    // two lines of small text.
    val size = LocalSize.current
    val frame = render.frame(size)
    // Only take over the tile if there's actually something to show:
    // resolvedActions filters by brand (Kia and the Canada backend have
    // no flash/horn endpoint), so a widget configured with only those
    // resolves to an empty list, and returning here regardless would
    // render a completely blank banner.
    if (controlsPriority(render) && resolvedActions(car, render, max = 6).isNotEmpty()) {
        val edge = controlsMiniStatusEdge(size, Scale.innerHeight(frame))
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            if (edge >= 16.dp) {
                MiniStatus(car, render, edge)
                Spacer(GlanceModifier.width(8.dp))
            }
            ActionButtons(car, render, max = 6, modifier = GlanceModifier.defaultWeight())
        }
        return
    }
    val showsRing = render.config.showRing && car.percent != null
    // The width each weighted child of this Row REALLY gets: the padded
    // content width, less the one fixed 8dp spacer between them, halved.
    //
    // Two things were wrong here, in opposite directions:
    //
    // It subtracted a ring edge whenever showsRing was true -- but a banner
    // ALWAYS takes the bar treatment (see below), so no ring is ever drawn on
    // this tier and that was pure loss. The `if (showsRing && !useBar)` ring
    // branch it reserved for could not run at all: useBar was assigned
    // showsRing, so the condition read `showsRing && !showsRing`. On a 300x78
    // tile with a 52dp ring edge that cost both weighted children ~30dp of
    // width they actually had, which is what made button labels drop out and
    // text shrink earlier than it needed to.
    //
    // And it measured against the RAW tile width while the root has already
    // applied Scale.contentPadding on both sides, so it over-reported by that
    // much -- harmless while the ring subtraction was masking it, but not once
    // that goes. Removing only the ring term would have handed ActionButtons a
    // slice wider than the row really has, and its capacity check would then
    // fit one button too many. Both corrections belong together; `w` is the
    // same padded width MediumWide and LargeWide already compute.
    val w = Scale.innerWidth(frame)
    val slice = ((w - 8.dp) / 2).coerceAtLeast(24.dp)
    // A banner is almost pure width, the shape a bar was built for -- it
    // reads its value from across a room in a fraction of the height a
    // ring needs. The circle is for compact/vertical tiles, and this tile
    // is neither, so the bar is not a fallback here -- it is the treatment.
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            if (showsRing) {
                BarHero(car, render, width = slice)
            } else {
                NameAndStat(car, render, width = slice)
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        // A banner is nearly all width, so the buttons get a real share
        // of it rather than the thin sliver a normal compact row leaves.
        // The real slice, not a fraction-of-tile guess: this Row splits its
        // padded width, less the spacer above, evenly between the text
        // column and the buttons, so that is exactly what the capacity
        // maths should see.
        ActionButtons(
            car, render, max = 4,
            modifier = GlanceModifier.defaultWeight(),
            availableWidth = slice,
        )
    }
}

@Composable
internal fun RailLayout(car: VehicleSnapshot, render: Render) {
    // The vertical mirror of BANNER, down to 40x640. Deliberately shows
    // NO name: at 40dp wide the content box is 28dp, and any car name
    // there would letter-stack into a column taller than the tile (see
    // FitText). That is the only thing this tier gives up -- everything
    // else here now scales with the tile the way every other tier does.
    //
    // A Rail resized tall used to spend almost none of the extra height:
    // a ring capped well short of what the width allowed, at most 4
    // buttons regardless of how many were configured or how much room
    // there was to stack them, and no map even with location switched
    // on -- a small cluster centred in a sea of empty photo, reported
    // from real devices across several widget sizes in one batch. The
    // buttons' own reserved zone is sized for every CONFIGURED action
    // (not a fixed handful), and the ring/map split whatever is left the
    // same way [MediumTallLayout] and the other tall tiers already do.
    val size = LocalSize.current
    val frame = render.frame(size)
    val allActions = resolvedActions(car, render, max = WidgetAction.ALL.size)
    if (controlsPriority(render) && allActions.isNotEmpty()) {
        val budgetH = Scale.innerHeight(frame)
        val edge = controlsMiniStatusEdge(size, Scale.innerWidth(frame), fraction = 1f)
        val spacerH = if (edge >= 16.dp) 8.dp else 0.dp
        Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (edge >= 16.dp) {
                MiniStatus(car, render, edge)
                Spacer(GlanceModifier.height(spacerH))
            }
            ActionButtons(
                car, render, max = WidgetAction.ALL.size, vertical = true,
                modifier = GlanceModifier.defaultWeight(),
                availableHeight = (budgetH - edge - spacerH).coerceAtLeast(0.dp),
            )
        }
        return
    }
    // Reserved BEFORE the hero content is sized, same reasoning as the
    // map-before-ring fix elsewhere in this file: the thing with a real,
    // guaranteed size requirement has to claim its room first, or a
    // "hero grows to fill whatever's offered" element (the ring here)
    // just eats the space a variable-length button stack actually needs.
    //
    // The count itself is capped by what the BUDGET can actually hold,
    // not a flat number -- six stacked buttons at this tier's own button
    // height can exceed the whole content box near Rail's 220dp floor,
    // before the ring or a map has claimed anything. See maxStackedButtons.
    // overhead is 16.dp, not buttonZone's own 8.dp: buttonZone's trailing
    // +8.dp is matched here, PLUS the separate forced Spacer(8.dp) that
    // renders unconditionally right before ActionButtons below whenever
    // buttonCount > 0 -- that spacer isn't part of buttonZone, so a
    // button count chosen without reserving it too overflowed the tile
    // by up to 2dp whenever the map spacer also landed on top.
    // The whole name+button+ring/split reservation is WidgetLayout.tallPlan now -- the ONE
    // definition of this tier's budget that the WidgetScaleTest sweep also calls, so the
    // numbers the widget renders and the numbers the sweep asserts can't drift. RAIL's spec
    // (no name, no rows, no map) lives in WidgetLayout; the render tree below is unchanged.
    // A location map is never eligible on RAIL: at under 110dp wide it reads as a random
    // zoomed-in street fragment (reported from a real device), so that width goes to the ring.
    val plan = WidgetLayout.tallPlan(WidgetTier.RAIL, size, render.theme.textScale, allActions.size)
    val buttonCount = plan.buttonCount
    val buttonZone = plan.buttonZone
    val split = plan.split
    Column(
        modifier = GlanceModifier.fillMaxSize().clickable(openAction(LocalContext.current)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Weighted spacers above and below centre the ring in whatever the button stack
        // left, rather than piling it at the top. RAIL draws no map (the plan's spec has
        // no map slot), so the ring simply sits between two weighted spacers.
        Spacer(GlanceModifier.defaultWeight())
        RingOrGlyph(car, render, edgeDp = split.ring.value.toInt())
        Spacer(GlanceModifier.defaultWeight())
        if (buttonCount > 0) {
            Spacer(GlanceModifier.height(8.dp))
            ActionButtons(car, render, max = WidgetAction.ALL.size, vertical = true, availableHeight = buttonZone)
        }
    }
}

@Composable
internal fun CompactSquareLayout(car: VehicleSnapshot, render: Render) {
    // Controls priority here gets a real 2x2 grid instead of a single
    // row/column -- there's enough room on a near-square 90dp+ tile for
    // four properly-sized buttons arranged like a mini keypad, a shape
    // none of the other controls-priority layouts use.
    val size = LocalSize.current
    val frame = render.frame(size)
    if (controlsPriority(render)) {
        val actions = resolvedActions(car, render, max = 4)
        if (actions.isNotEmpty()) {
            val edge = controlsMiniStatusEdge(size, minOf(Scale.innerWidth(frame), Scale.innerHeight(frame)))
            Column(modifier = GlanceModifier.fillMaxSize().padding(4.dp)) {
                if (edge >= 16.dp) {
                    Row(GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        MiniStatus(car, render, edge)
                    }
                    Spacer(GlanceModifier.height(4.dp))
                }
                actions.chunked(2).forEachIndexed { i, row ->
                    if (i > 0) Spacer(GlanceModifier.height(4.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        row.forEachIndexed { j, action ->
                            if (j > 0) Spacer(GlanceModifier.width(4.dp))
                            ActionButton(action, car, render, modifier = GlanceModifier.defaultWeight())
                        }
                    }
                }
            }
            return
        }
    }
    val scale = render.theme.textScale
    // The last fraction-of-the-tile ring cap in this file, and it was
    // wrong the same way every other one was: 55% of the height assumes
    // the name above and the stat below are small, and they aren't at
    // larger text sizes. Of the COMPACT_SQUARE size range this column
    // overran its tile on 498 configurations, by as much as 33dp -- a
    // third of a small tile's content bleeding past the bottom edge,
    // which is what RemoteViews does with an overfull Column instead of
    // clipping it. Budgeted from what the text actually leaves now, and
    // capped by the width too so the circle stays a circle.
    val budget = Scale.innerHeight(frame)
    val left = (budget - Scale.lineHeight(Scale.titleSp(size).value, scale) - 8.dp).coerceAtLeast(0.dp)
    val rows = Scale.infoRowsIn(size, left, scale, cap = 1)
    val ringEdge = Scale.ring(
        size,
        minOf(left - Scale.infoBlockHeight(size, rows, scale), size.width - 8.dp),
    )
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // innerWidth, not size.width - 8: the name is inside Content's root padding, so its
        // real width is the tile minus that padding (up to 18dp/side), not a flat 8dp. And
        // singleLine, because the column reserves exactly one nameHeight line for it below.
        FitText(
            car.name, titleStyle(render.theme),
            maxWidth = Scale.innerWidth(frame), horizontalAlignment = Alignment.CenterHorizontally,
            singleLine = true,
        )
        Spacer(GlanceModifier.height(4.dp))
        RingOrGlyph(car, render, edgeDp = ringEdge.value.toInt())
        Spacer(GlanceModifier.height(4.dp))
        if (rows > 0) InfoStack(car, render, max = rows)
    }
}

@Composable
internal fun CompactWideNarrowLayout(car: VehicleSnapshot, render: Render) {
    val size = LocalSize.current
    val frame = render.frame(size)
    if (controlsPriority(render)) {
        val actions = resolvedActions(car, render, max = 2)
        if (actions.isNotEmpty()) {
            val edge = controlsMiniStatusEdge(size, Scale.innerHeight(frame))
            Row(GlanceModifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (edge >= 16.dp) {
                    MiniStatus(car, render, edge)
                    Spacer(GlanceModifier.width(6.dp))
                }
                ActionButtons(car, render, max = 2, modifier = GlanceModifier.defaultWeight())
            }
            return
        }
    }
    // Narrower than COMPACT_WIDE's own threshold -- no room for a
    // subtitle line beside the ring too, just the name, and only 2
    // buttons instead of 3.
    // Against the real inner height. This was `size.height - 12.dp`, a literal
    // standing in for 2 * contentPadding -- which spans 12dp to 36dp, so the ring
    // exceeded the padded box by the difference whenever the height term bound.
    val ringEdge = Scale.ring(size, Scale.innerHeight(frame).coerceAtLeast(18.dp))
    // Not just `showRing && percent != null`: Scale.ring returns 0 when the
    // column can't fit a legible circle, and RingImage early-returns on that.
    // Asking the question as "is a ring configured" left the 6dp spacer below
    // rendering beside nothing on every tile too short for one.
    val drawsRing = render.config.showRing && car.percent != null && ringEdge > 0.dp
    // The width each weighted child of this Row REALLY gets: whatever is
    // left once the ring and the fixed spacers are taken out, split
    // between the text column and the buttons. The fraction-of-tile
    // guesses this replaces were wrong twice over -- they under-reported
    // the slice, and they kept assuming a ring was there even when one
    // isn't drawn, so a widget with the ring switched off still laid its
    // text and buttons out as if a third of the row were missing.
    //
    // Against the PADDED width, not the raw tile width. BANNER and
    // COMPACT_WIDE both carry this correction and both spell out why -- the
    // root has already applied Scale.contentPadding on both sides, so raw
    // width over-reports by that much and ActionButtons' capacity check
    // fits one button too many. This tier was written from the same
    // template and never got it, because its own two-part fix looked
    // complete: the ring term genuinely does belong here (unlike in those
    // two, this tile really draws one), which made the missing padding term
    // easy to overlook while comparing the shape of the formulas rather
    // than their terms.
    val w = Scale.innerWidth(frame)
    val slice = ((w - (if (drawsRing) ringEdge + 6.dp else 0.dp) - 6.dp) / 2)
        .coerceAtLeast(24.dp)
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        if (drawsRing) {
            RingImage(car, render, edgeDp = ringEdge.value.toInt())
            Spacer(GlanceModifier.width(6.dp))
        }
        FitText(
            car.name, titleStyle(render.theme),
            maxWidth = slice, modifier = GlanceModifier.defaultWeight(),
        )
        Spacer(GlanceModifier.width(6.dp))
        ActionButtons(car, render, max = 2, modifier = GlanceModifier.defaultWeight(), availableWidth = slice)
    }
}

@Composable
internal fun CompactWideLayout(car: VehicleSnapshot, render: Render) {
    val size = LocalSize.current
    val frame = render.frame(size)
    if (controlsPriority(render)) {
        val actions = resolvedActions(car, render, max = 4)
        if (actions.isNotEmpty()) {
            val edge = controlsMiniStatusEdge(size, Scale.innerHeight(frame))
            Row(GlanceModifier.fillMaxSize().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (edge >= 16.dp) {
                    MiniStatus(car, render, edge)
                    Spacer(GlanceModifier.width(8.dp))
                }
                ActionButtons(car, render, max = 4, modifier = GlanceModifier.defaultWeight())
            }
            return
        }
    }
    val showsRing = render.config.showRing && car.percent != null
    // The width each weighted child of this Row REALLY gets. Same two
    // corrections as BannerLayout's own slice -- see there for the long
    // version. In short: no ring is ever drawn on this tier, because useBar
    // was assigned showsRing and so the `showsRing && !useBar` ring branch
    // read `showsRing && !showsRing`; subtracting a ring edge therefore cost
    // both weighted children width they actually had. And the measurement
    // has to be against the PADDED content width, not the raw tile width, or
    // ActionButtons' capacity check is handed a slice wider than the row
    // really is and fits one button too many.
    //
    // The height-capped ringEdge this used to compute went with it: it existed
    // only to be subtracted here and to feed the unreachable branch.
    val w = Scale.innerWidth(frame)
    val slice = ((w - 8.dp) / 2).coerceAtLeast(24.dp)
    // Same call as BANNER: this tile is wide, not compact/vertical, so the
    // bar is the treatment here rather than a fallback for a shrinking ring.
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        // BUG this fixes: ActionButtons' own default modifier is
        // fillMaxWidth(), which is correct when it's the sole/last child
        // of a Column (every other call site) but wrong here -- as a
        // plain, unweighted sibling of this Row's own weighted text
        // column, "fill max width" meant "claim the width of the WHOLE
        // row", not "whatever's left after the ring and text", pushing
        // the button row past the tile's right edge entirely (clipped
        // only by the outer corner's rounding, which is what made it
        // look like a button was cut in half rather than missing outright).
        // Giving it a weight too makes it share the remaining space
        // fairly with the text column instead of overrunning it.
        Column(modifier = GlanceModifier.defaultWeight()) {
            if (showsRing) {
                BarHero(car, render, width = slice)
            } else {
                NameAndStat(car, render, width = slice)
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        ActionButtons(car, render, max = 3, modifier = GlanceModifier.defaultWeight(), availableWidth = slice)
    }
}

@Composable
internal fun CompactTallNarrowLayout(car: VehicleSnapshot, render: Render) {
    // At its OWN minimum this tier really is name + ring/glyph + one
    // button with nothing to spare, which is what the old fixed caps
    // matched -- but a widget resized well past that floor kept the same
    // single button and no location no matter how tall it got. Same
    // treatment as Rail now: the button zone is sized for every
    // configured action, and whatever height that leaves is split
    // between the ring and an optional map the same way every other
    // tall tier already does.
    val size = LocalSize.current
    val frame = render.frame(size)
    val allActions = resolvedActions(car, render, max = WidgetAction.ALL.size)
    if (controlsPriority(render) && allActions.isNotEmpty()) {
        val budgetH = Scale.innerHeight(frame)
        val edge = controlsMiniStatusEdge(size, Scale.innerWidth(frame), fraction = 1f)
        val spacerH = if (edge >= 16.dp) 6.dp else 0.dp
        Column(GlanceModifier.fillMaxSize().padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (edge >= 16.dp) {
                MiniStatus(car, render, edge)
                Spacer(GlanceModifier.height(spacerH))
            }
            ActionButtons(
                car, render, max = WidgetAction.ALL.size, vertical = true,
                modifier = GlanceModifier.defaultWeight(),
                availableHeight = (budgetH - edge - spacerH - 8.dp).coerceAtLeast(0.dp),
            )
        }
        return
    }
    // One WidgetLayout.tallPlan call owns the name + button + split reservation (the sweep
    // calls the same one). This tier's spec: a name line (+4dp gap), buttons capped at 4 so
    // a tall-but-narrow tile doesn't become a button ladder, one info row, no map -- under
    // 150dp wide a location map reads as an unreadable street fragment, so the width goes to
    // the ring/name/buttons instead. The render tree below is unchanged.
    val plan = WidgetLayout.tallPlan(WidgetTier.COMPACT_TALL_NARROW, size, render.theme.textScale, allActions.size)
    val buttonCount = plan.buttonCount
    val buttonZone = plan.buttonZone
    val split = plan.split
    // The width cap that already existed here, kept: it's what keeps the
    // circle round on a genuinely narrow tile.
    val ringEdge = minOf(split.ring, size.width - 12.dp)
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // innerWidth + singleLine: inside Content's root padding, and the column reserves
        // one nameHeight line for this. See CompactSquareLayout's own note.
        FitText(
            car.name, titleStyle(render.theme),
            maxWidth = Scale.innerWidth(frame), horizontalAlignment = Alignment.CenterHorizontally,
            singleLine = true,
        )
        Spacer(GlanceModifier.height(4.dp))
        // No map on this tier, so the ring is centred by a weighted spacer above and below.
        Spacer(GlanceModifier.defaultWeight())
        RingOrGlyph(car, render, edgeDp = ringEdge.value.toInt())
        if (split.rows > 0) {
            Spacer(GlanceModifier.height(4.dp))
            InfoStack(car, render, max = split.rows)
        }
        Spacer(GlanceModifier.defaultWeight())
        if (buttonCount > 0) {
            Spacer(GlanceModifier.height(4.dp))
            ActionButtons(car, render, max = WidgetAction.ALL.size, vertical = true, availableHeight = buttonZone)
        }
    }
}

@Composable
internal fun CompactTallLayout(car: VehicleSnapshot, render: Render) {
    // COMPACT_TALL's threshold only proves the HEIGHT is roomy, not the
    // width, so the ring is still capped against the width -- but the
    // height it does have should be USED. This tier used a fixed 2 info
    // rows and 2 buttons regardless of how tall it actually got resized,
    // and never showed a map even with location on. Reported from real
    // devices: a name, one info row, and two buttons with the rest of a
    // very tall tile left as bare photo above and below.
    val size = LocalSize.current
    val frame = render.frame(size)
    val allActions = resolvedActions(car, render, max = WidgetAction.ALL.size)
    if (controlsPriority(render) && allActions.isNotEmpty()) {
        val budgetH = Scale.innerHeight(frame)
        val edge = controlsMiniStatusEdge(size, Scale.innerWidth(frame), fraction = 1f)
        val spacerH = if (edge >= 16.dp) 8.dp else 0.dp
        Column(GlanceModifier.fillMaxSize().padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (edge >= 16.dp) {
                MiniStatus(car, render, edge)
                Spacer(GlanceModifier.height(spacerH))
            }
            ActionButtons(
                car, render, max = WidgetAction.ALL.size, vertical = true,
                modifier = GlanceModifier.defaultWeight(),
                availableHeight = (budgetH - edge - spacerH - 12.dp).coerceAtLeast(0.dp),
            )
        }
        return
    }
    // One WidgetLayout.tallPlan call owns the name + button + split reservation (the sweep
    // calls the same one). This tier's spec: a name line (no extra gap), buttons STACKED and
    // capped at every configured action (a single row truncated to width while tall space
    // sat empty -- reported from a device), up to 4 info rows, no map (still under 150dp
    // wide). Its 20dp button overhead (12 trailing + the forced 8dp pre-button spacer) lives
    // in WidgetLayout now; this tier once passed only 12 and under-reserved that spacer.
    val plan = WidgetLayout.tallPlan(WidgetTier.COMPACT_TALL, size, render.theme.textScale, allActions.size)
    val buttonCount = plan.buttonCount
    val buttonZone = plan.buttonZone
    val split = plan.split
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        // innerWidth + singleLine: inside Content's root padding, and the column reserves
        // one nameHeight line for this. See CompactSquareLayout's own note.
        FitText(
            car.name, titleStyle(render.theme),
            maxWidth = Scale.innerWidth(frame), horizontalAlignment = Alignment.CenterHorizontally,
            singleLine = true,
        )
        // No map on this tier, so a weighted spacer above the hero centres what the ring
        // and rows don't claim rather than piling it at the top.
        Spacer(GlanceModifier.defaultWeight())
        if (render.config.showRing && car.percent != null) {
            RingImage(car, render, edgeDp = split.ring.value.toInt())
            Spacer(GlanceModifier.height(6.dp))
        } else {
            // The glyph every sibling tier falls back to (Rail, both Compact
            // Tall Narrow and Square, Micro, LargeTall). Without it this tier
            // showed a name, info rows and buttons and NO status iconography
            // at all whenever the ring was switched off or percent was
            // unknown -- while still subtracting MIN_HERO_RESERVE from the
            // button budget for a hero that never rendered.
            StatusGlyph(car, render.theme, sizeDp = split.ring.value.toInt())
        }
        if (split.rows > 0) InfoStack(car, render, max = split.rows)
        Spacer(GlanceModifier.defaultWeight())
        if (buttonCount > 0) {
            Spacer(GlanceModifier.height(8.dp))
            ActionButtons(car, render, max = WidgetAction.ALL.size, vertical = true, availableHeight = buttonZone)
        }
    }
}
