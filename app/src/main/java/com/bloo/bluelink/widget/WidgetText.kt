package com.bloo.bluelink.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxWidth
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/**
 * The widget's shared "never cut off" text-fitting chain, split out of
 * CarWidget.kt on its own: every function here is a pure adapter over
 * [Scale]'s fit model plus the couple of Glance-only fallbacks ([FitLine]'s
 * shrink and [VerticalText]'s last-resort stack) built on top of it, none of
 * them touching a single bit of CarWidget's own state. That made them the
 * first, safest slice to pull into a file of their own -- and, as a direct
 * consequence of no longer being private members of a GlanceAppWidget
 * subclass, the first part of the widget's render layer [WidgetTextTest] can
 * exercise directly instead of only indirectly through a composable's output.
 */

/** Whether [style]'s weight counts as bold for width-estimation purposes.
 *  The single place the Glance TextStyle is reduced to the plain flag
 *  [Scale]'s fit model takes. */
internal fun isBold(style: TextStyle): Boolean = style.fontWeight == FontWeight.Bold

/** The size [Scale] should measure this style at. RemoteViews renders a
 *  null fontSize at its own default, which this estimate has to guess at;
 *  12sp matches the smallest style actually declared in CarWidget.kt, so
 *  guessing wrong errs toward "won't fit" rather than toward clipping. */
internal fun styleSp(style: TextStyle): Float = style.fontSize?.value ?: 12f

/** Whether [text] in [style] would overflow [maxWidth]. Thin adapter over
 *  [Scale.overflows] -- see there for why the estimate is approximate and
 *  why it lives in Scale now. */
internal fun wouldOverflow(text: String, style: TextStyle, maxWidth: Dp): Boolean =
    Scale.overflows(text.length, styleSp(style), isBold(style), maxWidth)

/** Breaks [text] across lines at word boundaries (never mid-word), so a
 *  name like "Lana's Whip Deluxe" reads as real words per line rather
 *  than being dumped one letter per row.
 *
 *  Always returns at least one line and never gives up: a word too wide
 *  to fit becomes its own line and is handed to [FitLine], which shrinks
 *  or stacks just that word. This used to bail out entirely if ANY single
 *  word was too wide, which meant one long word dragged the whole string
 *  down to letter-stacking -- "Locked · Charging · Climate on" became a
 *  25-row column in a narrow slot purely because "Charging" alone didn't
 *  fit, even though every other word wrapped fine. */
internal fun wordWrap(text: String, style: TextStyle, maxWidth: Dp): List<String> {
    val words = text.split(" ").filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    val lines = mutableListOf<String>()
    var line = words.first()
    for (word in words.drop(1)) {
        val candidate = "$line $word"
        if (wouldOverflow(candidate, style, maxWidth)) {
            lines += line
            line = word
        } else {
            line = candidate
        }
    }
    lines += line
    return lines
}

// MIN_FONT_SCALE, MIN_FONT_SP, ABSOLUTE_MIN_SP, FIT_SLACK and
// MAX_STACK_CHARS moved to Scale, which is where the arithmetic using them
// now lives. MAX_STACK_CHARS is still read below, as Scale.MAX_STACK_CHARS.

/** [FitText]'s second-choice fallback, for a single token that [wordWrap]
 *  can't help with (no spaces to break on): shrink the type just enough
 *  that it fits [maxWidth] on one ordinary line. Preferred over
 *  [VerticalText] because a slightly smaller word still reads as a word,
 *  where a stack of single letters reads as a puzzle -- and because
 *  stacking grows downward without bound, which is its own overflow.
 *
 *  [relaxed] drops the comfortable floors, used only once stacking has
 *  been ruled out too. Returns null when even that won't fit.
 *
 *  The solve itself is [Scale.fittedSp]; all this adds is the TextStyle
 *  round trip. Returning [style] unchanged rather than a copy when nothing
 *  needs to shrink keeps the identity Glance can skip recomposing on. */
internal fun shrunkToFit(
    text: String, style: TextStyle, maxWidth: Dp, relaxed: Boolean = false,
): TextStyle? {
    val sp = style.fontSize?.value ?: return null
    val fitted = Scale.fittedSp(text.length, sp, isBold(style), maxWidth, relaxed) ?: return null
    return if (fitted >= sp) style else style.copy(fontSize = fitted.sp)
}

/** The shared "never cut off" contract every user-data label, value and
 *  name in the widget goes through, generalized from what used to be a
 *  one-off fallback just for [WidgetInfoField.PERCENT].
 *
 *  Two stages. First [wordWrap] breaks the string into lines at word
 *  boundaries; then every resulting line independently goes through
 *  [FitLine], which owns the per-line fallbacks. Splitting it this way
 *  is what lets a single over-wide word shrink on its own without
 *  dragging the rest of the string down with it.
 *
 *  Every stage keeps the whole string on screen -- what changes is only
 *  how readable the result is, so the chain always takes the
 *  least-damaging option that actually fits. */
@Composable
internal fun FitText(
    text: String,
    style: TextStyle,
    maxWidth: Dp,
    modifier: GlanceModifier = GlanceModifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    // Cap on wrapped lines. Default (unbounded) keeps the free-wrap behaviour every
    // header/footer/subtitle relies on. `singleLine = true` (maxLines 1) is for a slot
    // whose HEIGHT was reserved for exactly one line -- the compact tiers reserve
    // `nameHeight` as a single Scale.lineHeight, so a name allowed to wrap to two lines
    // there would bleed past the reserved column (RemoteViews doesn't clip). With it set,
    // FitText skips wordWrap entirely and goes straight to FitLine, which shrinks the type
    // to fit one line rather than wrapping -- the fit that matches the reservation.
    singleLine: Boolean = false,
) {
    if (text.isBlank()) return
    if (!wouldOverflow(text, style, maxWidth)) {
        Text(text, style = style, maxLines = 1, modifier = modifier)
        return
    }
    if (singleLine) {
        FitLine(text, style, maxWidth, modifier, horizontalAlignment)
        return
    }
    val lines = wordWrap(text, style, maxWidth)
    if (lines.size <= 1) {
        FitLine(lines.firstOrNull() ?: text, style, maxWidth, modifier, horizontalAlignment)
        return
    }
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        lines.forEach { line ->
            FitLine(line, style, maxWidth, GlanceModifier, horizontalAlignment)
        }
    }
}

/** Renders ONE already-wrapped line, which by definition has no word
 *  break left to exploit, so the remaining options are all about size:
 *
 *  1. it fits [maxWidth] as-is, so render it;
 *  2. [shrunkToFit] shrinks the type just enough, staying legible;
 *  3. for a token short enough to stay a few rows tall, [VerticalText]
 *     stacks it one character per row;
 *  4. otherwise shrink past the comfortable floor, because a long stack
 *     would overflow the tile's height and that is just clipping again
 *     on the other axis. */
@Composable
internal fun FitLine(
    text: String,
    style: TextStyle,
    maxWidth: Dp,
    modifier: GlanceModifier,
    horizontalAlignment: Alignment.Horizontal,
) {
    if (!wouldOverflow(text, style, maxWidth)) {
        Text(text, style = style, maxLines = 1, modifier = modifier)
        return
    }
    val shrunk = shrunkToFit(text, style, maxWidth)
    if (shrunk != null) {
        Text(text, style = shrunk, maxLines = 1, modifier = modifier)
        return
    }
    if (text.length > Scale.MAX_STACK_CHARS) {
        // Text too small to read is recoverable by resizing the widget;
        // text cut off the edge is not.
        val forced = shrunkToFit(text, style, maxWidth, relaxed = true)
        if (forced != null) {
            Text(text, style = forced, maxLines = 1, modifier = modifier)
            return
        }
    }
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        VerticalText(text, style)
    }
}

/** Renders [text] one character per line, centered -- the LAST-resort
 *  fallback for a SHORT unsplittable token (a percent, mainly) that's
 *  still too wide for its line even on its own, e.g. "82%" becoming
 *  "8" / "2" / "%".
 *
 *  Deliberately last: stacking trades horizontal overflow for vertical
 *  extent, and a long token stacked this way (a 16-character name with
 *  no spaces) is 16 rows tall, which overflows a small tile's HEIGHT
 *  just as badly as the clipping it was avoiding. [FitText] therefore
 *  tries [wordWrap] and then [shrunkToFit] first, both of which keep
 *  text on ordinary horizontal lines, and only lands here for tokens
 *  short enough that the resulting stack stays small. */
@Composable
internal fun VerticalText(text: String, style: TextStyle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = GlanceModifier.fillMaxWidth()) {
        text.forEach { ch -> if (!ch.isWhitespace()) Text(ch.toString(), maxLines = 1, style = style) }
    }
}
