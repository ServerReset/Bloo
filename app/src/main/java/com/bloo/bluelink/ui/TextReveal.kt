package com.bloo.bluelink.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlinx.coroutines.delay

/**
 * The app's standard text entrance, applied in [secondaryTextStyle]'s consumers (every secondary
 * line, caption, label and section heading) so ALL text enters the same way everywhere.
 *
 * The reveal is left to right: the line is written out letter by letter, played once each time the
 * text composes (so a pebble's text is re-written every time it opens, and every fresh result is
 * written in rather than appearing all at once). Pass a [staggerMs] to hold a block back so a run of
 * line-by-line text arrives sentence by sentence, each on the heels of the last.
 *
 * Reduce-motion (and, via it, battery saver) skips the reveal entirely -- the text is simply there.
 */
@Composable
internal fun rememberTextReveal(staggerMs: Int = 0): State<Float> {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce || progress.value >= 1f) return@LaunchedEffect
        if (staggerMs > 0) delay(staggerMs.toLong())
        progress.animateTo(1f, tween(durationMillis = TextRevealMs, easing = LinearEasing))
    }
    return progress.asState()
}

/** How long one line takes to write itself out, left to right. */
private const val TextRevealMs = 340

/**
 * Clips the content to a left-to-right reveal of [fraction] (0 = nothing, 1 = the whole line). Read
 * in the draw phase, so the reveal never recomposes the text it is writing out.
 */
internal fun Modifier.textReveal(fraction: () -> Float): Modifier = drawWithContent {
    val f = fraction().coerceIn(0f, 1f)
    if (f >= 1f) {
        drawContent()
    } else {
        clipRect(right = size.width * f) { this@drawWithContent.drawContent() }
    }
}
