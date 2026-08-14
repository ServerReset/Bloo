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

/** Numbers that trigger [WiggleText]'s travelling-wave bounce -- see that
 *  function's own doc for why the list stays this short. */
private val WIGGLE_NUMBERS = listOf("67", "42")

/** Trailing unit characters [WiggleText] tolerates after a wiggle number
 *  ("67°", "67F", "42mi" -- degrees, and the one-letter temperature units). */
private val UNIT_SUFFIXES = charArrayOf('F', 'C', '°')

/**
 * Renders [text] normally — but when the displayed number is one of
 * [WIGGLE_NUMBERS], the digits bounce up and down in a travelling wave.
 * Callers must resolve [Color.Unspecified] and merge fontWeight into [style]
 * before calling, so this function always receives a fully-specified style.
 *
 * [reduceMotion] has no default, deliberately — see [AnimatedValue], where the
 * default this used to have cost three call sites.
 */
@Composable
fun WiggleText(
    text: String,
    style: TextStyle,
    maxLines: Int = 1,
    reduceMotion: Boolean,
) {
    // Fires only when the trimmed text is exactly one of WIGGLE_NUMBERS, optionally
    // followed by a single trailing unit character ("67", "67°", "67F", "42mi").
    // Matching the whole string this way -- rather than filtering digits out and
    // parsing what's left -- means multi-number strings like "6-7", "6 7" or "1670"
    // never collapse to "67" and falsely trigger the wave.
    //
    // 42 joined 67 for the same reason 67 was here alone: a number that means
    // something to whoever's holding the phone, waiting for a bounce that has
    // nothing to do with the car underneath it. Kept short and universal on
    // purpose -- this isn't the place for an ever-growing list of in-jokes, just
    // the rare few that are genuinely widely recognized.
    val trimmed = text.trim()
    val wiggles = WIGGLE_NUMBERS.any { n ->
        trimmed == n || (trimmed.length == n.length + 1 && trimmed.startsWith(n) && trimmed.last() in UNIT_SUFFIXES)
    }
    if (!wiggles || reduceMotion) {
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
    val transition = rememberInfiniteTransition(label = "wiggleFunNumber")
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
 * rendering so its fun-number bounce works inside the transition.
 *
 * [reduceMotion] has no default, deliberately. It used to default to false, and
 * three of the four live call sites took that default: the phone's [StatusRow] --
 * i.e. nearly every status value the phone displays -- the phone's set-temperature
 * readout, and the watch's own ring gauge label. All three animated regardless of
 * whether the user had turned animations off in system accessibility settings,
 * while the fourth, the watch's own [AnimatedValue] wrapper, honoured it correctly.
 * Both surfaces already publish the setting as a composition local, so there was
 * nothing to plumb -- only a default quietly answering a question on behalf of
 * callers who had never been asked it.
 *
 * So callers must now name it. This is the convention [AnimatedSlider] already
 * follows for its own `reduceMotion`, which is very likely why both of ITS call
 * sites pass it and none of these did.
 */
@Composable
fun AnimatedValue(
    value: String,
    style: TextStyle,
    maxLines: Int = 1,
    reduceMotion: Boolean,
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
