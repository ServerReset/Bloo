package com.bloo.bluelink.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import com.bloo.bluelink.data.VehicleSnapshot

/**
 * Draws whatever [WidgetBlueprint] decided, for any size, with no per-tier
 * knowledge whatsoever.
 *
 * This is the second half of the widget's reboot and the reason the first
 * half was worth doing. There used to be eighteen layout functions here,
 * one per aspect-ratio tier, each independently deciding what to show AND
 * arranging it AND budgeting its own vertical space. Those three jobs being
 * tangled together in eighteen places is what made the widget's bug history
 * what it was: a fix to one tier's budget taught the other seventeen
 * nothing, so the same mistake kept reappearing one tier over.
 *
 * Here the deciding is already done. [WidgetCanvas] walks the blueprint's
 * bands in order and draws each one INSIDE THE HEIGHT IT WAS GIVEN, so the
 * arrangement cannot disagree with the budget -- the budget is an argument,
 * not a convention. A module that wants more room than its band does not
 * push the next module off the tile; it fits itself, because every module
 * below already takes the room it has as a parameter.
 *
 * Three arrangements cover all 42 sizes, and which one is used is a property
 * of the blueprint rather than of a tier table:
 *
 *  - a one-row STRIP (every 2x1..7x1), which has no vertical axis to divide;
 *  - a SIDE-BY-SIDE row, for wide short tiles where stacking would starve
 *    both halves but a row gives each a real slice;
 *  - the ordinary vertical STACK for everything else.
 */
@Composable
internal fun WidgetCanvas(car: VehicleSnapshot, render: Render) {
    val size = LocalSize.current
    val blueprint = WidgetBlueprint.plan(size, render.config, factsFor(car, render))
    when {
        // Asked, not re-derived. Inferring "is this a strip" here from the row
        // or band count would put the decision in two places, and two places
        // deciding the same thing is precisely how a tile gets arranged one way
        // and budgeted the other.
        blueprint.isStrip -> Strip(car, render, blueprint)
        blueprint.sideBySide -> SideBySide(car, render, blueprint)
        else -> Stack(car, render, blueprint)
    }
}

/**
 * What the blueprint needs to know about this particular car and render.
 *
 * Kept here rather than inside the allocator so the allocator stays a pure
 * function of plain values -- which is what lets its test enumerate 24,192
 * combinations directly instead of constructing snapshots.
 *
 * [WidgetBlueprint.Facts.actionCount] is the RESOLVED count, not the
 * configured one: a Kia has no flash/horn endpoint and a car with no
 * chargeable pack hides every charge verb, so budgeting for the configured
 * list would reserve button room for buttons that are never drawn.
 */
private fun factsFor(car: VehicleSnapshot, render: Render) = WidgetBlueprint.Facts(
    hasPercent = car.percent != null,
    // Coordinates and a successfully fetched tile are already one question by
    // the time rendering starts -- provideGlance only builds a bitmap when the
    // map is switched on AND the car has a position AND the tiles came back.
    hasCoords = render.mapBitmap != null,
    hasMapBitmap = render.mapBitmap != null,
    actionCount = resolvedActions(car, render, max = Int.MAX_VALUE).size,
    infoFieldCount = render.config.infoFields.size,
    multipleCars = render.hasSwitcher,
)

/** One band, drawn inside exactly the height it was allocated. */
@Composable
private fun BandBox(height: Dp, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier.fillMaxWidth().height(height),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * The ordinary vertical arrangement: bands top to bottom, each in its own
 * allotted height, separated by the blueprint's gap.
 *
 * Note what is absent -- there is no trailing weighted [Spacer]. Every big
 * tier used to end with one, which by definition collects ALL the slack in a
 * single place: on a 600x520 tile that was a black gap taller than the
 * content above it, with the buttons shoved against the bottom edge. The
 * allocator already distributed that slack to the modules that can use it,
 * so there is nothing left over to pool.
 */
@Composable
private fun Stack(
    car: VehicleSnapshot,
    render: Render,
    blueprint: WidgetBlueprint.Blueprint,
) {
    Column(
        modifier = GlanceModifier.fillMaxWidth().fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        // Centred, which only ever shows on a tile that has more room than it has
        // things to say -- a large widget with both the map and the ring switched
        // off, say. Everything else fills its height exactly (the allocator's
        // ceilings and its spread gap see to that), so this is a no-op there.
        //
        // Where it does apply, centred content reads as deliberate and top-stacked
        // content reads as a layout that ran out. The blueprint has already capped
        // the gap at the point where spacing stops looking like rhythm, so the rest
        // belongs as an even margin rather than a hole under the last band.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        blueprint.bands.forEachIndexed { i, band ->
            if (i > 0) Spacer(GlanceModifier.height(blueprint.gap))
            BandBox(band.height) { Module(band.module, band.height, car, render, blueprint) }
        }
    }
}

/**
 * Hero beside content, for the wide short shapes (7x2, 7x3 and their
 * neighbours) where there is width to spare and almost no height.
 *
 * Stacking these starves both halves: the hero band ends up too short to
 * hold a ring and the remaining bands too short to hold much else. A row
 * gives the mark its own column at the tile's full height -- which is how a
 * 7x2 can carry a real gauge at all -- and hands everything else the width
 * that is genuinely left, not the whole tile, so nothing measures itself
 * against room it does not have.
 */
@Composable
private fun SideBySide(
    car: VehicleSnapshot,
    render: Render,
    blueprint: WidgetBlueprint.Blueprint,
) {
    val markWidth = blueprint.ringEdge
    // Only subtract the gap when there is actually a mark to sit beside it.
    // Charging for a separator that is never drawn would hand the content
    // column less width than it has, and a column measuring itself against
    // room it does not have is the same double-booking mistake in reverse.
    val rest = if (markWidth > 0.dp) {
        (blueprint.innerWidth - markWidth - blueprint.gap).coerceAtLeast(0.dp)
    } else {
        blueprint.innerWidth
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth().fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (markWidth > 0.dp) {
            Box(
                modifier = GlanceModifier.width(markWidth).fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) { Hero(blueprint, car, render, room = markWidth, width = markWidth) }
            Spacer(GlanceModifier.width(blueprint.gap))
        }
        Column(
            modifier = GlanceModifier.width(rest).fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val others = blueprint.bands.filter { it.module != WidgetBlueprint.Module.HERO }
            others.forEachIndexed { i, band ->
                if (i > 0) Spacer(GlanceModifier.height(blueprint.gap))
                Box(
                    modifier = GlanceModifier.width(rest).height(band.height),
                    contentAlignment = Alignment.Center,
                ) { Module(band.module, band.height, car, render, blueprint, width = rest) }
            }
        }
    }
}

/**
 * The one-row strip: every 2x1 through 7x1 tile.
 *
 * A tile roughly 40dp tall has no vertical axis worth dividing, so this is
 * the one arrangement that is horizontal by nature rather than by choice.
 * The mark takes its square, the name takes what it can, and the buttons
 * take the rest -- with the count already truncated by the blueprint to what
 * genuinely fits, because a partial button drawn past the edge is not a
 * smaller button, it is an invisible one.
 */
@Composable
private fun Strip(
    car: VehicleSnapshot,
    render: Render,
    blueprint: WidgetBlueprint.Blueprint,
) {
    val edge = blueprint.ringEdge
    val afterMark = (blueprint.innerWidth - edge - if (edge > 0.dp) blueprint.gap else 0.dp)
        .coerceAtLeast(0.dp)
    Row(
        modifier = GlanceModifier.fillMaxWidth().fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (edge > 0.dp) {
            RingOrGlyph(car, render, edgeDp = edge.value.toInt())
            Spacer(GlanceModifier.width(blueprint.gap))
        }
        if (blueprint.buttonCount <= 0) {
            // Nothing competing for the width, so the name gets all of it.
            Box(modifier = GlanceModifier.width(afterMark), contentAlignment = Alignment.Center) {
                NameAndStat(car, render, width = afterMark)
            }
            return@Row
        }
        val nameWidth = afterMark * 0.35f
        val buttonWidth = (afterMark - nameWidth).coerceAtLeast(0.dp)
        Box(modifier = GlanceModifier.width(nameWidth), contentAlignment = Alignment.Center) {
            NameAndStat(car, render, width = nameWidth)
        }
        Box(modifier = GlanceModifier.width(buttonWidth), contentAlignment = Alignment.Center) {
            ActionButtons(
                car, render,
                max = blueprint.buttonCount,
                modifier = GlanceModifier.fillMaxWidth(),
                availableWidth = buttonWidth,
                availableHeight = blueprint.innerHeight,
            )
        }
    }
}

/**
 * One module, drawn into a known width and a known height.
 *
 * Every branch passes the band's real room down rather than letting the
 * module ask [LocalSize] for the whole tile. That is the single rule this
 * whole rebuild is organised around: a module that measures itself against
 * the tile while sharing that tile with five other modules is double-booking
 * the same space, and RemoteViews resolves double-booking by drawing the
 * loser off the edge.
 */
@Composable
private fun Module(
    module: WidgetBlueprint.Module,
    room: Dp,
    car: VehicleSnapshot,
    render: Render,
    blueprint: WidgetBlueprint.Blueprint,
    width: Dp = blueprint.innerWidth,
) {
    when (module) {
        WidgetBlueprint.Module.HEADER -> HeaderRow(car, render, availableWidth = width)

        WidgetBlueprint.Module.HERO -> Hero(blueprint, car, render, room = room, width = width)

        WidgetBlueprint.Module.INFO -> InfoStack(
            car, render,
            max = blueprint.infoRows,
            availableWidth = width,
            // The footer already says "Updated 9 min ago"; without this the
            // same sentence appeared twice on one tile, once as an info row
            // and once as the footer beneath the buttons.
            footerShown = blueprint.has(WidgetBlueprint.Module.FOOTER),
            // Whatever the hero is already showing. The hero is the authority
            // on those numbers, so the stack yields them rather than printing
            // 69% a second time directly underneath the first.
            hideFields = blueprint.suppressedInfo,
        )

        WidgetBlueprint.Module.MAP -> MapModule(render, room = room)

        WidgetBlueprint.Module.BUTTONS -> ActionButtons(
            car, render,
            max = blueprint.buttonCount,
            vertical = blueprint.buttonsStacked,
            modifier = GlanceModifier.fillMaxWidth(),
            availableWidth = width,
            availableHeight = room,
        )

        WidgetBlueprint.Module.FOOTER -> FooterRow(car, render)
    }
}

/**
 * The charge state, drawn the way the blueprint decided it can be drawn --
 * from the room the hero band actually won, never from a tier.
 *
 * The distinction matters because the alternative is what shipped: the tier
 * decided the treatment, then the treatment discovered it had no room, and a
 * treatment with no room drew nothing at all. Here every branch is already
 * known to fit, so the only way to get an empty hero is for the blueprint to
 * have said [WidgetBlueprint.Hero.NONE] -- an explicit decision rather than a
 * silent failure.
 */
@Composable
private fun Hero(
    blueprint: WidgetBlueprint.Blueprint,
    car: VehicleSnapshot,
    render: Render,
    room: Dp,
    width: Dp,
) {
    when (blueprint.hero) {
        WidgetBlueprint.Hero.RING ->
            RingOrGlyph(car, render, edgeDp = blueprint.ringEdge.value.toInt())

        WidgetBlueprint.Hero.BAR -> BarHero(
            car, render,
            width = width,
            avail = room,
            // The name is already in the header whenever there is one, so the
            // fallback would print it twice -- "Lanas Whip", then "Lanas Whip
            // / 67% · 219 mi" directly under it.
            showNameFallback = !blueprint.has(WidgetBlueprint.Module.HEADER),
        )

        // The percentage sits ABOVE the bar rather than beside it: beside would
        // split a width this tile does not have into two halves that each fit
        // nothing. FitText's own chain then handles the label -- and this is the
        // one place character stacking is right, so a "74%" too wide even for the
        // narrowest tile becomes 7 / 4 / % down the column instead of vanishing.
        WidgetBlueprint.Hero.VBAR -> Column(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val labelHeight = Scale.lineHeight(Scale.valueSp(LocalSize.current).value, render.theme.textScale)
            val barHeight = (room - labelHeight - 4.dp).coerceAtLeast(0.dp)
            FitText(
                "${car.percent ?: 0}%",
                valueStyle(render.theme),
                maxWidth = width,
                horizontalAlignment = Alignment.CenterHorizontally,
            )
            if (barHeight > 0.dp) {
                Spacer(GlanceModifier.height(4.dp))
                VerticalChargeBar(
                    car, render.theme,
                    // A bar wide enough to read as a gauge but never wider than
                    // the slot it was given.
                    width = minOf(width, 14.dp),
                    height = barHeight,
                )
            }
        }

        WidgetBlueprint.Hero.LINE -> PrimaryInfoLine(car, render, maxWidth = width)

        WidgetBlueprint.Hero.NONE -> Unit
    }
}
