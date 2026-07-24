package com.bloo.uicommon

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Renders [text] normally — but when the displayed number is exactly 67, the
 * digits bounce up and down in a travelling wave. Callers must resolve
 * [Color.Unspecified] and merge fontWeight into [style] before calling, so this
 * function always receives a fully-specified style.
 */
@Composable
fun WiggleText(
    text: String,
    style: TextStyle,
    maxLines: Int = 1,
    reduceMotion: Boolean = false,
) {
    // Fires only when the trimmed text is exactly "67", optionally followed by a
    // single trailing unit character ("67", "67°", "67F", "67C"). Matching the
    // whole string this way -- rather than filtering digits out and parsing what's
    // left -- means multi-number strings like "6-7", "6 7" or "1670" never collapse
    // to "67" and falsely trigger the wave.
    val trimmed = text.trim()
    val isSixSeven = trimmed == "67" ||
        (trimmed.length == 3 && trimmed.startsWith("67") && trimmed.last() in charArrayOf('F', 'C', '°'))
    if (!isSixSeven || reduceMotion) {
        // BasicText defaults to TextOverflow.Clip -- a value long enough to
        // exceed maxLines (a long status string routed through AnimatedValue,
        // not just short numeric readouts) hard-clipped instead of trailing
        // off with "...", unlike nearly every other truncating Text in the app.
        BasicText(text, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        return
    }
    // A continuously-looping "phase" value that sweeps 0 -> 2π every 620ms and then
    // restarts (RepeatMode.Restart, so it snaps back to 0 rather than reversing) --
    // effectively a free-running clock in radians driving the sine wave below.
    // LinearEasing keeps the sweep rate constant so the wave travels smoothly
    // rather than speeding up/slowing down.
    val transition = rememberInfiniteTransition(label = "wiggle67")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(620, easing = LinearEasing), RepeatMode.Restart),
        label = "wigglePhase",
    )
    // How far up/down (in px) each character travels at the peak of its bounce,
    // scaled relative to the text's own font size so the wiggle looks proportional
    // at any text size rather than a fixed pixel amount.
    val amplitude = with(LocalDensity.current) { (style.fontSize.value * 0.22f).dp.toPx() }
    Row {
        // Each character is its own BasicText with its own graphicsLayer offset, so
        // they can each be displaced independently to form a travelling wave: adding
        // `i * 1.1f` to the shared phase before taking sin() gives every subsequent
        // character a slightly later point in the same sine cycle, so the bounce
        // ripples left-to-right across the digits rather than every digit bobbing
        // perfectly in sync.
        text.forEachIndexed { i, ch ->
            BasicText(
                ch.toString(),
                style = style,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { translationY = sin(phase + i * 1.1f) * amplitude },
            )
        }
    }
}

/**
 * Animates value changes with a fade + vertical slide, using [WiggleText] for
 * rendering so the "67 bounce" works inside the transition.
 */
@Composable
fun AnimatedValue(
    value: String,
    style: TextStyle,
    maxLines: Int = 1,
    reduceMotion: Boolean = false,
    // Needed for callers that must size this within a Row/Column layout (e.g.
    // a label/value row using Modifier.weight on the value cell) -- without
    // this, AnimatedContent's own layout node had no way to receive that
    // modifier, since it's the top-level thing this function emits.
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            if (reduceMotion) fadeIn(tween(1)) togetherWith fadeOut(tween(1))
            else (fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 3 }) togetherWith
                (fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 3 })
        },
        label = "animVal",
    ) { v -> WiggleText(v, style = style, maxLines = maxLines, reduceMotion = reduceMotion) }
}
