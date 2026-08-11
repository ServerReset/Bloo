package com.bloo.bluelink.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.bloo.bluelink.data.VehicleSnapshot

/**
 * The nine MEDIUM, LARGE and XL tier layouts -- every shape from 3x3 up,
 * including the wide, short awkward ones (7x2, 7x3) that have the most room
 * horizontally and the least vertically.
 *
 * These have room for the full stack (header, ring or bar hero, info rows,
 * map, buttons, footer), so their shared risk is the opposite of the compact
 * tiers': not what to cut, but keeping the vertical sum inside the tile.
 * RemoteViews does not clip, so an over-budgeted column does not overflow
 * visibly -- it renders off the tile and simply looks like missing content.
 * That is why the arithmetic behind every reservation here lives in Scale and
 * WidgetLayout, where WidgetScaleTest sweeps it, rather than inline.
 */

@Composable
internal fun MediumSquareLayout(car: VehicleSnapshot, render: Render) {
    // Same reasoning as the LARGE/XL tiers' own clamp: the header +
    // button rows can leave less than the ring's continuous target size
    // at MEDIUM's own minimum height (150dp).
    val size = LocalSize.current
    val frame = render.frame(size)
    // WidgetLayout.squarePlan owns the ringRoom -> squareSplit sequence and this tier's
    // constants (16dp spacer allowance = the two Spacer(8.dp) in this column, capRows 3, no
    // footer, no map). It replaced an infoCap row estimate that overflowed thousands of
    // sizes (worst 22.4dp at 150x150, 1.4x text); the sweep asserts the assembled column
    // fits by calling the same squarePlan.
    val split = WidgetLayout.squarePlan(
        WidgetTier.MEDIUM_SQUARE, frame,
        showHeader = render.config.showHeader, showFooter = false, wantMap = false,
    ).split
    val ringEdge = split.ring
    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeaderRow(car, render)
        Spacer(GlanceModifier.height(8.dp))
        ChargeBarFallback(car, render, ringEdge, split.ringRoom)
        // NOT .defaultWeight() on the row/column itself -- ringEdge is
        // already sized from ringRoom, the exact leftover this row has,
        // so it doesn't need to be stretched to claim more. A weighted
        // row with real drawn content (the ring, the info text) shares
        // the same failure mode as a weighted MapFill ahead of fixed
        // content elsewhere in this file: on a real device the
        // ActionButtons row below it rendered nothing at all, not merely
        // squeezed.
        //
        // A bare, contentless Spacer(defaultWeight()) right after it is
        // the safe way to reclaim whatever's left, though -- the same
        // pattern RailLayout already uses around its own hero content,
        // proven not to starve the fixed buttons that follow it. Without
        // this, whenever the ring hits Scale.ring's own 140dp curve
        // ceiling well below what ringRoom actually budgeted for it, the
        // slack collected as unclaimed blank space at the very bottom of
        // the tile instead of here.
        if (render.config.showRing && car.percent != null) {
            // RingWithContent auto-stacks vertically instead of
            // squeezing ring+info into a cramped row if the tile's
            // actual measured width can't fit them side by side.
            RingWithContent(
                modifier = GlanceModifier.fillMaxWidth(),
                minRowWidth = WidgetLayout.squareRowWidth(WidgetTier.MEDIUM_SQUARE),
                ringWidth = ringEdge,
                frame = render.frame(LocalSize.current),
                ring = { RingImage(car, render, edgeDp = ringEdge.value.toInt()) },
                // hideFields drops PERCENT: RingImage's own centerText
                // already bakes "82%" into the ring beside this stack, so a
                // user with Battery/Fuel selected saw the same number twice
                // on one tile. The LARGE and XL tiers already carried this
                // guard; the two MEDIUM tiers drawing the identical ring did
                // not. Only this branch needs it -- the else below has no
                // ring, so there the field is the only place it appears.
                content = { w ->
                    InfoStack(
                        car, render, max = split.rows,
                        availableWidth = w, hideFields = setOf(WidgetInfoField.PERCENT),
                    )
                },
            )
        } else {
            Column(modifier = GlanceModifier.fillMaxWidth()) {
                InfoStack(car, render, max = split.rows)
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.height(8.dp))
        // availableHeight pinned to one row's worth -- ringRoom only ever
        // reserved buttonHeight(size) ONCE for this whole button block,
        // not a stacked count. Left at its default (the whole tile's
        // height), ActionButtons' own row-vs-stack capacity check
        // compared against far more room than was ever actually budgeted
        // for it, and could pick the stacked column by mistake for any
        // configuration wider than it is tall -- ballooning the button
        // block to many times the one row this tier was ever built for.
        // See ActionButtons' own capacity-check comment for the full
        // mechanism; confirmed by reconstructing it for a realistic
        // XL_TALL size with several actions configured, which overflowed
        // by hundreds of dp before this fix.
        ActionButtons(car, render, max = 4, availableHeight = Scale.buttonHeight(size))
    }
}

@Composable
internal fun MediumWideLayout(car: VehicleSnapshot, render: Render) {
    // Wide MEDIUM: put the ring beside the header/info/buttons stack
    // instead of above it, so a wide-but-short tile spends its extra
    // width on layout instead of leaving it empty beside a centered
    // ring -- RingWithContent falls back to stacking if that width
    // doesn't actually pan out.
    val size = LocalSize.current
    val frame = render.frame(size)
    // Every child is handed the width this column actually gets, so the
    // header's name, the info rows, and the button row all judge their
    // own fit against the real space beside the ring rather than the
    // whole tile.
    // Rows bounded by what is actually LEFT, the way the sibling branch below does it, rather
    // than by Scale.infoCap's flat 38% of raw tile height. This is the no-gauge path, so the
    // column holds exactly a header, the rows and a button row plus two 6dp spacers -- every
    // term of that is a Scale function, so the remainder is computable instead of guessable.
    //
    // infoCap knew nothing about the header or the buttons, so whenever it came out tighter it
    // dropped rows that fit and the tile showed less than it had room for. Where it came out
    // LOOSER, the branch's own comment conceded the problem ("Unbudgeted branch"): nothing was
    // stopping the rows from pushing the buttons off the bottom.
    val rowsRoom = (
        Scale.innerHeight(frame) - Scale.headerHeight(frame) - Scale.buttonHeight(size) - 12.dp
        ).coerceAtLeast(0.dp)
    val content: @Composable (Dp) -> Unit = { w ->
        HeaderRow(car, render, availableWidth = w)
        Spacer(GlanceModifier.height(6.dp))
        InfoStack(
            car, render,
            max = Scale.infoRowsIn(size, rowsRoom, render.theme.textScale, 3),
            availableWidth = w,
        )
        Spacer(GlanceModifier.height(6.dp))
        ActionButtons(car, render, max = 4, availableWidth = w, availableHeight = Scale.buttonHeight(size))
    }
    val showsRing = render.config.showRing && car.percent != null
    // A wide tile spends its axis better on a bar than a circle: it runs
    // the full width under the header, and the column gets the whole
    // tile instead of what was left beside a circle. Always preferred
    // now, not just as a fallback once the ring shrinks below
    // RING_WORTH_IT -- same call BANNER and COMPACT_WIDE make.
    if (showsRing) {
        val w = Scale.innerWidth(frame)
        val barH = Scale.barHeight(size)
        // WidgetLayout.mediumWideBarPlan owns the ringRoom(18dp, no footer) - bar -> tallSplit
        // (capRows 2) sequence -- the same plan the sweep's wide-medium-bar test calls, so
        // the reservation and the assertion can't drift. tallSplit reserves the map first
        // and hands rows the rest; the bar is subtracted before that three-way division.
        val split = WidgetLayout.mediumWideBarPlan(
            frame, showHeader = render.config.showHeader, barHeight = barH,
            wantMap = render.mapBitmap != null,
        )
        val rows = split.rows
        Column(modifier = GlanceModifier.fillMaxSize()) {
            HeaderRow(car, render, availableWidth = w)
            Spacer(GlanceModifier.height(6.dp))
            ChargeBar(car, render.theme, width = w, height = barH)
            if (rows > 0) {
                Spacer(GlanceModifier.height(6.dp))
                InfoStack(car, render, max = rows, availableWidth = w)
            }
            // A FIXED-height module, not a weighted MapFill -- a weighted
            // element here left ActionButtons with no real room at all on
            // a real device (buttons rendered nothing, not even clipped,
            // just absent), regardless of how much space was actually
            // left over. Capped at split.map, the room tallSplit actually
            // reserved for it once the rows above had theirs.
            MapModule(render, split.map)
            // Same availableHeight fix as MediumSquareLayout's own note
            // -- ringRoom only ever reserved one row's worth for this.
            ActionButtons(car, render, max = 4, availableWidth = w, availableHeight = Scale.buttonHeight(size))
        }
        return
    }
    // No ring to show at all (off, or no percent yet): the column gets
    // the whole tile, same shape as the bar branch above without a
    // gauge of any kind. Unbudgeted branch -- content() itself pins
    // ActionButtons' height too, for the same reason.
    // innerWidth, not size.width. The ring branch above hands its column
    // Scale.innerWidth(frame); this one handed over the RAW tile width, so the header's name,
    // the info rows and the button row all judged their own fit against space the root padding
    // had already taken -- two sibling branches of one function disagreeing about what "the
    // width available" means.
    Column(modifier = GlanceModifier.fillMaxSize()) { content(Scale.innerWidth(frame)) }
}

@Composable
internal fun MediumTallLayout(car: VehicleSnapshot, render: Render) {
    // Tall MEDIUM: everything stacked in one column, ring centered --
    // the mirror of MediumWideLayout's side-by-side arrangement.
    val size = LocalSize.current
    val frame = render.frame(size)
    // Hand the ring everything left after the header, buttons, info rows
    // and the map's reserve, instead of a fixed curve plus a trailing
    // void -- see Scale.tallSplit for why the map has to be taken out
    // before the ring is sized rather than after.
    // WidgetLayout.ringHeroPlan owns this tier's spacer allowance (16dp) and capRows 3 (a
    // real per-tier maximum, not an infoCap fraction of raw tile height) -- the same plan the
    // sweep calls. MEDIUM_TALL has no footer and no primaryValue line.
    val split = WidgetLayout.ringHeroPlan(
        WidgetTier.MEDIUM_TALL, frame,
        showHeader = render.config.showHeader, showFooter = false,
        wantMap = render.mapBitmap != null,
    ).split
    val rows = split.rows
    val ringEdge = split.ring
    // Whatever is left after the header, ring, stats and buttons goes to
    // the map when the user has one enabled and the car has coordinates.
    // MEDIUM is the smallest tier that shows one at all now: it used to
    // start at LARGE, so a 2x2 spent its entire remainder on two weighted
    // spacers -- dead space by construction. A weighted map takes exactly
    // the same room and puts the car's location in it, and collapses to
    // nothing when there's no bitmap, which is when the spacers are the
    // right answer again.
    val showsRing = render.config.showRing && car.percent != null
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        HeaderRow(car, render)
        Spacer(GlanceModifier.height(8.dp))
        // The bar the gauge falls back to when tallSplit left room for a
        // gauge but not for a RING: Scale.ring yields 0 below MIN_RING (24dp)
        // rather than drawing a smudge, and StatusGlyph declines at 0 too, so
        // without this the tier drew no gauge whatsoever in that case -- a
        // bar needs 10-14dp where a ring needs 24. Draws only when ringEdge
        // came back 0, out of room the ring was budgeted and didn't use, so
        // it can't squeeze anything below it.
        ChargeBarFallback(car, render, ringEdge, split.ringRoom)
        if (showsRing) {
            RingImage(car, render, edgeDp = ringEdge.value.toInt())
            Spacer(GlanceModifier.height(8.dp))
        } else {
            // The same fallback glyph every sibling tier carries.
            StatusGlyph(car, render.theme, sizeDp = ringEdge.value.toInt())
        }
        // PERCENT is dropped only when the ring is actually drawn -- its
        // centerText bakes the number in, so showing the field too printed it
        // twice. In the glyph branch nothing else carries it, so it stays.
        InfoStack(
            car, render, max = rows,
            hideFields = if (showsRing) setOf(WidgetInfoField.PERCENT) else emptySet(),
        )
        // A FIXED-height module, not a weighted MapFill -- see
        // MediumWideLayout's own note: a weighted element ahead of
        // ActionButtons left it with no real room on a real device,
        // buttons rendered absent rather than merely clipped. MapModule
        // already draws nothing when there's no bitmap.
        MapModule(render, split.map)
        // Same availableHeight fix as MediumSquareLayout's own note.
        ActionButtons(car, render, max = 4, availableHeight = Scale.buttonHeight(size))
    }
}

@Composable
internal fun LargeWideLayout(car: VehicleSnapshot, render: Render) {
    // A wide tile spends its axis better on a bar than a circle. The
    // earlier version of this fix kept the ring's old side-by-side
    // column shape and just drew a bar inside it -- which bounded the
    // bar to a fraction of the tile's WIDTH the same way the ring used
    // to be bounded by height, so the bar read as "unusually small"
    // sitting in a narrow column with empty space above and below it
    // (RingWithContent centres its row vertically; a short bar in a
    // tall weighted row leaves slack on both sides). Reported from a
    // real device. Rebuilt to run the bar the FULL width under the
    // header instead, the same shape MediumWideLayout's own bar branch
    // already uses -- no side column, no wasted vertical margin.
    val size = LocalSize.current
    val frame = render.frame(size)
    val w = Scale.innerWidth(frame)
    val showsRing = render.config.showRing && car.percent != null
    // BarHero's own default budget assumes it's the row's only vertical
    // content, true for BannerLayout/CompactWideLayout but not here --
    // this column also carries a header, footer, info rows, a map and a
    // button row. ringRoom's own header/footer/button/spacer subtraction
    // gives the safe leftover; tallSplit then divides THAT between the
    // map, the info rows and whatever's left for the hero, the exact
    // division every other tall tier already trusts -- reused here for
    // its whole three-way split, not just its ring size.
    //
    // wantMap = true is the fix for a real overflow this tier had: the
    // map used to be handed its own full natural height with NOTHING
    // subtracted from the budget for it, on the theory that a fixed-
    // height MapModule was safe because it wasn't a weighted MapFill.
    // Fixed-height only avoids the "weighted element swallows a
    // sibling's room" failure mode -- it does nothing to stop the SUM of
    // everything in the column from exceeding the tile if the map's own
    // height was never subtracted from what the hero and rows above it
    // were sized against. Reported from a real device: a header, a bar,
    // a map, and then nothing -- the buttons had overflowed off the
    // bottom of the tile's own allocated bounds.
    //
    // WidgetLayout.wideBarPlan owns this tier's spacer allowance (30dp = the three explicit
    // 10dp Spacers below the header: before the hero, after it, before the buttons) and its
    // capRows 4 -- the same plan the sweep calls, so they can't drift.
    val split = WidgetLayout.wideBarPlan(
        WidgetTier.LARGE_WIDE, frame,
        showHeader = render.config.showHeader, showFooter = render.config.showFooter,
        wantMap = render.mapBitmap != null,
    ).split
    val rows = split.rows
    val heroAvail = split.ring
    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeaderRow(car, render)
        FooterRow(car, render)
        Spacer(GlanceModifier.height(10.dp))
        if (showsRing) {
            BarHero(car, render, width = w, avail = heroAvail, showNameFallback = false)
            Spacer(GlanceModifier.height(10.dp))
        }
        if (rows > 0) {
            InfoStack(
                car, render, max = rows, availableWidth = w, footerShown = true,
                // Hide PERCENT only when the ring's BarHero is actually drawing it above.
                // With the ring off, BarHero doesn't render (see the `if (showsRing)` block),
                // so the Battery field is the ONLY place the percent would appear -- hiding it
                // unconditionally dropped it entirely. Mirrors MediumTall/MediumSquare.
                hideFields = if (showsRing) setOf(WidgetInfoField.PERCENT) else emptySet(),
            )
        }
        // A FIXED-height module, not a weighted MapFill -- see
        // MediumWideLayout's own note -- capped at split.map, the room
        // tallSplit actually reserved for it, not the map's own ideal
        // height.
        MapModule(render, split.map)
        Spacer(GlanceModifier.height(10.dp))
        // Same availableHeight fix as MediumSquareLayout's own note --
        // ringRoom only ever reserved one row's worth for this block.
        ActionButtons(car, render, max = 5, availableHeight = Scale.buttonHeight(size))
    }
}

@Composable
internal fun LargeSquareLayout(car: VehicleSnapshot, render: Render) {
    // Square LARGE: same ring-left / info-right split as LargeWideLayout,
    // but the map runs full-width below the row instead of being
    // squeezed inside the narrower info column -- the balanced
    // ring/square version of the Wide/Square/Tall split MEDIUM and XL
    // already have, giving LARGE its own third shape too.
    val size = LocalSize.current
    val frame = render.frame(size)
    // Through Scale.squareSplit so the info rows come out of the same
    // column budget as the ring instead of Scale.infoCap's fraction of the
    // raw tile height -- see squareSplit's note for what that overflowed.
    // WidgetLayout.squarePlan owns this tier's constants (20dp spacer allowance, capRows 4,
    // footer, map). The sweep calls the same plan.
    val split = WidgetLayout.squarePlan(
        WidgetTier.LARGE_SQUARE, frame,
        showHeader = render.config.showHeader, showFooter = render.config.showFooter,
        wantMap = render.mapBitmap != null,
    ).split
    // Full-width map below the row, so here it competes with the ring for
    // the same column and has to be taken out of the ring's budget first.
    val mapRoom = split.map
    val ringEdge = split.ring
    // RingOrGlyph draws the percent-bearing ring only when both hold; otherwise the
    // icon-only glyph shows and carries no number.
    val showsRing = render.config.showRing && car.percent != null
    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeaderRow(car, render)
        FooterRow(car, render)
        Spacer(GlanceModifier.height(10.dp))
        ChargeBarFallback(car, render, ringEdge, split.ringRoom)
        // NOT .defaultWeight() -- same fix as MediumSquareLayout's own
        // note: ringEdge is already sized from ringRoom - mapRoom, the
        // real leftover this row has, so it doesn't need to be stretched
        // to fill anything. A weighted row here is the same failure mode
        // as a weighted MapFill ahead of fixed content -- ActionButtons
        // below it rendered nothing at all on a real device, not merely
        // squeezed. Matched to XlSquareLayout's own RingWithContent call,
        // which was already unweighted for this exact reason.
        RingWithContent(
            modifier = GlanceModifier.fillMaxWidth(),
            minRowWidth = WidgetLayout.squareRowWidth(WidgetTier.LARGE_SQUARE),
            ringWidth = ringEdge,
            frame = render.frame(LocalSize.current),
            ring = {
                RingOrGlyph(car, render, edgeDp = ringEdge.value.toInt())
            },
            // Hide PERCENT only when the ring is drawn: RingImage's centerText already bakes
            // "82%" into the ring beside this stack, so showing the field too printed it
            // twice. But with the ring off the glyph carries no number, so the field is the
            // only place the percent appears -- hiding it unconditionally dropped it. Mirrors
            // MediumSquare/MediumTall.
            content = { w ->
                InfoStack(
                    car, render, max = split.rows, availableWidth = w,
                    footerShown = true,
                    hideFields = if (showsRing) setOf(WidgetInfoField.PERCENT) else emptySet(),
                )
            },
        )
        // Bare, contentless Spacer -- see MediumSquareLayout's own note:
        // reclaims whatever Scale.ring's 140dp curve ceiling left
        // unclaimed once a big enough tile budgeted more than that for
        // it, without risking the "weighted content starves a later
        // fixed sibling" failure a weighted RingWithContent had here.
        Spacer(GlanceModifier.defaultWeight())
        MapModule(render, mapRoom)
        Spacer(GlanceModifier.height(10.dp))
        // Same availableHeight fix as MediumSquareLayout's own note.
        ActionButtons(car, render, max = 5, availableHeight = Scale.buttonHeight(size))
    }
}

@Composable
internal fun LargeTallLayout(car: VehicleSnapshot, render: Render) {
    // Tall LARGE: ring centered full-width above the info stack instead
    // of beside it -- there's more height to spend than width here, so a
    // side-by-side split would leave the info column cramped.
    val size = LocalSize.current
    val frame = render.frame(size)
    // WidgetLayout.ringHeroPlan owns this tier's spacer allowance (20dp) and capRows 4 -- the
    // same plan the sweep calls. LARGE_TALL has a footer, no primaryValue line.
    val split = WidgetLayout.ringHeroPlan(
        WidgetTier.LARGE_TALL, frame,
        showHeader = render.config.showHeader, showFooter = render.config.showFooter,
        wantMap = render.mapBitmap != null,
    ).split
    val ringEdge = split.ring
    // Matches RingOrGlyph's own guard: the ring (which bakes "82%" into its centre) draws
    // only when both hold; otherwise the icon-only glyph shows and carries no number.
    val showsRing = render.config.showRing && car.percent != null
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        HeaderRow(car, render)
        FooterRow(car, render)
        Spacer(GlanceModifier.height(10.dp))
        // Bar fallback for when tallSplit left room for a gauge but not for
        // a RING -- see MediumTallLayout's own note. Both branches below
        // decline at 0 (RingImage and StatusGlyph each early-return), so
        // without this the tier drew no gauge at all, which is reachable at
        // 1.4x text on a tile near LARGE's own 240x170 floor.
        ChargeBarFallback(car, render, ringEdge, split.ringRoom)
        RingOrGlyph(car, render, edgeDp = ringEdge.value.toInt())
        Spacer(GlanceModifier.height(10.dp))
        // Hide PERCENT only when the ring is actually drawn (its centerText bakes "82%" in,
        // so showing the field too would print it twice). With the ring off the glyph shows
        // instead and carries no number, so the Battery field is the only place the percent
        // appears -- dropping it there hid it entirely. Mirrors MediumTallLayout.
        InfoStack(
            car, render, max = split.rows, footerShown = true,
            hideFields = if (showsRing) setOf(WidgetInfoField.PERCENT) else emptySet(),
        )
        // A FIXED-height module, not a weighted MapFill -- see
        // MediumWideLayout's own note: a weighted element ahead of
        // ActionButtons left it with no real room on a real device.
        MapModule(render, split.map)
        // Same availableHeight fix as MediumSquareLayout's own note.
        ActionButtons(car, render, max = 5, availableHeight = Scale.buttonHeight(size))
    }
}

@Composable
internal fun XlWideLayout(car: VehicleSnapshot, render: Render) {
    // Same rebuild as LargeWideLayout, same reasoning: the bar used to
    // live in a side column bounded to a fraction of the tile's width
    // (mirroring the ring it replaced), which read as an undersized bar
    // floating in a tall, mostly empty row. Runs the full width under
    // the header now instead, matching MediumWideLayout's own shape.
    val size = LocalSize.current
    val frame = render.frame(size)
    val w = Scale.innerWidth(frame)
    val showsRing = render.config.showRing && car.percent != null
    // See LargeWideLayout's own note: BarHero's default budget assumes
    // it's the row's only content, which isn't true here either, and
    // tallSplit does the map/row/leftover three-way split instead of
    // handing the map its own full natural height with nothing
    // subtracted from what everything else was budgeted against --
    // wantMap = true is the fix for the exact overflow LargeWideLayout's
    // own note describes.
    // WidgetLayout.wideBarPlan owns this tier's spacer allowance (42dp = three explicit 14dp
    // Spacers below the header) and its capRows (all info fields), shared with the sweep.
    val split = WidgetLayout.wideBarPlan(
        WidgetTier.XL_WIDE, frame,
        showHeader = render.config.showHeader, showFooter = render.config.showFooter,
        wantMap = render.mapBitmap != null,
    ).split
    val rows = split.rows
    val heroAvail = split.ring
    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeaderRow(car, render)
        FooterRow(car, render)
        Spacer(GlanceModifier.height(14.dp))
        if (showsRing) {
            BarHero(car, render, width = w, avail = heroAvail, showNameFallback = false)
            Spacer(GlanceModifier.height(14.dp))
        }
        if (rows > 0) {
            InfoStack(
                car, render, max = rows, availableWidth = w, footerShown = true,
                // See LargeWideLayout: hide PERCENT only when the ring's BarHero draws it.
                hideFields = if (showsRing) setOf(WidgetInfoField.PERCENT) else emptySet(),
            )
        }
        // A FIXED-height module, not a weighted MapFill -- see
        // MediumWideLayout's own note -- capped at split.map, not the
        // map's own ideal height.
        MapModule(render, split.map)
        Spacer(GlanceModifier.height(14.dp))
        // Same availableHeight fix as MediumSquareLayout's own note.
        ActionButtons(car, render, max = WidgetAction.ALL.size, availableHeight = Scale.buttonHeight(size))
    }
}

@Composable
internal fun XlTallLayout(car: VehicleSnapshot, render: Render) {
    // Tall XL: one big centered ring up top with the primary value under
    // it, the full info stack and map stacked below rather than split
    // into side-by-side columns that would squeeze on a narrow-but-tall
    // dashboard-sized tile.
    val size = LocalSize.current
    val frame = render.frame(size)
    // The primaryValue line under the ring ("69% · 219 mi") is real,
    // known-size content that was never subtracted from the budget
    // tallSplit divides between the ring, the info rows and the map --
    // ringRoom's own spacers argument only ever covered the fixed
    // Spacer()s in this column (14 + 8 + 14 = 36.dp), not the text line
    // sitting between two of them. Undercounting it meant ring + rows +
    // map could together claim EVERYTHING ringRoom reported free, this
    // line's own height included, overflowing the tile by however tall
    // it rendered -- confirmed by rebuilding this arithmetic outside the
    // codebase: up to 46dp on a real XL_TALL size, enough to push the
    // map and every button off the bottom of the tile's own bounds.
    // WidgetLayout.ringHeroPlan owns this tier's 36dp base spacer allowance PLUS the extra
    // title line it reserves for the primaryValue ("69% . 219 mi") drawn under the ring --
    // undercounting that line once overflowed the tile by up to 46dp. capRows is all info
    // fields. The sweep calls the same plan.
    val split = WidgetLayout.ringHeroPlan(
        WidgetTier.XL_TALL, frame,
        showHeader = render.config.showHeader, showFooter = render.config.showFooter,
        wantMap = render.mapBitmap != null,
    ).split
    val ringEdge = split.ring
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        HeaderRow(car, render)
        FooterRow(car, render)
        Spacer(GlanceModifier.height(14.dp))
        // Same bar fallback as LargeTallLayout / MediumTallLayout: this tier
        // reserves a whole extra text line (primaryValueHeight) on top of
        // header, footer and buttons, so it runs out of ring room sooner
        // than its size suggests.
        ChargeBarFallback(car, render, ringEdge, split.ringRoom)
        RingOrGlyph(car, render, edgeDp = ringEdge.value.toInt())
        Spacer(GlanceModifier.height(8.dp))
        // innerWidth + singleLine: this value line's height (primaryValueHeight, one
        // Scale.lineHeight) is reserved once in ringRoom's spacers, so it must not wrap;
        // and it sits inside Content's root padding, so innerWidth is its real width. The
        // old `- 24.dp` was both a guess at the padding and unbounded on lines.
        FitText(
            primaryValue(car, render), titleStyle(render.theme),
            maxWidth = Scale.innerWidth(frame), horizontalAlignment = Alignment.CenterHorizontally,
            singleLine = true,
        )
        Spacer(GlanceModifier.height(14.dp))
        InfoStack(
            car, render, max = split.rows, footerShown = true,
            hideFields = setOf(WidgetInfoField.RANGE, WidgetInfoField.PERCENT),
        )
        // A FIXED-height module, not a weighted MapFill -- see
        // MediumWideLayout's own note: a weighted element ahead of
        // ActionButtons left it with no real room on a real device.
        MapModule(render, split.map)
        // Same availableHeight fix as MediumSquareLayout's own note.
        ActionButtons(car, render, max = WidgetAction.ALL.size, availableHeight = Scale.buttonHeight(size))
    }
}

@Composable
internal fun XlSquareLayout(car: VehicleSnapshot, render: Render) {
    // Square XL: a balanced ring-left / info-right split above a full-
    // width map, distinct from XlWideLayout's value-under-ring emphasis
    // and XlTallLayout's fully stacked column.
    val size = LocalSize.current
    val frame = render.frame(size)
    // WidgetLayout.squarePlan owns this tier's constants (24dp spacer allowance, capRows 4,
    // footer, map) -- the same plan the sweep calls, so rows come out of the same column
    // budget as the ring instead of an infoCap fraction of raw tile height.
    val split = WidgetLayout.squarePlan(
        WidgetTier.XL_SQUARE, frame,
        showHeader = render.config.showHeader, showFooter = render.config.showFooter,
        wantMap = render.mapBitmap != null,
    ).split
    // Full-width map below the row, competing with the ring for the same
    // column -- reserved first, as in LargeSquareLayout.
    val mapRoom = split.map
    val ringEdge = split.ring
    // RingOrGlyph draws the percent-bearing ring only when both hold; else the glyph (no
    // number) shows, so the Battery field becomes the sole carrier of the percent.
    val showsRing = render.config.showRing && car.percent != null
    Column(modifier = GlanceModifier.fillMaxSize()) {
        HeaderRow(car, render)
        FooterRow(car, render)
        Spacer(GlanceModifier.height(12.dp))
        ChargeBarFallback(car, render, ringEdge, split.ringRoom)
        RingWithContent(
            modifier = GlanceModifier.fillMaxWidth(),
            minRowWidth = WidgetLayout.squareRowWidth(WidgetTier.XL_SQUARE),
            ringWidth = ringEdge,
            frame = render.frame(LocalSize.current),
            ring = {
                RingOrGlyph(car, render, edgeDp = ringEdge.value.toInt())
            },
            // Hide PERCENT only when the ring draws it -- see LargeSquareLayout's own note;
            // with the ring off the field is the only place the percent shows.
            content = { w ->
                InfoStack(
                    car, render, max = split.rows, availableWidth = w,
                    footerShown = true,
                    hideFields = if (showsRing) setOf(WidgetInfoField.PERCENT) else emptySet(),
                )
            },
        )
        // Bare, contentless Spacer -- see LargeSquareLayout's own note:
        // reclaims whatever Scale.ring's 140dp curve ceiling left
        // unclaimed on a big XL_SQUARE tile without risking a weighted
        // RingWithContent starving the fixed content after it. XL_SQUARE
        // has no upper size bound, so this gap grows without one too --
        // confirmed up to 166dp of unclaimed space at 600x600 before
        // this fix, versus none once this spacer can claim it instead.
        Spacer(GlanceModifier.defaultWeight())
        // A FIXED-height module, not a weighted MapFill -- see
        // MediumWideLayout's own note.
        MapModule(render, mapRoom)
        // Same availableHeight fix as MediumSquareLayout's own note.
        ActionButtons(car, render, max = WidgetAction.ALL.size, availableHeight = Scale.buttonHeight(size))
    }
}
