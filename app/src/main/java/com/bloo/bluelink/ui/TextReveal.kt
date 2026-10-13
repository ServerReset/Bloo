package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle

/**
 * The app's standard text entrance: a line is written out WORD BY WORD, each word a beat behind the
 * one before it, so the sentence builds itself the way it would be spoken. Applied in every shared
 * text helper, so all secondary text, captions, labels, headings and pebble titles enter the same way.
 *
 * Off entirely (text simply there) under reduce-motion OR battery saver -- see [textRevealAllowed] --
 * and remembered across a pass so a settled line is never re-animated.
 */

/** Whether the text entrance may play right now: off under reduce-motion OR power saving. */
@Composable
private fun textRevealAllowed(): Boolean =
    !LocalReduceMotion.current && !LocalPowerPolicy.current.batterySaver

/** One word's own beat. Long enough to read as a deliberate build, short enough not to drag. */
private const val MsPerWord = 95

/** Bounds on a whole line's reveal, so a one-word label still eases and a paragraph still finishes. */
private const val MinRevealMs = 220
private const val MaxRevealMs = 1600

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
 * A [Text] that writes itself out word by word. Drop-in for `Text` in the shared helpers; a settled
 * line (or one with the entrance off) is a plain [Text], so there is no per-frame cost once it is
 * done.
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
    val progress by rememberTextReveal(text.wordCount())
    if (progress >= 1f) {
        Text(text, modifier, style = style, color = color, fontWeight = fontWeight, maxLines = maxLines, overflow = overflow, softWrap = softWrap, textAlign = textAlign)
        return
    }
    // Each word's alpha ramps over its own slice of the progress, so the line fills in word by word
    // with a beat between them.
    val words = remember(text) { Regex("\\S+\\s*").findAll(text).map { it.value }.toList().ifEmpty { listOf(text) } }
    val n = words.size
    val base = if (color == Color.Unspecified) LocalContentColor.current else color
    val annotated = buildAnnotatedString {
        words.forEachIndexed { i, w ->
            val a = ((progress * n) - i).coerceIn(0f, 1f)
            withStyle(SpanStyle(color = base.copy(alpha = a))) { append(w) }
        }
    }
    Text(annotated, modifier, style = style, color = Color.Unspecified, fontWeight = fontWeight, maxLines = maxLines, overflow = overflow, softWrap = softWrap, textAlign = textAlign)
}
