package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.delay

/**
 * The app's standard text entrance: a line is revealed WORD BY WORD, each word a beat behind the one
 * before it, so the sentence builds itself. Applied in every shared text helper and the pebble title,
 * so all text enters identically.
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
 * A [Text] that writes itself out word by word, in the draw phase. Drop-in for `Text`; a settled line
 * (or one with the entrance off) is a plain draw, so there is no per-frame cost once it is done.
 */
@Composable
internal fun RevealText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    textAlign: TextAlign? = null,
) {
    val progress = rememberTextReveal(text.wordCount())
    val ranges = remember(text) { wordRanges(text) }
    // Set once by the text layout; read in the draw phase so the reveal never recomposes.
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
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
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        overflow = overflow,
        softWrap = softWrap,
        textAlign = textAlign,
        onTextLayout = { layout = it },
    )
}
