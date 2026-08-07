package com.bloo.bluelink.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Continuous size scaling for the widget, lifted out of [CarWidget] so it can
 * be tested directly.
 *
 * It used to be a private object inside CarWidget, which put every vertical
 * budget in this file -- the ones that decide whether a layout fits its tile
 * at all -- out of reach of any test. Those budgets have been wrong four
 * times (the button-height cap, the ring room, the bar hero, the name/stat
 * pair), each time found by simulating this arithmetic outside the codebase
 * and each time re-provable only by simulating it again. WidgetScaleTest now
 * does that in CI instead, against the real functions rather than a
 * hand-copied model of them, which is the whole reason for the move --
 * [tierFor] was extracted to [WidgetTier] for exactly the same reason.
 *
 * Nothing else changes: it is still `Scale` in the same package, so every
 * call site reads identically.
 */
internal object Scale {
    // The real span this app's widget can ever be measured at: MIN matches
    // the manifest's declared floor (car_widget_info.xml minWidth/Height),
    // MAX is comfortably into XL territory -- past it every value here is
    // already at its ceiling, so a bigger widget just gets more empty
    // margin rather than ever-growing text.
    private const val MIN_DIM = 40f
    private const val MAX_DIM = 320f

    private fun lerp(t: Float, from: Float, to: Float): Float = from + (to - from) * t.coerceIn(0f, 1f)

    /** 0f at the smallest possible widget, 1f at MAX_DIM and up, based on
     *  the SHORTER measured side (the one that actually constrains how
     *  much can fit, regardless of how long the other side stretches). */
    fun progress(size: DpSize): Float {
        val short = minOf(size.width.value, size.height.value)
        return ((short - MIN_DIM) / (MAX_DIM - MIN_DIM)).coerceIn(0f, 1f)
    }

    /** The ring's continuous target size, capped by [maxAvailable] -- each
     *  tier already knows how much room it actually has left after its own
     *  header/button/footer siblings (see each RingImage call site's own
     *  comment), so this is deliberately two numbers combined: "how big
     *  the ring WANTS to be at this size" and "how big it's SAFE to be
     *  here," never just one or the other. */
    fun ring(size: DpSize, maxAvailable: Dp): Dp =
        // Below MIN_RING the ring reads as a smudge rather than a gauge,
        // and drawing it would push the column past its budget anyway --
        // so yield the space to the text instead. RingImage/StatusGlyph
        // both render nothing at 0, so callers need no extra branch.
        if (maxAvailable < MIN_RING) 0.dp
        else minOf(lerp(progress(size), 28f, 140f).dp, maxAvailable)

    /** Smallest ring worth drawing; below this the space goes to text. */
    private val MIN_RING = 24.dp

    /**
     * The ring on a TALL tile, where it is the hero rather than one item
     * in a row.
     *
     * [ring]'s continuous curve tops out at 140dp, which is right when
     * the ring shares a row with text but leaves a tall widget looking
     * broken: a 230x535 tile spent its first 140dp on the ring and the
     * remaining ~250dp on nothing at all, because the leftover went into
     * a single trailing weighted Spacer. Reported from a real device --
     * a huge black void down the middle of the widget.
     *
     * Here the ring simply takes the room it's given, bounded by the
     * tile's own width so it stays circular rather than by an arbitrary
     * ceiling.
     */
    fun ringHero(size: DpSize, maxAvailable: Dp): Dp {
        val room = minOf(maxAvailable, size.width - 24.dp)
        return if (room < MIN_RING) 0.dp else room
    }

    // ringWide() was removed. It bounded a WIDE tier's ring to 0.42 of the tile
    // width, which fixed a real device report (a 300dp-tall ring row with a 140dp
    // ring floating in the middle of it) -- but the wide tiers then moved to the
    // BAR treatment outright (BarHero/ChargeBar running the full width under the
    // header) rather than a ring in a side column, and nothing called it again.
    //
    // Deleted rather than left in place because it was the misleading kind of dead
    // code: a considered 14-line docstring presenting itself as the live answer to
    // "how does a wide tile size its ring", for a question the code no longer
    // asks. Ring-vs-bar is decided by the WidgetTier dispatch in CarWidget.Content;
    // within a tier, Scale.ring returning 0 is what selects ChargeBarFallback.

    /** Estimated height of an [InfoStack] of [rows] rows, so a tall tier
     *  can leave space for it before handing the rest to [ringHero]. */
    fun infoBlockHeight(size: DpSize, rows: Int, textScale: Float): Dp =
        (lineHeight(valueSp(size).value, textScale).value * rows + 2f * rows).dp

    /** The inverse of [infoBlockHeight]: how many rows actually fit in
     *  [room]. [infoCap] estimates the room from a fraction of the tile;
     *  this is for the layouts that have already worked out exactly what
     *  they have left. */
    fun infoRowsIn(size: DpSize, room: Dp, textScale: Float, cap: Int): Int {
        val row = lineHeight(valueSp(size).value, textScale).value + 2f
        return (room.value / row).toInt().coerceIn(0, cap)
    }

    /** Approximate rendered height of one text line at [sp]. RemoteViews
     *  gives no measurement callback, so every vertical estimate in this
     *  file goes through this one factor rather than each inventing its
     *  own. */
    fun lineHeight(sp: Float, textScale: Float): Dp = (sp * textScale * 1.35f).dp

    /**
     * The vertical room actually left for the ring, once the header above
     * it and the buttons (and optional footer) below it have taken theirs.
     *
     * Every tier used to cap the ring at a FRACTION OF THE TILE
     * (height * 0.5 and friends), which silently assumed the rest of the
     * column was small. It isn't at larger text sizes: on a 186x150 tile
     * at 1.4x text the header alone is ~47dp and the buttons ~38dp, so of
     * 129dp of padded budget only ~27dp remained -- while the fraction
     * still authorised a 75dp ring. That is the same mistake
     * [buttonHeight] had, capping against the wrong quantity, and it
     * scales with the text-size option so it got worse exactly where a
     * user had asked for bigger type.
     */
    fun ringRoom(
        frame: Frame,
        hasHeader: Boolean,
        hasFooter: Boolean,
        spacers: Dp,
    ): Dp {
        // All three terms now come from the functions that model what is actually
        // drawn -- innerHeight knows about the pill corner, headerHeight about the
        // switcher pill, footerHeight about the real footer font. Each used to be
        // open-coded here from the size alone, and each was wrong in one case.
        val header = if (hasHeader) headerHeight(frame) else 0.dp
        val footer = if (hasFooter) footerHeight(frame) else 0.dp
        // Floors at zero, NOT at some minimum ring size: when the column
        // genuinely has no room left, forcing a 'small' ring anyway just
        // reinstates the overflow at a smaller scale. Scale.ring turns
        // too little room into no ring at all.
        return (innerHeight(frame) - header - footer - buttonHeight(frame.size) - spacers)
            .coerceAtLeast(0.dp)
    }

    fun titleSp(size: DpSize): TextUnit = lerp(progress(size), 11f, 20f).sp
    fun subtitleSp(size: DpSize): TextUnit = lerp(progress(size), 9f, 13f).sp
    fun valueSp(size: DpSize): TextUnit = lerp(progress(size), 10f, 15f).sp

    /** The root content padding around every tier's layout. */
    fun contentPadding(size: DpSize): Dp = lerp(progress(size), 6f, 18f).dp

    /** An action button's height, capped by the room the padded content
     *  box actually has.
     *
     *  The cap is the whole point: [progress] is driven by the SHORTER
     *  side, so on a short-but-wide strip the ideal height is computed
     *  from a generous width while the height is the thing that has to
     *  hold it. Uncapped that overflowed on 902 sizes across the resize
     *  range -- by 4dp at 98x40dp, where a 32dp button was being asked to
     *  sit in 28dp of room. Same "wants to be" versus "safe to be"
     *  pairing [ring] already uses, just self-capping since every button
     *  lives in that same box. */
    fun buttonHeight(size: DpSize): Dp {
        val ideal = lerp(progress(size), 32f, 48f).dp
        val room = (size.height - contentPadding(size) * 2).coerceAtLeast(16.dp)
        return minOf(ideal, room)
    }

    /**
     * How many buttons stacked VERTICALLY actually fit within [budget], up to
     * [cap] -- so a caller reserving room for "every configured action" does
     * not reserve more than the tile can hold.
     *
     * This exists because reserving a fixed "up to N" without checking that N
     * buttons' worth of height actually fits is the same mistake that cost
     * this file several device-reported overflows already: on a Rail tile
     * near its own 220dp floor, six stacked buttons at this tier's own button
     * height can add up to MORE than the whole padded content box, before
     * the ring or a map has claimed anything. Swept the full tier size range
     * against this exact formula (WidgetScaleTest) rather than trusting it
     * by inspection, the same way every other budget in this file is.
     */
    fun maxStackedButtons(size: DpSize, budget: Dp, overhead: Dp, cap: Int): Int {
        val h = buttonHeight(size)
        val gap = buttonGap(size)
        val n = ((budget - overhead + gap).value / (h + gap).value).toInt()
        return n.coerceIn(0, cap)
    }

    /** The big percentage in the bar treatment -- deliberately larger
     *  than [titleSp], because on a wide tile the number IS the content
     *  and the space is horizontal. */
    fun heroSp(size: DpSize): TextUnit = lerp(progress(size), 22f, 44f).sp

    /** Below this a hero number is no bigger than the ordinary title it
     *  replaced, so the treatment has bought nothing. */
    const val HERO_MIN_SP = 15f

    /**
     * The hero number's size on a tile with [avail] of vertical room, once
     * [reserve] (the bar and its spacer) is set aside.
     *
     * [heroSp] alone is keyed off [progress], i.e. the SHORTER side of the
     * tile -- which on a short-but-wide strip is exactly the side that has
     * to hold the line, so the ideal and the room disagree. Capping here is
     * the same fix applied to [buttonHeight] and [ringRoom]: measure the
     * thing against the quantity that actually constrains it.
     *
     * Null means what's left can't hold a legible number, and BarHero
     * declines rather than shrinking into illegibility -- the same
     * yield-nothing-rather-than-something-wrong contract as [ring].
     */
    fun heroSpIn(size: DpSize, avail: Dp, reserve: Dp, textScale: Float): Float? {
        val ideal = heroSp(size).value * textScale
        val room = (avail - reserve).value / 1.35f
        val chosen = minOf(ideal, room)
        return if (chosen < HERO_MIN_SP) null else chosen
    }

    /** Height of the horizontal charge bar. */
    fun barHeight(size: DpSize): Dp = lerp(progress(size), 8f, 14f).dp

    // RING_WORTH_IT (52.dp) was removed along with ringWide(). It was the
    // threshold below which a wide tile fell back from a ring to a bar -- a
    // decision no tier makes any more, since the wide tiers now take the bar
    // unconditionally. Nothing read it; three comments in CarWidget referred to
    // it, all of them saying the bar is "no longer just a fallback below
    // RING_WORTH_IT", which is exactly why keeping the constant around to be
    // named by its own obituary was worse than deleting it.

    /** Smallest button width worth laying out, scaled by tile size. Small
     *  tiles get a much lower floor so every configured control still fits
     *  across rather than some being dropped -- see ActionButtons. */
    fun minButtonWidth(size: DpSize): Dp = lerp(progress(size), 20f, 44f).dp

    // ---- Action-button capacity ------------------------------------------
    //
    // These four moved out of the ActionButtons composable, which is where the
    // widget's REAL button geometry lived. [maxStackedButtons] above was only ever
    // consulted by some tiers to pick a reservation; the composable then decided
    // independently how many buttons to draw and how tall they were, using its own
    // arithmetic. So the sweep in WidgetScaleTest -- which can only call this file --
    // could not see the numbers that actually shipped.
    //
    // Two of them were also wrong in the same direction: they could exceed the height
    // the calling tier had budgeted, and RemoteViews does not clip an overflowing
    // Column, so the excess bleeds past the tile edge rather than being cut off.

    /** How many buttons fit ACROSS [availableWidth], at this tile's minimum button
     *  width and gap. May be 0 on a very narrow tile -- see [buttonsForced]. */
    fun buttonsAcross(size: DpSize, availableWidth: Dp): Int {
        val gap = buttonGap(size)
        return ((availableWidth + gap).value / (minButtonWidth(size) + gap).value).toInt().coerceAtLeast(0)
    }

    /** How many buttons fit STACKED in [availableHeight]. May be 0 -- see
     *  [buttonsForced]. */
    fun buttonsDown(size: DpSize, availableHeight: Dp): Int {
        val gap = buttonGap(size)
        return ((availableHeight + gap).value / (buttonHeight(size) + gap).value).toInt().coerceAtLeast(0)
    }

    /**
     * The count actually drawn: at least one, even where [capacity] says none fits.
     *
     * Deliberate, and ActionButtons' own comment is the reason -- "a 20dp button on a
     * tile that size is small, but it's a deliberate trade, and still a real target,
     * whereas a missing button can't be pressed." Kept exactly as it was. What changes
     * is that the forced button is no longer allowed to overflow: [rowButtonHeight] and
     * [stackedButtonHeight] clamp to the budget instead.
     */
    fun buttonsForced(capacity: Int, configured: Int): Int =
        minOf(configured, capacity.coerceAtLeast(1))

    /**
     * Height of a single ROW of buttons inside [availableHeight].
     *
     * Was `buttonHeight(size)` alone, which is capped by the whole TILE
     * (`size.height - contentPadding * 2`) and knows nothing about the smaller
     * reservation a tier may have handed over. A tier that budgeted less than the
     * tile's ideal therefore got a taller row than it reserved, and the difference
     * left the tile rather than being clipped.
     */
    fun rowButtonHeight(size: DpSize, availableHeight: Dp): Dp =
        minOf(buttonHeight(size), availableHeight.coerceAtLeast(0.dp))

    /**
     * Height each of [count] STACKED buttons gets inside [availableHeight].
     *
     * The 16dp floor is kept -- below it a button stops being a usable target -- but it
     * can no longer win against the budget. Previously `.coerceAtLeast(16.dp)` was the
     * last operation, so a 10dp reservation produced a 16dp button and 6dp of overflow.
     */
    fun stackedButtonHeight(size: DpSize, availableHeight: Dp, count: Int): Dp {
        if (count <= 0) return 0.dp
        val avail = availableHeight.coerceAtLeast(0.dp)
        val gap = buttonGap(size)
        val each = (avail - gap * (count - 1)) / count
        return minOf(each.coerceAtLeast(16.dp), avail)
    }

    /** Gap between buttons, tightened on small tiles for the same reason:
     *  spacing is the cheapest thing to give up when the choice is between
     *  a gap and showing one more control. */
    fun buttonGap(size: DpSize): Dp = lerp(progress(size), 3f, 8f).dp

    /** Kept proportional to the button that contains it, so a button shrunk
     *  by [buttonHeight]'s cap doesn't end up with an icon wider than itself. */
    fun buttonIcon(size: DpSize): Dp =
        minOf(lerp(progress(size), 16f, 26f).dp, buttonHeight(size) * 0.62f)

    /** The car-switcher [IconPill]'s own size -- was a fixed 36dp/20dp
     *  regardless of tile size, which looked oversized pinned in a small
     *  HeaderRow and undersized in a big one. */
    fun pillSize(size: DpSize): Dp = lerp(progress(size), 26f, 40f).dp
    fun pillIcon(size: DpSize): Dp = lerp(progress(size), 14f, 22f).dp

    /** [MapModule]'s thumbnail height -- was a fixed per-tier constant
     *  (72/80/88/96dp) chosen ad hoc per layout; one continuous curve
     *  keeps it proportioned the same way everything else here is. */
    fun mapHeight(size: DpSize): Dp = lerp(progress(size), 56f, 110f).dp

    /** Smallest map worth drawing. Below this it reads as a stripe of
     *  colour rather than a picture of anywhere, and the modules around it
     *  make better use of the room. */
    val MAP_MIN = 44.dp

    /**
     * The height to set aside for the map before anything else is sized,
     * out of a column with [room] to spend.
     *
     * The map used to be given a weighted slot and left to claim whatever
     * the ring had not already taken -- but the ring's own sizing takes
     * everything it is offered, so on most tiles that was nothing. Capped at
     * a third of the column so the map stays a supporting module rather than
     * displacing the gauge, and zero when a third isn't enough to be a map
     * at all, in which case the caller draws no map and keeps the space.
     */
    fun mapReserve(size: DpSize, room: Dp, wantMap: Boolean): Dp {
        if (!wantMap) return 0.dp
        val want = minOf(mapHeight(size) + 8.dp, room * 0.35f)
        return if (want < MAP_MIN) 0.dp else want
    }

    /** How a tall tier divides its free column. See [tallSplit].
     *
     *  [ringRoom] is what was OFFERED to the ring, which is not the same as
     *  [ring] -- [ringHero] yields 0 below [MIN_RING] rather than drawing a
     *  smudge, so a caller that wants to put something else in that space (a
     *  bar, via ChargeBarFallback) needs to know how much space there was, not
     *  just that the ring declined it. Without this the Tall tiers had no way
     *  to tell "no room for a gauge" apart from "no room for a RING", and drew
     *  no gauge at all in the second case. */
    data class TallSplit(val ring: Dp, val rows: Int, val map: Dp, val ringRoom: Dp)

    /**
     * Splits a tall tier's free column between the hero ring, the info rows
     * and the optional map.
     *
     * The tall tiers used to size the ring from `ringRoom - infoBlockHeight`
     * and then hand the map a weighted slot below it. But [ringHero] takes
     * everything it is offered, so that subtraction left exactly nothing for
     * the weight to claim: turning on the location option produced a map of
     * zero height on every tall tile whose ring was room-bound rather than
     * width-bound, which is most of them. The map has to be reserved BEFORE
     * the ring is sized, not left to compete with it afterwards.
     *
     * The reserve is capped at a third of the column so the map stays a
     * supporting module and the ring stays the hero -- and the row count is
     * then derived from what's actually left rather than from [infoCap]'s
     * fixed fraction of the tile, so the three claims can't sum past the
     * column they share.
     */
    fun tallSplit(
        size: DpSize,
        room: Dp,
        capRows: Int,
        textScale: Float,
        wantMap: Boolean,
    ): TallSplit {
        val map = mapReserve(size, room, wantMap)
        val rest = (room - map).coerceAtLeast(0.dp)
        val rows = infoRowsIn(size, rest * 0.45f, textScale, capRows)
        // Hoisted rather than inlined into the ringHero call so it can be
        // reported back out -- see TallSplit.ringRoom.
        val ringRoom = rest - infoBlockHeight(size, rows, textScale)
        val ring = ringHero(size, ringRoom)
        return TallSplit(ring, rows, map, ringRoom.coerceAtLeast(0.dp))
    }

    // ---- The tall-column reservation, shared by RAIL / COMPACT_TALL_NARROW /
    //      COMPACT_TALL ---------------------------------------------------------
    //
    // These two constants and [tallColumn] below were three near-identical copies of the
    // same arithmetic inside CarWidget's three tall tiers, differing only in four
    // numbers. The constants were `private` there, so WidgetScaleTest duplicated their
    // VALUES with a comment saying so ("Mirrors TALL_TIER_MARGIN in CarWidget.kt --
    // private there") — a silent-drift vector on top of the duplication.

    /** Breathing room kept at the bottom of a tall column so the hero never sits flush
     *  against the tile edge. */
    val TALL_TIER_MARGIN = 16.dp

    /** Floor reserved for the hero before buttons are allowed to claim height, so a tall
     *  tile can't end up as a stack of buttons with no gauge above them. */
    val MIN_HERO_RESERVE = 40.dp

    /** What a tall column decided: how many stacked buttons, the height they claim
     *  including their trailing gap, and what is left for the hero. */
    data class TallColumn(val buttonCount: Int, val buttonZone: Dp, val heroRoom: Dp)

    /**
     * The reservation every tall tier makes: name (if it shows one), then as many stacked
     * buttons as fit while leaving [MIN_HERO_RESERVE], then the rest to the hero.
     *
     * The four values the three tiers genuinely differ on are parameters; everything else
     * was identical, and having it in one place is what lets WidgetScaleTest assert on the
     * numbers the composables actually use instead of re-deriving them. The previous
     * mirror had already gone stale: the test modelled COMPACT_TALL_NARROW as
     * `ringRoom(spacers = 8.dp) - name` plus a single `buttonHeight`, a shape that tier
     * stopped having, so it was asserting against arithmetic no composable ran.
     *
     * @param nameHeight 0 for RAIL, which shows no name at all.
     * @param buttonOverhead what [maxStackedButtons] must keep free — the trailing gap
     *   plus any spacer the tier always emits.
     * @param buttonTrailingGap the gap after the last button, included in [TallColumn.buttonZone].
     */
    fun tallColumn(
        size: DpSize,
        nameHeight: Dp,
        buttonOverhead: Dp,
        buttonTrailingGap: Dp,
        buttonCap: Int,
    ): TallColumn {
        val budget = size.height - contentPadding(size) * 2
        val count = maxStackedButtons(
            size,
            (budget - nameHeight - MIN_HERO_RESERVE).coerceAtLeast(0.dp),
            overhead = buttonOverhead,
            cap = buttonCap,
        )
        val zone = if (count > 0) {
            buttonHeight(size) * count + buttonGap(size) * (count - 1) + buttonTrailingGap
        } else {
            0.dp
        }
        val hero = (budget - nameHeight - zone - TALL_TIER_MARGIN).coerceAtLeast(0.dp)
        return TallColumn(count, zone, hero)
    }

    /** How many [InfoStack] rows to actually show.
     *
     *  The stack shares a Column with the header, ring, buttons and
     *  footer, and unlike a single Text, RemoteViews doesn't clip an
     *  overflowing Column -- it lets the children bleed past the tile's
     *  bottom edge, which reads exactly like the clipping the whole
     *  FitText path exists to prevent. So the count has to come from the
     *  room available rather than being fixed per tier.
     *
     *  Now also keyed off [textScale]: rows get taller as the user's
     *  chosen text size grows, so a count that fit at 1.0x need not fit
     *  at 1.4x. The reserve fraction is an estimate of what the header,
     *  ring, buttons and footer claim -- deliberately generous, since
     *  showing one row fewer is a far smaller cost than spilling off the
     *  tile. */
    fun infoCap(size: DpSize, capMax: Int, textScale: Float): Int {
        // A row is one value line plus its 2dp spacer; ~1.35x font size
        // approximates the line height RemoteViews gives it.
        val rowDp = valueSp(size).value * textScale * 1.35f + 2f
        val room = size.height.value * (1f - 0.62f)
        return (room / rowDp).toInt().coerceIn(1, capMax)
    }

    /**
     * The per-render facts every vertical budget in this file needs, resolved once
     * per composition instead of re-derived by each tier.
     *
     * This exists because the same three questions were being answered separately
     * in every tier, and the copies disagreed:
     *
     *  - **The padded content box.** `size.height - contentPadding(size) * 2`
     *    appeared 11 times, plus three literal approximations of it (`- 16.dp` in
     *    MICRO_TINY, `- 22.dp` in MICRO, `- 12.dp` in COMPACT_WIDE_NARROW), each
     *    correct at only one point on a curve that spans 12dp to 36dp. And not one
     *    of the fourteen knew that a PILL corner adds 4dp of padding per side, so
     *    on a pill widget under 180dp every tier's budget over-reported by 8dp.
     *  - **The header.** Reserved as two text lines, but HeaderRow is a Row whose
     *    height is `max(textColumn, switcherPill)`, and the pill is 26..40dp. At
     *    the 0.8x and 0.9x text settings the pill wins, under-reserving by up to
     *    4.4dp on a multi-car follow widget. [ringRoom] was not even told whether
     *    the switcher was there.
     *  - **The footer.** Reserved from `subtitleSp * textScale`, but FooterRow
     *    hard-coded 11sp for the stale variant, so reserved and rendered came from
     *    two unrelated font sizes.
     *
     * [hasSwitcher] and [pillCorner] are required, not defaulted. A default here
     * would be a wrong answer to a question the caller never noticed it was being
     * asked, which is how the header term came to ignore the pill in the first
     * place.
     */
    data class Frame(
        val size: DpSize,
        val textScale: Float,
        val pillCorner: Boolean,
        val hasSwitcher: Boolean,
    )

    /** Padding the root applies on every side. Mirrors the padding modifier in
     *  CarWidget.Content, the only other place this may be computed. */
    fun rootPadding(size: DpSize, pillCorner: Boolean): Dp =
        contentPadding(size) + if (pillCorner) PILL_EXTRA_PADDING else 0.dp

    /** A pill corner curves hard enough that content clips against it without a
     *  little extra room. Set by Content; reserved here. */
    private val PILL_EXTRA_PADDING = 4.dp

    /** Above this short side a pill corner is drawn as an ordinary round one, and
     *  so takes no extra padding either.
     *
     *  Lives here rather than in CarWidget because both the corner radius Content
     *  draws and the padding every budget subtracts hang off the same threshold,
     *  and Render -- a nested, non-inner class -- cannot reach CarWidget's own
     *  members to share a constant with it. */
    private val PILL_MAX_SHORT_SIDE = 180.dp

    /** Whether a pill-corner CONFIG actually renders as a pill at this size. The
     *  config half of the question stays in CarWidget, which owns WidgetConfig. */
    fun pillAppliesAt(size: DpSize): Boolean =
        minOf(size.width, size.height) < PILL_MAX_SHORT_SIDE

    /** The height actually available inside the root padding: the number every
     *  vertical budget has to fit within. */
    fun innerHeight(frame: Frame): Dp =
        (frame.size.height - rootPadding(frame.size, frame.pillCorner) * 2).coerceAtLeast(0.dp)

    /** [innerHeight]'s horizontal twin, for the tiers that split a row. */
    fun innerWidth(frame: Frame): Dp =
        (frame.size.width - rootPadding(frame.size, frame.pillCorner) * 2).coerceAtLeast(0.dp)

    /** What HeaderRow actually occupies: its two text lines, or the car-switcher
     *  pill beside them if that is taller. A Row is as tall as its tallest child. */
    fun headerHeight(frame: Frame): Dp {
        val text = lineHeight(titleSp(frame.size).value, frame.textScale) +
            lineHeight(subtitleSp(frame.size).value, frame.textScale)
        return if (frame.hasSwitcher) maxOf(text, pillSize(frame.size)) else text
    }

    /** What FooterRow actually occupies: its leading 6dp spacer plus one subtitle
     *  line. Stale and fresh are the same height by construction now -- the stale
     *  variant only changes colour, having previously also hard-coded 11sp. */
    fun footerHeight(frame: Frame): Dp =
        lineHeight(subtitleSp(frame.size).value, frame.textScale) + FOOTER_GAP

    private val FOOTER_GAP = 6.dp

    data class SquareSplit(val ring: Dp, val rows: Int, val map: Dp, val ringRoom: Dp)

    /**
     * [tallSplit] for the square tiers, which never got it.
     *
     * MEDIUM_SQUARE, LARGE_SQUARE and XL_SQUARE each sized their ring from
     * [ringRoom] and then, entirely separately, asked [infoCap] how many info
     * rows to draw. [infoCap] answers from a fraction of the RAW TILE HEIGHT --
     * which is the precise mistake [ringRoom]'s own docstring exists to record,
     * still being made for the row count. So the rows were never subtracted from
     * anything, and ring + rows + header + footer + buttons could sum past the
     * column they share.
     *
     * Swept over the resize range at three text scales, that overflowed 10,422
     * sizes: worst 37.9dp at 240x211 and 1.4x text, where a 42dp ring sat beside
     * an 80dp info block in a 42dp band. RemoteViews does not clip an
     * overflowing Column, so the action buttons simply bleed off the bottom edge.
     * The three tall tiers were immune the whole time, because [tallSplit]
     * derives their rows from the real remainder.
     *
     * [sideBySide] is what makes this a separate function rather than a call to
     * [tallSplit]: above its own width threshold a square tier puts the rows
     * BESIDE the ring (RingWithContent's Row branch) instead of below it. Then
     * the two share one vertical band and neither subtracts from the other --
     * the band just has to hold the taller of them. Stacked, they need the
     * subtraction, plus the 12dp of that branch's own internal spacer.
     */
    fun squareSplit(
        size: DpSize,
        room: Dp,
        capRows: Int,
        textScale: Float,
        wantMap: Boolean,
        sideBySide: Boolean,
    ): SquareSplit {
        // Reserved before the ring is sized, for tallSplit's reason: Scale.ring
        // takes everything offered, so anything left to compete with it afterwards
        // gets nothing.
        val map = mapReserve(size, room, wantMap)
        val rest = (room - map).coerceAtLeast(0.dp)
        if (sideBySide) {
            return SquareSplit(
                ring = ring(size, rest),
                rows = infoRowsIn(size, rest, textScale, capRows),
                map = map,
                ringRoom = rest,
            )
        }
        // Same 0.45 ceiling tallSplit uses, so the rows stay a supporting module
        // and the ring stays the hero on a tile shaped for one.
        val rows = infoRowsIn(size, rest * 0.45f, textScale, capRows)
        val ringRoom =
            (rest - infoBlockHeight(size, rows, textScale) - STACKED_INFO_GAP).coerceAtLeast(0.dp)
        return SquareSplit(ring(size, ringRoom), rows, map, ringRoom)
    }

    /** RingWithContent's own gap between the ring and the rows when it stacks
     *  them -- its `Spacer(height = 8.dp)`, not the `width = 12.dp` one in its
     *  side-by-side branch, which costs width and so does not belong in a
     *  vertical budget. Reserved here because no caller was reserving it. */
    private val STACKED_INFO_GAP = 8.dp

    // --- Horizontal fit ----------------------------------------------------
    //
    // Until now everything above was vertical: this object decided how much
    // room each module got down the tile, while every "will this string fit
    // ACROSS its slot" decision stayed inside CarWidget's own composables,
    // where no test could reach it. That is the same gap that let four
    // vertical budgets ship wrong before they were lifted here.
    //
    // Deliberately expressed in primitives -- a length, an sp value, a bold
    // flag -- rather than in Glance's TextStyle. Two reasons: this file has no
    // Glance dependency and should keep none so the whole model stays a plain
    // JVM unit test, and the arithmetic genuinely does not care about anything
    // else a TextStyle carries. CarWidget keeps thin adapters that unwrap a
    // TextStyle and delegate here.

    /** Average glyph width as a fraction of font size. Bold sets measurably
     *  wider at the same size, and every use of this estimate should err
     *  toward "won't fit" rather than let a title clip, so bold gets its own
     *  wider ratio instead of one average for everything. */
    const val GLYPH_RATIO_BOLD = 0.64f
    const val GLYPH_RATIO_REGULAR = 0.6f

    /** The comfortable floors [fittedSp] won't shrink past: no smaller than
     *  78% of the style's own size (so a shrunk line still reads as the same
     *  typographic step as its neighbours), and never below 9sp outright,
     *  which is about where widget text stops being legible at arm's length. */
    const val MIN_FONT_SCALE = 0.78f
    const val MIN_FONT_SP = 9f

    /** The floor once every better option is exhausted and the only remaining
     *  choice is small-but-whole versus clipped. */
    const val ABSOLUTE_MIN_SP = 5f

    /** How much of the available width [fittedSp] aims to fill. The remainder
     *  is deliberate slack: solving for the size that fills the width EXACTLY
     *  leaves the result on the boundary, where rounding and this estimate's
     *  own imprecision can tip it a hair over and clip it. */
    const val FIT_SLACK = 0.96f

    /** Longest token still worth stacking one character per row. Past this the
     *  column grows taller than the tile, which is just clipping on the other
     *  axis. */
    const val MAX_STACK_CHARS = 6

    fun glyphRatio(bold: Boolean): Float = if (bold) GLYPH_RATIO_BOLD else GLYPH_RATIO_REGULAR

    /** Estimated rendered width, in dp, of [length] characters at [sp].
     *
     *  An estimate by necessity: RemoteViews gives no text-measurement
     *  callback the way Compose's `onTextLayout` does, so nothing here can be
     *  exact. It is used only to pick which rung of the fallback chain to
     *  take, never to lay out pixel-perfect. */
    fun textWidth(length: Int, sp: Float, bold: Boolean): Float = length * sp * glyphRatio(bold)

    fun overflows(length: Int, sp: Float, bold: Boolean, maxWidth: Dp): Boolean =
        textWidth(length, sp, bold) > maxWidth.value

    /** The font size at which [length] characters fit [maxWidth], or null if
     *  even the floor won't fit. Returns [sp] unchanged when the text already
     *  fits, so a caller can treat "same value back" as "no change needed".
     *
     *  The exact inverse of [overflows], sharing [glyphRatio] with it, so the
     *  "does this fit" test and the "what size would fit" solve cannot drift
     *  apart and disagree -- which they could when they were two separate
     *  bodies of arithmetic. [fitsAfterShrink] asserts it, and
     *  WidgetFitModelTest sweeps it.
     *
     *  [relaxed] drops the comfortable floors for [ABSOLUTE_MIN_SP]. */
    fun fittedSp(
        length: Int,
        sp: Float,
        bold: Boolean,
        maxWidth: Dp,
        relaxed: Boolean = false,
    ): Float? {
        if (length <= 0) return null
        val needed = (maxWidth.value * FIT_SLACK) / (length * glyphRatio(bold))
        if (needed >= sp) return sp
        val floor = if (relaxed) ABSOLUTE_MIN_SP else maxOf(sp * MIN_FONT_SCALE, MIN_FONT_SP)
        if (needed < floor) return null
        return needed
    }

    /** Whether [fittedSp]'s answer actually fits -- the property the pair is
     *  supposed to guarantee. Exposed rather than left inside the test so the
     *  claim lives next to the code making it. True when the text won't fit at
     *  any allowed size, since declining to shrink is not a fit failure. */
    fun fitsAfterShrink(
        length: Int,
        sp: Float,
        bold: Boolean,
        maxWidth: Dp,
        relaxed: Boolean = false,
    ): Boolean {
        val fitted = fittedSp(length, sp, bold, maxWidth, relaxed) ?: return true
        return !overflows(length, fitted, bold, maxWidth)
    }
}
