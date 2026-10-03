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
import androidx.compose.ui.Alignment
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
 * Animates value changes with the app's rolling-number language, using
 * [WiggleText] for rendering so its fun-number bounce works inside the
 * transition.
 *
 * THE standard animation for any value that can change: every caller that
 * shows a refreshed quantity routes through this, directly (phone `StatusRow`,
 * the climate setpoint readout, the watch's ring-gauge label) or through the
 * phone's big-headline `RollingNumber`, which is a thin alias of this one.
 *
 * Two behaviours, chosen per value so the animation never breaks layout:
 *
 * COMPACT (single line, short, contains digits): split into runs of digits and
 * static text ([RollingTokenRow]). Each digit run rolls VERTICALLY in the
 * direction the number moved -- up when it grew, down when it shrank -- and
 * static runs crossfade separately: "84%" becomes "85%" with the digits
 * sliding between the unmoving "%"; "4 min ago" becomes "12 min ago" with the
 * 4 rolling up and the caption fading. Rolling the whole string instead read
 * as the entire readout lifting off.
 *
 * EVERYTHING ELSE (long, multi-line, or digit-free): a fade + vertical slide
 * of the whole string, the right shape for a value that changed wholesale (a
 * locked state, a VIN, a plate) and the path that keeps two-line wrapping
 * working -- the tokenized path is a Row, which does not wrap.
 *
 * [reduceMotion] has no default, deliberately. It used to default to false, and
 * three of the four live call sites took that default: the phone's [StatusRow] --
 * i.e. nearly every status value the phone displays -- the phone's set-temperature
 * readout, and the watch's own ring gauge label. All three animated regardless of
 * whether the user had turned animations off in system accessibility settings,
 * while the fourth, the watch's own [AnimatedValue] wrapper, honoured it correctly.
 * Callers already publish the setting as a composition local, so there was
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
    if (reduceMotion || maxLines > 1 || value.length > RollTokenMaxChars ||
        !value.any { it.isDigit() } || value.contains('\n')
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
        return
    }
    RollingTokenRow(value, style, modifier)
}

/** Longest value still eligible for the tokenized digit-roll path. */
private const val RollTokenMaxChars = 24

/** Roll timings, local to :uicommon on purpose (it cannot reach :app's tokens;
 *  these match :app's MotionShort-side values and :app's own digit roll). */
private const val RollFadeIn = 180
private const val RollFadeOut = 150

/**
 * One token of a to-be-rolled value: a digit run (rolls vertically,
 * directionally) or a static run (crossfades). See [RollingTokenRow].
 */
internal data class RollToken(val digits: Boolean, val text: String)

/** Splits [value] into digit runs and static runs, in order. */
internal fun rollingTokens(value: String): List<RollToken> {
    val tokens = ArrayList<RollToken>()
    val sb = StringBuilder()
    for (c in value) {
        if (sb.isNotEmpty() && c.isDigit() != sb[0].isDigit()) {
            tokens.add(RollToken(sb[0].isDigit(), sb.toString()))
            sb.clear()
        }
        sb.append(c)
    }
    if (sb.isNotEmpty()) tokens.add(RollToken(sb[0].isDigit(), sb.toString()))
    return tokens
}

/**
 * The tokenized roll itself: one [AnimatedContent] per token, digit runs
 * sliding directionally, static runs fading. Baseline alignment keeps the
 * seams between tokens invisible across different heights, and 0dp spacing
 * keeps the rendered value letter-for-letter identical to a plain Text --
 * parity that matters most for a component this widely applied.
 */
@Composable
private fun RollingTokenRow(
    value: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    // Direction is derived from AnimatedContent's own initialState/targetState
    // per digit run, the same way :app's original roll did (and NOT from a
    // separately-tracked "previous value"): the derived direction can never lag
    // the actual change. token * static-token runs never reach this; only
    // digit runs, which toLongOrNull resolves.
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        for (t in rollingTokens(value)) {
            AnimatedContent(
                targetState = t.text,
                transitionSpec = {
                    if (t.digits) {
                        val dir =
                            if ((targetState.toLongOrNull() ?: 0L) >= (initialState.toLongOrNull() ?: 0L)) 1 else -1
                        (fadeIn(tween(RollFadeIn)) + slideInVertically(tween(RollFadeIn)) { dir * it / 2 }) togetherWith
                            (fadeOut(tween(RollFadeOut)) + slideOutVertically(tween(RollFadeOut)) { -dir * it / 2 })
                    } else {
                        fadeIn(tween(RollFadeIn)) togetherWith fadeOut(tween(RollFadeOut))
                    }
                },
                label = if (t.digits) "rollDigit" else "rollStatic",
            ) { s -> WiggleText(s, style = style, maxLines = 1, reduceMotion = false) }
        }
    }
}

