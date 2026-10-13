package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/**
 * The app's standard text entrance: a line is revealed WORD BY WORD, each word a beat behind the one
 * before it, so the sentence builds itself. Every text in the app draws through here (directly or via
 * the shared helpers), so all text enters identically.
 *
 * The reveal is done in the DRAW phase (a clip over the already-laid-out words), NOT by rebuilding the
 * string -- so it costs one cheap clip per frame and never recomposes the text. That also clips the
 * word's own halo, so a shadow can never appear before its word.
 *
 * Off entirely (text simply there) under reduce-motion OR battery saver.
 */

/** Whether the text entrance may play right now: off under reduce-motion or power saving. */
@Composable
private fun textRevealAllowed(): Boolean =
    !LocalReduceMotion.current && !LocalPowerPolicy.current.batterySaver

/** One word's own beat. Short -- the words should trip out quickly, not crawl. */
private const val MsPerWord = 55

/** Bounds on a whole line's reveal, so a one-word label still eases and a paragraph still finishes. */
private const val MinRevealMs = 140
private const val MaxRevealMs = 1200

/**
 * The longest text the entrance will animate. Past this it draws settled instead.
 *
 * The reveal builds a clip rectangle per revealed word every frame by asking the text layout for
 * each character's box -- cost that grows with the text. That is nothing for a label, but a long
 * blob (the activity log is one string of up to five hundred lines) turned into thousands of boxes
 * per frame, which is exactly the stutter from scrolling Settings over the Debug card: the card was
 * composing mid-scroll and the whole log was trying to write itself out word by word.
 */
private const val MaxRevealChars = 320

/** A rough word count for sizing the reveal, without allocating the list. */
private fun String.wordCount(): Int {
    var n = 0
    var inWord = false
    for (c in this) {
        val ws = c.isWhitespace()
        if (!ws && !inWord) n++
        inWord = !ws
    }
    return n.coerceAtLeast(1)
}

/** Character ranges, one per word (each keeping the whitespace that follows it). */
private fun wordRanges(text: String): List<IntRange> {
    val out = ArrayList<IntRange>()
    var i = 0
    while (i < text.length) {
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length) break
        val start = i
        while (i < text.length && !text[i].isWhitespace()) i++
        while (i < text.length && text[i].isWhitespace()) i++
        out += start until i
    }
    return out
}

/**
 * The shared progress for one line's entrance, 0..1, with a duration scaled to [wordCount] so a long
 * sentence builds at a steady per-word pace. Returns 1 immediately (nothing to animate) when the
 * entrance is off.
 */
@Composable
internal fun rememberTextReveal(wordCount: Int): State<Float> {
    val allowed = textRevealAllowed()
    val progress = remember { Animatable(if (allowed) 0f else 1f) }
    LaunchedEffect(wordCount, allowed) {
        if (!allowed || progress.value >= 1f) return@LaunchedEffect
        progress.animateTo(
            1f,
            tween(
                durationMillis = (wordCount * MsPerWord).coerceIn(MinRevealMs, MaxRevealMs),
                easing = LinearEasing,
            ),
        )
    }
    return progress.asState()
}

/**
 * A drop-in replacement for Material3's `Text` (the `String` overload) that writes itself out word by
 * word, in the draw phase. Every parameter of `Text` is supported, so any bare `Text(...)` in the app
 * can -- and does -- become a `RevealText(...)` with no other change.
 *
 * A settled line (or one with the entrance off) is a plain draw, so there is no per-frame cost once
 * it is done. When [text] changes the new value simply appears (no re-reveal): text that genuinely
 * streams or ticks would otherwise flash, and the components that want an ANIMATED update --
 * [RollingNumber] for values, `AnimatedContent` for a swap -- already provide it around their text.
 */
@Composable
internal fun RevealText(
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
    // Draw settled -- no reveal machinery at all (word ranges, layout capture, the draw node, the
    // chained callback) -- when the entrance is off, or the text is too long to animate cheaply.
    // This is the one place the reveal costs anything, and both cases want it gone entirely.
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
    val progress = rememberTextReveal(text.wordCount())
    val ranges = remember(text) { wordRanges(text) }
    // Set once by the text layout; read in the draw phase so the reveal never recomposes.
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    // A rare caller-supplied onTextLayout still runs; the reveal's own capture is chained on top.
    val callerOnTextLayout by rememberUpdatedState(onTextLayout)
    Text(
        text = text,
        modifier = modifier.drawWithContent {
            val p = progress.value
            val lr = layout
            if (p >= 1f || lr == null || ranges.isEmpty()) {
                drawContent()
                return@drawWithContent
            }
            // One rectangle per revealed word (the union of its characters' boxes), clipped together:
            // the words not yet due -- and, because the clip covers the halo too, their shadows -- are
            // simply not drawn.
            val path = Path()
            ranges.forEachIndexed { i, r ->
                if (p * ranges.size <= i) return@forEachIndexed
                var rect: Rect? = null
                for (c in r) {
                    val b = lr.getBoundingBox(c)
                    rect = rect?.let {
                        Rect(
                            minOf(it.left, b.left),
                            minOf(it.top, b.top),
                            maxOf(it.right, b.right),
                            maxOf(it.bottom, b.bottom),
                        )
                    } ?: b
                }
                rect?.let { path.addRect(it) }
            }
            clipPath(path) { this@drawWithContent.drawContent() }
        },
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
            layout = result
            callerOnTextLayout?.invoke(result)
        },
        style = style,
    )
}
