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
    val isSixSeven = text.filter { it.isDigit() }.toIntOrNull() == 67
    if (!isSixSeven || reduceMotion) {
        // BasicText defaults to TextOverflow.Clip -- a value long enough to
        // exceed maxLines (a long status string routed through AnimatedValue,
        // not just short numeric readouts) hard-clipped instead of trailing
        // off with "...", unlike nearly every other truncating Text in the app.
        BasicText(text, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        return
    }
    val transition = rememberInfiniteTransition(label = "wiggle67")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(620, easing = LinearEasing), RepeatMode.Restart),
        label = "wigglePhase",
    )
    val amplitude = with(LocalDensity.current) { (style.fontSize.value * 0.22f).dp.toPx() }
    Row {
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
