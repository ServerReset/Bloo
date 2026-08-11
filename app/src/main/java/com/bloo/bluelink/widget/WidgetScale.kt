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
 * hand-copied model of them, which is the whole reason for the move.
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

    // ringWide() was removed. It bounded a WIDE tier's ring to 0.42 of the tile
    // width, which fixed a real device report (a 300dp-tall ring row with a 140dp
    // ring floating in the middle of it) -- but the wide tiers then moved to the
    // BAR treatment outright (BarHero/ChargeBar running the full width under the
    // header) rather than a ring in a side column, and nothing called it again.
    //
    // Deleted rather than left in place because it was the misleading kind of dead
    // code: a considered 14-line docstring presenting itself as the live answer to
    // "how does a wide tile size its ring", for a question the code no longer
    // asks. Ring-vs-bar is now decided by WidgetBlueprint, from the height the
    // hero band actually won rather than from which tier a size fell into.

    /** How many info rows actually fit in [room], for
     *  layouts that have already worked out exactly what they have left. (The old
     *  fraction-of-tile estimator this contrasted with, `infoCap`, was deleted -- see
     *  its tombstone below; every caller now derives [room] from a real remainder.) */
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
    // widget's REAL button geometry lived. A tier's own reservation helper was only
    // ever consulted to pick a budget; the composable then decided
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
        // NO floor. A 16.dp floor here was applied after the gaps were subtracted, so
        // when the budget could not hold the stack it returned a height whose own
        // total -- count heights plus count-1 gaps -- came to MORE than avail, and
        // RemoteViews does not clip a Column: the last button drew past the bottom of
        // the tile. A floor that overflows is not a floor, it is the overflow bug in
        // miniature.
        //
        // Keeping a button tappable is still the right instinct, but it belongs where
        // the count is chosen: WidgetBlueprint gives the band its own minimum and
        // DROPS the module rather than squeezing it, so a stack that reaches here has
        // already been judged to fit.
        return each.coerceAtLeast(0.dp)
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

    // ---- The tall-column reservation, shared by RAIL / COMPACT_TALL_NARROW /
    //      COMPACT_TALL ---------------------------------------------------------
    //
    // These constants and the column reservation below were three near-identical copies of the
    // same arithmetic inside CarWidget's three tall tiers, differing only in four
    // numbers. The constants were `private` there, so WidgetScaleTest duplicated their
    // VALUES with a comment saying so ("Mirrors TALL_TIER_MARGIN in CarWidget.kt --
    // private there") — a silent-drift vector on top of the duplication.

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
    // infoCap() was deleted here. It answered "how many info rows fit?" from a flat fraction of
    // the RAW tile height -- `size.height * 0.38` -- subtracted from nothing. It knew nothing
    // about the header, the gauge, the button row or the map reserve on the tile it was asked
    // about, so it was wrong in both directions: tighter than the truth on tiles that had room
    // (rows silently dropped, the tile showing less than it could) and looser on tiles that did
    // not (rows pushing the button row off the bottom).
    //
    // Every caller now derives the row count from what is ACTUALLY left -- infoRowsIn() over a
    // remainder built from innerHeight minus the things that really occupy it, or a plain per-tier
    // maximum where a split already computed the remainder. Nothing calls this any more.
    //
    // Deleted rather than left available, because it reads like the obvious helper for exactly the
    // question it answers badly, and this file's history is largely tiers that trusted it.

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
