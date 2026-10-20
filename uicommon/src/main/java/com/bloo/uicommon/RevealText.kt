package com.bloo.uicommon

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.launch

/**
 * The app's standard text entrance: a block is revealed LINE BY LINE, each line a beat behind the one
 * above it, so a sentence builds itself top to bottom. Every text in the app draws through here
 * (directly or via the shared helpers), so all text enters identically.
 *
 * The reveal is a clip over the already-laid-out text, in the DRAW phase, so it never rebuilds the
 * string and never recomposes the text -- it costs one clip per frame while it runs and nothing once
 * it settles. That also clips each line's own halo, so a shadow can never appear before its line.
 *
 * Off entirely (text simply there) under reduce-motion OR battery saver, and for text too long to
 * animate cheaply.
 */

/** Whether the text entrance may play right now: off under reduce-motion or power saving. */
@Composable
private fun textRevealAllowed(): Boolean =
    !LocalReduceMotion.current && !LocalPowerPolicy.current.batterySaver

/** How long a changed value takes to fade in. Short: a change is not an entrance. */
private const val TextChangeMs = 180

/** One line's own beat. Short -- the lines should trip out quickly, not crawl. */
private const val MsPerLine = 110

/** Bounds on a block's reveal, so a single line still eases and a long paragraph still finishes. */
private const val MinRevealMs = 160
private const val MaxRevealMs = 900

/**
 * The longest text the entrance will animate. Past this it draws settled instead: a very long block
 * (the activity log is one string of up to five hundred lines) would otherwise spend its whole
 * reveal budget on a flicker nobody asked for.
 */
private const val MaxRevealChars = 320

/**
 * A drop-in replacement for Material3's `Text` (the `String` overload) that writes itself out line by
 * line, in the draw phase. Every parameter of `Text` is supported, so any bare `Text(...)` in the app
 * can -- and does -- become a `RevealText(...)` with no other change.
 *
 * A settled block (or one with the entrance off) is a plain draw, so there is no per-frame cost once
 * it is done. When [text] changes the new value simply appears (no re-reveal): text that genuinely
 * streams or ticks would otherwise flash, and the components that want an ANIMATED update --
 * [RollingNumber] for values, `AnimatedContent` for a swap -- already provide it around their text.
 */
@Composable
fun RevealText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    // Draw settled -- no reveal machinery at all (the layout capture, the draw node, the chained
    // callback) -- when the entrance is off, or the text is too long to animate cheaply. Both cases
    // want it gone entirely.
    if (!textRevealAllowed() || text.length > MaxRevealChars) {
        Text(
            text = text,
            modifier = modifier,
            color = color,
            fontSize = fontSize,
            fontStyle = fontStyle,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            textDecoration = textDecoration,
            textAlign = textAlign,
            lineHeight = lineHeight,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            onTextLayout = onTextLayout,
            style = style,
        )
        return
    }
    // The layout and the reveal's progress are BOTH read only in the draw phase; the reveal is
    // STARTED from the layout callback. Reading the layout in composition to size the reveal by its
    // line count made every text compose twice -- once, then again the moment its layout landed --
    // which across a page of ~150 texts is ~150 extra recompositions, and it lands exactly when a
    // page is composed for a swipe.
    val progress = remember { Animatable(0f) }
    val layoutState = remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val scope = rememberCoroutineScope()
    // Whether the reveal has finished. Kept as a DERIVED state so it flips exactly once, and read in
    // composition only to decide whether the clip node exists at all: once a text has settled, its
    // draw lambda is gone -- and it is not free to keep, because every text's draw lambda otherwise
    // runs (and reads two states) on every frame of a scroll, ~150 of them.
    // --- The change animation ---------------------------------------------------------------------
    // Every text in the app comes through here, so this is the one place a text CHANGE can be animated
    // for all of them, with no call site involved. "Appears" is the line-by-line reveal below; this is
    // "changes": the new value fades in rather than hard-swapping.
    //
    // NOT on the first composition -- that is the reveal's job, and doing both reads as a flicker. And
    // not on the frames of a reveal already in flight, which would fight it.
    //
    // When ONLY a number changed ("78%" to "79%", "4 min ago" to "5 min ago") the words did not, so the block stays put
    // and just the changed digits fade in: [numberFade] drives those, and [changeFade] (the whole block) is left alone.
    val changeFade = remember { Animatable(1f) }
    val numberFade = remember { Animatable(1f) }
    var seenOnce by remember { mutableStateOf(false) }
    var previousText by remember { mutableStateOf(text) }
    var changedFrom by remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        if (!seenOnce) {
            seenOnce = true
            previousText = text
            return@LaunchedEffect
        }
        val before = previousText
        previousText = text
        if (onlyNumbersChanged(before, text)) {
            changedFrom = before
            numberFade.snapTo(0f)
            numberFade.animateTo(1f, tween(TextChangeMs))
        } else {
            changeFade.snapTo(0f)
            changeFade.animateTo(1f, tween(TextChangeMs))
        }
    }
    // The text drawn: plain, or with the digit runs that just changed at the fade's alpha.
    val ink = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    val shown: AnnotatedString = if (numberFade.value < 1f) {
        fadedNumbers(text, changedFrom, ink, numberFade.value)
    } else {
        AnnotatedString(text)
    }
    // On the LAYER, so a frame of the fade costs a re-composite rather than a recomposition -- across
    // every text in the app at once, which is what "universally" has to mean here.
    val motion = Modifier.graphicsLayer { alpha = changeFade.value }
    val settled by remember { derivedStateOf { progress.value >= 1f } }
    // ONE path, reused for the whole reveal: it is rebuilt every frame the clip runs, and a page of
    // texts revealing together was otherwise allocating (and then collecting) a Path per text per
    // frame -- a native allocation each.
    val revealPath = remember { Path() }
    // A rare caller-supplied onTextLayout still runs; the reveal's own capture is chained on top.
    val callerOnTextLayout by rememberUpdatedState(onTextLayout)
    Text(
        text = shown,
        modifier = (if (settled) {
            // Done: the plain modifier, so nothing of the reveal is even a node any more.
            modifier
        } else {
            modifier.drawWithContent {
                val lr = layoutState.value
                val p = progress.value
                if (lr == null || p >= 1f || lr.lineCount <= 1) {
                    drawContent()
                    return@drawWithContent
                }
                // One full-width band per revealed line (the lines not yet due -- and, because the
                // clip covers the halo too, their shadows -- are simply not drawn).
                revealPath.reset()
                val n = lr.lineCount
                var i = 0
                while (i < n && p * n > i) {
                    revealPath.addRect(Rect(0f, lr.getLineTop(i), size.width, lr.getLineBottom(i)))
                    i++
                }
                clipPath(revealPath) { this@drawWithContent.drawContent() }
            }
        }).then(motion),
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = { result ->
            layoutState.value = result
            // Start the reveal here -- the layout phase, where the line count becomes known -- rather
            // than from a composition that read it.
            if (progress.value == 0f && result.lineCount > 0) {
                scope.launch {
                    progress.animateTo(
                        1f,
                        tween(
                            durationMillis = (result.lineCount * MsPerLine).coerceIn(MinRevealMs, MaxRevealMs),
                            easing = LinearEasing,
                        ),
                    )
                }
            }
            callerOnTextLayout?.invoke(result)
        },
        style = style,
    )
}

/** [text] with every digit run replaced by a placeholder: two texts with equal skeletons differ only in their numbers. */
private fun skeleton(text: String): String = text.replace(Regex("[0-9]+"), "#")

/** Whether [after] differs from [before] in its numbers alone (same words, same layout of digit runs). */
internal fun onlyNumbersChanged(before: String, after: String): Boolean =
    before != after && after.any { it.isDigit() } && skeleton(before) == skeleton(after)

/**
 * [text] with the digit runs that differ from [previous] drawn at [alpha], so only the number that changed animates
 * and the words around it stay still. Runs are matched in order (the skeletons are equal, so there are as many).
 */
private fun fadedNumbers(text: String, previous: String, ink: Color, alpha: Float): AnnotatedString {
    val runs = Regex("[0-9]+")
    val old = runs.findAll(previous).map { it.value }.toList()
    val faded = SpanStyle(color = ink.copy(alpha = ink.alpha * alpha))
    return androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        runs.findAll(text).forEachIndexed { i, m ->
            if (old.getOrNull(i) != m.value) addStyle(faded, m.range.first, m.range.last + 1)
        }
    }
}
