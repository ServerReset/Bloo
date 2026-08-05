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

    /**
     * The ring for a WIDE tier's RingWithContent row -- [ring]'s own 140dp
     * curve ceiling applies there too, and on a big LARGE/XL wide tile that
     * left most of the ring's own row empty above and below a comparatively
     * tiny circle: reported from real devices, a 300dp-tall ring row with a
     * 140dp ring floating in the middle of it.
     *
     * Same "take the room you're given" fix as [ringHero], but bounded by a
     * FRACTION of the tile's width rather than nearly all of it -- here the
     * ring shares its row with a whole second column (header/info/buttons),
     * so it can't claim the width [ringHero] can when it's the only thing on
     * the row. 0.42 leaves the content column the majority side of the
     * split RingWithContent already settles into above its minRowWidth
     * threshold.
     */
    fun ringWide(size: DpSize, maxAvailable: Dp): Dp {
        val room = minOf(maxAvailable, size.width * 0.42f)
        return if (room < MIN_RING) 0.dp else room
    }

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
        size: DpSize,
        textScale: Float,
        hasHeader: Boolean,
        hasFooter: Boolean,
        spacers: Dp,
    ): Dp {
        val budget = size.height - contentPadding(size) * 2
        val header = if (hasHeader) {
            lineHeight(titleSp(size).value, textScale) + lineHeight(subtitleSp(size).value, textScale)
        } else 0.dp
        val footer = if (hasFooter) lineHeight(subtitleSp(size).value, textScale) + 6.dp else 0.dp
        // Floors at zero, NOT at some minimum ring size: when the column
        // genuinely has no room left, forcing a 'small' ring anyway just
        // reinstates the overflow at a smaller scale. Scale.ring turns
        // too little room into no ring at all.
        return (budget - header - footer - buttonHeight(size) - spacers).coerceAtLeast(0.dp)
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

    /** Below this the ring is a token rather than a gauge, and a wide tile
     *  is better served by the bar treatment -- see BarHero. */
    val RING_WORTH_IT = 52.dp

    /** Smallest button width worth laying out, scaled by tile size. Small
     *  tiles get a much lower floor so every configured control still fits
     *  across rather than some being dropped -- see ActionButtons. */
    fun minButtonWidth(size: DpSize): Dp = lerp(progress(size), 20f, 44f).dp

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

    /** How a tall tier divides its free column. See [tallSplit]. */
    data class TallSplit(val ring: Dp, val rows: Int, val map: Dp)

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
        val ring = ringHero(size, rest - infoBlockHeight(size, rows, textScale))
        return TallSplit(ring, rows, map)
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
}
